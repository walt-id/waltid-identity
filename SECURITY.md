# Security Policy

walt.id takes the security of our software and services seriously. This policy explains how to report vulnerabilities privately, how we handle reports, and how we coordinate fixes and public security advisories. Our approach is informed by ISO/IEC 29147 and ISO/IEC 30111 and supports our information security management practices.

## Supported Versions

Security updates are provided for the latest release of this project and for versions we actively maintain. If you are unsure whether your version is supported, include its details in your report and we will advise.

| Version | Security support |
| --- | --- |
| Latest release | Yes |
| Older releases | At our discretion (please still report issues) |

## Reporting a Vulnerability

**Do not disclose suspected vulnerabilities in public GitHub issues, pull requests, discussions, or other public channels.** Please use one of the private reporting methods below.

### Option 1 - GitHub Private Vulnerability Reporting (preferred for this open-source repository)

**[Report a vulnerability privately on GitHub](https://github.com/walt-id/waltid-identity/security/advisories/new)**

If private vulnerability reporting is enabled, GitHub lets you send a confidential report directly to the repository maintainers. The report initially enters **Triage**; it is **not a public security advisory**. walt.id reviews it and may accept it as a draft advisory for private remediation and coordinated disclosure.

If you cannot access the GitHub reporting form, use the email option below. You do **not** need to create a public GitHub issue.

### Option 2 - Email (alternative and preferred for Enterprise-only issues)

Email **[security@walt.id](mailto:security@walt.id)**.

Use email if you prefer not to use GitHub, need to discuss an issue spanning multiple walt.id repositories or products, are reporting an Enterprise-only vulnerability or a security incident, or cannot access the GitHub reporting form. You do not need a GitHub account to report by email.

Please avoid sending the same report through both channels unless you cross-reference the existing report. If the details are especially sensitive, ask for our PGP/GPG public key before sending the full report.

### What to include

To help us investigate, provide as much of the following as possible:

- **Summary and impact:** What is the vulnerability, and what could an attacker achieve?
- **Affected components and versions:** Product, repository, release tag, package version, or commit hash.
- **Reproduction details:** Steps, a minimal proof of concept, or relevant evidence (shared privately).
- **Environment:** Deployment method, configuration, and prerequisites, if relevant.
- **Mitigations:** Any workarounds or suggested fixes you are aware of (optional).
- **Contact and credit preference:** How to reach you, and whether you want public acknowledgment or anonymity.

A severity estimate is welcome but not required. Please do not include other people's personal data, credentials, or production secrets in a report.

## How walt.id Handles Vulnerability Reports

**walt.id is responsible for triage, remediation, CVE coordination, and advisory publication.** A reporter is not expected to create or publish a security advisory or independently request a CVE for an issue under our coordination.

| Report origin | What happens next |
| --- | --- |
| **GitHub private report** | We review the report in **Triage**. If accepted, we use **Accept and open as draft** to turn that same report into a private draft GitHub Security Advisory. The reporter does not have to submit it again. |
| **Email report** | We acknowledge and assess the report. For a confirmed vulnerability affecting this public repository, an authorized walt.id maintainer creates a **draft GitHub Security Advisory** and may invite the reporter to collaborate. The reporter does not have to re-report through GitHub. |
| **Internally discovered issue** | An authorized walt.id maintainer creates a draft advisory for a confirmed, independently reportable vulnerability affecting this public repository. |

Once a report has been validated, we generally:

1. **Assess and scope:** Determine affected products/versions, exploitability, severity, and any immediate mitigations.
2. **Coordinate privately:** Use the draft advisory and restricted internal channels to discuss the issue, prepare a fix, and verify it. We may collaborate with the reporter when appropriate.
3. **Coordinate CVE assignment:** For eligible vulnerabilities in public repositories, walt.id normally requests the CVE identifier through GitHub's advisory workflow. We first check whether an identifier has already been assigned and avoid duplicate requests. GitHub determines eligibility and assignment. For issues outside GitHub's CNA scope, we coordinate through an appropriate alternative CNA if needed.
4. **Release and disclose:** Coordinate the patch release with publication of the GitHub Security Advisory. Where relevant, include affected and fixed versions, severity, mitigations, CVE/GHSA identifiers, and researcher credit. Reference the advisory in release notes, and notify affected Enterprise customers as appropriate.

**One vulnerability may affect multiple walt.id editions or components.** We coordinate identifiers and references to avoid issuing duplicate CVEs for the same underlying vulnerability. Not every report results in a public advisory or CVE (for example, if it is not a confirmed security vulnerability).

For Enterprise-only issues that cannot appropriately be handled as an advisory in a public open-source repository, we coordinate disclosure and customer communication separately. We do not publish sensitive vulnerability details before coordinated disclosure.

## Our Commitment

When you report a vulnerability in good faith, we will:

1. **Acknowledge** your report within **5 business days**.
2. **Assess** it and provide an initial triage update within **10 business days after acknowledgment**.
3. **Keep you informed** of remediation progress where appropriate.
4. **Coordinate disclosure** of the issue and any fix or advisory once remediation is available or an agreed disclosure date is reached.
5. **Credit** reporters who wish to be acknowledged, unless they prefer anonymity. For GitHub advisories, public credit may require acceptance through GitHub.

Timelines may vary depending on severity, complexity, and third-party dependencies. Critical issues affecting production users will be prioritized.

## Coordinated Disclosure

We ask researchers to:

- Give us a reasonable opportunity to investigate and remediate before public disclosure.
- Avoid accessing, modifying, or destroying data that does not belong to you.
- Avoid degrading the availability of our services or our customers' services.
- Refrain from social engineering, phishing, and physical attacks against walt.id staff, users, or customers.
- Coordinate the public disclosure date with us rather than publishing exploit details while the issue remains unpatched.

We aim to resolve and disclose issues as promptly as possible. As a guideline, we typically request a coordinated disclosure window of up to **90 days from acknowledgment**, which may be extended by mutual agreement for complex issues. We encourage researchers to contact us if disclosure timing becomes a concern.

## Scope

### In scope

- Security vulnerabilities in the source code and components maintained in this repository.
- Misconfigurations or insecure defaults in our published open-source projects that could lead to unauthorized access, data exposure, privilege escalation, or similar impact when used as documented.

### Out of scope

Unless they demonstrate a security impact on our software, the following are generally out of scope:

- Issues in third-party dependencies that are not exploitable in our projects (please report those upstream where appropriate; we welcome a heads-up if our usage is affected).
- Social engineering, phishing, or physical security testing.
- Denial-of-service testing against production or shared demo environments without prior written approval.
- Findings from automated scanners without demonstrated security impact or reproduction steps.
- Missing security headers or best-practice recommendations that do not present a practical risk.
- Vulnerabilities in unsupported or end-of-life versions where a fix already exists in a supported release.

If you believe an out-of-scope issue still presents significant risk, please report it privately anyway.

## Safe Harbor

We consider security research conducted in accordance with this policy to be authorized and beneficial. We will not pursue legal action against researchers who:

- Make a good-faith effort to follow this policy.
- Avoid privacy violations, destruction of data, and interruption of services.
- Report findings promptly and privately using [GitHub Private Vulnerability Reporting](https://github.com/walt-id/waltid-identity/security/advisories/new) or [security@walt.id](mailto:security@walt.id).

If you are unsure whether your research activities comply with this policy, contact us before proceeding.

## Security Resources

| Resource | Link |
| --- | --- |
| Report a vulnerability privately (GitHub, when enabled) | [GitHub vulnerability reporting form](https://github.com/walt-id/waltid-identity/security/advisories/new) |
| Report a vulnerability by email | [security@walt.id](mailto:security@walt.id) |
| Published advisories for this repository | [GitHub Security Advisories](https://github.com/walt-id/waltid-identity/security/advisories) |
| Releases and release notes | [GitHub Releases](https://github.com/walt-id/waltid-identity/releases) |
| Security policy | [SECURITY.md](https://github.com/walt-id/waltid-identity/blob/main/SECURITY.md) |
| GitHub reporting instructions | [Privately reporting a security vulnerability](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/report-privately) |
| GitHub advisories and CVE workflow | [Repository security advisories](https://docs.github.com/en/code-security/concepts/vulnerability-reporting-and-management/repository-security-advisories) |

## Non-security Bugs

For non-security bugs and feature requests, please use [GitHub Issues](https://github.com/walt-id/waltid-identity/issues).

## Contact

- Security reports: [security@walt.id](mailto:security@walt.id)
- General inquiries: see [README.md](README.md) and [walt.id](https://walt.id)

Thank you for helping keep walt.id and its users safe.
