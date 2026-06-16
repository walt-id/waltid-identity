@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.openid4vp.conformance.testplans.plans

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.openid4vp.conformance.testplans.runner.req.ExpectedVerifierOutcome
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.UrlConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionRedirects
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SdJwtVcX509SanDnsRequestUriSignedDirectPostJwt(
    verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session",

    conformanceHost: String = "localhost.emobix.co.uk",
    conformancePort: Int = 8443
) : TestPlan {

    // Verifier
    val verifierKey =
        Json.decodeFromString<DirectSerializedKey>("""{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}""")
    val verifierCertificateChain = listOf(
        // Leaf cert: CN=verifier.example.com, issued by walt.id Verifier CA (not self-signed)
        // SAN DNS: verifier.example.com
        // Trust anchor (walt.id Verifier CA) is configured separately via request_object_trust_anchor_pem
        // and must NOT be included in the x5c chain per OID4VP-1FINAL-5.9.3
        "MIIB1DCCAXqgAwIBAgIUIwFilmYdNfDNrzQ2YxHRvXZVRxYwCgYIKoZIzj0EAwIwMDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5pZDAeFw0yNjA1MTkwNDA4MTZaFw0yNzA1MTkwNDA4MTZaMDExHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMRAwDgYDVQQKDAd3YWx0LmlkMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG/TgBc0BkmMipiQ/6gkamIn3mmp7hcTrZuyrLTmknP1WRExl1dhdIx9/kAkuuceI3THkxXq7/y+sBzK0ZR7jPqNxMG8wDAYDVR0TAQH/BAIwADAfBgNVHREEGDAWghR2ZXJpZmllci5leGFtcGxlLmNvbTAdBgNVHQ4EFgQUgiWdm4wdVbizPJbzfHvzODGJi78wHwYDVR0jBBgwFoAUXYaP+ypRou+GJnixaizH2x+nEpMwCgYIKoZIzj0EAwIDSAAwRQIgfp2vzdTnzzjPlOyu9oUMDgPIfgJ1MrK0HbCnnK3oBH8CIQDre3cP/D1jGLma8XHSWftWaWPHpkjqIV+z7kNyVPXanQ=="
    )

    // Wallet
    val signerCertificateChain = Json.encodeToString(
        listOf("MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB")
    )


    // language=JSON
    private val signerJwk = """
        {
            "kty": "EC",
            "crv": "P-256",
            "alg": "ES256",
            
            
            "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                    
            "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
            "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E",
            
            "x5c": $signerCertificateChain
        }
    """.trimIndent()

    // DCQL

    // language=JSON
    val dcqlQuery = """
        {
            "credentials": [
                {
                    "id": "pid",
                    "format": "dc+sd-jwt",
                    "meta": {
                        "vct_values": ["https://credentials.example.com/identity_credential"]
                    },
                    "claims": [
                        {"path": ["given_name"]},
                        {"path": ["family_name"]},
                        {"path": ["birthdate"]},
                        {"path": ["age_in_years"]}
                    ]
                }
            ]
        }
    """.trimIndent()


    override val config = TestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", "oid4vp-1final-verifier-haip-test-plan")
            append(
                "variant", /* language=json*/
                """{
                           "credential_format": "sd_jwt_vc",
                           "response_mode": "direct_post.jwt"
                         }""".trimIndent().replace(" ", "")
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "credential": {
                    "signing_jwk": $signerJwk
                },
                "client": {
                    "client_id": "test123",
                    "request_object_trust_anchor_pem": "-----BEGIN CERTIFICATE-----\nMIIBlzCCAT2gAwIBAgIUUffF2b0tyOxgDu7q+kMpwY3pfNUwCgYIKoZIzj0EAwIw\nMDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5p\nZDAeFw0yNjA1MTkwNDA4MTZaFw0zNjA1MTYwNDA4MTZaMDAxHDAaBgNVBAMME3dh\nbHQuaWQgVmVyaWZpZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIB\nBggqhkjOPQMBBwNCAAQnFYwN1ypusrveHnOwC2ZFBT6PosWX5l1caoRPoziV8jn8\nEJx0uKD5RHC0p1CbYGHBqE74YUw7xlydTT1jXfCsozUwMzASBgNVHRMBAf8ECDAG\nAQH/AgEAMB0GA1UdDgQWBBRdho/7KlGi74YmeLFqLMfbH6cSkzAKBggqhkjOPQQD\nAgNIADBFAiEAudxJV83uP0g5zLXI85ExlkRMKZI52mkBkk074ST2KPACIEsFnJDr\nxtEgGXjHNMaUj7FOpC4tJyGlg2DSpXSOlCkl\n-----END CERTIFICATE-----"
                },
                "description": "Verifier - sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post",
                "server": {
                  "authorization_endpoint": "https://$conformanceHost:$conformancePort"
                },
                "publish": "everything"
            }
        """.trimIndent()
        ),


        moduleVariant = """{"client_id_prefix":"x509_hash","request_method":"request_uri_signed","vp_profile":"haip"}""",

        moduleOutcomes = mapOf(
            "oid4vp-1final-verifier-happy-flow"                   to ExpectedVerifierOutcome.ACCEPT,
            "oid4vp-1final-verifier-minimal-cnf-jwk"              to ExpectedVerifierOutcome.ACCEPT,
            "oid4vp-1final-verifier-request-uri-method-post"      to ExpectedVerifierOutcome.ACCEPT_OR_SKIP,
            "oid4vp-1final-verifier-invalid-kb-jwt-signature"     to ExpectedVerifierOutcome.REJECT,
            "oid4vp-1final-verifier-invalid-credential-signature" to ExpectedVerifierOutcome.REJECT,
            "oid4vp-1final-verifier-invalid-sd-hash"              to ExpectedVerifierOutcome.REJECT,
            "oid4vp-1final-verifier-invalid-kb-jwt-nonce"         to ExpectedVerifierOutcome.REJECT,
            "oid4vp-1final-verifier-invalid-kb-jwt-aud"           to ExpectedVerifierOutcome.REJECT,
            "oid4vp-1final-verifier-kb-jwt-iat-in-past"           to ExpectedVerifierOutcome.REJECT,
            "oid4vp-1final-verifier-kb-jwt-iat-in-future"         to ExpectedVerifierOutcome.REJECT,
        ),

        verificationSessionSetup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = Json.decodeFromString(dcqlQuery),
                policies = Verification2Session.DefinedVerificationPolicies(),

                signedRequest = true,
                encryptedResponse = true,

                clientId = "x509_hash:1P4N1ojALCdefwgW0rES-vAUOHmHKBebWpAbi_YbGR4",

                key = verifierKey,
                x5c = verifierCertificateChain
            ),
            urlConfig = UrlConfig(
                urlPrefix = verifier2UrlPrefix,
                //urlHost, // <-- set by TestPlanRunner
            ),
            redirects = VerificationSessionRedirects(
                successRedirectUri = "https://example.org/verifcation-success"
            )
        )
    )
}
