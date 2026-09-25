# TS-12 payment demo backend

The issuer2 configuration adds the synthetic `scaPaymentCardSdJwt` profile and
`sca_payment_card_sd_jwt` credential configuration (`dc+sd-jwt`). Verifier2 exposes
a matching SD-JWT payment example with numeric amount and nested payee fields.

Deploy the issuer2 service and its matching configuration together. The service
and Docker configurations also publish English/German payment instructions through
the existing VCT routes, using `sdJwtVcTypeMetadataConfiguration`. This supports
the subsequent WAL-1417 wallet consent implementation without another metadata
service or a separate catalogue deployment.

Before testing a wallet, check that:

- Issuer metadata advertises `sca_payment_card_sd_jwt`.
- Issuance uses the same VCT advertised by the configuration.
- Both VCT routes return the configured payment category, transaction type,
  built-in schema identifier, claims and UI catalogue.
- The verifier request binds transaction data to its DCQL query ID and enables
  `dc+sd-jwt/transaction-data-hash-check`.

The public example uses `https://issuer2.demo.walt.id`. A local deployment must
configure its own issuer authority consistently in metadata, profiles and wallet
trust configuration. Do not derive trust from incoming credential headers.

Backend contract and issuance tests run before deployment. Public-demo wallet
acceptance requires the matching deployed backend; local wallet acceptance can
use the matching local service revision instead.

This is a synthetic demo profile, not a registered banking attestation or a
complete regulated SCA implementation. SD-JWT transaction hashes are bound in the
KB-JWT; the existing mdoc demo retains its MSO/device-signature semantics.
