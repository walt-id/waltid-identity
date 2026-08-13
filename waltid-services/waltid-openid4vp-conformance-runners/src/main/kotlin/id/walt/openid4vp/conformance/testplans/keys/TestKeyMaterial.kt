package id.walt.openid4vp.conformance.testplans.keys

/**
 * Test key material for conformance testing.
 *
 * Contains certificates, keys, and other cryptographic material
 * used across verifier, wallet, and issuer test plans.
 *
 * IMPORTANT: These are TEST KEYS ONLY - never use in production!
 */
object TestKeyMaterial {

    // ================================
    // Verifier Keys & Certificates
    // ================================

    /**
     * Verifier private key (P-256/ES256)
     */
    const val VERIFIER_KEY_JWK =
        """{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}"""

    /**
     * Verifier leaf certificate (CN=verifier.example.com, SAN DNS=verifier.example.com)
     * Signed by walt.id Verifier CA
     */
    const val VERIFIER_LEAF_CERT =
        "MIIB1DCCAXqgAwIBAgIUIwFilmYdNfDNrzQ2YxHRvXZVRxYwCgYIKoZIzj0EAwIwMDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5pZDAeFw0yNjA1MTkwNDA4MTZaFw0yNzA1MTkwNDA4MTZaMDExHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMRAwDgYDVQQKDAd3YWx0LmlkMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG/TgBc0BkmMipiQ/6gkamIn3mmp7hcTrZuyrLTmknP1WRExl1dhdIx9/kAkuuceI3THkxXq7/y+sBzK0ZR7jPqNxMG8wDAYDVR0TAQH/BAIwADAfBgNVHREEGDAWghR2ZXJpZmllci5leGFtcGxlLmNvbTAdBgNVHQ4EFgQUgiWdm4wdVbizPJbzfHvzODGJi78wHwYDVR0jBBgwFoAUXYaP+ypRou+GJnixaizH2x+nEpMwCgYIKoZIzj0EAwIDSAAwRQIgfp2vzdTnzzjPlOyu9oUMDgPIfgJ1MrK0HbCnnK3oBH8CIQDre3cP/D1jGLma8XHSWftWaWPHpkjqIV+z7kNyVPXanQ=="

    /**
     * Verifier CA certificate (walt.id Verifier CA)
     * Self-signed root CA for testing
     */
    const val VERIFIER_CA_CERT =
        "MIIBlzCCAT2gAwIBAgIUUffF2b0tyOxgDu7q+kMpwY3pfNUwCgYIKoZIzj0EAwIwMDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5pZDAeFw0yNjA1MTkwNDA4MTZaFw0zNjA1MTYwNDA4MTZaMDAxHDAaBgNVBAMME3dhbHQuaWQgVmVyaWZpZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQnFYwN1ypusrveHnOwC2ZFBT6PosWX5l1caoRPoziV8jn8EJx0uKD5RHC0p1CbYGHBqE74YUw7xlydTT1jXfCsozUwMzASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBRdho/7KlGi74YmeLFqLMfbH6cSkzAKBggqhkjOPQQDAgNIADBFAiEAudxJV83uP0g5zLXI85ExlkRMKZI52mkBkk074ST2KPACIEsFnJDrxtEgGXjHNMaUj7FOpC4tJyGlg2DSpXSOlCkl"

    /**
     * Verifier CA certificate in PEM format (for conformance suite configuration)
     */
    const val VERIFIER_CA_PEM = """-----BEGIN CERTIFICATE-----
MIIBlzCCAT2gAwIBAgIUUffF2b0tyOxgDu7q+kMpwY3pfNUwCgYIKoZIzj0EAwIw
MDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5p
ZDAeFw0yNjA1MTkwNDA4MTZaFw0zNjA1MTYwNDA4MTZaMDAxHDAaBgNVBAMME3dh
bHQuaWQgVmVyaWZpZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIB
BggqhkjOPQMBBwNCAAQnFYwN1ypusrveHnOwC2ZFBT6PosWX5l1caoRPoziV8jn8
EJx0uKD5RHC0p1CbYGHBqE74YUw7xlydTT1jXfCsozUwMzASBgNVHRMBAf8ECDAG
AQH/AgEAMB0GA1UdDgQWBBRdho/7KlGi74YmeLFqLMfbH6cSkzAKBggqhkjOPQQD
AgNIADBFAiEAudxJV83uP0g5zLXI85ExlkRMKZI52mkBkk074ST2KPACIEsFnJDr
xtEgGXjHNMaUj7FOpC4tJyGlg2DSpXSOlCkl
-----END CERTIFICATE-----"""

    /**
     * Verifier CA PEM as JSON string (escaped for JSON embedding)
     */
    val VERIFIER_CA_PEM_JSON: String
        get() = "\"${VERIFIER_CA_PEM.replace("\n", "\\n")}\""

