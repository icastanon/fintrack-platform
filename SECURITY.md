# Security Policy

## Supported versions

FinTrack does not currently publish versioned releases. Security updates apply to the latest code on the default branch and the currently hosted demonstration environment.

| Version | Supported |
|---|---|
| Latest `main` branch | Yes |
| Current hosted environment | Yes |
| Older commits or deployments | No |

## Reporting a vulnerability

Do not report suspected security vulnerabilities through public GitHub Issues, Discussions, pull requests, or application data.

Use [GitHub private vulnerability reporting](https://github.com/icastanon/fintrack-platform/security/advisories/new) to submit the report privately.

Include as much of the following information as possible:

- the affected component or endpoint;
- the security impact;
- the steps required to reproduce the behavior;
- the affected commit, environment, or configuration;
- sanitized logs, requests, responses, or screenshots;
- any known mitigations or suggested fixes.

Never include passwords, access tokens, refresh tokens, signing keys, cloud credentials, private financial information, or other real secrets in the report. If a credential may have been exposed, identify the credential type without copying its value.

## Responsible testing

Use a local development environment whenever possible.

Do not:

- access or modify another user’s data;
- perform destructive testing against the hosted environment;
- intentionally degrade service availability;
- exfiltrate data or retain sensitive information;
- use social engineering or physical attacks;
- publicly disclose an unresolved vulnerability.

Stop testing and report the issue privately if you encounter private data, credentials, or evidence of unauthorized access.

## Response and disclosure

Security reports will be reviewed and prioritized according to their reproducibility, impact, and affected surface.

The maintainer may request additional information, coordinate a fix through a private security advisory, and credit the reporter when appropriate. Please allow time for investigation and remediation before public disclosure.

Non-sensitive bugs and ordinary feature requests should use the repository’s public GitHub Issue forms.