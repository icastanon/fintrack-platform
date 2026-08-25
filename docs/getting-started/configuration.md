# Configuration

FinTrack uses Spring Boot profiles and environment variables to support local development, Docker Compose, automated tests, and AWS deployment without maintaining separate application builds.

Most contributors only need the `local` profile and a small number of local environment variables.

## Configuration sources

Configuration is distributed across these files:

| Source | Purpose |
|---|---|
| `services/api-service/src/main/resources/application.properties` | Shared API defaults |
| `services/api-service/src/main/resources/application-local.properties` | API LocalStack configuration |
| `services/api-service/src/main/resources/application-deployment.properties` | API deployment logging, metrics, and secret requirements |
| `services/worker-service/src/main/resources/application.properties` | Shared worker defaults |
| `services/worker-service/src/main/resources/application-local.properties` | Worker LocalStack configuration |
| `services/worker-service/src/main/resources/application-deployment.properties` | Worker deployment logging and metrics |
| `infrastructure/.env` | Local Docker Compose secrets and machine-specific values |
| `infrastructure/docker-compose.yml` | Container-specific environment overrides |
| `frontend/.env.local` | Local frontend development configuration |
| `deployment/terraform/ecs.tf` | AWS ECS runtime configuration |
| AWS Secrets Manager | Deployed database credentials and JWT signing secret |

Configuration that differs by machine or contains a secret must not be committed.

## Spring Boot profiles

### Default configuration

The base `application.properties` files contain shared behavior and safe development defaults, including:

- Local PostgreSQL connection information
- Local Redis host and port
- Queue and bucket names
- Database-pool settings
- Rate limits
- Upload limits
- AWS SDK timeouts
- Worker concurrency and recovery settings

Running without an active profile does not provide the complete LocalStack configuration. Use the `local` profile for normal backend development.

### `local`

The `local` profile configures the applications to use LocalStack and development AWS credentials.

It provides:

- Region `us-east-1`
- Placeholder AWS credentials
- LocalStack S3 endpoint
- Path-style S3 access
- LocalStack SQS endpoint for the worker
- API outbox relay activation

Use this profile when running either Spring Boot application from IntelliJ:

```text
local
```

The API service currently also needs this environment variable when it is run directly from the IDE:

```text
SPRING_CLOUD_AWS_SQS_ENDPOINT=http://localhost:4566
```

The worker already receives that endpoint from its local profile.

### `deployment`

The `deployment` profile enables behavior intended for deployed or containerized environments:

- Structured JSON console logs
- Service and environment metric tags
- Bounded exception stack traces
- Runtime JWT secret injection for the API

The API cannot start with this profile unless `JWT_SECRET` is supplied.

AWS ECS activates only:

```text
deployment
```

Terraform supplies the required database, Redis, S3, SQS, region, and secret values.

### `local,deployment`

Docker Compose activates both profiles:

```text
local,deployment
```

This combination provides:

- LocalStack endpoints from the `local` profile
- Structured logs and runtime secret handling from the `deployment` profile
- Container hostnames supplied by Docker Compose

You normally do not need this combination when running the services directly from IntelliJ.

## Configuration precedence

Spring Boot allows higher-priority configuration sources to override lower-priority defaults.

The important order for FinTrack is:

1. Command-line arguments
2. Environment variables
3. Profile-specific properties
4. Base `application.properties`

For example:

```properties
spring.data.redis.host=${REDIS_HOST:localhost}
```

uses `localhost` by default but uses the value of `REDIS_HOST` when that environment variable exists.

Spring Boot also supports relaxed environment-variable binding. For example:

```text
FINTRACK_OUTBOX_RELAY_ENABLED
```

overrides:

```text
fintrack.outbox.relay.enabled
```

This is how Docker Compose and ECS replace local defaults without modifying the application files.

## Local Docker configuration

Create the ignored local environment file:

```bash
cp infrastructure/.env.example infrastructure/.env
```

The available values are:

| Variable | Required | Purpose |
|---|---:|---|
| `LOCALSTACK_AUTH_TOKEN` | Yes | Allows the LocalStack container to start |
| `JWT_SECRET` | No | Overrides the development JWT signing key when running the API through Docker Compose |

