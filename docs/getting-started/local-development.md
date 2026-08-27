# Local Development

This guide explains how to run FinTrack locally without an AWS account.

The recommended workflow runs PostgreSQL, Redis, and LocalStack in Docker while running the API and worker directly from IntelliJ. This provides the fastest Java development experience with normal breakpoints, debugging, and application restarts.

A second option runs the entire platform through Docker Compose.

> The default settings are sufficient for most contributors. If you need to change connection details, profiles, timeouts, concurrency, or other environment-specific values, see the [configuration reference](configuration.md).

## What runs locally

FinTrack uses the following local infrastructure:

| Dependency | Local implementation | Purpose |
|---|---|---|
| PostgreSQL | Docker | Business data, authentication data, outbox events, Batch metadata, and import state |
| Redis | Docker | Distributed rate limiting and categorization-rule caching |
| Amazon S3 | LocalStack | Source CSV files and rejected-row artifacts |
| Amazon SQS | LocalStack | Transaction-processing and import-job messages |
| API service | IntelliJ or Docker | HTTP API, security, uploads, queries, Flyway, and outbox relay |
| Worker service | IntelliJ or Docker | Message consumption, categorization, budgets, notifications, and Spring Batch imports |

LocalStack automatically creates:

- the `fintrack-imports` S3 bucket;
- the `fintrack-transaction-processing` queue and its dead-letter queue;
- the `fintrack-import-jobs` queue and its dead-letter queue.

You do not need an AWS account, AWS CLI, Terraform, RDS, ElastiCache, or an ECS environment for normal backend development.

## Requirements

Install:

