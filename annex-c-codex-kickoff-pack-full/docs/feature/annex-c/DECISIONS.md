# Annex C Support – Decisions (ADRs)

## ADR-0001: Separate library for Annex C exchange
**Decision:** Implement the Annex C exchange (request + response + HPKE) in a new library `waltid-18013-7-verifier`.
**Rationale:** Clean separation from OID4VP flows; enables reuse in other services; keeps verifier2 API thin.

## ADR-0002: Keep endpoints in verifier2 API
**Decision:** Similar like https://verifier2.portal.test.waltid.cloud/swagger/index.html#/ expose the following endpoints in `waltid-verifier-api2`:
- `/annex-c/create` (take configuration and creates session)
- `/annex-c/request` (returns credential request that can directly be passed to the DC API)
- `/annex-c/response` (takes the response from the DC API and kicks off the asynchronous validation)
- `/annex-c/info` (session state as well as the validation result, once the processing is completed)
**Rationale:** verifier2 already contains policy validation + deployment; easiest adoption for clients.

## ADR-0003: Strict protocol identifier
**Decision:** Use `"org.iso.mdoc"` as protocol string in API response.
**Rationale:** Normative requirement for DC API Annex C compatibility.

## ADR-0004: Transaction-scoped HPKE keypair with TTL
**Decision:** Generate P-256 recipient keypair per transaction; store server-side with short TTL.
**Rationale:** Avoid key reuse, minimize key lifetime, simplify state.

## ADR-0005: Origin is an explicit API input
**Decision:** Require `origin` when creating an Annex C session (`/annex-c/create`) and use it for transcript binding and response verification.
**Rationale:** Makes transcript binding explicit and testable; prevents server guessing wrong origin.
