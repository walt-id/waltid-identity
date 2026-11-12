# ``CredentialStatusObservation/init(from:)``

Decodes a shared credential-status value using the configured timestamp decoder.

- Parameter decoder: Decoder positioned at this status value.

Malformed data throws a decoding error. Wallet bridge operations sanitize that failure before returning it to the app.
