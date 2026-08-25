# FinTrack Documentation

This directory contains the detailed guides for running, understanding, contributing to, deploying, and operating FinTrack.

For the project overview, features, live application, architecture summary, and quick start, see the [main README](../README.md).

## Choose your path

### I want to contribute Java or Spring code

Start with:

1. [Local development](getting-started/local-development.md)
2. [Architecture overview](architecture/overview.md)
3. [Contributing guide](../CONTRIBUTING.md)
4. [Open GitHub Issues](https://github.com/icastanon/fintrack-platform/issues)

Most backend contributions can be developed and tested locally with Docker Compose and LocalStack. You do **not** need an AWS account or previous AWS experience.

Read [Reliability and concurrency](architecture/reliability-and-concurrency.md) when working on messaging, imports, transactions, retries, locking, idempotency, or failure recovery.

### I want to understand the engineering design

Read:

1. [Architecture overview](architecture/overview.md)
2. [Reliability and concurrency](architecture/reliability-and-concurrency.md)
3. [Architecture decisions](architecture/decisions/README.md)
4. [Engineering case study](portfolio/engineering-case-study.md)

### I want to deploy or operate FinTrack on AWS

Read [AWS deployment](deployment/aws-deployment.md).

The AWS documentation is separate from the normal Java contribution path so contributors can work locally without learning the entire cloud environment.

### I want to evaluate the project

Read:

1. [Engineering case study](portfolio/engineering-case-study.md)
2. [Public roadmap](roadmap.md)

The case study explains the project’s engineering challenges, decisions, constraints, tradeoffs, and future evolution. The roadmap separates implemented capabilities from planned product and foundation work.

## Complete documentation reference

| Document                                                                   | Purpose |
|----------------------------------------------------------------------------|---|
| [Local development](getting-started/local-development.md)                  | Run the backend and its dependencies locally. |
| [Configuration](getting-started/configuration.md)                          | Understand required environment variables and application profiles. |
| [Architecture overview](architecture/overview.md)                          | Understand the services, data flows, boundaries, and major technologies. |
| [Reliability and concurrency](architecture/reliability-and-concurrency.md) | Understand transactions, idempotency, retries, leases, locks, and failure recovery. |
| [Architecture decisions](architecture/decisions/README.md)                 | Review important design decisions, alternatives, and tradeoffs. |
| [AWS deployment](deployment/aws-deployment.md)                             | Provision and deploy FinTrack in an AWS account. |
| [Public roadmap](roadmap.md)                                               | Review the short-, medium-, and long-term product and engineering direction. |
| [Engineering case study](portfolio/engineering-case-study.md)              | Review the project’s engineering decisions, tradeoffs, limitations, and lessons. |

## Sources of truth

FinTrack avoids maintaining changing technical information in multiple places.

| Subject | Authoritative source |
|---|---|
| HTTP endpoints and request contracts | Generated OpenAPI specification and Swagger UI |
| Database schema and schema history | Flyway migrations |
| AWS infrastructure | Terraform configuration |
| Build and deployment automation | GitHub Actions workflows |
| Active engineering tasks | GitHub Issues |
| Planned project direction | Public roadmap |
| Architectural rationale | Architecture Decision Records |
| Current implementation behavior | Application code and automated tests |

When documentation disagrees with the implementation, verify the authoritative source and update the affected document.