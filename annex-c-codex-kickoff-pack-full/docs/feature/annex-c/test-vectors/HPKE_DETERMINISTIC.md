# HPKE deterministic testing (Annex C)

For fully deterministic crypto tests, you need:
- `skR` (recipient private key) used when creating `EncryptionInfo.recipientPublicKey`
- the captured `EncryptedResponse` (enc + cipherText)
- `origin`
- the exact `encryptionInfoB64` string returned to the browser

Once you fill `recipientPrivateKeyHex` in `ANNEXC-REAL-001.json` and (optionally)
`expected.deviceResponseCborSha256Hex`, the test harness will:
1) recompute SessionTranscript
2) HPKE-decrypt to DeviceResponse CBOR bytes
3) assert SHA-256 of plaintext matches expected

If `expected.deviceResponseCborSha256Hex` is empty, the harness prints it so you can paste it back.
