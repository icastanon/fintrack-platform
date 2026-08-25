# FinTrack Engineering Case Study

FinTrack is a production-inspired personal-finance platform built to explore how financial correctness, asynchronous processing, batch imports, security, cloud deployment, and operational recovery fit together in one coherent Java and Spring system.

The application is intentionally more than a collection of CRUD endpoints. It demonstrates how apparently simple actions—creating a transaction, updating a balance, importing a CSV, rotating a token, or notifying a user—become distributed reliability problems once database transactions, queues, object storage, retries, and concurrent workers are involved.

This case study explains the engineering reasoning and tradeoffs behind the project. Detailed implementation rules remain in the architecture, reliability, configuration, and deployment documentation.

## Product problem

FinTrack helps a user:

- manage financial accounts and balances;
- record income and expense transactions;
- categorize activity;
- define monthly budgets;
- review spending summaries;
- receive budget notifications;
- import transaction CSV files and inspect rejected rows.

The product begins with trustworthy manual and CSV workflows. The longer-term direction is to add convenient automation and eventually connected financial accounts without weakening the existing financial invariants.

## Engineering goals

The project was designed around several goals:

1. Preserve account balances and financial records across failures and concurrency.
2. Keep slow enrichment and import work outside HTTP request latency.
3. Treat duplicate message delivery and worker interruption as normal conditions.
4. Make large CSV imports restartable and explainable.
5. Keep security and ownership checks at every public data boundary.
6. Provide a realistic local environment without requiring contributors to own an AWS account.
7. Demonstrate cloud infrastructure and deployment while controlling recurring cost.
8. Remain understandable and welcoming as an open-source Java and Spring project.

These goals intentionally prioritize backend correctness and learning value over broad frontend architecture or rapid accumulation of loosely connected features.

## System shape

FinTrack contains two independently deployable Spring Boot applications:

- the API service owns HTTP requests, Spring Security, commands, queries, uploads, Flyway migrations, and the transactional outbox relay;
- the worker service owns SQS consumption, asynchronous transaction processing, persistent notification creation, and Spring Batch imports.

A shared `event-contracts` library provides immutable, versioned payloads without introducing direct service-to-service Java dependencies. A lightweight React client demonstrates the supported backend workflows.

PostgreSQL is authoritative. Redis is limited to distributed rate limiting and categorization-rule caching. SQS provides at-least-once delivery, while S3 stores source CSV files and rejected-row artifacts.

The two applications currently share PostgreSQL because accounts, balances, transactions, budgets, notifications, imports, and Batch recovery remain one financial-management bounded context. Future extraction follows domain ownership and measured operational need rather than the location of a Java process.

## Challenge 1: committing financial state without losing asynchronous work

Creating a manual transaction must immediately change the account balance, but categorization and budget evaluation should not increase HTTP latency.

A naive implementation might commit the transaction and then publish directly to SQS. If the process stops between those actions, the transaction remains committed but its asynchronous work is lost.

FinTrack uses a transactional outbox instead. One PostgreSQL transaction:

1. creates the financial transaction;
2. updates the account balance;
3. stores the event that requests asynchronous processing.

A scheduled relay later claims and publishes outbox rows. Claim ownership, `FOR UPDATE SKIP LOCKED`, bounded retry, failed status, and stale-claim recovery allow more than one relay instance and protect work after interruption.

There remains an unavoidable window in which SQS accepts a message but the relay stops before recording publication. The system resolves that ambiguity by allowing duplicate publication and requiring idempotent consumption.

## Challenge 2: making at-least-once delivery safe

SQS Standard queues do not promise exactly-once delivery. A consumer may also commit database work and stop before acknowledging its message.

Every FinTrack event therefore has a stable event identifier and explicit version. The worker inserts `(consumer_name, event_id)` into a `processed_message` table using a database uniqueness boundary.

The idempotency insert and the event’s business changes occur in the same transaction:

- if processing commits, both the business effects and idempotency record commit;
- if processing rolls back, both roll back and the message can safely retry;
- if an already committed event is delivered again, the uniqueness boundary suppresses repeated effects.

This does not claim exactly-once message delivery. It provides effectively-once committed business effects for the protected consumer operation.

## Challenge 3: importing CSV files without treating them as one giant transaction

Importing an entire file in one transaction would hold locks for too long, consume unnecessary memory, and force a complete restart after a late failure.

FinTrack streams the source CSV from private S3 storage and processes it through Spring Batch chunks. Each committed chunk atomically persists:

- imported transactions;
- account-balance changes;
- budget evaluations and required notifications;
- durable rejected-row staging;
- Spring Batch progress.

Imported transactions have a unique `(import_id, import_row_number)` identity. That database constraint is the final protection against inserting the same source row twice.

Long-running jobs also introduce ownership risk. A worker may pause, lose visibility, and resume after another worker has acquired the import. FinTrack combines:

- a database processing lease;
- a unique processing owner;
- a monotonically increasing fencing token;
- periodic lease renewal;
- SQS visibility extension;
- a lease check at every chunk commit and finalization.

A stale worker may continue executing temporarily, but it cannot commit another chunk after ownership moves elsewhere.

Supported row-level failures are staged durably and reconstructed into a rejected-row CSV. Finalization compares Batch skip counts with durable staging counts and refuses to mark the import complete if they disagree.

## Challenge 4: protecting concurrent financial and authentication changes

Different conflicts use different controls:

| Risk | Control |
|---|---|
| Concurrent API updates | Optimistic version columns and conflict responses. |
| Concurrent import balance updates | Pessimistic account locking inside each chunk. |
| Concurrent budget evaluation | Pessimistic budget locking and notification uniqueness. |
| Concurrent refresh-token rotation | Pessimistic token-row locking, revocation, and replacement in one transaction. |
| Duplicate imported source rows | Database uniqueness on import and row identity. |

