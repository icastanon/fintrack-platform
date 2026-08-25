# FinTrack Reference Frontend

This directory contains the lightweight React and TypeScript client used to demonstrate and manually exercise the FinTrack backend.

The frontend supports the project’s backend workflows but is not FinTrack’s primary contribution or architectural focus.

## Run the frontend locally

Requirements:

- Node.js 22
- npm
- access to either a local or deployed FinTrack API

Install the dependencies:

```bash
npm ci
```

The Vite development server forwards relative `/api` requests to the backend configured through `VITE_API_PROXY_TARGET`.

If `frontend/.env.local` already exists, keep it and verify that it points to the backend you intend to use.

To use a locally running API:

```properties
VITE_API_PROXY_TARGET=http://localhost:8080
```

To use the deployed API:

```properties
VITE_API_PROXY_TARGET=https://your-cloudfront-domain.cloudfront.net
```

Start the development server:

```bash
npm run dev
```

Then open `http://localhost:5173`.

## Verify changes

Run:

```bash
npm run lint
npm run build
```

For complete project setup and troubleshooting, see the [local development guide](../docs/getting-started/local-development.md).