Use a development-only JWT value containing at least 32 characters:

```text
JWT_SECRET=replace-with-a-random-local-development-secret
```

Never place AWS credentials, production secrets, or reusable personal credentials in this file.

## IntelliJ configuration

### API service

Run:

```text
com.fintrack.apiservice.ApiServiceApplication
```

Recommended Run Configuration:

```text
Active profile:
local
```

```text
Environment variables:
SPRING_CLOUD_AWS_SQS_ENDPOINT=http://localhost:4566
```

You may also provide a development JWT secret:

```text
JWT_SECRET=replace-with-a-random-local-development-secret
```

The remaining local connection settings already default to:

| Setting | Value |
|---|---|
| PostgreSQL URL | `jdbc:postgresql://localhost:5432/fintrack` |
| PostgreSQL username | `fintrack` |
| PostgreSQL password | `fintrack` |
| Redis host | `localhost` |
| Redis port | `6379` |
| S3 bucket | `fintrack-imports` |
| Transaction queue | `fintrack-transaction-processing` |
| Import queue | `fintrack-import-jobs` |

### Worker service

Run:

```text
com.fintrack.workerservice.WorkerServiceApplication
```

Recommended Run Configuration:

```text
Active profile:
local
```

The worker’s local profile already points S3 and SQS to LocalStack. Its PostgreSQL and Redis defaults point to the locally published Docker ports.

## Common application overrides

These values already have defaults. Most contributors should change them only when working on the corresponding behavior.

### Shared database and Redis settings

| Environment variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |
| `REDIS_HOST` | Redis hostname |
| `REDIS_PORT` | Redis port |
| `DB_POOL_MAX_SIZE` | Maximum HikariCP connection-pool size |
| `DB_POOL_MIN_IDLE` | Minimum idle database connections |
| `DB_CONNECTION_TIMEOUT_MS` | Maximum wait for a pooled connection |
| `DB_VALIDATION_TIMEOUT_MS` | Connection validation timeout |
| `DB_IDLE_TIMEOUT_MS` | Idle connection lifetime |
| `DB_MAX_LIFETIME_MS` | Maximum pooled connection lifetime |

### AWS integration settings

| Environment variable | Purpose |
|---|---|
| `SPRING_CLOUD_AWS_REGION_STATIC` | AWS region |
| `SPRING_CLOUD_AWS_S3_ENDPOINT` | Optional custom S3 endpoint |
| `SPRING_CLOUD_AWS_SQS_ENDPOINT` | Optional custom SQS endpoint |
| `FINTRACK_S3_IMPORT_BUCKET` | Import source and rejected-output bucket |
| `FINTRACK_SQS_TRANSACTION_PROCESSING_QUEUE` | Manual transaction queue |
| `FINTRACK_SQS_IMPORT_JOBS_QUEUE` | Transaction-import queue |
| `S3_API_CALL_TIMEOUT` | Overall S3 operation deadline |
| `S3_API_CALL_ATTEMPT_TIMEOUT` | Deadline for one S3 attempt |
| `SQS_API_CALL_TIMEOUT` | Overall SQS operation deadline |
| `SQS_API_CALL_ATTEMPT_TIMEOUT` | Deadline for one SQS attempt |

Custom AWS endpoints should normally be set only for LocalStack.

### API-specific settings

| Environment variable | Purpose |
|---|---|
| `JWT_SECRET` | JWT signing secret |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins permitted by the API |
| `MAX_UPLOAD_FILE_SIZE` | Maximum accepted import file size |
| `MAX_UPLOAD_REQUEST_SIZE` | Maximum multipart request size |
| `FINTRACK_OUTBOX_RELAY_ENABLED` | Enables or disables scheduled outbox publishing |
| `FINTRACK_HTTP_TRUST_CLOUDFRONT_VIEWER_ADDRESS` | Allows deployed rate limiting to use the viewer address supplied through CloudFront |

The CloudFront viewer-address setting should not be enabled for a deployment that accepts requests from untrusted infrastructure paths.

### Worker-specific settings

