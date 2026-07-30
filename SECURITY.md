# Security Policy

walt.id takes the security of our software and services seriously. This document describes how to report security vulnerabilities in this repository in a coordinated and responsible manner, consistent with industry practice for vulnerability disclosure and handling (including ISO/IEC 29147 and ISO/IEC 30111, as commonly applied within an ISO/IEC 27001 information security management system).

## Supported Versions

Security updates are provided for the latest release of this project and for versions that we actively maintain. If you are unsure whether your version is still supported, include the version details in your report and we will advise.

| Version | Supported |
| ------- | --------- |
| Latest release | Yes |
| Older releases | At our discretion (please still report issues) |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues, pull requests, or discussions.**

Report vulnerabilities privately by emailing:

**[security@walt.id](mailto:security@walt.id)**

If possible, encrypt sensitive details. You may request our PGP/GPG public key by emailing the same address before sending the full report.

### What to include

To help us triage and remediate quickly, please include as much of the following as you can:

- A clear description of the vulnerability and its potential impact
- Affected component(s), product(s), and version(s) (e.g. commit hash, release tag, package version)
- Step-by-step reproduction instructions or a proof of concept
- Environment details (OS, deployment method, configuration relevant to the issue)
- Any known workarounds or mitigations
- Your preferred contact details and whether you wish to be publicly acknowledged

### Preferred report format

A structured report (for example following a common advisory style) is appreciated but not required:

1. **Title** — short summary
2. **Severity** — your estimate (e.g. Critical / High / Medium / Low), if known
3. **Affected versions** — as precisely as possible
4. **Description** — technical details
5. **Reproduction steps** — minimal, reliable steps
6. **Impact** — confidentiality, integrity, availability, and any privacy implications
7. **Suggested fix** — optional

## Our Commitment

When you report a vulnerability in good faith, we will:

1. **Acknowledge** receipt of your report within **5 business days**
2. **Assess** the report and provide an initial triage update within **10 business days** of acknowledgment
3. **Keep you informed** of remediation progress where appropriate
4. **Coordinate disclosure** of the issue and any fix or advisory once a remediation is available or an agreed disclosure date is reached
5. **Credit** reporters who wish to be acknowledged (unless you prefer to remain anonymous)

Timelines may vary depending on severity, complexity, and dependency on third parties. Critical issues affecting production users will be prioritized.

## Coordinated Disclosure

We ask that you:

- Give us a reasonable opportunity to investigate and remediate before any public disclosure
- Avoid accessing, modifying, or destroying data that does not belong to you
- Avoid degrading the availability of our services or those of our customers
- Do not use social engineering, phishing, or physical attacks against walt.id staff, users, or customers
- Do not publicly disclose the vulnerability until we have coordinated a disclosure date, or until we agree disclosure may proceed

We aim to resolve and disclose issues as promptly as possible. As a guideline, we typically request a coordinated disclosure window of up to **90 days** from acknowledgment, which may be extended by mutual agreement for complex issues.

## Scope

### In scope

- Security vulnerabilities in the source code and components maintained in this repository
- Misconfigurations or insecure defaults in our published open-source projects that could lead to unauthorized access, data exposure, privilege escalation, or similar impact when used as documented

### Out of scope

Unless they demonstrate a security impact on our software, the following are generally out of scope:

- Issues in third-party dependencies that are not exploitable in our projects (please report those upstream where appropriate; we welcome a heads-up if our usage is affected)
- Social engineering, phishing, or physical security testing
- Denial-of-service testing against production or shared demo environments without prior written approval
- Findings from automated scanners without a demonstrated security impact or reproduction steps
- Missing security headers or best-practice recommendations that do not present a practical risk
- Vulnerabilities in unsupported or end-of-life versions where a fix already exists in a supported release

If you believe an out-of-scope issue still presents significant risk, please report it privately anyway.

## Safe Harbor

We consider security research conducted in accordance with this policy to be authorized and beneficial. We will not pursue legal action against researchers who:

- Make a good-faith effort to follow this policy
- Avoid privacy violations, destruction of data, and interruption of services
- Report findings promptly and privately to [security@walt.id](mailto:security@walt.id)

If you are unsure whether your research activities comply with this policy, contact us before proceeding.

## Non-security Bugs

For non-security bugs and feature requests, please use [GitHub Issues](https://github.com/walt-id/waltid-identity/issues).

## Contact

- Security reports: [security@walt.id](mailto:security@walt.id)
- General inquiries: see [README.md](README.md) and [https://walt.id](https://walt.id)

Thank you for helping keep walt.id and our users safe.