    /**
     * Verifier signing JWK set for the `client.jwks` field of a wallet test configuration.
     *
     * In wallet test plans the conformance suite plays the verifier, so it needs the *private*
     * key to sign request objects, plus the `x5c` chain that backs the `client_id`. The suite
     * fails with "client.jwks is missing from configuration or not a JSON object"
     * (`SetClientIdToX509Hash`) when this is absent, and requires an `x5c` entry on the signing
     * key. `x509_hash` client ids are the SHA-256 of the DER of `x5c[0]`.
     *
     * Only the leaf belongs in `x5c`: per OID4VP-1FINAL-5.9.3 the trust anchor is configured out of
     * band by the relying party, and including it in the chain makes chain validation reject the
     * request object as unverifiable.
     */
    val VERIFIER_SIGNING_JWKS: String
        get() = """{"keys":[$SUITE_VERIFIER_SIGNING_KEY]}"""

    /**
     * Signing key the conformance suite uses when it plays the verifier in wallet test plans.
     *
     * Distinct from [VERIFIER_KEY_JWK]: Wallet2 authenticates an `x509_san_dns` / `x509_hash` client
     * identifier with `validateClientAuthenticationCertificateChain`, which requires the leaf to
     * carry the `clientAuth` extended key usage (OID 1.3.6.1.5.5.7.3.2). [VERIFIER_LEAF_CERT] has no
     * EKU at all, so it cannot be used here.
     *
     * Leaf `CN=verifier.example.com` (SAN `verifier.example.com`, EKU `clientAuth`) is issued by
     * [CREDENTIAL_ISSUER_CA_PEM]. Only the leaf goes in `x5c`: per OID4VP-1FINAL-5.9.3 the trust
     * anchor is configured out of band, and including it makes chain validation reject the request.
     */
    const val SUITE_VERIFIER_SIGNING_KEY =
        """{"kty":"EC","crv":"P-256","alg":"ES256","use":"sig","kid":"conformance-suite-verifier","d":"xSG28uAwFg66brJLL6mM7cFxRxBpPW94OcRDSnH6U_I","x":"rdbIuZHczJDvu1r8WdLISCm60Crkc2n0I03wrCdB6ek","y":"CRrCael02Z3qkPuuj1xVmCqXpK_GcKcaH2t-AnGEjSc","x5c":["MIICGTCCAb6gAwIBAgIBAjAKBggqhkjOPQQDAjBQMS8wLQYDVQQDDCZ3YWx0LmlkIE9wZW5JRDRWQ0kgQ29uZm9ybWFuY2UgVGVzdCBDQTEQMA4GA1UECgwHd2FsdC5pZDELMAkGA1UEBhMCVVQwHhcNMjYwODExMTUyNDU1WhcNMzYwODA4MTUyNDU1WjA+MQswCQYDVQQGEwJVVDEQMA4GA1UECgwHd2FsdC5pZDEdMBsGA1UEAwwUdmVyaWZpZXIuZXhhbXBsZS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASt1si5kdzMkO+7WvxZ0shIKbrQKuRzafQjTfCsJ0Hp6QkawmnpdNmd6pD7ro9cVZgql6SvxnCnGh9rfgJxhI0no4GaMIGXMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsGAQUFBwMCMB8GA1UdEQQYMBaCFHZlcmlmaWVyLmV4YW1wbGUuY29tMB0GA1UdDgQWBBS9L0MCwYBRFR/2zhXvclIucH8x0TAfBgNVHSMEGDAWgBRQZ/DWHFTxa0sdrlGc/51VEg1VNjAKBggqhkjOPQQDAgNJADBGAiEAqOyuorenm73UuDECNQzYDqaPTv1ZC9JMWlboIbKowdACIQDllusv3QC+JO7cJIy3vwj+DsqPGK5OIZyOVRNPWKFd9w=="]}"""

    /** Leaf certificate of [SUITE_VERIFIER_SIGNING_KEY] (`CN=verifier.example.com`, EKU `clientAuth`). */
    const val SUITE_VERIFIER_LEAF_CERT =
        "MIICGTCCAb6gAwIBAgIBAjAKBggqhkjOPQQDAjBQMS8wLQYDVQQDDCZ3YWx0LmlkIE9wZW5JRDRWQ0kgQ29uZm9ybWFuY2UgVGVzdCBDQTEQMA4GA1UECgwHd2FsdC5pZDELMAkGA1UEBhMCVVQwHhcNMjYwODExMTUyNDU1WhcNMzYwODA4MTUyNDU1WjA+MQswCQYDVQQGEwJVVDEQMA4GA1UECgwHd2FsdC5pZDEdMBsGA1UEAwwUdmVyaWZpZXIuZXhhbXBsZS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASt1si5kdzMkO+7WvxZ0shIKbrQKuRzafQjTfCsJ0Hp6QkawmnpdNmd6pD7ro9cVZgql6SvxnCnGh9rfgJxhI0no4GaMIGXMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsGAQUFBwMCMB8GA1UdEQQYMBaCFHZlcmlmaWVyLmV4YW1wbGUuY29tMB0GA1UdDgQWBBS9L0MCwYBRFR/2zhXvclIucH8x0TAfBgNVHSMEGDAWgBRQZ/DWHFTxa0sdrlGc/51VEg1VNjAKBggqhkjOPQQDAgNJADBGAiEAqOyuorenm73UuDECNQzYDqaPTv1ZC9JMWlboIbKowdACIQDllusv3QC+JO7cJIy3vwj+DsqPGK5OIZyOVRNPWKFd9w=="