| Environment variable | Purpose |
|---|---|
| `DB_LOCK_TIMEOUT_MS` | PostgreSQL lock wait timeout |
| `TRANSACTION_PROCESSING_MAX_CONCURRENT_MESSAGES` | Concurrent manual-transaction messages per worker |
| `TRANSACTION_PROCESSING_MAX_MESSAGES_PER_POLL` | Manual-transaction messages requested per poll |
| `IMPORT_JOBS_MAX_CONCURRENT_MESSAGES` | Concurrent import jobs per worker |
| `IMPORT_JOBS_MAX_MESSAGES_PER_POLL` | Import messages requested per poll |
| `FAILED_IMPORT_RECOVERY_WINDOW` | Time during which failed imports remain recoverable |
| `FAILED_IMPORT_ABANDONMENT_BATCH_SIZE` | Maximum stale failed imports handled per cleanup run |
| `FAILED_IMPORT_ABANDONMENT_DELAY` | Delay between abandonment cleanup runs |
| `FAILED_IMPORT_ABANDONMENT_INITIAL_DELAY` | Delay before the first abandonment cleanup run |

Defaults are intentionally conservative. Increasing concurrency also increases database, Redis, S3, and SQS pressure.

## Frontend configuration

The reference frontend sends API requests using relative `/api` paths.

For local frontend development, copy the example:

```bash
cp frontend/.env.example frontend/.env.local
```

Set:

```text
VITE_API_PROXY_TARGET=http://localhost:8080
```

Vite then proxies local `/api` requests to the API service.

To test the local frontend against the deployed backend instead, set the variable to the CloudFront URL:

```text
VITE_API_PROXY_TARGET=https://your-cloudfront-domain.cloudfront.net
```

`VITE_API_PROXY_TARGET` affects only the Vite development server. It is not a secret and is not used by the deployed static frontend.

The frontend also supports `VITE_API_BASE_URL`, but it should normally remain unset. Leaving it unset allows the deployed frontend and API to use the same CloudFront origin.

## AWS deployment configuration

Terraform supplies deployed application configuration through ECS task definitions.

Non-secret runtime values include:

- Spring profile
- Environment name
- Database endpoint
- Redis endpoint
- S3 bucket name
- SQS queue names
- AWS region
- JVM memory options

Sensitive values are injected from AWS Secrets Manager:

- PostgreSQL username
- PostgreSQL password
- JWT signing secret

Secrets must not be stored in:

- `terraform.tfvars`
- Terraform source files
- GitHub workflow files
- Dockerfiles
- Spring property files
- Committed `.env` files

Detailed infrastructure setup belongs in the [AWS deployment guide](../deployment/aws-deployment.md).

## GitHub Actions configuration

The deployment workflows use GitHub repository variables for non-secret identifiers and GitHub repository secrets for sensitive values.

Current repository variables include:

| Variable | Purpose |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | AWS role assumed by GitHub Actions through OIDC |
| `API_BASE_URL` | Deployed API URL used by deployment verification |
| `FRONTEND_BUCKET_NAME` | S3 bucket receiving the built frontend |

The backend verification job also uses this repository secret:

| Secret | Purpose |
|---|---|
| `LOCALSTACK_AUTH_TOKEN` | Starts LocalStack during CI verification |

GitHub Actions authenticates to AWS through OIDC. Long-lived AWS access keys should not be stored in GitHub.

## Safe configuration rules

- Commit example files, never real secret files.
- Keep `infrastructure/.env` and `frontend/.env.local` untracked.
- Use development-only credentials locally.
- Inject deployed secrets through Secrets Manager.
- Do not commit generated Terraform state or saved plan files.
- Prefer environment-variable overrides over editing shared defaults for one machine.
- Document new externally configurable properties when adding them.
- Use conservative defaults for concurrency, timeouts, and pool sizes.
- Never print secret values while troubleshooting.

## When adding new configuration

Before adding a new property:

1. Decide whether it is genuinely environment-specific.
2. Provide a safe default when possible.
3. Require runtime injection when the value is sensitive.
4. Add the property to the appropriate Spring profile.
5. Add a Docker Compose or ECS override only when that environment needs one.
6. Update this guide if contributors or operators must understand the setting.
7. Add automated coverage for behavior that depends on the property.

Avoid introducing configuration for values that should remain application invariants.