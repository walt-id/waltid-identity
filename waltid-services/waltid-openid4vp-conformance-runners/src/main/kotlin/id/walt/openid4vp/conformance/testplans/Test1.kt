package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.testplans.http.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.http.CreateTestResponse
import id.walt.openid4vp.conformance.testplans.http.TestRunResult
import id.walt.openid4vp.conformance.utils.JsonUtils.fromJson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

suspend fun main() {
    val http = HttpClient() {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    val createTestPlanUrl = URLBuilder("https://localhost.emobix.co.uk:8443/api/plan").apply {
        parameters.apply {
            append("planName", "oid4vp-1final-verifier-test-plan")
            append(
                "variant",
                /* language=json*/
                """{
                  "credential_format": "iso_mdl",
                  "client_id_prefix": "x509_san_dns",
                  "request_method": "request_uri_signed",
                  "response_mode": "direct_post"
                }
            """
            )
        }
    }.build()

    println("Creating test plan... ($createTestPlanUrl)")
    val creationResponse = http.post(createTestPlanUrl) {
        contentType(ContentType.Application.Json)
        setBody(
            // language=json
            """
            {
                "credential": {
                    "signing_jwk": {
                        "kty": "EC",
                        "crv": "P-256",
                        "alg": "ES256",
                        
                        "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
                        
                        "d": "QN9Y3k_3Hy2OV0C5Pmez_ObEXJKcXonnMg3xTpcLOAg",
                                
                        "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
                        "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo",
                        
                        "x5c": [ "MIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M" ]
                    }
                },
                "client": {
                    "client_id": "test123",
                    "dcql": {
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
                    },
                    "jwks": {
                        "keys": [
                            {
                                "kty": "EC",
                                "crv": "P-256",
                                "alg": "ES256",
                                
                                "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
                                
                                "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
                                "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo",
                                
                                "x5c": [ "MIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M" ]
                            }
                        ]
                    }
                },
                "description": "Verifier - iso_mdl + x509_san_dns + request_uri_signed + direct_post",
                "server": {
                    "authorization_endpoint": "https://localhost.emobix.co.uk:8443"
                }
            }
        """.trimIndent()
        )
    }.bodyAsText().also { println(it) }.fromJson<CreateTestPlanResponse>()

    val testPlanId = creationResponse.id

    val testModule = creationResponse.modules.first().testModule
    if (creationResponse.modules.size > 1) {
        println("NOTICE: Suddenly, there is more than one test module available!")
    }

    println("Created test plan: $testPlanId")


    val createTestUrl = URLBuilder("https://localhost.emobix.co.uk:8443/api/runner").apply {
        parameters.apply {
            append("test", testModule)
            append("plan", testPlanId)
            append("variant", "{}")
        }
    }.build()

    println("Creating test... ($createTestUrl)")

    val createTestResponse = http.post(createTestUrl).body<CreateTestResponse>()
    val testId = createTestResponse.id

    println("View test run at: https://localhost.emobix.co.uk:8443/log-detail.html?log=${testId}")

    val testRunResult = http.get("https://localhost.emobix.co.uk:8443/api/runner/$testId").body<TestRunResult>()

    val authorizationEndpointToUse = testRunResult.getExposedAuthorizationEndpoint()

    println("Use authorization endpoint: $authorizationEndpointToUse")

}
