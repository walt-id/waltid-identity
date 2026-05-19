@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.openid4vp.conformance.testplans.plans

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.openid4vp.conformance.testplans.runner.req.ExpectedVerifierOutcome
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.UrlConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionRedirects
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class MdlX509SanDnsRequestUriSignedDirectPost(
    verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session",

    conformanceHost: String = "localhost.emobix.co.uk",
    conformancePort: Int = 8443
) : TestPlan {

    // Verifier
    val verifierKey =
        Json.decodeFromString<DirectSerializedKey>("""{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}""")
    val verifierCertificateChain =
        listOf(
            // Leaf cert: CN=verifier.example.com, SAN DNS=verifier.example.com, signed by walt.id Verifier CA
            "MIIB1DCCAXqgAwIBAgIUIwFilmYdNfDNrzQ2YxHRvXZVRxYwCgYIKoZIzj0EAwIwMDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5pZDAeFw0yNjA1MTkwNDA4MTZaFw0yNzA1MTkwNDA4MTZaMDExHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMRAwDgYDVQQKDAd3YWx0LmlkMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG/TgBc0BkmMipiQ/6gkamIn3mmp7hcTrZuyrLTmknP1WRExl1dhdIx9/kAkuuceI3THkxXq7/y+sBzK0ZR7jPqNxMG8wDAYDVR0TAQH/BAIwADAfBgNVHREEGDAWghR2ZXJpZmllci5leGFtcGxlLmNvbTAdBgNVHQ4EFgQUgiWdm4wdVbizPJbzfHvzODGJi78wHwYDVR0jBBgwFoAUXYaP+ypRou+GJnixaizH2x+nEpMwCgYIKoZIzj0EAwIDSAAwRQIgfp2vzdTnzzjPlOyu9oUMDgPIfgJ1MrK0HbCnnK3oBH8CIQDre3cP/D1jGLma8XHSWftWaWPHpkjqIV+z7kNyVPXanQ=="
        )

    // DCQL

    // language=JSON
    val dcqlQuery = """
        {
            "credentials": [
                {
                    "id": "my_photoid",
                    "format": "mso_mdoc",
                    "meta": {
                        "doctype_value": "org.iso.23220.photoid.1"
                    },
                    "claims": [
                        { "path": [ "org.iso.18013.5.1", "family_name_unicode" ] },
                        { "path": [ "org.iso.18013.5.1", "given_name_unicode" ] },
                        { "path": [ "org.iso.18013.5.1", "issuing_authority_unicode" ] },
                        {
                            "path": [ "org.iso.18013.5.1", "resident_postal_code" ],
                            "values": [ 1180, 1190, 1200, 1210 ]
                        },
                        {
                            "path": [ "org.iso.18013.5.1", "issuing_country" ],
                            "values": [ "AT" ]
                        },
                        { "path": [ "org.iso.23220.photoid.1", "person_id" ] },
                        { "path": [ "org.iso.23220.photoid.1", "resident_street" ] },
                        { "path": [ "org.iso.23220.photoid.1", "administrative_number" ] },
                        { "path": [ "org.iso.23220.photoid.1", "travel_document_number" ] },
                        { "path": [ "org.iso.23220.dtc.1", "dtc_version" ] },
                        { "path": [ "org.iso.23220.dtc.1", "dtc_dg1" ] }
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
                           "credential_format": "iso_mdl",
                           "client_id_prefix": "x509_san_dns",
                           "request_method": "request_uri_signed",
                           "vp_profile":"plain_vp",
                           "response_mode": "direct_post"
                         }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "credential": {
                    "signing_jwk": ["MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB"]
                },
                "client": {
                    "client_id": "verifier.example.com",
                    "request_object_trust_anchor_pem": "-----BEGIN CERTIFICATE-----\nMIIBlzCCAT2gAwIBAgIUUffF2b0tyOxgDu7q+kMpwY3pfNUwCgYIKoZIzj0EAwIw\nMDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5p\nZDAeFw0yNjA1MTkwNDA4MTZaFw0zNjA1MTYwNDA4MTZaMDAxHDAaBgNVBAMME3dh\nbHQuaWQgVmVyaWZpZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIB\nBggqhkjOPQMBBwNCAAQnFYwN1ypusrveHnOwC2ZFBT6PosWX5l1caoRPoziV8jn8\nEJx0uKD5RHC0p1CbYGHBqE74YUw7xlydTT1jXfCsozUwMzASBgNVHRMBAf8ECDAG\nAQH/AgEAMB0GA1UdDgQWBBRdho/7KlGi74YmeLFqLMfbH6cSkzAKBggqhkjOPQQD\nAgNIADBFAiEAudxJV83uP0g5zLXI85ExlkRMKZI52mkBkk074ST2KPACIEsFnJDr\nxtEgGXjHNMaUj7FOpC4tJyGlg2DSpXSOlCkl\n-----END CERTIFICATE-----"
                },
                "description": "Verifier - iso_mdl + x509_san_dns + request_uri_signed + direct_post",
                "server": {
                    "authorization_endpoint": "https://$conformanceHost:$conformancePort"
                },
                "publish": "everything"
            }
        """.trimIndent()
        ),
        moduleVariant = """{"client_id_prefix":"x509_san_dns","request_method":"request_uri_signed","vp_profile":"plain_vp"}""",
        moduleOutcomes = mapOf(
            "oid4vp-1final-verifier-happy-flow"                  to ExpectedVerifierOutcome.ACCEPT,
            "oid4vp-1final-verifier-request-uri-method-post"     to ExpectedVerifierOutcome.ACCEPT_OR_SKIP,
            "oid4vp-1final-verifier-invalid-session-transcript"  to ExpectedVerifierOutcome.REJECT,
        ),
        verificationSessionSetup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = Json.decodeFromString(dcqlQuery),
                policies = Verification2Session.DefinedVerificationPolicies(),

                signedRequest = true,
                encryptedResponse = false,

                clientMetadata = ClientMetadata(),
                // x509_san_dns: clientId must match the SAN DNS entry of the leaf cert in x5c
                clientId = "x509_san_dns:verifier.example.com",

                key = verifierKey,
                x5c = verifierCertificateChain
            ),
            urlConfig = UrlConfig(
                urlPrefix = verifier2UrlPrefix,
                //urlHost // <-- set by TestPlanRunner
            ),
            redirects = VerificationSessionRedirects(
                successRedirectUri = "https://example.org/verifcation-success"
            )
        )
    )
}
