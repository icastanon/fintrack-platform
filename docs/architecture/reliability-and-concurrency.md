# Reliability and Concurrency

This document explains how FinTrack protects financial data while work moves across HTTP requests, PostgreSQL transactions, the transactional outbox, Amazon SQS, and Spring Batch.

It is intended for contributors changing transactions, balances, imports, messaging, retries, idempotency, locks, leases, refresh tokens, or failure recovery. For the broader component and data-flow model, begin with the [architecture overview](overview.md).

## Reliability model

FinTrack does not assume exactly-once execution.

The system expects:

- an HTTP client may retry a request;
- an outbox publisher may crash after sending a message but before recording success;
- Amazon SQS may deliver a message more than once;
- more than one worker may attempt related work concurrently;
- a worker may stop during a long-running import;
- an external S3 or SQS operation may succeed while the following database operation fails;
- supported CSV rows may be committed while other rows are rejected.

Reliability therefore comes from combining explicit database transactions, stable identities, idempotent consumers, database constraints, bounded retries, dead-letter queues, locks, processing leases, fencing tokens, and observable terminal states.

PostgreSQL remains the authoritative source of financial and workflow state. Redis, SQS, S3, and Spring Batch support specialized behavior but do not replace the database as the source of truth.

## Consistency boundaries

| Operation | Consistency boundary |
|---|---|
| Manual transaction creation | The transaction, account-balance change, and outbox event commit or roll back together in PostgreSQL. |
| Import submission | The source file is uploaded to S3 first; the queued import and outbox event then commit together. A failed database step triggers a best-effort S3 cleanup. |
| Outbox publication | Claiming, publishing, and recording publication are separate steps because PostgreSQL and SQS do not share a transaction. |
| Manual transaction processing | Consumer idempotency, categorization, processed status, budget evaluation, and required notification creation occur in one worker transaction. |
| Import chunk processing | Valid transactions, account-balance changes, budget effects, notifications, skip staging, Batch metadata, and the lease fence participate in the chunk transaction. |
| Import finalization | Lease verification, authoritative row counts, terminal import state, and consumer-idempotency recording commit together. |
| Refresh-token rotation | Locking, revocation of the presented token, and creation of its replacement occur in one database transaction. |

An external service call cannot participate in a PostgreSQL transaction. Workflows that cross PostgreSQL, S3, or SQS must therefore tolerate retries and reconcile state through durable identities.

## Manual transaction creation

The API verifies that the account belongs to the authenticated user and is active. One PostgreSQL transaction then:

1. creates the financial transaction with `PENDING` processing status;
2. applies the income or expense to the account balance;
3. creates a transaction-processing outbox event.

If any of those database operations fails, the entire transaction rolls back. The API never commits the financial transaction without its corresponding outbox event.

Categorization, budget evaluation, and notification creation are derived work. They occur asynchronously and do not delay the HTTP response.

Financial accounts and transactions use optimistic version columns. Concurrent API updates cannot silently overwrite a newer committed version. A version conflict is returned to the caller instead.

The manual transaction endpoint does not currently accept an idempotency key. A client retry after an uncertain HTTP outcome can therefore create a second transaction. Endpoint-level idempotency is a planned improvement and is separate from SQS consumer idempotency.

## Transactional outbox

The outbox closes the database-to-message gap. Business state and the intent to publish are committed together before SQS is contacted.

The relay:

1. selects available `PENDING` rows using `FOR UPDATE SKIP LOCKED`;
2. claims each row with a relay-owner identifier and changes it to `PROCESSING`;
3. publishes the immutable event to its SQS queue;
4. marks the row `PUBLISHED` after SQS reports success;
5. reschedules a failed publication with a bounded delay or marks it `FAILED` after the configured attempt limit;
6. returns abandoned `PROCESSING` claims to `PENDING` after the claim timeout.

`SKIP LOCKED` allows more than one relay instance to claim different rows without blocking on the same batch. Claim ownership prevents one relay from finalizing another relay’s work.

There is an unavoidable duplicate-delivery window: SQS may accept a message and the API may stop before the outbox row is marked `PUBLISHED`. Stale-claim recovery will publish the event again. The worker must therefore remain idempotent.

Published outbox rows are not currently removed by a retention job. Permanently failed outbox events also require operational investigation; they are not silently treated as published.

## SQS delivery and consumer idempotency

FinTrack uses SQS Standard queues and assumes at-least-once delivery.

Each event contains:

- a stable event identifier;
- an explicit event version;
- the identifiers required to revalidate authoritative database state;
- a correlation identifier for tracing the workflow.

The worker records `(consumer_name, event_id)` in `processed_message` through `INSERT ... ON CONFLICT DO NOTHING`. That insert occurs inside the same database transaction as the event’s business effects.

This creates two safe outcomes:

- If processing commits, the business effects and idempotency record commit together.
- If processing rolls back, the idempotency record also rolls back and SQS can retry the event.

When an already committed event is delivered again, the uniqueness boundary reports that it was previously processed and the worker acknowledges the duplicate without repeating its effects.

