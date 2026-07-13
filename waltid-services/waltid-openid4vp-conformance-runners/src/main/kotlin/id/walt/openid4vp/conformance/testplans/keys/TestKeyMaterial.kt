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
    const val VERIFIER_KEY_JWK = """{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}"""

    /**
     * Verifier leaf certificate (CN=verifier.example.com, SAN DNS=verifier.example.com)
     * Signed by walt.id Verifier CA
     */
    const val VERIFIER_LEAF_CERT = "MIIB1DCCAXqgAwIBAgIUIwFilmYdNfDNrzQ2YxHRvXZVRxYwCgYIKoZIzj0EAwIwMDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5pZDAeFw0yNjA1MTkwNDA4MTZaFw0yNzA1MTkwNDA4MTZaMDExHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMRAwDgYDVQQKDAd3YWx0LmlkMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG/TgBc0BkmMipiQ/6gkamIn3mmp7hcTrZuyrLTmknP1WRExl1dhdIx9/kAkuuceI3THkxXq7/y+sBzK0ZR7jPqNxMG8wDAYDVR0TAQH/BAIwADAfBgNVHREEGDAWghR2ZXJpZmllci5leGFtcGxlLmNvbTAdBgNVHQ4EFgQUgiWdm4wdVbizPJbzfHvzODGJi78wHwYDVR0jBBgwFoAUXYaP+ypRou+GJnixaizH2x+nEpMwCgYIKoZIzj0EAwIDSAAwRQIgfp2vzdTnzzjPlOyu9oUMDgPIfgJ1MrK0HbCnnK3oBH8CIQDre3cP/D1jGLma8XHSWftWaWPHpkjqIV+z7kNyVPXanQ=="

    /**
     * Verifier CA certificate (walt.id Verifier CA)
     * Self-signed root CA for testing
     */
    const val VERIFIER_CA_CERT = "MIIBlzCCAT2gAwIBAgIUUffF2b0tyOxgDu7q+kMpwY3pfNUwCgYIKoZIzj0EAwIwMDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5pZDAeFw0yNjA1MTkwNDA4MTZaFw0zNjA1MTYwNDA4MTZaMDAxHDAaBgNVBAMME3dhbHQuaWQgVmVyaWZpZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQnFYwN1ypusrveHnOwC2ZFBT6PosWX5l1caoRPoziV8jn8EJx0uKD5RHC0p1CbYGHBqE74YUw7xlydTT1jXfCsozUwMzASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBRdho/7KlGi74YmeLFqLMfbH6cSkzAKBggqhkjOPQQDAgNIADBFAiEAudxJV83uP0g5zLXI85ExlkRMKZI52mkBkk074ST2KPACIEsFnJDrxtEgGXjHNMaUj7FOpC4tJyGlg2DSpXSOlCkl"

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
     * Root CA certificate for the newer verifier chain:
     * verifier leaf -> verifier intermediate -> verifier root.
     */
    const val VERIFIER_ROOT_CA_PEM = """-----BEGIN CERTIFICATE-----
MIIBvzCCAWWgAwIBAgIUfwihQAhmEdaEwBYsG+ejcHcFjTwwCgYIKoZIzj0EAwIw
NTEhMB8GA1UEAwwYd2FsdC5pZCBWZXJpZmllciBSb290IENBMRAwDgYDVQQKDAd3
YWx0LmlkMB4XDTI2MDcwMTEwMDUyNVoXDTM2MDYyODEwMDUyNVowNTEhMB8GA1UE
AwwYd2FsdC5pZCBWZXJpZmllciBSb290IENBMRAwDgYDVQQKDAd3YWx0LmlkMFkw
EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXBk/JIOavtCGTtnheu6Ow3KEUzrXANwX
P2XfbZQ+MG8jwJy37glKsQdJqs2t+l4AnlU10881D27TFUm5aq5286NTMFEwHQYD
VR0OBBYEFNEEQtfaftObiHN0R3y3rMCfAONHMB8GA1UdIwQYMBaAFNEEQtfaftOb
iHN0R3y3rMCfAONHMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIh
AMDtiBAc264oLWmkjWbckhhZ/XbC9rCdBV5Lu3M/aWCAAiBKUMq7gaf7i9iKNT30
gw4g1u9yPw6wqf/QCx3ODl3BJg==
-----END CERTIFICATE-----"""

    val VERIFIER_ROOT_CA_PEM_JSON: String
        get() = "\"${VERIFIER_ROOT_CA_PEM.replace("\n", "\\n")}\""

    // ================================
    // mDOC Issuer Certificates
    // ================================

    /**
     * mDOC issuer certificate for testing mDL credentials
     * Used by conformance suite to issue test mDL documents
     */
    const val MDOC_ISSUER_CERT = "MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB"

    /**
     * mDOC issuer certificate as JSON array (for conformance suite configuration)
     */
    val MDOC_ISSUER_CERT_JSON_ARRAY: String
        get() = "[\"$MDOC_ISSUER_CERT\"]"

    // ================================
    // SD-JWT VC Issuer Keys & Certificates
    // ================================

    /**
     * SD-JWT VC Issuer CA certificate (walt.id Issuer CA)
     * Self-signed root CA for issuing SD-JWT VC credentials
     * Used as trust anchor for credential validation in wallet conformance tests
     */
    const val ISSUER_CA_CERT = "MIIBsDCCAVegAwIBAgIUD7VMTYFSHCCKaME4UTehV2rsbvkwCgYIKoZIzj0EAwIwLjEaMBgGA1UEAwwRd2FsdC5pZCBJc3N1ZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwHhcNMjYwNzEzMTQyNjQwWhcNMzYwNzEwMTQyNjQwWjAuMRowGAYDVQQDDBF3YWx0LmlkIElzc3VlciBDQTEQMA4GA1UECgwHd2FsdC5pZDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABH+SamerI90NTCuDNSupIo0rvJRegHl2yTkKva2OOUXfXywgh7AbDGAiWxBEmedHDivpUCIoyxe+SuAVvDFfUxujUzBRMB0GA1UdDgQWBBRbugmrpGAaDgyj77GgBQevG29eyjAfBgNVHSMEGDAWgBRbugmrpGAaDgyj77GgBQevG29eyjAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIGZmRguYeYoBxNyjyEF8gBXFFVJTOIcGLBuBs2cYxN1TAiAQPvIuddRQdrm3Dc+S6sdK4P8/7zsYbmiGrqELhqT4+g=="

    /**
     * SD-JWT VC Issuer CA certificate in PEM format
     */
    const val ISSUER_CA_PEM = """-----BEGIN CERTIFICATE-----
MIIBsDCCAVegAwIBAgIUD7VMTYFSHCCKaME4UTehV2rsbvkwCgYIKoZIzj0EAwIw
LjEaMBgGA1UEAwwRd2FsdC5pZCBJc3N1ZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQw
HhcNMjYwNzEzMTQyNjQwWhcNMzYwNzEwMTQyNjQwWjAuMRowGAYDVQQDDBF3YWx0
LmlkIElzc3VlciBDQTEQMA4GA1UECgwHd2FsdC5pZDBZMBMGByqGSM49AgEGCCqG
SM49AwEHA0IABH+SamerI90NTCuDNSupIo0rvJRegHl2yTkKva2OOUXfXywgh7Ab
DGAiWxBEmedHDivpUCIoyxe+SuAVvDFfUxujUzBRMB0GA1UdDgQWBBRbugmrpGAa
Dgyj77GgBQevG29eyjAfBgNVHSMEGDAWgBRbugmrpGAaDgyj77GgBQevG29eyjAP
BgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIGZmRguYeYoBxNyjyEF8
gBXFFVJTOIcGLBuBs2cYxN1TAiAQPvIuddRQdrm3Dc+S6sdK4P8/7zsYbmiGrqEL
hqT4+g==
-----END CERTIFICATE-----"""

    /**
     * SD-JWT VC Issuer CA PEM as JSON string (escaped for JSON embedding)
     */
    val ISSUER_CA_PEM_JSON: String
        get() = "\"${ISSUER_CA_PEM.replace("\n", "\\n")}\""

    /**
     * SD-JWT VC Issuer leaf certificate (CN=issuer.walt.id)
     * Signed by walt.id Issuer CA
     */
    const val ISSUER_LEAF_CERT = "MIIBzDCCAXKgAwIBAgIUdlm7mnmFbjlu0tqZlKro9u+PfjMwCgYIKoZIzj0EAwIwLjEaMBgGA1UEAwwRd2FsdC5pZCBJc3N1ZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwHhcNMjYwNzEzMTQyNjQwWhcNMjcwNzEzMTQyNjQwWjBaMRcwFQYDVQQDDA5pc3N1ZXIud2FsdC5pZDEQMA4GA1UECgwHd2FsdC5pZDELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEGibjXhCooyI29MCa7z3MZMtCt1e_jSQEDQl-b8Ol2KHlL5HT9qxDkPPbuRH39nRplRZhrcfhuklmdLEYDMmESKNCMEAwHQYDVR0OBBYEFLqI0SKpK613gxs1LXdDS8GNLUbHMB8GA1UdIwQYMBaAFFu6CaukYBoODKPvsaAFB68bb17KMAoGCCqGSM49BAMCA0gAMEUCIGOWJtH0LKqE6sBRG+d4RdYxI+7SIjH2hdVuNalW77IJAiEAs9_G9CghrRRVkm-CIqIGv5YSyt7BsbW-bcuApoJQhA4="

    /**
     * SD-JWT VC Issuer private key (P-256/ES256)
     * Corresponds to ISSUER_LEAF_CERT
     */
    const val ISSUER_KEY_JWK = """{"type":"jwk","jwk":{"kty":"EC","d":"ZNGqW-fqvLsO_kscAYNecT6LZ2uNUiL8yd591XoE5l0","crv":"P-256","x":"GibjXhCooyI29MCa7z3MZMtCt1e_jSQEDQl-b8Ol2KE","y":"5S-R0_asQ5Dz27kR9_Z0aZUWYa3H4bpJZnSxGAzJhEg"}}"""

    /**
     * SD-JWT VC Issuer certificate chain for x5c header
     * HAIP-6.1.1: Only leaf cert - trust anchor must NOT be in x5c
     */
    fun getIssuerCertificateChain(): List<String> = listOf(ISSUER_LEAF_CERT)

    /**
     * SD-JWT VC issuer key (P-256/ES256) - LEGACY, uses old MDOC ROOT CA
     * Used by conformance suite to issue test SD-JWT VCs
     * @deprecated Use ISSUER_KEY_JWK with getIssuerCertificateChain() instead
     */
    const val SDJWT_ISSUER_KEY_JWK = """{"kty":"EC","crv":"P-256","alg":"ES256","d":"KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}"""

    /**
     * SD-JWT VC issuer key with x5c chain (for HAIP compliance)
     */
    const val SDJWT_ISSUER_KEY_WITH_X5C = """{"kty":"EC","crv":"P-256","alg":"ES256","d":"KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E","x5c":["MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB"]}"""

    // ================================
    // Helper Methods
    // ================================

    /**
     * Get verifier certificate chain as list.
     */
    fun getVerifierCertificateChain(): List<String> = listOf(VERIFIER_LEAF_CERT)
}
