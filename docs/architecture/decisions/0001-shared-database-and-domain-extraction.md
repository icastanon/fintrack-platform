# ADR-001: Keep One Financial Database Until a Domain Earns Extraction

- Status: Accepted
- Date: 2026-08-24

## Context

FinTrack has two independently deployable Spring Boot applications:

- the API service handles synchronous HTTP requests, authentication, commands, queries, uploads, Flyway migrations, and outbox publication;
- the worker service handles SQS consumption, transaction enrichment, budget evaluation, notifications, and Spring Batch imports.

The applications communicate asynchronously through versioned events and do not depend on each other’s Java code. They currently share one PostgreSQL database because they belong to one financial-management bounded context.

Several important operations cross API and worker execution boundaries while retaining closely related financial invariants:

- manual transaction creation updates the account balance and creates an outbox event atomically;
- transaction processing categorizes the transaction and evaluates its budget effects;
- import chunks create transactions, update balances, evaluate budgets, create notifications, and persist Batch progress atomically;
- import recovery depends on authoritative import state, lease state, financial rows, rejected-row staging, and Spring Batch metadata.

Separating databases merely because code runs in different processes would replace local database transactions and joins with distributed consistency, duplicated state, additional failure modes, and more complex recovery.

At the same time, future capabilities such as bank ingestion, notification delivery, or analytics may eventually develop independent ownership, security, scaling, or availability requirements. The current design must not imply that every capability must remain in one database forever.

## Decision

FinTrack will keep one shared PostgreSQL database for the current financial-management bounded context.

The API and worker remain separate deployable runtimes, but the runtime boundary is not treated as a data-ownership boundary. The API continues to own Flyway migrations for the shared schema.

Future extraction will follow coherent domain ownership and demonstrated operational requirements—not whether code currently runs in the API or worker.

The following rules preserve future options:

1. API and worker code must not depend directly on one another.
2. Cross-runtime messages use stable identifiers and explicit contract versions.
3. PostgreSQL remains authoritative for financial and workflow state.
4. Redis and SQS must not become authoritative financial stores.
5. Feature packages retain clear domain ownership; generic shared packages must not become an unowned integration layer.
6. External ingestion uses provider-neutral account, connection, and source-transaction identities.
7. Idempotency boundaries are based on durable business or event identities.
8. New services, queues, or databases require an identified owner, consistency model, failure model, and operational benefit.
9. An extracted domain owns its data and publishes events through an outbox stored in the same database transaction as its authoritative changes.
10. Event payloads evolve through versioning when an extracted consumer can no longer read authoritative state from the shared database.

## Outbox ownership

The transactional outbox remains colocated with the database whose business changes produce the event.

This preserves the guarantee:

> authoritative business change + intent to publish = one local database transaction

The relay may later be scaled independently, moved into a separate runtime, or replaced with change-data capture. Those changes do not require moving outbox rows into a centralized database.

If FinTrack eventually has multiple domain databases, each publishing domain should own its local outbox. A centralized cross-domain outbox would reintroduce the distributed commit problem the pattern is intended to avoid.

## Extraction triggers

A domain or capability should be considered for extraction when several of the following are true:

- it has a clear owner and can define an authoritative data model;
- its deployment cadence differs materially from core financial functionality;
- its traffic or scaling behavior creates measured contention for other workloads;
- its security or data-access requirements require stronger isolation;
- its availability or recovery objectives differ from the core application;
- its failures need stronger isolation than the existing process and queue boundaries provide;
- its integration contract can remain stable without synchronous access to another domain’s tables;
- the value of independent operation outweighs distributed-consistency and operational costs.

Scale alone is not sufficient. Database CPU, storage, or throughput pressure should first be evaluated using indexing, query design, connection management, read models, partitioning, or vertical and horizontal database options where appropriate.

## Likely future candidates

Potential extraction candidates include:

- external bank-provider ingestion and webhook processing;
- notification delivery to email or other external channels;
- analytics or reporting workloads that can use independently built read models.

Accounts, balances, transactions, and budgeting should remain together longer because their invariants are tightly related. Import processing may be separated only when ownership of ingestion state and the command boundary for creating authoritative financial transactions are explicit.

These examples are directional, not commitments to create additional services.

## Alternatives considered

### Give the worker its own database now

Rejected. The worker is an execution role containing several capabilities, not one coherent data domain. Moving all worker data would split tightly coupled financial operations and Spring Batch recovery without a demonstrated operational benefit.

### Create a centralized outbox service or database

Rejected. A remote outbox cannot atomically commit with each domain’s authoritative database change. It would add another distributed failure boundary instead of closing one.

### Combine the API and worker into one deployable application

Rejected. Separate runtimes provide useful scaling, deployment, failure-isolation, and workload boundaries while SQS keeps asynchronous processing outside HTTP requests.

### Split every major feature into a service and database

Rejected. This would introduce distributed consistency and operational overhead before independent ownership or scaling requirements exist.

## Consequences

### Benefits

- Financial invariants remain enforceable with local PostgreSQL transactions and constraints.
- Import restart and recovery can use one authoritative state model.
- The API and worker can still scale and deploy independently.
- Future boundaries remain visible through packages, event contracts, stable identities, and explicit extraction triggers.
- Infrastructure and local development remain understandable for contributors.

### Costs and limitations

- API and worker database changes require coordinated schema compatibility.
- A poorly designed query or workload can affect both runtimes.
- The shared database limits independent data ownership.
- Future extraction will require deliberate contract evolution and data migration.
- The current event payloads sometimes carry identifiers and expect the worker to reload authoritative state; an extracted database may require richer versioned events or a new command boundary.

Keeping the decision open does not make a future split free. It keeps the required migration bounded, explainable, and driven by evidence.

## Reconsideration

Revisit this decision when an extraction trigger is supported by production measurements, security requirements, team ownership, or an approved product capability.

Any proposal must identify:

- the domain being extracted;
- the tables and data owner;
- the new command and event boundaries;
- how existing data will migrate;
- how cross-domain consistency will work;
- how retries, idempotency, reconciliation, and recovery will work;
- how the change will be deployed and rolled back;
- why the operational benefit justifies the additional complexity.

## Related documentation

- [Architecture overview](../overview.md)
- [Reliability and concurrency](../reliability-and-concurrency.md)
- [Public roadmap](../../roadmap.md)
- [AWS deployment architecture](../../deployment/aws-deployment.md)