Unsupported event versions fail explicitly instead of being interpreted as a newer or older contract shape.

## Manual transaction processing

For a first delivery, the worker transaction:

1. records the consumer-idempotency boundary;
2. reloads the transaction using both transaction and user identity;
3. applies automatic categorization unless the user has manually overridden the category;
4. marks the transaction `PROCESSED`;
5. evaluates the relevant expense budget;
6. creates a persistent notification when the budget transition requires one.

Manual category overrides are authoritative. A later worker delivery must not replace an explicitly selected category.

Budget evaluation uses a pessimistic database lock for the relevant user, category, and month. Notification uniqueness constraints provide a final guard against duplicate historical notifications.

If the listener throws, the SQS message is not acknowledged and remains eligible for redelivery. In the deployed environment, messages that exhaust the configured receive attempts move to the transaction-processing dead-letter queue.

There is not yet an automated workflow that converts a transaction still marked `PENDING` to `FAILED` when its message reaches the dead-letter queue. DLQ reconciliation is a known reliability gap.

## CSV import submission

The import API validates the file and verifies ownership of an active account before creating the workflow.

The source CSV is streamed to a private S3 object. After the upload succeeds, one PostgreSQL transaction creates:

- the `QUEUED` import record;
- the import-requested outbox event.

S3 and PostgreSQL cannot commit atomically. If database persistence fails after the upload, the API attempts to delete the uploaded object. That cleanup is compensating behavior rather than a cross-system transaction.

The import event carries the import, account, user, source-object, event, and correlation identities. The worker reloads the authoritative import and rejects a message whose ownership or source-object identity does not match the database.

## Import ownership, leases, and fencing

A long-running import requires stronger ownership than a short message handler.

Before launching Spring Batch, the worker locks the import row and attempts to acquire a processing lease. A successful claim creates:

- a unique processing-owner identifier;
- a lease expiration time based on database time;
- an incremented fencing token.

The outcomes are explicit:

- `COMPLETED` imports are acknowledged without being processed again;
- `ABANDONED` imports are acknowledged without being revived;
- an unexpired lease causes the competing delivery to fail and retry later;
- a missing or expired lease can be acquired by a new processing attempt.

The fencing token protects against a paused or partitioned worker resuming after ownership has moved elsewhere. Every chunk commit and finalization transaction verifies the current owner and fencing token. A stale worker may continue executing code temporarily, but it cannot commit another chunk after losing ownership.

## Visibility heartbeat

The import listener extends SQS message visibility before starting the job. While processing continues, a heartbeat:

1. renews the database processing lease;
2. extends SQS message visibility only after lease renewal succeeds.

The heartbeat interval is shorter than both the lease duration and visibility extension.

If lease renewal fails, the processing attempt is marked as having lost ownership and visibility is no longer extended. Chunk fencing prevents the stale attempt from committing further work. Closing the heartbeat cancels its scheduled task and attempts to release the lease without releasing ownership that has already moved to another worker.

## Spring Batch restart and chunk integrity

Each import uses a stable identifying job parameter based on the import ID. Spring Batch stores job, execution, step, and checkpoint metadata in PostgreSQL.

If a previous execution is still marked as running after its worker disappeared, the worker recovers that stale execution before attempting the restart. A retry uses the same logical job identity, allowing Spring Batch to continue from durable metadata rather than intentionally creating an unrelated job.

Each chunk:

- locks the account before applying balance changes;
- creates imported transactions with `(import_id, import_row_number)` identity;
- updates the account balance;
- evaluates each affected budget in deterministic order;
- creates required notifications;
- persists Batch progress;
- verifies the active import lease before commit.

The database uniqueness constraint on `(import_id, import_row_number)` is the final guard against inserting the same CSV row twice.

If a chunk fails, its database work and checkpoint roll back together. Previously committed chunks remain durable and provide the restart position.

## Rejected rows and finalization

Supported row-level validation failures are skipped instead of failing the entire import. Rejected records are staged durably with a unique `(import_id, row_number)` identity in the same transactional processing model as the Batch step.

After Batch completes, the worker reconstructs and uploads the rejected-row CSV. Finalization then:

1. verifies that the processing lease is still active;
2. records the event in the consumer-idempotency table;
3. counts successfully persisted transactions from PostgreSQL;
4. compares Spring Batch skip counts with durable rejected-row counts;
5. marks the import `COMPLETED` with authoritative totals and the rejected-output key.

A mismatch between Batch skip metadata and durable rejected-row staging fails finalization instead of publishing inconsistent totals.

Immediate rejected-row staging cleanup is best effort. A scheduled retention workflow deletes old staging rows for completed imports if immediate cleanup fails.

A terminal unsuccessful Batch execution marks the import `FAILED` and leaves the message unacknowledged so SQS can retry it. Failed imports remain recoverable for a configured window. A scheduled workflow eventually changes sufficiently old failed imports to `ABANDONED` and removes their rejected-row staging.

