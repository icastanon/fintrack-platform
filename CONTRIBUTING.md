# Contributing to FinTrack

Thank you for considering a contribution to FinTrack.

FinTrack primarily welcomes meaningful Java and Spring Boot contributions involving security, persistence, messaging, batch processing, reliability, observability, and AWS infrastructure. The React reference frontend demonstrates backend workflows. Focused frontend fixes and improvements are also welcome, but contributors working on backend issues are not expected to modify it.

You do not need AWS experience or an AWS account for most backend contributions. PostgreSQL, Redis, S3-compatible storage, and SQS-compatible queues can all run locally through Docker Compose and LocalStack.

## Before you begin

Before starting substantial work:

1. Review the [open issues](https://github.com/icastanon/fintrack-platform/issues).
2. Check whether another issue or pull request already covers the change.
3. Open an issue if you are proposing new behavior, changing an architectural boundary, or making a large refactor.
4. Agree on the intended behavior and scope before investing significant time.

Small bug fixes, focused tests, and clear documentation corrections may be submitted directly when their intent is unambiguous.

Suspected security vulnerabilities must not be reported in a public issue. Follow the [security policy](SECURITY.md) and use GitHub private vulnerability reporting.

Suggestions are welcome. You may open an issue to propose features, challenge an architectural decision, suggest roadmap changes, or identify an area that should be simplified.

## Choose a contribution

FinTrack contains work for different experience levels.

Good focused contributions may include:

- improving validation or error handling;
- adding tests for existing behavior;
- improving database queries or repository behavior;
- implementing a documented cleanup or retention workflow;
- improving an existing Spring Security flow;
- strengthening failure recovery or observability;
- correcting backend documentation.

More advanced contributions may involve:

- asynchronous messaging and idempotency;
- Spring Batch restart and recovery behavior;
- transaction boundaries and concurrency control;
- distributed leases, fencing tokens, and locking;
- authentication and token lifecycle management;
- database migrations and data-model evolution;
- AWS infrastructure and deployment automation.

The [public roadmap](docs/roadmap.md) describes the project’s planned direction. GitHub Issues are the source of truth for specific work that is ready to be implemented.

Issue metadata helps contributors choose appropriate work:

- `good first issue` identifies a bounded, well-defined task suitable for someone learning the relevant part of FinTrack;
- `help wanted` identifies work the maintainer has intentionally opened to contributors;
- `area:*` labels identify the affected technical area.

Each implementation issue also identifies its expected experience level and learning areas. The [contribution-level guide](docs/roadmap.md#contribution-focus) explains the project’s Focused, Intermediate, and Advanced levels.

## Set up the project

Follow the [local development guide](docs/getting-started/local-development.md).

The recommended Java workflow is:

- run PostgreSQL, Redis, and LocalStack through Docker Compose;
- run the API and worker directly from IntelliJ;
- use the `local` Spring profile;
- place breakpoints and restart the individual Java service normally.

Most contributors do not need to understand or deploy the AWS environment.

The default configuration is sufficient for normal development. Consult the [configuration reference](docs/getting-started/configuration.md) only when changing profiles, connection settings, timeouts, concurrency, or environment-specific behavior.

## Understand the affected area

Read only the documentation relevant to your contribution.

For an ordinary API or persistence change, begin with:

- [Local development](docs/getting-started/local-development.md)
- [Architecture overview](docs/architecture/overview.md)

Before changing transactions, messaging, imports, retries, locks, leases, idempotency, or recovery behavior, also read:

- [Reliability and concurrency](docs/architecture/reliability-and-concurrency.md)

AWS documentation is not required unless your contribution changes Terraform, ECS deployment, networking, permissions, or cloud operations.

The [documentation guide](docs/README.md) provides routes for different kinds of contributors.

## Create a branch

Create a focused branch from the current `main` branch:

```bash
git switch main
git pull
git switch -c feature/short-description
```

Appropriate branch prefixes include:

- `feature/`
- `fix/`
- `test/`
- `docs/`
- `refactor/`

A contribution should solve one coherent problem. Avoid combining unrelated cleanup, formatting, and behavior changes in the same pull request.

## Backend engineering guidelines

### Preserve existing boundaries

FinTrack is organized by feature across the API service and worker service. Place new code with the domain it belongs to instead of creating generic packages without a clear owner.

Do not introduce a new service, queue, database boundary, or shared abstraction without discussing the architectural impact first.

### Keep transaction boundaries intentional

Database transactions should protect a clear atomic business operation.

Avoid performing slow network operations inside database transactions unless the existing workflow intentionally requires it. S3, SQS, and other external calls cannot participate in a PostgreSQL transaction.

When changing transaction behavior, consider rollback, retries, duplicate delivery, concurrent workers, and partial failure.

### Treat asynchronous messages as at-least-once delivery

SQS messages may be delivered more than once.

Message-processing changes must consider:

- idempotency;
- transaction boundaries;
- acknowledgement timing;
- visibility timeouts;
- retries and dead-letter queues;
- worker crashes;
- stale ownership;
- observable terminal states.

Do not assume that a message is processed exactly once.

### Protect financial data

Use `BigDecimal` for monetary calculations. Avoid floating-point monetary arithmetic.

Preserve account ownership checks, currency invariants, validation rules, and authorization boundaries.

### Evolve the database through Flyway

Every production schema change requires a new Flyway migration.

Never modify a migration that may already have been applied. Add a new versioned migration instead.

Consider indexes, uniqueness constraints, foreign keys, nullability, existing data, and rollback behavior when changing the schema.

### Write useful comments

Comments should explain important reasoning, invariants, failure behavior, or non-obvious tradeoffs.

Do not add comments that merely repeat what a method or statement already says.

### Keep configuration external

Do not commit:

- passwords;
- authentication tokens;
- AWS credentials;
- JWT signing secrets;
- Terraform state;
- local `.env` files;
- generated build output.

Add new environment-specific values through Spring configuration and document them in the configuration reference.

## Testing expectations

Tests should prove behavior, not merely increase coverage.

Use unit tests for isolated business logic and collaboration between components.

Use PostgreSQL integration tests when behavior depends on:

- native SQL;
- constraints;
- locking;
- transaction rollback;
- repository projections;
- database-specific types;
- concurrency;
- Flyway migrations.

Use failure-path tests for messaging, retries, idempotency, recovery, and batch-processing changes.

Do not replace meaningful integration behavior with mocks when the important invariant belongs to PostgreSQL, Spring transactions, or an external-system boundary.

## Verify backend changes

Docker Desktop must be running because the verification suite includes Testcontainers-based integration tests.

From the repository root, run:

```bash
./mvnw verify
```

This verifies the shared event contracts, API service, and worker service.

During development, you may run focused tests from IntelliJ or through an individual Maven module. Run the complete verification suite before submitting the pull request.

## Verify frontend changes

The frontend is a reference interface for demonstrating backend functionality. Focused frontend fixes and improvements are welcome when proposed independently, but backend contributions do not require corresponding frontend changes.

If your change affects `frontend/`, run:

```bash
cd frontend
npm ci
npm run lint
npm run build
```

Discuss substantial frontend architecture changes in an issue before implementation.

## Infrastructure contributions

Discuss material infrastructure changes before implementation.

Infrastructure changes may affect:

- recurring AWS cost;
- network exposure;
- IAM permissions;
- availability;
- deployment behavior;
- Terraform state;
- operational recovery.

Format and validate Terraform changes before submitting them. Never run `terraform apply` against an AWS environment you do not own or have permission to modify.

Do not include Terraform plan files or state files in a pull request.

## Documentation changes

Update documentation when a contribution changes documented behavior, configuration, architecture, deployment, or operational procedures.

Avoid duplicating information whose authoritative source already exists:

- OpenAPI and Swagger UI define HTTP contracts;
- Flyway migrations define database history;
- Terraform defines AWS infrastructure;
- GitHub Actions defines deployment automation;
- application code and tests define current behavior;
- GitHub Issues define active implementation work;
- the public roadmap defines longer-term direction.

## Commit and pull-request guidance

Write concise commit messages that describe the completed change, for example:

```text
Add expired refresh-token cleanup workflow
```

A pull request should explain:

- what changed;
- why it changed;
- which issue it addresses;
- how it was verified;
- whether it changes APIs, database schema, configuration, security, or infrastructure;
- any important limitations or tradeoffs.

Keep the pull request small enough to review confidently.

If reviewers request changes, update the existing branch and pull request instead of opening a replacement.

## Review priorities

Reviews prioritize:

1. correctness and preservation of business invariants;
2. security and authorization;
3. transaction and failure behavior;
4. data integrity and idempotency;
5. appropriate automated verification;
6. maintainability and clarity;
7. performance where evidence shows it matters.

A contribution may be declined or redirected if it adds unnecessary complexity, expands scope without a clear benefit, weakens an existing invariant, or conflicts with the project’s documented direction.

## Need help?

If an issue is unclear, ask questions on the issue before beginning implementation.

If you discover a bug, missing requirement, architectural concern, or better alternative while working, explain it openly. Constructive disagreement and design suggestions are welcome.