    /**
     * [SUITE_VERIFIER_SIGNING_KEY] in walt.id serialized form, for Verifier2's request signing.
     *
     * Verifier2 uses this rather than [VERIFIER_KEY_JWK] so that the certificate it signs request
     * objects with carries the `clientAuth` extended key usage that Wallet2 requires when
     * authenticating an `x509_san_dns` / `x509_hash` client identifier. With the older EKU-less
     * certificate a walt.id verifier could not authenticate to a walt.id wallet.
     */
    val SUITE_VERIFIER_SERIALIZED_KEY: String
        get() = """{"type":"jwk","jwk":$SUITE_VERIFIER_SIGNING_KEY}"""

    // ================================
    // Wallet OAuth client identity
    // ================================

    /**
     * The wallet's OAuth client key for VCI wallet conformance runs, used to sign the RFC 7523
     * `private_key_jwt` client assertion at the token endpoint.
     *
     * VCI wallet plans must register the public half as `client.jwks` with this exact `kid`:
     * `AbstractVCIWalletTest` rules out `none` and every `client_secret_*` method, so the wallet has
     * to authenticate with a key the suite already holds. The `kid` is what lets the suite select it.
     *
     * The wallet imports this key rather than generating one, because a freshly generated key could
     * not match the pre-registered JWKS. The same key also binds the issued credential - a wallet
     * acting as its own OAuth client has one instance key.
     */
    const val SUITE_WALLET_CLIENT_KEY =
        """{"kty":"EC","crv":"P-256","alg":"ES256","use":"sig","kid":"wallet-static-key","d":"c6TUFwkoQ8QMiz1wZ-4BqJJzvD56RRlcgn0R-XKqQjk","x":"d5KVpCdze-46QteHfgAswRurlSYUylJ1JntvcbaZ__Y","y":"uqvaPeOm7SGsdXr34frqkJGAz8tHmR0EmpsSbfqgwDA"}"""

    /** [SUITE_WALLET_CLIENT_KEY] in walt.id serialized form, for wallet key import. */
    val SUITE_WALLET_CLIENT_SERIALIZED_KEY: String
        get() = """{"type":"jwk","jwk":$SUITE_WALLET_CLIENT_KEY}"""

    /** Public half of [SUITE_WALLET_CLIENT_KEY], as VCI wallet plans must register it in `client.jwks`. */
    val SUITE_WALLET_CLIENT_PUBLIC_JWK: String
        get() = """{"kty":"EC","crv":"P-256","x":"d5KVpCdze-46QteHfgAswRurlSYUylJ1JntvcbaZ__Y","y":"uqvaPeOm7SGsdXr34frqkJGAz8tHmR0EmpsSbfqgwDA","use":"sig","alg":"ES256","kid":"wallet-static-key"}"""

    // ================================
    // Wallet-held Credential Issuer
    // ================================