The database remains the final correctness boundary. Application checks improve behavior and error reporting, but they do not replace constraints and transactional locking.

## Challenge 5: demonstrating cloud architecture without hiding cost tradeoffs

Terraform defines a two-Availability-Zone VPC foundation with public, private application, and private data subnets. CloudFront is the public HTTPS entry point. An Application Load Balancer routes API traffic to ECS Fargate, while the worker runs privately and consumes SQS messages. RDS provides PostgreSQL, ElastiCache provides Redis, ECR stores images, Secrets Manager supplies runtime secrets, and CloudWatch and SNS provide operational visibility.

GitHub Actions verifies changes and deploys through AWS OIDC without stored long-lived AWS access keys.

The live environment deliberately controls recurring cost. It uses one API task, one worker task, one PostgreSQL instance, one Redis node, and one NAT gateway. CloudFront-to-ALB traffic currently uses HTTP, and there is no cross-region disaster-recovery environment.

The network foundation demonstrates how higher availability could be added, but the project does not misrepresent single-capacity resources as fully highly available.

## Deliberate tradeoffs

| Decision | Benefit today | Cost or limitation |
|---|---|---|
| Two deployable applications | Separates HTTP and asynchronous workloads. | Both runtimes still coordinate schema compatibility. |
| Shared PostgreSQL | Keeps financial invariants and Batch recovery local and transactional. | Limits independent data ownership and can create shared-resource contention. |
| Transactional outbox | Prevents committed business work from losing publication intent. | Adds relay state, retry behavior, retention needs, and possible duplicate publication. |
| SQS Standard | Supports scalable asynchronous processing. | Requires idempotency, retries, DLQs, and reconciliation. |
| Streaming Spring Batch imports | Provides bounded memory, checkpoints, skips, and restart. | Requires leases, fencing, Batch metadata, and careful finalization. |
| Limited Redis responsibilities | Keeps financial state authoritative in PostgreSQL. | Redis cannot replace database reads for correctness-critical behavior. |
| One immutable base currency per user | Keeps summaries and budgets mathematically valid. | Does not provide transaction-level currency conversion. |
| Lightweight frontend | Demonstrates backend behavior without redirecting the project’s focus. | Does not represent a mature independent frontend architecture. |
| Cost-conscious AWS capacity | Makes a live portfolio deployment affordable. | Does not provide complete high availability. |

## Conscious omissions and current gaps

FinTrack does not present itself as a production banking platform. Current gaps include:

- no idempotency key for manual transaction creation;
- no paginated import-history workflow or complete public transaction/import origin tracking;
- no automatic reconciliation between DLQ arrival and transaction or import terminal state;
- no cleanup of expired or revoked refresh-token rows;
- no cleanup of old successfully published outbox rows;
- no cleanup of old transaction-import source or rejected-output S3 objects;
- no recurring budget templates;
- no email verification, password-recovery, or change-password workflow;
- no external email delivery for persistent application notifications;
- no connected bank or card accounts;
- no true transaction-level multi-currency accounting;
- no fully redundant runtime, database, or cache deployment.

These limitations are explicit roadmap inputs. They are not hidden behind production-ready language, and they are not all treated as reasons to redesign the foundation before delivering another useful feature.

## Protecting future evolution

FinTrack preserves future options through:

- feature-oriented packages;
- separate deployable runtimes;
- no direct API-to-worker code dependency;
- stable and versioned event contracts;
- durable source and idempotency identities;
- provider-neutral future ingestion boundaries;
- local transactional outboxes;
- documented extraction triggers;
- targeted architecture tests after boundaries are stable.

Possible future extraction candidates include provider ingestion, notification delivery, and analytics. Accounts, balances, transactions, and budgets should remain together until a different ownership and consistency model is justified.

Keeping a decision open does not make future migration free. It makes the migration evidence-driven, bounded, and explainable.

## Verification philosophy

FinTrack treats tests as proof of invariants rather than a coverage-number exercise.

Unit tests cover isolated behavior and component collaboration. PostgreSQL integration tests cover constraints, native queries, transactions, locking, migration behavior, and concurrency. Failure-path tests are required for messaging, idempotency, Batch restart, recovery, and retention changes. The complete Maven verification suite runs through GitHub Actions before backend deployment.

Infrastructure and reliability work is considered complete only after the relevant failure or end-to-end path is verified. Code existing is not, by itself, proof that a distributed workflow is operationally complete.

## What the project demonstrates

FinTrack demonstrates practical experience with:

- Java 21 and Spring Boot application design;
- Spring Security and token lifecycle management;
- PostgreSQL transactions, constraints, locking, and schema evolution;
- event-driven workflows and transactional outbox publication;
- idempotent SQS consumers and dead-letter handling;
- Spring Batch chunk processing, skip handling, restart, and recovery;
- Redis-backed distributed behavior without treating cache state as authoritative;
- AWS networking, compute, storage, messaging, security, and observability;
- Terraform and GitHub Actions deployment automation;
- explicit architecture, cost, time, and complexity tradeoffs.

The project’s value is not the number of technologies present. It is the way those technologies cooperate around observable financial and workflow invariants.

## Related documentation

- [Main project README](../../README.md)
- [Documentation index](../README.md)
- [Architecture overview](../architecture/overview.md)
- [Reliability and concurrency](../architecture/reliability-and-concurrency.md)
- [Architecture decisions](../architecture/decisions/README.md)
- [AWS deployment architecture](../deployment/aws-deployment.md)
- [Public roadmap](../roadmap.md)
- [Contributing guide](../../CONTRIBUTING.md)
