# Independent NFC reader test oracle

`SelectCorrectedNfcReader.kt` derives from Multipaz `NfcTransportMdocReader.kt` at
[0.100.0 / 7c0988bee3384d13a0732e0c33336ae0faf3b863](https://github.com/openwallet-foundation/multipaz/blob/7c0988bee3384d13a0732e0c33336ae0faf3b863/multipaz/src/commonMain/kotlin/org/multipaz/mdoc/transport/NfcTransportMdocReader.kt).
Upstream source SHA-256: `46fbfd751ca95fc022a67cb4769dd656ca563a3ac14b464724a2d660fa59d931`.
The accompanying `LICENSE` is upstream Apache-2.0. No standard procedures are copied here.
Other reader operations use the test-only `org.multipaz:multipaz-jvm:0.100.0` dependency.

Changes to the upstream file:

- Relocate and rename the class to keep the unmodified peer available as a negative control.
- Encode retrieval SELECT with P2 `0C`, as required by the selected
  ISO/IEC DIS 18013-5:2026 clause 11.2, Table 8. Multipaz's general-purpose
  `selectApplication` sends P2 `00`; the production holder rejects that command.
- Add this provenance header. The peer's framing, message queue and response-chaining code are unchanged.

This is interoperability with a **SELECT-corrected peer**, not a claim that stock Multipaz passes.
The harness routes encoded APDUs unchanged into the production holder. The fixed-profile synthetic
credential is produced by the wallet issuer, but request generation, session encryption/decryption,
issuer and device signature verification, and returned field digest checks run in Multipaz.
Issuer trust is an additional JCA PKIX check against an explicitly configured synthetic root;
revocation is disabled for that offline fixture only. Parsing alone is never the acceptance oracle.

To refresh, compare the source file and its hash against the pinned release; review each change
against the selected standards, preserve the license and this ledger, and run both the unmodified
peer rejection and corrected peer exchange controls. Do not silently drop either control or correct
holder behavior to match peer behavior. These tests exercise software APDUs, not physical NFC.
