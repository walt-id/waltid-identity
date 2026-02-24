# WAL-670 Implementation Tracker (AuthNZ Phase 0 + 1)

## Scope in this repo
- OIDC external-role extraction (Keycloak realm/client roles)
- Normalized session metadata for downstream authorization mapping
- Config-first and backwards-compatible defaults
- Unit tests + docs

## Progress log

### 2026-02-24
- [x] Created implementation tracker.
- [x] Baseline test run before changes.
- [x] Implement extraction model + config.
- [x] Wire extraction into OIDC callback session data.
- [x] Add unit tests for extraction edge-cases.
- [x] Update `docs/oidc.md`.

### Commands run
- Baseline: `./gradlew :waltid-libraries:auth:waltid-ktor-authnz:test --tests "id.walt.OidcExample" --tests "id.walt.KtorAuthnzE2ETest"`
- New coverage: `./gradlew :waltid-libraries:auth:waltid-ktor-authnz:test --tests "id.walt.OidcExternalRoleExtractorTest"`

## Notes
- Feature must be no-op by default.
- Keep existing OIDC identifier/account resolution behavior unchanged.