Moving an import message to a DLQ does not currently perform immediate domain-state reconciliation. An import can remain `FAILED` until retention marks it `ABANDONED`, and a failure before Batch finalization may require operational investigation.

## Concurrency controls

| Risk | Current control |
|---|---|
| Concurrent API updates overwrite one another | JPA optimistic version columns and explicit version-conflict handling. |
| Concurrent imported chunks corrupt an account balance | Pessimistic account lock inside each chunk transaction. |
| Concurrent budget evaluations create inconsistent transitions | Pessimistic lock on the relevant budget row plus notification uniqueness constraints. |
| Multiple relays publish the same pending batch concurrently | `FOR UPDATE SKIP LOCKED`, claim ownership, and stale-claim recovery. |
| Duplicate SQS delivery repeats committed effects | Stable event IDs and transactional `processed_message` insertion. |
| Two workers process one import concurrently | Database lease, expiration, unique owner, and monotonically increasing fencing token. |
| A stale import worker commits after losing its lease | Lease verification during every chunk commit and finalization. |
| Concurrent refresh requests rotate the same token | Pessimistic lock on the stored refresh-token row. |
| The same import row is inserted twice | Database uniqueness on `(import_id, import_row_number)`. |

Locks are bounded by the worker database lock timeout. A lock timeout is treated as a retryable processing failure rather than allowing indefinite waiting.

## Refresh-token concurrency

Refresh tokens are generated from secure random bytes and only their SHA-256 hashes are stored.

Rotation loads the presented token with a pessimistic write lock. While holding that lock, the API verifies that the token is neither revoked nor expired, revokes it, and stores a replacement token. Concurrent attempts to rotate the same token serialize on the database row; after the first transaction commits, later attempts observe the revoked state and fail.

Logout uses the same locking boundary before revocation.

Expired and revoked refresh-token rows are not currently removed by a scheduled retention workflow.

## Failure outcomes

| Failure point | Current outcome |
|---|---|
| Manual transaction database work fails | Transaction, balance change, and outbox event roll back together. |
| S3 upload succeeds but import persistence fails | The API attempts compensating deletion of the uploaded object. |
| Outbox publication fails | The row is rescheduled, then marked `FAILED` after the bounded attempt limit. |
| Relay stops after SQS accepts a message | Stale-claim recovery may publish a duplicate; consumer idempotency absorbs it. |
| Worker fails before its transaction commits | Business changes and idempotency record roll back; SQS can redeliver. |
| Worker commits but stops before acknowledgement | SQS may redeliver; the committed idempotency record suppresses repeated effects. |
| Import worker stops during a job | Visibility eventually expires, the lease expires, and another attempt can recover the Batch execution. |
| Import worker loses ownership while still running | Chunk and finalization fencing prevent additional stale commits. |
| A supported CSV row is invalid | The row is skipped, staged durably, and included in rejected output. |
| Import final counts disagree | Finalization fails rather than recording inconsistent completion data. |
| A message exhausts queue retries | SQS moves it to the corresponding DLQ and alarms provide operational visibility. |

## Current gaps and non-guarantees

The following limitations must not be described as solved:

- Manual transaction creation is not yet idempotent at the HTTP boundary.
- DLQ arrival does not automatically reconcile transaction or import status.
- Published outbox rows do not yet have a retention cleanup workflow.
- Expired and revoked refresh-token rows do not yet have a retention cleanup workflow.
- Permanently failed outbox events require investigation and do not automatically recreate their domain workflow.
- SQS provides at-least-once delivery; FinTrack does not claim exactly-once message delivery.
- The cost-conscious AWS environment does not provide full runtime or stateful high availability. Those availability tradeoffs are documented separately in the AWS deployment guide.

These items belong in the public roadmap until implementation and failure-path verification prove that they are complete.

## Contributor checklist

Before changing a reliability-sensitive workflow, answer:

1. What is the authoritative state?
2. Which writes must commit or roll back together?
3. Which external calls occur outside that transaction?
4. What stable identity makes a retry safe?
5. What happens if the process stops before commit, after commit, or before acknowledgement?
6. Which lock, uniqueness constraint, lease, or version column protects concurrency?
7. What terminal state is visible to users and operators?
8. What automated test proves the failure path?

Do not weaken an existing transaction, ownership check, idempotency boundary, database constraint, lease assertion, or fencing check without documenting and verifying the replacement guarantee.

## Sources of truth

When implementation detail matters, verify:

- application services and repositories for transaction and locking boundaries;
- Flyway migrations for constraints and persistent workflow state;
- event contracts for stable message identity and versioning;
- Spring configuration for timeouts, listener concurrency, and retention windows;
- Terraform for queue redrive, visibility, encryption, and alarms;
- automated tests for verified success, duplicate, concurrency, and failure behavior.

## Related documentation

- [Architecture overview](overview.md)
- [Local development](../getting-started/local-development.md)
- [Configuration](../getting-started/configuration.md)
- [AWS deployment architecture](../deployment/aws-deployment.md)
- [Contributing guide](../../CONTRIBUTING.md)
