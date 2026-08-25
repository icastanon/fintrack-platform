# FinTrack Roadmap

This roadmap describes how FinTrack will evolve from a trustworthy manual and CSV-based finance application into a more practical, automated, and eventually connected personal-finance platform.

The roadmap communicates direction rather than delivery dates. GitHub Issues are the source of truth for implementation-ready tasks, ownership, acceptance criteria, and current progress.

## Product direction

FinTrack’s progression is:

> trustworthy manual tracking → convenient automation → connected accounts → mature personal-finance platform

Work should improve real user workflows while preserving financial correctness, security, recoverability, and clear architectural boundaries.

The project will alternate practical product work with targeted foundation work. It will not delay every useful feature for speculative scale, and it will not add features by weakening transaction, ownership, idempotency, or recovery guarantees.

## Prioritization principles

Roadmap work is prioritized using the following rules:

1. Protect correctness of balances, transactions, imports, budgets, authentication, and ownership.
2. Complete existing manual and CSV workflows before introducing bank synchronization.
3. Prefer changes that make the application more usable while creating meaningful Java and Spring contribution opportunities.
4. Add architectural safeguards when they protect a real boundary or known future requirement.
5. Keep PostgreSQL authoritative and make cross-system workflows explicitly retryable and observable.
6. Require evidence before optimizing storage, concurrency, or infrastructure.
7. Avoid new services, queues, or databases unless ownership, security, scaling, or operational behavior genuinely requires them.
8. Document accepted limitations instead of presenting a cost-conscious development deployment as fully production-ready.

## Current foundation

FinTrack currently provides:

- registration, login, logout, JWT access tokens, and rotating hashed refresh tokens;
- user-selected immutable base currency using `USD`, `EUR`, `GBP`, `CAD`, or `AUD`;
- financial accounts and balance tracking;
- manual income and expense transactions;
- asynchronous categorization, budget evaluation, and persistent notifications;
- monthly budgets and financial summaries;
- CSV upload, private S3 storage, import status, Spring Batch processing, restart and recovery, skip handling, and rejected-row downloads;
- a transactional outbox and idempotent SQS consumers;
- Redis-backed rate limiting and categorization-rule caching;
- a lightweight React reference frontend;
- Docker and LocalStack development infrastructure;
- Terraform-defined AWS infrastructure and GitHub Actions deployments.

This foundation is functional and portfolio-ready, but it is not yet a complete consumer-finance product or production banking platform.

## Now — trustworthy and practical existing workflows

The near-term goal is to make manual tracking and CSV imports safer, easier to understand, and practical for regular use.

Items are listed in recommended implementation order. Each item should become one or more focused GitHub Issues before implementation.

### 1. Manual transaction request idempotency

Accept an idempotency key when creating a manual transaction and ensure a client retry cannot create a second transaction or apply the balance change twice.

The design must define key ownership, request fingerprinting, stored response behavior, expiration, concurrency handling, and conflict responses.

### 2. Import history and transaction provenance

- Add a paginated `GET /api/v1/imports?page=0&size=10` endpoint scoped to the authenticated user.
- Expose import identity for imported transactions so API and UI consumers can trace a transaction to its source import.
- Add the corresponding reference-frontend views needed to demonstrate the backend workflow.

This work improves usability now and establishes source identity needed by future ingestion providers.

### 3. Retention and terminal-state reconciliation

- Delete expired and revoked refresh-token rows through a bounded scheduled cleanup workflow.
- Delete old successfully published outbox events without removing records still needed for investigation or retry.
- Define safe retention for consumer-idempotency records before adding any cleanup for them.
- Reconcile transaction and import domain status when messages exhaust retries or are moved to a DLQ.
- Provide an explicit operational recovery or redrive procedure for terminal messaging failures.

Retention and DLQ work must include failure-path tests and observable outcomes; a scheduler existing is not enough to call the workflow complete.

### 4. Recurring monthly budgets

