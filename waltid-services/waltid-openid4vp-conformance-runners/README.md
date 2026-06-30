# OpenID4VCI/VP Conformance Test Runners

Kotlin test runners for OpenID Foundation conformance suite.

## Quick Start

See **[QUICKSTART.md](./QUICKSTART.md)** for:
- 5-minute setup with Cloudflare Tunnel
- Running issuer conformance tests
- Test configuration options

## What's Tested

- **OpenID4VCI Issuer**: Metadata, PAR, authorization code flow, pre-authorized code, DPoP, client attestation
- **OpenID4VP Verifier**: (separate test plans)

## Architecture

- **Test Plans**: `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/`
- **Runner**: `src/main/kotlin/id/walt/openid4vp/conformance/testplans/runner/`
- **Tests**: `src/test/kotlin/id/walt/openid4vp/conformance/`

## License

See [NOTICE.md](NOTICE.md) and [THIRD-PARTY-NOTICE.md](THIRD-PARTY-NOTICE.md).
