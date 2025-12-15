# References

- ISO/IEC 18013-7 Annex C (DC API flow)
- Apple: Requesting a mobile document on the web
  - https://developer.apple.com/documentation/identitydocumentservices/requesting-a-mobile-document-on-the-web
- Multipaz verifier (useful for interoperability sanity checks)
  - https://verifier.multipaz.org/# W3C DC API (18013-7 Annex C)

Key Annex C requirements summary:
- protocol identifier: `org.iso.mdoc`
- Request contains:
  - deviceRequest = b64url(no-pad) CBOR DeviceRequest
  - encryptionInfo = b64url(no-pad) CBOR EncryptionInfo = ["dcapi", {nonce, recipientPublicKey(COSE_Key)}]
- Response contains:
  - response = b64url(no-pad) CBOR EncryptedResponse = ["dcapi", {enc, cipherText}]
- HPKE single-shot suite (fixed): Base / DHKEM(P-256) / HKDF-SHA256 / AES-128-GCM
- HPKE `info` = CBOR(SessionTranscript)
- SessionTranscript:
  - dcapiInfo = [ Base64EncryptionInfoString, SerializedOrigin ]
  - st = [ null, null, ["dcapi", sha256(cbor(dcapiInfo))] ]