    /**
     * Issuer key for credentials provisioned into the wallet during wallet conformance runs.
     *
     * Leaf `CN=walt.id Conformance Credential Issuer` is issued by
     * [CREDENTIAL_ISSUER_CA_PEM], so the conformance suite (acting as the verifier) can build a
     * chain from the presented credential to a configured trust anchor.
     */
    const val CREDENTIAL_ISSUER_KEY_WITH_X5C =
        """{"kty":"EC","crv":"P-256","alg":"ES256","d":"fg9ocgw9QtOXLJmFXbVXk-QcxapRzdxd9ogqVIJvrW8","x":"yPEmBYGbqaZ16b1DztnI8e6Ejw1-4rULH73T8X0DY4I","y":"rYgQz3UWBHkF-LZHgtpEI36Wynd5YLqp0ZNbiFRZuNc","x5c":["MIICATCCAaagAwIBAgIBAjAKBggqhkjOPQQDAjBQMS8wLQYDVQQDDCZ3YWx0LmlkIE9wZW5JRDRWQ0kgQ29uZm9ybWFuY2UgVGVzdCBDQTEQMA4GA1UECgwHd2FsdC5pZDELMAkGA1UEBhMCVVQwHhcNMjYwODExMTQxNjA5WhcNMzYwODA4MTQxNjA5WjBPMQswCQYDVQQGEwJVVDEQMA4GA1UECgwHd2FsdC5pZDEuMCwGA1UEAwwld2FsdC5pZCBDb25mb3JtYW5jZSBDcmVkZW50aWFsIElzc3VlcjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABMjxJgWBm6mmdem9Q87ZyPHuhI8NfuK1Cx+90/F9A2OCrYgQz3UWBHkF+LZHgtpEI36Wynd5YLqp0ZNbiFRZuNejcjBwMAkGA1UdEwQCMAAwDgYDVR0PAQH/BAQDAgeAMBMGA1UdJQQMMAoGCCsGAQUFBwMCMB0GA1UdDgQWBBTVuBRTUgLVLOydkSRoeSY0PapMgDAfBgNVHSMEGDAWgBRQZ/DWHFTxa0sdrlGc/51VEg1VNjAKBggqhkjOPQQDAgNJADBGAiEAt2YNqH9W2HUQ3CaZX6mzWSen6K1W7V+g0R4cBB5VdoACIQDltQA2OQriOnhhYEO3x7eoMyDbrlVilkExLCZnBbSV8g=="]}"""

    /** Trust anchor for [CREDENTIAL_ISSUER_KEY_WITH_X5C] (`walt.id OpenID4VCI Conformance Test CA`). */
    const val CREDENTIAL_ISSUER_CA_PEM = """-----BEGIN CERTIFICATE-----
MIICCTCCAa6gAwIBAgIUd2OgSqKSx5bt1dwVpxyOsdBrCwEwCgYIKoZIzj0EAwIw
UDEvMC0GA1UEAwwmd2FsdC5pZCBPcGVuSUQ0VkNJIENvbmZvcm1hbmNlIFRlc3Qg
Q0ExEDAOBgNVBAoMB3dhbHQuaWQxCzAJBgNVBAYTAlVUMB4XDTI2MDcxMzE2MTYz
M1oXDTM2MDcxMDE2MTYzM1owUDEvMC0GA1UEAwwmd2FsdC5pZCBPcGVuSUQ0VkNJ
IENvbmZvcm1hbmNlIFRlc3QgQ0ExEDAOBgNVBAoMB3dhbHQuaWQxCzAJBgNVBAYT
AlVUMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEcKWoEYWPMA8sMHQt4Whhdnyb
eGY4uxNJ61K8qEkR7yjxpDPlTUwMLoFY4LwvDZbmrd1wuAQzC19vN3ZCKy0waqNm
MGQwHwYDVR0jBBgwFoAUUGfw1hxU8WtLHa5RnP+dVRINVTYwEgYDVR0TAQH/BAgw
BgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFFBn8NYcVPFrSx2uUZz/
nVUSDVU2MAoGCCqGSM49BAMCA0kAMEYCIQC/45X54n1VyZuAN8vmin6cluuoNBD5
VACJ445Tx9FAuQIhAN6yqTj1u30N51FsULyrdbwXRgBRo7CgE1CZC9ejeD1E
-----END CERTIFICATE-----"""

    /** [CREDENTIAL_ISSUER_CA_PEM] as a JSON string literal, for embedding in suite configuration. */
    val CREDENTIAL_ISSUER_CA_PEM_JSON: String
        get() = "\"${CREDENTIAL_ISSUER_CA_PEM.replace("\n", "\\n")}\""

    // ================================
    // mDOC Issuer Certificates
    // ================================

    /**
     * mDOC issuer certificate for testing mDL credentials
     * Used by conformance suite to issue test mDL documents
     */
    const val MDOC_ISSUER_CERT =
        "MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB"

    /**
     * mDOC issuer certificate as JSON array (for conformance suite configuration)
     */
    val MDOC_ISSUER_CERT_JSON_ARRAY: String
        get() = "[\"$MDOC_ISSUER_CERT\"]"

    // ================================
    // SD-JWT VC Issuer Keys
    // ================================

    /**
     * SD-JWT VC issuer key (P-256/ES256)
     * Used by conformance suite to issue test SD-JWT VCs
     */
    const val SDJWT_ISSUER_KEY_JWK =
        """{"kty":"EC","crv":"P-256","alg":"ES256","d":"KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}"""

    /**
     * SD-JWT VC issuer key with x5c chain (for HAIP compliance)
     */
    const val SDJWT_ISSUER_KEY_WITH_X5C =
        """{"kty":"EC","crv":"P-256","alg":"ES256","d":"KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E","x5c":["MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB"]}"""

    // ================================
    // Helper Methods
    // ================================
    /**
     * Get verifier certificate chain as list.
     */
    fun getVerifierCertificateChain(): List<String> = listOf(VERIFIER_LEAF_CERT)
}