- Java 21;
- Docker Desktop;
- Git;
- IntelliJ IDEA or another Java IDE;
- a [LocalStack Developer Auth Token](https://docs.localstack.cloud/aws/getting-started/auth-token/).

Node.js and npm are optional and are only required when running the reference frontend.

## Clone and configure the project

Clone the repository:

```bash
git clone https://github.com/icastanon/fintrack-platform.git
cd fintrack-platform
```

Create the ignored local environment file:

```bash
cp infrastructure/.env.example infrastructure/.env
```

Sign in to the [LocalStack web application](https://app.localstack.cloud), open the Auth Tokens page, and copy your Developer Auth Token.

Add it to `infrastructure/.env`:

```properties
LOCALSTACK_AUTH_TOKEN=replace-with-your-localstack-auth-token
JWT_SECRET=replace-with-a-random-local-development-secret
```

The JWT secret must contain at least 32 characters.

Never commit `infrastructure/.env`. It contains local credentials and is intentionally ignored by Git.

## Recommended workflow: infrastructure in Docker, Java in IntelliJ

This is the recommended workflow for Java and Spring contributors.

### 1. Start the infrastructure dependencies

From the repository root, run:

```bash
docker compose \
  --env-file infrastructure/.env \
  --file infrastructure/docker-compose.yml \
  up -d --wait postgres redis localstack
```

This starts only PostgreSQL, Redis, and LocalStack. It does not start the API or worker containers.

Check their status:

```bash
docker compose \
  --env-file infrastructure/.env \
  --file infrastructure/docker-compose.yml \
  ps
```

All three dependencies should report a healthy status.

### 2. Import the Maven project

Open the repository root in IntelliJ and allow it to import the root `pom.xml` as a Maven project.

Confirm that IntelliJ is using Java 21 for:

- the project SDK;
- the Maven runner;
- the API run configuration;
- the worker run configuration.

The Maven wrapper included in the repository manages Maven itself, so a separate Maven installation is not required.

### 3. Run the API service

Create an IntelliJ Spring Boot run configuration with:

- Main class: `com.fintrack.apiservice.ApiServiceApplication`
- Module: `api-service`
- Active profile: `local`

Alternatively, set this environment variable in the run configuration:

```properties
SPRING_PROFILES_ACTIVE=local
```

Start the API before the worker. The API owns the Flyway migrations and creates or updates the PostgreSQL schema during startup.

The `local` profile:

- connects to PostgreSQL on `localhost:5432`;
- connects to Redis on `localhost:6379`;
- connects to LocalStack on `localhost:4566`;
- uses local AWS test credentials;
- enables the transactional outbox relay.

Verify the API:

- Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- Readiness: [http://localhost:8080/actuator/health/readiness](http://localhost:8080/actuator/health/readiness)

#### Authenticate requests in Swagger UI

Registration and login are public, but most FinTrack endpoints require a JWT access token.

1. Open Swagger UI.
2. Use `POST /api/v1/auth/register` to create an account if needed.
3. Use `POST /api/v1/auth/login` with the username and password.
4. Copy the `accessToken` from the response.
5. Select **Authorize** at the top of Swagger UI.
6. Paste the access token into the `bearerAuth` field and select **Authorize**.

Paste only the token. Swagger UI adds the `Bearer` prefix automatically. You can now call the protected endpoints as that user.

### 4. Run the worker service

After the API has completed its Flyway migrations, create another IntelliJ Spring Boot run configuration with:

- Main class: `com.fintrack.workerservice.WorkerServiceApplication`
- Module: `worker-service`
- Active profile: `local`

Alternatively, set:

```properties
SPRING_PROFILES_ACTIVE=local
```

The worker is intentionally not a web application, so it does not expose an HTTP port.

After startup, it begins polling the two LocalStack SQS queues:

- `fintrack-transaction-processing`;
- `fintrack-import-jobs`.

You can now place breakpoints in controllers, services, message listeners, Batch components, repositories, or recovery workflows and debug them normally.

## Running only one Java service

You can run only the API when working on synchronous HTTP behavior such as:

- authentication;
- account management;
- queries;
- validation;
- file-upload acceptance.

Asynchronous transaction enrichment and imports will not complete until a worker is running.

When developing the worker, start the API at least once first so Flyway can prepare the database. Most complete worker workflows also require the API because it creates the outbox events, S3 objects, and business records that initiate processing.

Do not run an IDE worker and a Docker worker simultaneously unless you are intentionally testing concurrent consumers. Both workers will poll the same queues.

## Optional reference frontend

The frontend is not required for backend development.

To run it against the local API:

```bash
cd frontend
cp .env.example .env.local
```

Set the local proxy target in `frontend/.env.local`:

```properties
VITE_API_PROXY_TARGET=http://localhost:8080
```

Then run:

```bash
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

Vite forwards `/api` requests to the local API, allowing the frontend and backend to be developed together without changing production deployment configuration.

## Alternative workflow: run everything in Docker

Use this option when you want to demonstrate the complete system without running Java through an IDE.

First stop any API or worker processes running in IntelliJ. Then run:

```bash
docker compose \
  --env-file infrastructure/.env \
  --file infrastructure/docker-compose.yml \
  up --build
```

This builds and starts:

- PostgreSQL;
- Redis;
- LocalStack;
- the API service;
- the worker service.

The Dockerized services use container network names instead of `localhost`, and the API waits for its dependencies before starting. The worker waits for the API to become healthy so Flyway can prepare the schema first.

Use this workflow for complete local demonstrations. Use the hybrid IntelliJ workflow for normal Java development.

## Stop the local environment

Stop the containers while preserving PostgreSQL and LocalStack data:

```bash
docker compose \
  --env-file infrastructure/.env \
  --file infrastructure/docker-compose.yml \
  down
```

The named Docker volumes preserve local database and LocalStack state between restarts.

### Reset all local data

The following command deletes the local PostgreSQL database and LocalStack state:

```bash
docker compose \
  --env-file infrastructure/.env \
  --file infrastructure/docker-compose.yml \
  down --volumes
```

Use it only when you intentionally want a completely clean local environment. The next startup will recreate the database schema, S3 bucket, queues, and dead-letter queues.

## Run the verification suite

Docker Desktop must be running because the integration tests use Testcontainers.

From the repository root:

```bash
./mvnw verify
```

This verifies the Maven reactor, including:

- `event-contracts`;
- `api-service`;
- `worker-service`;
- unit tests;
- PostgreSQL integration tests.

For a focused change, you can first run the relevant test class directly through IntelliJ. Run the complete Maven verification before submitting a pull request.

If your change affects the reference frontend, also run:

```bash
cd frontend
npm ci
npm run lint
npm run build
```

## Common problems

### LocalStack reports that the authentication token is missing

Confirm that `infrastructure/.env` exists and contains:

```properties
LOCALSTACK_AUTH_TOKEN=your-current-token
```

Run Docker Compose with both `--env-file infrastructure/.env` and `--file infrastructure/docker-compose.yml`.

### A required port is already in use

The local infrastructure uses:

- PostgreSQL: `5432`;
- Redis: `6379`;
- LocalStack: `4566`;
- API: `8080`;
- frontend: `5173`.

Stop the conflicting process or container before starting FinTrack.

### The worker fails because database tables are missing

Start the API first and wait for Flyway to finish. The API owns database migrations; the worker does not create the schema.

### Messages are processed unexpectedly or more than once

Confirm that you are not simultaneously running:

- an IDE worker;
- a Docker worker;
- another local FinTrack environment using the same LocalStack queues.

FinTrack uses at-least-once message delivery, so consumers are designed to tolerate duplicates, but normal development generally requires only one worker instance.

### The frontend calls the deployed AWS environment

Check `frontend/.env.local`. For local backend development it must contain:

```properties
VITE_API_PROXY_TARGET=http://localhost:8080
```

Restart the Vite development server after changing the file.

### Integration tests cannot start containers

Confirm that Docker Desktop is running and that the current user can start Docker containers. Testcontainers creates isolated PostgreSQL instances for integration tests.

## Next steps

- Read the [architecture overview](../architecture/overview.md).
- Read the [contributing guide](../../CONTRIBUTING.md).
- Consult [reliability and concurrency](../architecture/reliability-and-concurrency.md) before changing transactions, messaging, imports, locking, leases, retries, or failure recovery.