Allow users to define a recurring monthly budget template instead of recreating the same category budget every month.

Template identity, effective dates, edits, deletion, duplicate prevention, month generation, and interaction with manually created budgets must be explicit. Budget rollover is separate work and should not be silently included.

### 5. Authentication usability

- Add change-password behavior for authenticated users.
- Add a forgot-password and time-limited reset-token workflow.
- Add email verification and resend behavior.
- Define how password changes and resets affect existing refresh tokens.

OAuth2 or OpenID Connect login is not part of this unit. Core account recovery and verification should be correct before adding another identity provider.

### 6. Notification delivery

Deliver selected persistent application notifications through email while keeping the database notification history authoritative for the application UI.

The workflow must define delivery retries, duplicate prevention, user preferences, provider failures, and whether email delivery belongs inside the current worker or has reached a justified extraction trigger.

### 7. Focused architecture guardrails

Introduce ArchUnit only after the boundaries it will enforce are documented and stable. Initial rules should protect meaningful constraints such as:

- API and worker code do not depend directly on one another;
- `event-contracts` remains a shared contract library rather than a service or business-logic module;
- controllers do not expose JPA entities;
- domain features do not bypass intended ownership boundaries through generic shared packages.

The goal is regression protection, not architecture theater or a large collection of brittle style rules.

## Next — convenience, correctness, and bank-sync preparation

After the existing workflows are complete and reliable, focus on deeper day-to-day usefulness and provider-neutral ingestion foundations.

- Evolve categorization into an explainable layered pipeline:
    - normalize merchant text and support canonical merchant identities and aliases;
    - define precedence among system rules, user rules, provider-supplied classifications, and manual overrides;
    - record categorization provenance and confidence when applicable;
    - measure unmatched and manually corrected transactions;
    - allow users to create optional rules from manual corrections.
    - defer machine-learning classification until measured unmatched and correction rates show that deterministic rules and provider enrichment are insufficient.
- Transaction search and CSV export.
- Supported transaction corrections, deletions, refunds, transfers, and split transactions with explicit balance and budget semantics.
- Recurring transaction and subscription detection.
- Budget rollover, comparisons, and richer planning views.
- Data export and account-deletion workflows.
- Provider-neutral external-account and source-transaction identity.
- Encrypted provider-token storage boundaries.
- Idempotent webhook inbox and sync cursors.
- Connection health, reconnect state, and observable sync history.

Bank-sync preparation may begin inside the existing API and worker applications. It should not automatically create a third deployable service.

## Later — connected accounts and broader product capabilities

- Integrate Plaid or another first financial-data provider.
- Implement provider link-token and public-token exchange flows.
- Process provider webhooks and incremental synchronization.
- Reconcile pending, posted, modified, and removed provider transactions.
- Support multiple financial-data providers behind stable internal ingestion contracts.
- Add true transaction-level multi-currency accounting using explicit exchange-rate snapshots.
- Add savings goals, sinking funds, subscription management, and unusual-spending insights.
- Explore shared household finances and stronger administrative capabilities.
- Add OAuth2 or OpenID Connect login if it provides clear user value.
- Offer a high-availability AWS deployment option, autoscaling, and end-to-end CloudFront-to-ALB TLS.
- Automate complete Terraform environment bootstrap, validation, provisioning, and teardown workflows.
- Add advanced audit and compliance controls if the application’s users or data-handling obligations justify them.

These items are directional and should not all become GitHub Issues immediately.

## Deliberate current tradeoffs

The following choices are intentional and should not be filed as defects without new evidence:

| Current choice | Reason it is appropriate today | Reconsider when |
|---|---|---|
| Two deployable Spring Boot applications | Separates synchronous API work from asynchronous and Batch work without fragmenting the domain. | Another workload develops independent ownership, security, deployment, or scaling needs. |
| Shared PostgreSQL database | Keeps tightly coupled financial invariants and Spring Batch recovery practical. | A domain has clear data ownership and a justified cross-database consistency model. |
| SQS Standard queues | Supports scalable at-least-once delivery with explicit idempotency. | Ordering becomes a proven business requirement that cannot be modeled another way. |
| Redis limited to rate limiting and category caching | Prevents cache state from becoming authoritative financial state. | Measurements demonstrate another ephemeral workload with safe fallback behavior. |
| One immutable base currency per user | Allows correct summaries and budgets without pretending exchange-rate handling exists. | Transaction-level currencies and exchange-rate snapshots are designed as one coherent feature. |
| Streaming CSV processing from S3 | Avoids loading entire imports into application memory or local disk without evidence of a bottleneck. | Measurements show S3 streaming materially limits throughput or restart behavior. |
| Lightweight reference frontend | Demonstrates backend workflows while keeping contributions focused on Java and Spring. | Product goals expand to require an independently designed frontend architecture. |
| Cost-conscious single-capacity AWS resources | Keeps the live demonstration affordable while preserving a multi-AZ network foundation. | Availability objectives, usage, or funding justify redundant runtime and stateful capacity. |

## Explicitly avoided shortcuts

- Do not describe the shared-database design as independently owned database-per-service microservices.
- Do not split services merely to increase the apparent architecture count.
- Do not treat downloading an entire S3 object to local disk as an optimization without measurements.
- Do not introduce per-account currencies or aggregate unlike currencies without an exchange-rate model.
- Do not claim exactly-once delivery from SQS.
- Do not mark reliability work complete without failure-path verification.
- Do not create frontend-only roadmap work unless it exposes or demonstrates a backend capability.

## Contribution focus

FinTrack’s contribution catalog should remain primarily Java and Spring oriented. The following distribution is directional rather than a strict quota:

- approximately 60% Java and Spring product features;
- 20% reliability, concurrency, Spring Batch, and testing;
- 10% PostgreSQL, Redis, and query work;
- 5% AWS and Terraform;
- 5% documentation and contributor experience.

The frontend remains maintained, but it is not a separate feature track.

Implementation issues should identify both difficulty and learning area.

| Level | Appropriate work |
|---|---|
| Focused | Validation, error mapping, DTO changes, repository queries, targeted tests, documentation tied to verified behavior. |
| Intermediate | Endpoints, scheduled cleanup, migrations, pagination, transactional service behavior, notification delivery, integration tests. |
| Advanced | Idempotency, concurrent processing, Batch restart behavior, DLQ reconciliation, security token lifecycles, provider ingestion, Terraform and IAM changes. |

Starter issues should teach a real backend concept rather than using typo fixes as artificial engineering work.

## From roadmap item to GitHub Issue

The roadmap explains why and where the project is going. A GitHub Issue defines one implementable unit.

Every implementation issue should include:

- user or operational value;
- current verified behavior;
- intended behavior and exclusions;
- affected service and domain owner;
- API, schema, event, configuration, or infrastructure impact;
- important transaction, concurrency, idempotency, and failure cases;
- acceptance criteria;
- required verification;
- experience level and learning areas.

Large roadmap items should be split into independently reviewable issues without separating schema, behavior, and tests that must remain one correctness unit.

## Completion standard

A roadmap item is complete only when:

1. the intended behavior is implemented;
2. relevant success, authorization, concurrency, duplicate, and failure paths are verified;
3. migrations and configuration are complete where required;
4. observability and recovery behavior are defined where required;
5. public documentation reflects the verified result;
6. the corresponding GitHub Issue is closed with evidence.

Code existing is not, by itself, proof that a reliability or infrastructure workflow is complete.

## Related documentation

- [Main project README](../README.md)
- [Contributing guide](../CONTRIBUTING.md)
- [Documentation index](README.md)
- [Architecture overview](architecture/overview.md)
- [Reliability and concurrency](architecture/reliability-and-concurrency.md)
- [AWS deployment architecture](deployment/aws-deployment.md)
