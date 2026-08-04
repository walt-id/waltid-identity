<div align="center">
 <h1>walt.id Relying Party Registration Certificate CLI</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Issue and validate EUDI Wallet-Relying Party Registration Certificates (WRPRC, <code>rc-wrp+jwt</code>) from the command line</p>

  <a href="https://walt.id/community">
  <img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
  </a>
  <a href="https://www.linkedin.com/company/walt-id/">
  <img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
  </a>
</div>

Implements a command-line front end for the
[`waltid-relying-party-certificate`](../../waltid-libraries/protocols/waltid-relying-party-certificate) library:
create and sign a Wallet-Relying Party Registration Certificate as specified in
[ETSI TS 119 475](https://www.etsi.org/deliver/etsi_ts/119400_119499/119475/01.02.01_60/ts_119475v010201p.pdf),
and, on the wallet side, validate an OpenID4VP Authorization Request against one.

# How to use

## In development

```bash
git clone https://github.com/walt-id/waltid-identity.git
cd waltid-identity
cd waltid-applications/waltid-rp-certificate-cli
../../gradlew run --args="--help"
```

Every example below follows the same shape:

```bash
../../gradlew run --args="<command> <options...>"
```

| Command                                                             | What it does                                                                                          |
|:----------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------|
| `waltid-rpcert --help`                                               | Print usage message                                                                                    |
| `waltid-rpcert issue-jwt --help`                                     | Print `issue-jwt` usage message                                                                         |
| `waltid-rpcert validate-jwt --help`                                  | Print `validate-jwt` usage message                                                                      |
| `waltid-rpcert issue-jwt --payload payload.json --key key.jwk --x5c leaf.der --x5c root.der` | Issues a registration certificate JWT signed with real key/certificate material                |
| `waltid-rpcert issue-jwt --payload payload.json --generate-demo-ca`  | Issues a registration certificate JWT, signed with a freshly generated ephemeral CA (local testing only) |
| `waltid-rpcert validate-jwt --request @request.txt --trust-anchor ca.der` | Resolves the Authorization Request, extracts the registration certificate from its `verifier_info`, and validates its `x5c` chain against a pinned trust anchor; prints `true`/`false` |
| `waltid-rpcert validate-jwt --request 'openid4vp://authorize?...' --allow-self-signed` | Same, reading the request URL inline and accepting a self-signed root in the chain (local testing only) |

## In production

The module uses Gradle's standard `application` plugin, so a self-contained distribution is available without any extra setup:

```bash
cd waltid-identity/waltid-applications/waltid-rp-certificate-cli
../../gradlew installDist
```

This produces a launcher script and its dependencies under `build/install/waltid-rp-certificate-cli/`:

```bash
$ build/install/waltid-rp-certificate-cli/bin/waltid-rp-certificate-cli --help
```

`../../gradlew distZip` / `distTar` build a portable archive of the same layout under `build/distributions/` instead.

# Reference

## `waltid-rpcert` command

```
Usage: waltid-rpcert [<options>] <command> [<args>]...

  Issue and validate EUDI Wallet-Relying Party Registration Certificates
  (WRPRC / rc-wrp+jwt).

Options:
  -h, --help  Show this message and exit

Commands:
  issue-jwt     Issue and sign a Wallet-Relying Party Registration Certificate
                (rc-wrp+jwt), printed to stdout.
  validate-jwt  Validate an OpenID4VP Authorization Request against the
                Wallet-Relying Party Registration Certificate found in its
                verifier_info; prints true/false and exits 0/1 accordingly.
```

## `waltid-rpcert issue-jwt` command

```
Usage: waltid-rpcert issue-jwt [<options>]

  Issue and sign a Wallet-Relying Party Registration Certificate (rc-wrp+jwt),
  printed to stdout.

Options:
  --payload=<path>    Path to a JSON file with the
                      RelyingPartyRegistrationCertificate payload
  --key=<path>        Path to a JWK signing key file (use together with --x5c)
  --x5c=<path>        Path to a DER certificate file for the x5c chain, leaf
                      first (repeatable, use together with --key)
  --generate-demo-ca  Generate an ephemeral self-signed CA and signing key
                      instead of --key/--x5c
  -h, --help          Show this message and exit
```

`--payload` is always required. The signing key and certificate chain come from either:

* `--key` + one or more `--x5c` (leaf certificate first) — real key/certificate material, DER-encoded files; or
* `--generate-demo-ca` — an ephemeral, throwaway self-signed CA and signing key generated on the fly. **Local testing only** — the resulting chain has no real trust anchor.

The certificate payload is a JSON file matching `RelyingPartyRegistrationCertificate` (ETSI TS 119 475, clause 5.2.4, Table 7):

```json
{
  "name": "Example Bank AG",
  "sub": "EUID:ATU12345678",
  "country": "AT",
  "registry_uri": "https://registry.example.at",
  "srv_description": [[{"lang": "en", "content": "Account opening identification"}]],
  "entitlements": ["Service_Provider"],
  "privacy_policy": "https://bank.example.com/privacy",
  "supervisory_authority": {"email": "office@dsb.gv.at"},
  "iat": 1780000000,
  "purpose": [{"lang": "en", "content": "Identify customers for account opening"}],
  "credentials": [
    {
      "format": "mso_mdoc",
      "meta": {"doctype_value": "eu.europa.ec.eudi.pid.1"},
      "claim": [
        {"path": ["eu.europa.ec.eudi.pid.1", "given_name"]},
        {"path": ["eu.europa.ec.eudi.pid.1", "family_name"]}
      ]
    }
  ]
}
```

```bash
$ ../../gradlew run --args="issue-jwt --payload payload.json --key key.jwk --x5c leaf.der --x5c root.der"
eyJ4NWMiOlsiTUlJQmxU...(x5c chain, typ=rc-wrp+jwt)...eyJuYW1lIjoiRXhhbXBsZSBCYW5rIEFHIi...
```

## `waltid-rpcert validate-jwt` command

```
Usage: waltid-rpcert validate-jwt [<options>]

  Validate an OpenID4VP Authorization Request against the Wallet-Relying Party
  Registration Certificate found in its verifier_info; prints true/false and
  exits 0/1 accordingly.

Options:
  --request=<text>       openid4vp://authorize?... Authorization Request URL.
                         Prefix with @ to read it from a file.
  --trust-anchor=<path>  DER-encoded trust anchor certificate the registration
                         certificate's x5c chain must chain up to (repeatable)
  --allow-self-signed    Accept a self-signed root certificate within the x5c
                         chain as trust anchor (insecure, local testing only)
  -h, --help             Show this message and exit
```

`--request` takes an `openid4vp://authorize?...` Authorization Request URL, either inline or (prefixed with `@`) read
from a file. Three shapes are supported, resolved the same way a real wallet would:

* inline query parameters (e.g. `?client_id=...&dcql_query=...`);
* an inline signed `request` JWT parameter;
* a `request_uri` parameter — fetched over HTTP (`GET` or `POST`, per `request_uri_method`), including client-id-prefix
  signature authentication of the fetched request object.

There is no dedicated OpenID4VP field for a Wallet-Relying Party Registration Certificate: the command looks for it
among the request's `verifier_info` attestations, picking the one whose JWT `typ` header is `rc-wrp+jwt`. It fails
clearly if none (or more than one) is found.

By default the certificate's `x5c` chain must be trusted via `--trust-anchor` (repeatable, DER-encoded). `--allow-self-signed`
accepts a self-signed root bundled in the `x5c` chain itself instead — do not use this for anything that needs a
genuinely trusted registrar; it exists for local testing only.

```bash
$ ../../gradlew run --args="validate-jwt --request 'openid4vp://authorize?client_id=...&verifier_info=...' --trust-anchor ca-root.der"
ALLOWED: registration certificate 'Example Bank AG' covers all requested claims
true
$ echo $?
0
```

```bash
$ ../../gradlew run --args="validate-jwt --request 'openid4vp://authorize?client_id=...&verifier_info=...' --trust-anchor ca-root.der"
REJECTED: registration certificate 'Example Bank AG' does not cover all requested claims
false
$ echo $?
1
```

# Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

# License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
