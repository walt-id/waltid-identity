package id.walt.openid4vp.conformance.testplans.plans

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.openid4vp.verifier.data.CrossDeviceFlowSetup
import id.walt.openid4vp.verifier.data.GeneralFlowConfig
import id.walt.openid4vp.verifier.data.UrlConfig
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.data.Verification2Session.VerificationSessionRedirects
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SdJwtVcX509SanDnsRequestUriSignedDirectPost(
    verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session",

    conformanceHost: String = "localhost.emobix.co.uk",
    conformancePort: Int = 8443
) : TestPlan {

    // Verifier
    val verifierKey =
        Json.decodeFromString<DirectSerializedKey>("""{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}""")
    val verifierCertificateChain =
        listOf("MIIBVzCB/aADAgECAggNKZAvUrtimzAKBggqhkjOPQQDAjAfMR0wGwYDVQQDDBR2ZXJpZmllci5leGFtcGxlLmNvbTAeFw0yNTEwMTQwNjI0MjBaFw0yNjEwMTQwNjI0MjBaMB8xHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG/TgBc0BkmMipiQ/6gkamIn3mmp7hcTrZuyrLTmknP1WRExl1dhdIx9/kAkuuceI3THkxXq7/y+sBzK0ZR7jPqMjMCEwHwYDVR0RBBgwFoIUdmVyaWZpZXIuZXhhbXBsZS5jb20wCgYIKoZIzj0EAwIDSQAwRgIhAOu0RGM6BjVQUepeLBogw+ZD3MQ9vFppbPIGMPjtn/qdAiEAttfdfyXHfzJ2tr+Pczyckzv3NlM43461cvP96sIzOQA=")

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
            append("planName", "oid4vp-1final-verifier-test-plan")
            append(
                "variant", /* language=json*/
                """{
                           "credential_format": "sd_jwt_vc",
                           "client_id_prefix": "x509_san_dns",
                           "request_method": "request_uri_signed",
                           "response_mode": "direct_post"
                         }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "credential": {
                    "signing_jwk": $signerJwk
                },
                "client": {
                    "client_id": "test123"
                },
                "description": "Verifier - sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post",
                "server": {
                  "authorization_endpoint": "https://$conformanceHost:$conformancePort"
                }
            }
        """.trimIndent()
        ),
        verificationSessionSetup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = Json.decodeFromString(dcqlQuery),
                policies = Verification2Session.DefinedVerificationPolicies(),

                signedRequest = true,
                encryptedResponse = false,

                clientMetadata = ClientMetadata(),
                clientId = "x509_san_dns:test123",

                key = verifierKey,
                x5c = verifierCertificateChain
            ),
            urlConfig = UrlConfig(
                urlPrefix = verifier2UrlPrefix,
                //urlHost, // <-- set by TestPlanRunner
            ),
            redirects = VerificationSessionRedirects(
                successRedirectUri = "https://example.org/veriifcation-success"
            )
        )
    )
}
