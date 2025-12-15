# Annex C Support – Codex Kickoff Pack (Full)

Generated: 2025-12-15

This pack is intended to be copied into the root of the `waltid-identity` repository and handed to Codex
to implement ISO/IEC 18013-7 Annex C (DC API / Apple Wallet) support.

Includes:
- Feature Context Pack (spec, architecture, plan, decisions)
- Call-flow diagrams (Mermaid)
- Real-world sample payloads (Multipaz-style capture)
- **Test vector JSON**: `ANNEXC-REAL-001.json`
- **JUnit/Kotlin test harness** that loads the JSON and executes deterministic checks
- **HPKE deterministic test vector template** (expects a real `skR` from your backend session)

> Important: your provided sample capture does not include the server-side recipient private key (`skR`).
> The vector file includes a field for it; once you paste the real value from your backend session store/logs,
> the HPKE test becomes fully deterministic.
