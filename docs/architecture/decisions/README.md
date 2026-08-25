# Architecture Decision Records

This directory contains Architecture Decision Records for significant FinTrack decisions whose context, alternatives, consequences, and reconsideration triggers should remain visible over time.

The architecture overview explains how the system currently works. The reliability guide explains its correctness and failure model. ADRs explain why important boundaries were chosen and how those choices may evolve.

## Decision catalog

| Record | Status | Decision |
|---|---|---|
| [ADR-001: Keep One Financial Database Until a Domain Earns Extraction](0001-shared-database-and-domain-extraction.md) | Accepted | Keep the current financial-management data together while preserving explicit, evidence-driven extraction paths and local outbox ownership. |

## When to create an ADR

Create an ADR when a change materially affects one or more of the following:

- deployable application or module boundaries;
- domain or database ownership;
- synchronous versus asynchronous processing;
- event, queue, or integration strategy;
- transaction, consistency, idempotency, or recovery guarantees;
- authentication or security boundaries;
- authoritative storage responsibilities;
- infrastructure topology with lasting cost, security, or availability consequences;
- a deliberate constraint that future contributors might otherwise remove without understanding it.

Ordinary endpoint additions, bug fixes, implementation details, dependency upgrades, and reversible local refactors normally do not need an ADR.

## Record structure

An ADR should contain:

1. title, status, and date;
2. context and the problem being decided;
3. the decision;
4. alternatives considered;
5. benefits, costs, and limitations;
6. reconsideration or extraction triggers;
7. related documentation.

Include time, complexity, operational, and cost tradeoffs when they materially influenced the decision. State what is deliberately deferred and what evidence would justify revisiting it.

## Statuses

- **Proposed:** under discussion and not yet authoritative.
- **Accepted:** approved and reflected in the intended architecture.
- **Superseded:** replaced by a newer ADR that links back to the original.
- **Deprecated:** retained for historical context but no longer recommended.

## Naming and lifecycle

Use zero-padded sequential filenames:

```text
0002-short-decision-title.md
```

Accepted ADRs are historical records. Correct minor factual or link errors when necessary, but do not rewrite an accepted decision to hide a later change in direction.

When the architecture changes materially:

1. create a new ADR;
2. explain what changed and why;
3. mark the previous ADR `Superseded`;
4. link the old and new records;
5. update the architecture, reliability, deployment, or configuration documentation affected by the verified change.

An ADR records a decision; it does not prove that implementation is complete. Implementation status remains grounded in code, migrations, Terraform, workflows, and automated verification.

## Related documentation

- [Documentation index](../../README.md)
- [Architecture overview](../overview.md)
- [Reliability and concurrency](../reliability-and-concurrency.md)
- [Public roadmap](../../roadmap.md)
- [Contributing guide](../../../CONTRIBUTING.md)
