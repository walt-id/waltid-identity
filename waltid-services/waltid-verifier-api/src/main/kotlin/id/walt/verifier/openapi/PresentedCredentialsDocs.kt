package id.walt.verifier.openapi

import id.walt.verifier.oidc.models.presentedcredentials.PresentationSessionPresentedCredentials
import id.walt.verifier.oidc.models.presentedcredentials.PresentedCredentialsViewMode
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import kotlinx.serialization.json.Json

object PresentedCredentialsDocs {

    fun getPresentedCredentialsDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Credential Verification")
        summary = "Retrieve decoded credentials associated with a successfully verified presentation session"
        description =
            "Returns a structured, verbose representation of all credentials presented\n" +
                    "in a successfully verified presentation session. This endpoint is only available\n" +
                    "for sessions whose `vp_token` was verified with a positive result (`verificationResult == true`).\n" +
                    "\n" +
                    "Credentials are grouped by format and returned in both decoded and raw forms."

        request {
            pathParameter<String>("id") {
                description = "The identifier of the presentation session whose credentials should be retrieved."
                required = true
            }

            queryParameter<PresentedCredentialsViewMode>("viewMode") {
                description =
                    "Optional parameter controlling how detailed the response will be (defaults to `simple`)."
                required = false
            }
        }

        response {

            HttpStatusCode.OK to {
                body<PresentationSessionPresentedCredentials> {

                    required = true

                    example(
                        name = "University Degree W3C VC without disclosable claims (simple view)"
                    ) {
                        value = uniDegreeNoDisclosuresSimple
                    }

                    example(
                        name = "University Degree W3C VC without disclosable claims (verbose view)"
                    ) {
                        value = uniDegreeNoDisclosuresVerbose
                    }

                    example(
                        name = "Open Badge W3C VC without disclosable claims (simple view)"
                    ) {
                        value = openBadgeNoDisclosuresSimple
                    }

                    example(
                        name = "Open Badge W3C VC without disclosable claims (verbose view)"
                    ) {
                        value = openBadgeNoDisclosuresVerbose
                    }

                    example(
                        name = "University Degree W3C VC with two disclosable claims (simple view)"
                    ) {
                        value = uniDegreeTwoDisclosuresSimple
                    }

                    example(
                        name = "University Degree W3C VC with two disclosable claims (verbose view)"
                    ) {
                        value = uniDegreeTwoDisclosuresVerbose
                    }

                    example(
                        name = "Open Badge W3C VC with two disclosable claims (simple view)"
                    ) {
                        value = openBadgeTwoDisclosuresSimple
                    }

                    example(
                        name = "Open Badge W3C VC with two disclosable claims (verbose view)"
                    ) {
                        value = openBadgeTwoDisclosuresVerbose
                    }

                    example(
                        name = "University Degree & Open Badge W3C VCs both with two disclosable claims (simple view)"
                    ) {
                        value = uniDegreeOpenBadgeWithDisclosuresSimple
                    }

                    example(
                        name = "University Degree & Open Badge W3C VCs both with two disclosable claims (verbose view)"
                    ) {
                        value = uniDegreeOpenBadgeWithDisclosuresVerbose
                    }

                    example(
                        name = "SD-JWT-VC Identity Credential with two disclosable claims (simple view)"
                    ) {
                        value = sdJwtVcIdentityCredentialSimple
                    }

                    example(
                        name = "SD-JWT-VC Identity Credential with two disclosable claims (verbose view)"
                    ) {
                        value = sdJwtVcIdentityCredentialVerbose
                    }

                    example(
                        name = "ISO/IEC 18013-5 mDL with all mandatory properties (simple view)"
                    ) {
                        value = mDLSimple
                    }

                    example(
                        name = "ISO/IEC 18013-5 mDL with all mandatory properties (verbose view)"
                    ) {
                        value = mDLVerbose
                    }

                }


            }
        }
    }

    private val uniDegreeNoDisclosuresSimple = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_simple",
                        "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXY2a3BuS0l6akRLRTFZcXRONDJIMk9VMmxVZWVKamVpVmh4VEpRdVVJYyIsIngiOiJCdmt3bzh0Y0hLN3RsTy1lWUNZRjZLakNxTXNMeHdoTVJOQ3VtX0tJZUk4IiwieSI6IkQ3czc0WV9wdXVGdXJWNlJtWUtHRUpaTzlWdU1ycy1OdlNGeHdMd01SVVUifQ",
                        "verifiableCredentials": [
                            {
                                "header": {
                                    "kid": "did:key:zDnaeqgdw7qN5J8qZbwo8PCu18he2bK7PSHfaQEhmTw4xrDCC#zDnaeqgdw7qN5J8qZbwo8PCu18he2bK7PSHfaQEhmTw4xrDCC",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "payload": {
                                    "iss": "did:key:zDnaeqgdw7qN5J8qZbwo8PCu18he2bK7PSHfaQEhmTw4xrDCC",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXY2a3BuS0l6akRLRTFZcXRONDJIMk9VMmxVZWVKamVpVmh4VEpRdVVJYyIsIngiOiJCdmt3bzh0Y0hLN3RsTy1lWUNZRjZLakNxTXNMeHdoTVJOQ3VtX0tJZUk4IiwieSI6IkQ3czc0WV9wdXVGdXJWNlJtWUtHRUpaTzlWdU1ycy1OdlNGeHdMd01SVVUifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:ca31b7d7-2db6-458e-b776-c6213ee82190",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaeqgdw7qN5J8qZbwo8PCu18he2bK7PSHfaQEhmTw4xrDCC"
                                        },
                                        "issuanceDate": "2025-07-16T06:38:06.202581908Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXY2a3BuS0l6akRLRTFZcXRONDJIMk9VMmxVZWVKamVpVmh4VEpRdVVJYyIsIngiOiJCdmt3bzh0Y0hLN3RsTy1lWUNZRjZLakNxTXNMeHdoTVJOQ3VtX0tJZUk4IiwieSI6IkQ3czc0WV9wdXVGdXJWNlJtWUtHRUpaTzlWdU1ycy1OdlNGeHdMd01SVVUifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "name": "Bachelor of Science and Arts"
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaeqgdw7qN5J8qZbwo8PCu18he2bK7PSHfaQEhmTw4xrDCC",
                                        "expirationDate": "2026-07-16T06:38:06.202607447Z"
                                    },
                                    "jti": "urn:uuid:ca31b7d7-2db6-458e-b776-c6213ee82190",
                                    "exp": 1784183886,
                                    "iat": 1752647886,
                                    "nbf": 1752647886
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "simple"
        }
    """.trimIndent()
    )
    private val uniDegreeNoDisclosuresVerbose = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_verbose",
                        "vp": {
                            "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYUVoV2R6ZzVYMVl6TkVnelUxcG9VbnBPTkZCU2RHaElXa000VmpGWVVHbE1WbWR4ZFRSMmJHdFVieUlzSW5naU9pSlRUbTlEYWpVeWQwSnpYMHBoZHpSSGFrb3hRV2wwVld0VlNIZGZaemMwYlc5UVdUQnlXbXBHYzBwSklpd2llU0k2SWpBeldXNUVjVGhzTjNKblZVVlpUMEpOUm1sbU5YRmFUbEprU0VoSWVVNVRiMGN0TlRCUFZXWndjSGNpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYUVoV2R6ZzVYMVl6TkVnelUxcG9VbnBPTkZCU2RHaElXa000VmpGWVVHbE1WbWR4ZFRSMmJHdFVieUlzSW5naU9pSlRUbTlEYWpVeWQwSnpYMHBoZHpSSGFrb3hRV2wwVld0VlNIZGZaemMwYlc5UVdUQnlXbXBHYzBwSklpd2llU0k2SWpBeldXNUVjVGhzTjNKblZVVlpUMEpOUm1sbU5YRmFUbEprU0VoSWVVNVRiMGN0TlRCUFZXWndjSGNpZlEiLCJuYmYiOjE3NTI2NDgxNDIsImlhdCI6MTc1MjY0ODIwMiwianRpIjoibTJHdmNRU0dyZUpFIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaWFFaFdkemc1WDFZek5FZ3pVMXBvVW5wT05GQlNkR2hJV2tNNFZqRllVR2xNVm1keGRUUjJiR3RVYnlJc0luZ2lPaUpUVG05RGFqVXlkMEp6WDBwaGR6Ukhha294UVdsMFZXdFZTSGRmWnpjMGJXOVFXVEJ5V21wR2MwcEpJaXdpZVNJNklqQXpXVzVFY1Roc04zSm5WVVZaVDBKTlJtbG1OWEZhVGxKa1NFaEllVTVUYjBjdE5UQlBWV1p3Y0hjaWZRIiwibm9uY2UiOiJiZmJhMjZlYS1jNGQxLTRiZTUtODI1OC1jNWVkODhjZDQwYzIiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJtMkd2Y1FTR3JlSkUiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYUVoV2R6ZzVYMVl6TkVnelUxcG9VbnBPTkZCU2RHaElXa000VmpGWVVHbE1WbWR4ZFRSMmJHdFVieUlzSW5naU9pSlRUbTlEYWpVeWQwSnpYMHBoZHpSSGFrb3hRV2wwVld0VlNIZGZaemMwYlc5UVdUQnlXbXBHYzBwSklpd2llU0k2SWpBeldXNUVjVGhzTjNKblZVVlpUMEpOUm1sbU5YRmFUbEprU0VoSWVVNVRiMGN0TlRCUFZXWndjSGNpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsVnpkd09WRkZjM1oxZEVORmNGZGxkR2R5WTNWVWQweG9WbUpOYlRsSVZFUlZSVkJVYWtvMWVYWmFPV0lqZWtSdVlXVlhOM0E1VVVWemRuVjBRMFZ3VjJWMFozSmpkVlIzVEdoV1lrMXRPVWhVUkZWRlVGUnFTalY1ZGxvNVlpSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsVnpkd09WRkZjM1oxZEVORmNGZGxkR2R5WTNWVWQweG9WbUpOYlRsSVZFUlZSVkJVYWtvMWVYWmFPV0lpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBZVVZvVjJSNlp6VllNVmw2VGtWbmVsVXhjRzlWYm5CUFRrWkNVMlJIYUVsWGEwMDBWbXBHV1ZWSGJFMVdiV1I0WkZSU01tSkhkRlZpZVVselNXNW5hVTlwU2xSVWJUbEVZV3BWZVdRd1NucFlNSEJvWkhwU1NHRnJiM2hSVjJ3d1ZsZDBWbE5JWkdaYWVtTXdZbGM1VVZkVVFubFhiWEJIWXpCd1NrbHBkMmxsVTBrMlNXcEJlbGRYTlVWalZHaHpUak5LYmxaVlZscFVNRXBPVW0xc2JVNVlSbUZVYkVwclUwVm9TV1ZWTlZSaU1HTjBUbFJDVUZaWFduZGpTR05wWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2ZDNkM0xuY3pMbTl5Wnk4eU1ERTRMMk55WldSbGJuUnBZV3h6TDJWNFlXMXdiR1Z6TDNZeElsMHNJbWxrSWpvaWRYSnVPblYxYVdRNk1UZzVaREZrWVRndFlXUTJaUzAwTnpjeExXSmhORE10T0dOaVlqQTJZbU5sTkRCbElpd2lkSGx3WlNJNld5SldaWEpwWm1saFlteGxRM0psWkdWdWRHbGhiQ0lzSWxWdWFYWmxjbk5wZEhsRVpXZHlaV1VpWFN3aWFYTnpkV1Z5SWpwN0ltbGtJam9pWkdsa09tdGxlVHA2Ukc1aFpWYzNjRGxSUlhOMmRYUkRSWEJYWlhSbmNtTjFWSGRNYUZaaVRXMDVTRlJFVlVWUVZHcEtOWGwyV2psaUluMHNJbWx6YzNWaGJtTmxSR0YwWlNJNklqSXdNalV0TURjdE1UWlVNRFk2TkRNNk1qRXVOelExTURBek5UZ3dXaUlzSW1OeVpXUmxiblJwWVd4VGRXSnFaV04wSWpwN0ltbGtJam9pWkdsa09tcDNhenBsZVVweVpFaHJhVTlwU2taUmVVbHpTVzFPZVdScFNUWkpiRUYwVFdwVk1rbHBkMmxoTW14clNXcHZhV0ZGYUZka2VtYzFXREZaZWs1RlozcFZNWEJ2Vlc1d1QwNUdRbE5rUjJoSlYydE5ORlpxUmxsVlIyeE5WbTFrZUdSVVVqSmlSM1JWWW5sSmMwbHVaMmxQYVVwVVZHMDVSR0ZxVlhsa01FcDZXREJ3YUdSNlVraGhhMjk0VVZkc01GWlhkRlpUU0dSbVducGpNR0pYT1ZGWFZFSjVWMjF3UjJNd2NFcEphWGRwWlZOSk5rbHFRWHBYVnpWRlkxUm9jMDR6U201V1ZWWmFWREJLVGxKdGJHMU9XRVpoVkd4S2ExTkZhRWxsVlRWVVlqQmpkRTVVUWxCV1YxcDNZMGhqYVdaUklpd2laR1ZuY21WbElqcDdJblI1Y0dVaU9pSkNZV05vWld4dmNrUmxaM0psWlNJc0ltNWhiV1VpT2lKQ1lXTm9aV3h2Y2lCdlppQlRZMmxsYm1ObElHRnVaQ0JCY25SekluMTlMQ0pwYzNOMVpYSkVhV1FpT2lKa2FXUTZhMlY1T25wRWJtRmxWemR3T1ZGRmMzWjFkRU5GY0ZkbGRHZHlZM1ZVZDB4b1ZtSk5iVGxJVkVSVlJWQlVha28xZVhaYU9XSWlMQ0psZUhCcGNtRjBhVzl1UkdGMFpTSTZJakl3TWpZdE1EY3RNVFpVTURZNk5ETTZNakV1TnpRMU1ESXlOek0yV2lKOUxDSnFkR2tpT2lKMWNtNDZkWFZwWkRveE9EbGtNV1JoT0MxaFpEWmxMVFEzTnpFdFltRTBNeTA0WTJKaU1EWmlZMlUwTUdVaUxDSmxlSEFpT2pFM09EUXhPRFF5TURFc0ltbGhkQ0k2TVRjMU1qWTBPREl3TVN3aWJtSm1Jam94TnpVeU5qUTRNakF4ZlEuWHRpYVg4SGdxLTE2dURhWGhVaGV6Sk00QWxEVHF6UWh6ZFBaaGQxbmlhS0lyZU4xVFlDRTFlQ3NoWWZ0VTdFcVdLUU1RcGkxeE54QzdlcWo5UEtWbVEiXX19.Xm0c6E7tSLvr0EJuNN3rJMaYDsYXJFp3U1rTU5X8aYp4ofiZc-ps9Lu-J1FR5lYCafZVxEKuzRrbdSGkKA3vtw",
                            "header": {
                                "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiaEhWdzg5X1YzNEgzU1poUnpONFBSdGhIWkM4VjFYUGlMVmdxdTR2bGtUbyIsIngiOiJTTm9DajUyd0JzX0phdzRHakoxQWl0VWtVSHdfZzc0bW9QWTByWmpGc0pJIiwieSI6IjAzWW5EcThsN3JnVUVZT0JNRmlmNXFaTlJkSEhIeU5Tb0ctNTBPVWZwcHcifQ#0",
                                "typ": "JWT",
                                "alg": "ES256"
                            },
                            "payload": {
                                "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiaEhWdzg5X1YzNEgzU1poUnpONFBSdGhIWkM4VjFYUGlMVmdxdTR2bGtUbyIsIngiOiJTTm9DajUyd0JzX0phdzRHakoxQWl0VWtVSHdfZzc0bW9QWTByWmpGc0pJIiwieSI6IjAzWW5EcThsN3JnVUVZT0JNRmlmNXFaTlJkSEhIeU5Tb0ctNTBPVWZwcHcifQ",
                                "nbf": 1752648142,
                                "iat": 1752648202,
                                "jti": "m2GvcQSGreJE",
                                "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiaEhWdzg5X1YzNEgzU1poUnpONFBSdGhIWkM4VjFYUGlMVmdxdTR2bGtUbyIsIngiOiJTTm9DajUyd0JzX0phdzRHakoxQWl0VWtVSHdfZzc0bW9QWTByWmpGc0pJIiwieSI6IjAzWW5EcThsN3JnVUVZT0JNRmlmNXFaTlJkSEhIeU5Tb0ctNTBPVWZwcHcifQ",
                                "nonce": "bfba26ea-c4d1-4be5-8258-c5ed88cd40c2",
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "vp": {
                                    "@context": [
                                        "https://www.w3.org/2018/credentials/v1"
                                    ],
                                    "type": [
                                        "VerifiablePresentation"
                                    ],
                                    "id": "m2GvcQSGreJE",
                                    "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiaEhWdzg5X1YzNEgzU1poUnpONFBSdGhIWkM4VjFYUGlMVmdxdTR2bGtUbyIsIngiOiJTTm9DajUyd0JzX0phdzRHakoxQWl0VWtVSHdfZzc0bW9QWTByWmpGc0pJIiwieSI6IjAzWW5EcThsN3JnVUVZT0JNRmlmNXFaTlJkSEhIeU5Tb0ctNTBPVWZwcHcifQ",
                                    "verifiableCredential": [
                                        "eyJraWQiOiJkaWQ6a2V5OnpEbmFlVzdwOVFFc3Z1dENFcFdldGdyY3VUd0xoVmJNbTlIVERVRVBUako1eXZaOWIjekRuYWVXN3A5UUVzdnV0Q0VwV2V0Z3JjdVR3TGhWYk1tOUhURFVFUFRqSjV5dlo5YiIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlVzdwOVFFc3Z1dENFcFdldGdyY3VUd0xoVmJNbTlIVERVRVBUako1eXZaOWIiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYUVoV2R6ZzVYMVl6TkVnelUxcG9VbnBPTkZCU2RHaElXa000VmpGWVVHbE1WbWR4ZFRSMmJHdFVieUlzSW5naU9pSlRUbTlEYWpVeWQwSnpYMHBoZHpSSGFrb3hRV2wwVld0VlNIZGZaemMwYlc5UVdUQnlXbXBHYzBwSklpd2llU0k2SWpBeldXNUVjVGhzTjNKblZVVlpUMEpOUm1sbU5YRmFUbEprU0VoSWVVNVRiMGN0TlRCUFZXWndjSGNpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoidXJuOnV1aWQ6MTg5ZDFkYTgtYWQ2ZS00NzcxLWJhNDMtOGNiYjA2YmNlNDBlIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWUiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6RG5hZVc3cDlRRXN2dXRDRXBXZXRncmN1VHdMaFZiTW05SFREVUVQVGpKNXl2WjliIn0sImlzc3VhbmNlRGF0ZSI6IjIwMjUtMDctMTZUMDY6NDM6MjEuNzQ1MDAzNTgwWiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaWFFaFdkemc1WDFZek5FZ3pVMXBvVW5wT05GQlNkR2hJV2tNNFZqRllVR2xNVm1keGRUUjJiR3RVYnlJc0luZ2lPaUpUVG05RGFqVXlkMEp6WDBwaGR6Ukhha294UVdsMFZXdFZTSGRmWnpjMGJXOVFXVEJ5V21wR2MwcEpJaXdpZVNJNklqQXpXVzVFY1Roc04zSm5WVVZaVDBKTlJtbG1OWEZhVGxKa1NFaEllVTVUYjBjdE5UQlBWV1p3Y0hjaWZRIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIm5hbWUiOiJCYWNoZWxvciBvZiBTY2llbmNlIGFuZCBBcnRzIn19LCJpc3N1ZXJEaWQiOiJkaWQ6a2V5OnpEbmFlVzdwOVFFc3Z1dENFcFdldGdyY3VUd0xoVmJNbTlIVERVRVBUako1eXZaOWIiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDctMTZUMDY6NDM6MjEuNzQ1MDIyNzM2WiJ9LCJqdGkiOiJ1cm46dXVpZDoxODlkMWRhOC1hZDZlLTQ3NzEtYmE0My04Y2JiMDZiY2U0MGUiLCJleHAiOjE3ODQxODQyMDEsImlhdCI6MTc1MjY0ODIwMSwibmJmIjoxNzUyNjQ4MjAxfQ.XtiaX8Hgq-16uDaXhUhezJM4AlDTqzQhzdPZhd1niaKIreN1TYCE1eCshYftU7EqWKQMQpi1xNxC7eqj9PKVmQ"
                                    ]
                                }
                            }
                        },
                        "verifiableCredentials": [
                            {
                                "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFlVzdwOVFFc3Z1dENFcFdldGdyY3VUd0xoVmJNbTlIVERVRVBUako1eXZaOWIjekRuYWVXN3A5UUVzdnV0Q0VwV2V0Z3JjdVR3TGhWYk1tOUhURFVFUFRqSjV5dlo5YiIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlVzdwOVFFc3Z1dENFcFdldGdyY3VUd0xoVmJNbTlIVERVRVBUako1eXZaOWIiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYUVoV2R6ZzVYMVl6TkVnelUxcG9VbnBPTkZCU2RHaElXa000VmpGWVVHbE1WbWR4ZFRSMmJHdFVieUlzSW5naU9pSlRUbTlEYWpVeWQwSnpYMHBoZHpSSGFrb3hRV2wwVld0VlNIZGZaemMwYlc5UVdUQnlXbXBHYzBwSklpd2llU0k2SWpBeldXNUVjVGhzTjNKblZVVlpUMEpOUm1sbU5YRmFUbEprU0VoSWVVNVRiMGN0TlRCUFZXWndjSGNpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoidXJuOnV1aWQ6MTg5ZDFkYTgtYWQ2ZS00NzcxLWJhNDMtOGNiYjA2YmNlNDBlIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWUiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6RG5hZVc3cDlRRXN2dXRDRXBXZXRncmN1VHdMaFZiTW05SFREVUVQVGpKNXl2WjliIn0sImlzc3VhbmNlRGF0ZSI6IjIwMjUtMDctMTZUMDY6NDM6MjEuNzQ1MDAzNTgwWiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaWFFaFdkemc1WDFZek5FZ3pVMXBvVW5wT05GQlNkR2hJV2tNNFZqRllVR2xNVm1keGRUUjJiR3RVYnlJc0luZ2lPaUpUVG05RGFqVXlkMEp6WDBwaGR6Ukhha294UVdsMFZXdFZTSGRmWnpjMGJXOVFXVEJ5V21wR2MwcEpJaXdpZVNJNklqQXpXVzVFY1Roc04zSm5WVVZaVDBKTlJtbG1OWEZhVGxKa1NFaEllVTVUYjBjdE5UQlBWV1p3Y0hjaWZRIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIm5hbWUiOiJCYWNoZWxvciBvZiBTY2llbmNlIGFuZCBBcnRzIn19LCJpc3N1ZXJEaWQiOiJkaWQ6a2V5OnpEbmFlVzdwOVFFc3Z1dENFcFdldGdyY3VUd0xoVmJNbTlIVERVRVBUako1eXZaOWIiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDctMTZUMDY6NDM6MjEuNzQ1MDIyNzM2WiJ9LCJqdGkiOiJ1cm46dXVpZDoxODlkMWRhOC1hZDZlLTQ3NzEtYmE0My04Y2JiMDZiY2U0MGUiLCJleHAiOjE3ODQxODQyMDEsImlhdCI6MTc1MjY0ODIwMSwibmJmIjoxNzUyNjQ4MjAxfQ.XtiaX8Hgq-16uDaXhUhezJM4AlDTqzQhzdPZhd1niaKIreN1TYCE1eCshYftU7EqWKQMQpi1xNxC7eqj9PKVmQ",
                                "header": {
                                    "kid": "did:key:zDnaeW7p9QEsvutCEpWetgrcuTwLhVbMm9HTDUEPTjJ5yvZ9b#zDnaeW7p9QEsvutCEpWetgrcuTwLhVbMm9HTDUEPTjJ5yvZ9b",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "fullPayload": {
                                    "iss": "did:key:zDnaeW7p9QEsvutCEpWetgrcuTwLhVbMm9HTDUEPTjJ5yvZ9b",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiaEhWdzg5X1YzNEgzU1poUnpONFBSdGhIWkM4VjFYUGlMVmdxdTR2bGtUbyIsIngiOiJTTm9DajUyd0JzX0phdzRHakoxQWl0VWtVSHdfZzc0bW9QWTByWmpGc0pJIiwieSI6IjAzWW5EcThsN3JnVUVZT0JNRmlmNXFaTlJkSEhIeU5Tb0ctNTBPVWZwcHcifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:189d1da8-ad6e-4771-ba43-8cbb06bce40e",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaeW7p9QEsvutCEpWetgrcuTwLhVbMm9HTDUEPTjJ5yvZ9b"
                                        },
                                        "issuanceDate": "2025-07-16T06:43:21.745003580Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiaEhWdzg5X1YzNEgzU1poUnpONFBSdGhIWkM4VjFYUGlMVmdxdTR2bGtUbyIsIngiOiJTTm9DajUyd0JzX0phdzRHakoxQWl0VWtVSHdfZzc0bW9QWTByWmpGc0pJIiwieSI6IjAzWW5EcThsN3JnVUVZT0JNRmlmNXFaTlJkSEhIeU5Tb0ctNTBPVWZwcHcifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "name": "Bachelor of Science and Arts"
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaeW7p9QEsvutCEpWetgrcuTwLhVbMm9HTDUEPTjJ5yvZ9b",
                                        "expirationDate": "2026-07-16T06:43:21.745022736Z"
                                    },
                                    "jti": "urn:uuid:189d1da8-ad6e-4771-ba43-8cbb06bce40e",
                                    "exp": 1784184201,
                                    "iat": 1752648201,
                                    "nbf": 1752648201
                                },
                                "undisclosedPayload": {
                                    "iss": "did:key:zDnaeW7p9QEsvutCEpWetgrcuTwLhVbMm9HTDUEPTjJ5yvZ9b",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiaEhWdzg5X1YzNEgzU1poUnpONFBSdGhIWkM4VjFYUGlMVmdxdTR2bGtUbyIsIngiOiJTTm9DajUyd0JzX0phdzRHakoxQWl0VWtVSHdfZzc0bW9QWTByWmpGc0pJIiwieSI6IjAzWW5EcThsN3JnVUVZT0JNRmlmNXFaTlJkSEhIeU5Tb0ctNTBPVWZwcHcifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:189d1da8-ad6e-4771-ba43-8cbb06bce40e",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaeW7p9QEsvutCEpWetgrcuTwLhVbMm9HTDUEPTjJ5yvZ9b"
                                        },
                                        "issuanceDate": "2025-07-16T06:43:21.745003580Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiaEhWdzg5X1YzNEgzU1poUnpONFBSdGhIWkM4VjFYUGlMVmdxdTR2bGtUbyIsIngiOiJTTm9DajUyd0JzX0phdzRHakoxQWl0VWtVSHdfZzc0bW9QWTByWmpGc0pJIiwieSI6IjAzWW5EcThsN3JnVUVZT0JNRmlmNXFaTlJkSEhIeU5Tb0ctNTBPVWZwcHcifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "name": "Bachelor of Science and Arts"
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaeW7p9QEsvutCEpWetgrcuTwLhVbMm9HTDUEPTjJ5yvZ9b",
                                        "expirationDate": "2026-07-16T06:43:21.745022736Z"
                                    },
                                    "jti": "urn:uuid:189d1da8-ad6e-4771-ba43-8cbb06bce40e",
                                    "exp": 1784184201,
                                    "iat": 1752648201,
                                    "nbf": 1752648201
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "verbose"
        }
    """.trimIndent()
    )
    private val openBadgeNoDisclosuresSimple = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_simple",
                        "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                        "verifiableCredentials": [
                            {
                                "header": {
                                    "kid": "did:key:zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B#zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "payload": {
                                    "iss": "did:key:zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:7a7332e4-653c-40f0-b415-87da9dca44b3",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "issuanceDate": "2025-07-16T06:45:51.790721153Z",
                                        "expirationDate": "2026-07-16T06:45:51.790739868Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        }
                                    },
                                    "jti": "urn:uuid:7a7332e4-653c-40f0-b415-87da9dca44b3",
                                    "exp": 1784184351,
                                    "iat": 1752648351,
                                    "nbf": 1752648351
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "simple"
        }
    """.trimIndent()
    )
    private val openBadgeNoDisclosuresVerbose = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_verbose",
                        "vp": {
                            "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVGt0c2FubE9NRmsyVWtwNE1UQmFZelkxVkZwV01IWlhTMVUyVVVWSVpYUm9SbUZUYTBKMk4yTlZXU0lzSW5naU9pSnJjemg0TlZWM05uazRiR2xsZFVveVRYUnlVazVYYmtkTFYxQkJjazgxYlZGemVHVjFXVEJUYkdKUklpd2llU0k2SWt4eFNqbDFiVVZUZEVwNGJYVjBlR1JvTjB0bFpraDRWbkJrZGs5d1RYSkJZVlZYYzNkUE5tNU1VMVVpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVGt0c2FubE9NRmsyVWtwNE1UQmFZelkxVkZwV01IWlhTMVUyVVVWSVpYUm9SbUZUYTBKMk4yTlZXU0lzSW5naU9pSnJjemg0TlZWM05uazRiR2xsZFVveVRYUnlVazVYYmtkTFYxQkJjazgxYlZGemVHVjFXVEJUYkdKUklpd2llU0k2SWt4eFNqbDFiVVZUZEVwNGJYVjBlR1JvTjB0bFpraDRWbkJrZGs5d1RYSkJZVlZYYzNkUE5tNU1VMVVpZlEiLCJuYmYiOjE3NTI2NDgyOTIsImlhdCI6MTc1MjY0ODM1MiwianRpIjoicDY4OHlsdkdCRUtTIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVRrdHNhbmxPTUZrMlVrcDRNVEJhWXpZMVZGcFdNSFpYUzFVMlVVVklaWFJvUm1GVGEwSjJOMk5WV1NJc0luZ2lPaUpyY3poNE5WVjNObms0YkdsbGRVb3lUWFJ5VWs1WGJrZExWMUJCY2s4MWJWRnplR1YxV1RCVGJHSlJJaXdpZVNJNklreHhTamwxYlVWVGRFcDRiWFYwZUdSb04wdGxaa2g0Vm5Ca2RrOXdUWEpCWVZWWGMzZFBObTVNVTFVaWZRIiwibm9uY2UiOiIyY2M5NTI4Yi0xNWQ1LTRiMjgtOTA3NC01ODI3NjExNWUzMmUiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJwNjg4eWx2R0JFS1MiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVGt0c2FubE9NRmsyVWtwNE1UQmFZelkxVkZwV01IWlhTMVUyVVVWSVpYUm9SbUZUYTBKMk4yTlZXU0lzSW5naU9pSnJjemg0TlZWM05uazRiR2xsZFVveVRYUnlVazVYYmtkTFYxQkJjazgxYlZGemVHVjFXVEJUYkdKUklpd2llU0k2SWt4eFNqbDFiVVZUZEVwNGJYVjBlR1JvTjB0bFpraDRWbkJrZGs5d1RYSkJZVlZYYzNkUE5tNU1VMVVpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsY2xkRFJGTkhaRzFPUlhSQlVUaElkSFZRVlV0SFMwNUZVbE5DUm5kcWNrSlRhM1JpWjNwNGVHaEdNa0lqZWtSdVlXVnlWME5FVTBka2JVNUZkRUZST0VoMGRWQlZTMGRMVGtWU1UwSkdkMnB5UWxOcmRHSm5lbmg0YUVZeVFpSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsY2xkRFJGTkhaRzFPUlhSQlVUaElkSFZRVlV0SFMwNUZVbE5DUm5kcWNrSlRhM1JpWjNwNGVHaEdNa0lpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBWR3QwYzJGdWJFOU5SbXN5Vld0d05FMVVRbUZaZWxreFZrWndWMDFJV2xoVE1WVXlWVlZXU1ZwWVVtOVNiVVpVWVRCS01rNHlUbFpYVTBselNXNW5hVTlwU25KamVtZzBUbFpXTTA1dWF6UmlSMnhzWkZWdmVWUllVbmxWYXpWWVltdGtURll4UWtKamF6Z3hZbFpHZW1WSFZqRlhWRUpVWWtkS1VrbHBkMmxsVTBrMlNXdDRlRk5xYkRGaVZWWlVaRVZ3TkdKWVZqQmxSMUp2VGpCMGJGcHJhRFJXYmtKclpHczVkMVJZU2tKWlZsWllZek5rVUU1dE5VMVZNVlZwWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2Y0hWeWJDNXBiWE5uYkc5aVlXd3ViM0puTDNOd1pXTXZiMkl2ZGpOd01DOWpiMjUwWlhoMExtcHpiMjRpWFN3aWFXUWlPaUoxY200NmRYVnBaRG8zWVRjek16SmxOQzAyTlROakxUUXdaakF0WWpReE5TMDROMlJoT1dSallUUTBZak1pTENKMGVYQmxJanBiSWxabGNtbG1hV0ZpYkdWRGNtVmtaVzUwYVdGc0lpd2lUM0JsYmtKaFpHZGxRM0psWkdWdWRHbGhiQ0pkTENKdVlXMWxJam9pU2taR0lIZ2dkbU10WldSMUlGQnNkV2RHWlhOMElETWdTVzUwWlhKdmNHVnlZV0pwYkdsMGVTSXNJbWx6YzNWbGNpSTZleUowZVhCbElqcGJJbEJ5YjJacGJHVWlYU3dpYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsY2xkRFJGTkhaRzFPUlhSQlVUaElkSFZRVlV0SFMwNUZVbE5DUm5kcWNrSlRhM1JpWjNwNGVHaEdNa0lpTENKdVlXMWxJam9pU205aWN5Qm1iM0lnZEdobElFWjFkSFZ5WlNBb1NrWkdLU0lzSW5WeWJDSTZJbWgwZEhCek9pOHZkM2QzTG1wbVppNXZjbWN2SWl3aWFXMWhaMlVpT2lKb2RIUndjem92TDNjell5MWpZMmN1WjJsMGFIVmlMbWx2TDNaakxXVmtMM0JzZFdkbVpYTjBMVEV0TWpBeU1pOXBiV0ZuWlhNdlNrWkdYMHh2WjI5TWIyTnJkWEF1Y0c1bkluMHNJbWx6YzNWaGJtTmxSR0YwWlNJNklqSXdNalV0TURjdE1UWlVNRFk2TkRVNk5URXVOemt3TnpJeE1UVXpXaUlzSW1WNGNHbHlZWFJwYjI1RVlYUmxJam9pTWpBeU5pMHdOeTB4TmxRd05qbzBOVG8xTVM0M09UQTNNems0TmpoYUlpd2lZM0psWkdWdWRHbGhiRk4xWW1wbFkzUWlPbnNpYVdRaU9pSmthV1E2YW5kck9tVjVTbkprU0d0cFQybEtSbEY1U1hOSmJVNTVaR2xKTmtsc1FYUk5hbFV5U1dsM2FXRXliR3RKYW05cFZHdDBjMkZ1YkU5TlJtc3lWV3R3TkUxVVFtRlplbGt4Vmtad1YwMUlXbGhUTVZVeVZWVldTVnBZVW05U2JVWlVZVEJLTWs0eVRsWlhVMGx6U1c1bmFVOXBTbkpqZW1nMFRsWldNMDV1YXpSaVIyeHNaRlZ2ZVZSWVVubFZhelZZWW10a1RGWXhRa0pqYXpneFlsWkdlbVZIVmpGWFZFSlVZa2RLVWtscGQybGxVMGsyU1d0NGVGTnFiREZpVlZaVVpFVndOR0pZVmpCbFIxSnZUakIwYkZwcmFEUldia0pyWkdzNWQxUllTa0paVmxaWVl6TmtVRTV0TlUxVk1WVnBabEVpTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MFUzVmlhbVZqZENKZExDSmhZMmhwWlhabGJXVnVkQ0k2ZXlKcFpDSTZJblZ5YmpwMWRXbGtPbUZqTWpVMFltUTFMVGhtWVdRdE5HSmlNUzA1WkRJNUxXVm1aRGt6T0RVek5qa3lOaUlzSW5SNWNHVWlPbHNpUVdOb2FXVjJaVzFsYm5RaVhTd2libUZ0WlNJNklrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpTENKa1pYTmpjbWx3ZEdsdmJpSTZJbFJvYVhNZ2QyRnNiR1YwSUhOMWNIQnZjblJ6SUhSb1pTQjFjMlVnYjJZZ1Z6TkRJRlpsY21sbWFXRmliR1VnUTNKbFpHVnVkR2xoYkhNZ1lXNWtJR2hoY3lCa1pXMXZibk4wY21GMFpXUWdhVzUwWlhKdmNHVnlZV0pwYkdsMGVTQmtkWEpwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCeVpYRjFaWE4wSUhkdmNtdG1iRzkzSUdSMWNtbHVaeUJLUmtZZ2VDQldReTFGUkZVZ1VHeDFaMFpsYzNRZ015NGlMQ0pqY21sMFpYSnBZU0k2ZXlKMGVYQmxJam9pUTNKcGRHVnlhV0VpTENKdVlYSnlZWFJwZG1VaU9pSlhZV3hzWlhRZ2MyOXNkWFJwYjI1eklIQnliM1pwWkdWeWN5QmxZWEp1WldRZ2RHaHBjeUJpWVdSblpTQmllU0JrWlcxdmJuTjBjbUYwYVc1bklHbHVkR1Z5YjNCbGNtRmlhV3hwZEhrZ1pIVnlhVzVuSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnY21WeGRXVnpkQ0IzYjNKclpteHZkeTRnVkdocGN5QnBibU5zZFdSbGN5QnpkV05qWlhOelpuVnNiSGtnY21WalpXbDJhVzVuSUdFZ2NISmxjMlZ1ZEdGMGFXOXVJSEpsY1hWbGMzUXNJR0ZzYkc5M2FXNW5JSFJvWlNCb2IyeGtaWElnZEc4Z2MyVnNaV04wSUdGMElHeGxZWE4wSUhSM2J5QjBlWEJsY3lCdlppQjJaWEpwWm1saFlteGxJR055WldSbGJuUnBZV3h6SUhSdklHTnlaV0YwWlNCaElIWmxjbWxtYVdGaWJHVWdjSEpsYzJWdWRHRjBhVzl1TENCeVpYUjFjbTVwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCMGJ5QjBhR1VnY21WeGRXVnpkRzl5TENCaGJtUWdjR0Z6YzJsdVp5QjJaWEpwWm1sallYUnBiMjRnYjJZZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCaGJtUWdkR2hsSUdsdVkyeDFaR1ZrSUdOeVpXUmxiblJwWVd4ekxpSjlMQ0pwYldGblpTSTZleUpwWkNJNkltaDBkSEJ6T2k4dmR6TmpMV05qWnk1bmFYUm9kV0l1YVc4dmRtTXRaV1F2Y0d4MVoyWmxjM1F0TXkweU1ESXpMMmx0WVdkbGN5OUtSa1l0VmtNdFJVUlZMVkJNVlVkR1JWTlVNeTFpWVdSblpTMXBiV0ZuWlM1d2JtY2lMQ0owZVhCbElqb2lTVzFoWjJVaWZYMTlmU3dpYW5ScElqb2lkWEp1T25WMWFXUTZOMkUzTXpNeVpUUXROalV6WXkwME1HWXdMV0kwTVRVdE9EZGtZVGxrWTJFME5HSXpJaXdpWlhod0lqb3hOemcwTVRnME16VXhMQ0pwWVhRaU9qRTNOVEkyTkRnek5URXNJbTVpWmlJNk1UYzFNalkwT0RNMU1YMC5reEgtZGNDcE1zX1ZoLTJFSG84andIeWlPUEk5ZUhrQWhBbFp1SzllQlg5cl92XzBvXy1BSkpJd1RRWThaR01lZUZial9DT0ZlQkRVa2FHR2o1bkdoUSJdfX0.KSVdUE53BNdS19XbyEOx2B4vIzEuo7xzm4f-3hgEK2Y6AF5hR7wWI7PFVUuVNdBW891qRqTPGvJnHevQ4CrPsA",
                            "header": {
                                "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ#0",
                                "typ": "JWT",
                                "alg": "ES256"
                            },
                            "payload": {
                                "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                "nbf": 1752648292,
                                "iat": 1752648352,
                                "jti": "p688ylvGBEKS",
                                "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                "nonce": "2cc9528b-15d5-4b28-9074-58276115e32e",
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "vp": {
                                    "@context": [
                                        "https://www.w3.org/2018/credentials/v1"
                                    ],
                                    "type": [
                                        "VerifiablePresentation"
                                    ],
                                    "id": "p688ylvGBEKS",
                                    "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                    "verifiableCredential": [
                                        "eyJraWQiOiJkaWQ6a2V5OnpEbmFlcldDRFNHZG1ORXRBUThIdHVQVUtHS05FUlNCRndqckJTa3RiZ3p4eGhGMkIjekRuYWVyV0NEU0dkbU5FdEFROEh0dVBVS0dLTkVSU0JGd2pyQlNrdGJnenh4aEYyQiIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlcldDRFNHZG1ORXRBUThIdHVQVUtHS05FUlNCRndqckJTa3RiZ3p4eGhGMkIiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVGt0c2FubE9NRmsyVWtwNE1UQmFZelkxVkZwV01IWlhTMVUyVVVWSVpYUm9SbUZUYTBKMk4yTlZXU0lzSW5naU9pSnJjemg0TlZWM05uazRiR2xsZFVveVRYUnlVazVYYmtkTFYxQkJjazgxYlZGemVHVjFXVEJUYkdKUklpd2llU0k2SWt4eFNqbDFiVVZUZEVwNGJYVjBlR1JvTjB0bFpraDRWbkJrZGs5d1RYSkJZVlZYYzNkUE5tNU1VMVVpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDo3YTczMzJlNC02NTNjLTQwZjAtYjQxNS04N2RhOWRjYTQ0YjMiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJuYW1lIjoiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSIsImlzc3VlciI6eyJ0eXBlIjpbIlByb2ZpbGUiXSwiaWQiOiJkaWQ6a2V5OnpEbmFlcldDRFNHZG1ORXRBUThIdHVQVUtHS05FUlNCRndqckJTa3RiZ3p4eGhGMkIiLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIn0sImlzc3VhbmNlRGF0ZSI6IjIwMjUtMDctMTZUMDY6NDU6NTEuNzkwNzIxMTUzWiIsImV4cGlyYXRpb25EYXRlIjoiMjAyNi0wNy0xNlQwNjo0NTo1MS43OTA3Mzk4NjhaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVGt0c2FubE9NRmsyVWtwNE1UQmFZelkxVkZwV01IWlhTMVUyVVVWSVpYUm9SbUZUYTBKMk4yTlZXU0lzSW5naU9pSnJjemg0TlZWM05uazRiR2xsZFVveVRYUnlVazVYYmtkTFYxQkJjazgxYlZGemVHVjFXVEJUYkdKUklpd2llU0k2SWt4eFNqbDFiVVZUZEVwNGJYVjBlR1JvTjB0bFpraDRWbkJrZGs5d1RYSkJZVlZYYzNkUE5tNU1VMVVpZlEiLCJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX19fSwianRpIjoidXJuOnV1aWQ6N2E3MzMyZTQtNjUzYy00MGYwLWI0MTUtODdkYTlkY2E0NGIzIiwiZXhwIjoxNzg0MTg0MzUxLCJpYXQiOjE3NTI2NDgzNTEsIm5iZiI6MTc1MjY0ODM1MX0.kxH-dcCpMs_Vh-2EHo8jwHyiOPI9eHkAhAlZuK9eBX9r_v_0o_-AJJIwTQY8ZGMeeFbj_COFeBDUkaGGj5nGhQ"
                                    ]
                                }
                            }
                        },
                        "verifiableCredentials": [
                            {
                                "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFlcldDRFNHZG1ORXRBUThIdHVQVUtHS05FUlNCRndqckJTa3RiZ3p4eGhGMkIjekRuYWVyV0NEU0dkbU5FdEFROEh0dVBVS0dLTkVSU0JGd2pyQlNrdGJnenh4aEYyQiIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlcldDRFNHZG1ORXRBUThIdHVQVUtHS05FUlNCRndqckJTa3RiZ3p4eGhGMkIiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVGt0c2FubE9NRmsyVWtwNE1UQmFZelkxVkZwV01IWlhTMVUyVVVWSVpYUm9SbUZUYTBKMk4yTlZXU0lzSW5naU9pSnJjemg0TlZWM05uazRiR2xsZFVveVRYUnlVazVYYmtkTFYxQkJjazgxYlZGemVHVjFXVEJUYkdKUklpd2llU0k2SWt4eFNqbDFiVVZUZEVwNGJYVjBlR1JvTjB0bFpraDRWbkJrZGs5d1RYSkJZVlZYYzNkUE5tNU1VMVVpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDo3YTczMzJlNC02NTNjLTQwZjAtYjQxNS04N2RhOWRjYTQ0YjMiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJuYW1lIjoiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSIsImlzc3VlciI6eyJ0eXBlIjpbIlByb2ZpbGUiXSwiaWQiOiJkaWQ6a2V5OnpEbmFlcldDRFNHZG1ORXRBUThIdHVQVUtHS05FUlNCRndqckJTa3RiZ3p4eGhGMkIiLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIn0sImlzc3VhbmNlRGF0ZSI6IjIwMjUtMDctMTZUMDY6NDU6NTEuNzkwNzIxMTUzWiIsImV4cGlyYXRpb25EYXRlIjoiMjAyNi0wNy0xNlQwNjo0NTo1MS43OTA3Mzk4NjhaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVGt0c2FubE9NRmsyVWtwNE1UQmFZelkxVkZwV01IWlhTMVUyVVVWSVpYUm9SbUZUYTBKMk4yTlZXU0lzSW5naU9pSnJjemg0TlZWM05uazRiR2xsZFVveVRYUnlVazVYYmtkTFYxQkJjazgxYlZGemVHVjFXVEJUYkdKUklpd2llU0k2SWt4eFNqbDFiVVZUZEVwNGJYVjBlR1JvTjB0bFpraDRWbkJrZGs5d1RYSkJZVlZYYzNkUE5tNU1VMVVpZlEiLCJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX19fSwianRpIjoidXJuOnV1aWQ6N2E3MzMyZTQtNjUzYy00MGYwLWI0MTUtODdkYTlkY2E0NGIzIiwiZXhwIjoxNzg0MTg0MzUxLCJpYXQiOjE3NTI2NDgzNTEsIm5iZiI6MTc1MjY0ODM1MX0.kxH-dcCpMs_Vh-2EHo8jwHyiOPI9eHkAhAlZuK9eBX9r_v_0o_-AJJIwTQY8ZGMeeFbj_COFeBDUkaGGj5nGhQ",
                                "header": {
                                    "kid": "did:key:zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B#zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "fullPayload": {
                                    "iss": "did:key:zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:7a7332e4-653c-40f0-b415-87da9dca44b3",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "issuanceDate": "2025-07-16T06:45:51.790721153Z",
                                        "expirationDate": "2026-07-16T06:45:51.790739868Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        }
                                    },
                                    "jti": "urn:uuid:7a7332e4-653c-40f0-b415-87da9dca44b3",
                                    "exp": 1784184351,
                                    "iat": 1752648351,
                                    "nbf": 1752648351
                                },
                                "undisclosedPayload": {
                                    "iss": "did:key:zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:7a7332e4-653c-40f0-b415-87da9dca44b3",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaerWCDSGdmNEtAQ8HtuPUKGKNERSBFwjrBSktbgzxxhF2B",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "issuanceDate": "2025-07-16T06:45:51.790721153Z",
                                        "expirationDate": "2026-07-16T06:45:51.790739868Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiTktsanlOMFk2Ukp4MTBaYzY1VFpWMHZXS1U2UUVIZXRoRmFTa0J2N2NVWSIsIngiOiJrczh4NVV3Nnk4bGlldUoyTXRyUk5XbkdLV1BBck81bVFzeGV1WTBTbGJRIiwieSI6IkxxSjl1bUVTdEp4bXV0eGRoN0tlZkh4VnBkdk9wTXJBYVVXc3dPNm5MU1UifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        }
                                    },
                                    "jti": "urn:uuid:7a7332e4-653c-40f0-b415-87da9dca44b3",
                                    "exp": 1784184351,
                                    "iat": 1752648351,
                                    "nbf": 1752648351
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "verbose"
        }
    """.trimIndent()
    )

    private val uniDegreeTwoDisclosuresSimple = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_simple",
                        "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                        "verifiableCredentials": [
                            {
                                "header": {
                                    "kid": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB#zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "payload": {
                                    "iss": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:1d037cf4-8489-4bb8-8c20-8f2e2d13292d",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB"
                                        },
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "name": "Bachelor of Science and Arts"
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB",
                                        "expirationDate": "2026-07-16T06:52:03.063864637Z",
                                        "issuanceDate": "2025-07-16T06:52:03.063843966Z"
                                    },
                                    "jti": "urn:uuid:1d037cf4-8489-4bb8-8c20-8f2e2d13292d",
                                    "exp": 1784184723,
                                    "iat": 1752648723,
                                    "nbf": 1752648723
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "simple"
        }
    """.trimIndent()
    )
    private val uniDegreeTwoDisclosuresVerbose = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_verbose",
                        "vp": {
                            "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVmtwNlFsYzJiWFZWTVY4eWRHRnpjRGRTVEZoNk4ydG9kMHhXYjNFMk5UQnRVMHBXTkd4dFZ6Sm1UU0lzSW5naU9pSTBiVXg2TjJSeU1reHBUMEp2YUhFeWFUSTBXazlxTFdWVVZXbENkQzB4ZEVWdFNtOW1iRkJCZHpOcklpd2llU0k2SWw5bVMwSk5jMDVXVG5KNFpVdzBNMVYxZFZSME5WRk1hVVl3Y1VsdlNFRlVTa3QwZDFkeVlYQmhZbGtpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVmtwNlFsYzJiWFZWTVY4eWRHRnpjRGRTVEZoNk4ydG9kMHhXYjNFMk5UQnRVMHBXTkd4dFZ6Sm1UU0lzSW5naU9pSTBiVXg2TjJSeU1reHBUMEp2YUhFeWFUSTBXazlxTFdWVVZXbENkQzB4ZEVWdFNtOW1iRkJCZHpOcklpd2llU0k2SWw5bVMwSk5jMDVXVG5KNFpVdzBNMVYxZFZSME5WRk1hVVl3Y1VsdlNFRlVTa3QwZDFkeVlYQmhZbGtpZlEiLCJuYmYiOjE3NTI2NDg2NjMsImlhdCI6MTc1MjY0ODcyMywianRpIjoiOWtZQWFnTjVMSFBxIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVZrcDZRbGMyYlhWVk1WOHlkR0Z6Y0RkU1RGaDZOMnRvZDB4V2IzRTJOVEJ0VTBwV05HeHRWekptVFNJc0luZ2lPaUkwYlV4Nk4yUnlNa3hwVDBKdmFIRXlhVEkwV2s5cUxXVlVWV2xDZEMweGRFVnRTbTltYkZCQmR6TnJJaXdpZVNJNklsOW1TMEpOYzA1V1RuSjRaVXcwTTFWMWRWUjBOVkZNYVVZd2NVbHZTRUZVU2t0MGQxZHlZWEJoWWxraWZRIiwibm9uY2UiOiI1OTYzODg2ZS1iMmMyLTQ5NmMtYjM3My0yNzA5MjZhOTQ2NDIiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiI5a1lBYWdONUxIUHEiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVmtwNlFsYzJiWFZWTVY4eWRHRnpjRGRTVEZoNk4ydG9kMHhXYjNFMk5UQnRVMHBXTkd4dFZ6Sm1UU0lzSW5naU9pSTBiVXg2TjJSeU1reHBUMEp2YUhFeWFUSTBXazlxTFdWVVZXbENkQzB4ZEVWdFNtOW1iRkJCZHpOcklpd2llU0k2SWw5bVMwSk5jMDVXVG5KNFpVdzBNMVYxZFZSME5WRk1hVVl3Y1VsdlNFRlVTa3QwZDFkeVlYQmhZbGtpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsY1ZCT1JsbzBXV1JWVWxwamVrMXBUSEExWTA1d1VETlRkWEpZV2tFMVlqaDNORFl4ZUZKM01UbHphRUlqZWtSdVlXVnhVRTVHV2pSWlpGVlNXbU42VFdsTWNEVmpUbkJRTTFOMWNsaGFRVFZpT0hjME5qRjRVbmN4T1hOb1FpSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsY1ZCT1JsbzBXV1JWVWxwamVrMXBUSEExWTA1d1VETlRkWEpZV2tFMVlqaDNORFl4ZUZKM01UbHphRUlpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBWbXR3TmxGc1l6SmlXRlpXVFZZNGVXUkhSbnBqUkdSVFZFWm9OazR5ZEc5a01IaFhZak5GTWs1VVFuUlZNSEJYVGtkNGRGWjZTbTFVVTBselNXNW5hVTlwU1RCaVZYZzJUakpTZVUxcmVIQlVNRXAyWVVoRmVXRlVTVEJYYXpseFRGZFdWVlpYYkVOa1F6QjRaRVZXZEZOdE9XMWlSa0pDWkhwT2NrbHBkMmxsVTBrMlNXdzViVk13U2s1ak1EVlhWRzVLTkZwVmR6Qk5NVll4WkZaU01FNVdSazFoVlZsM1kxVnNkbE5GUmxWVGEzUXdaREZrZVZsWVFtaFpiR3RwWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2ZDNkM0xuY3pMbTl5Wnk4eU1ERTRMMk55WldSbGJuUnBZV3h6TDJWNFlXMXdiR1Z6TDNZeElsMHNJbWxrSWpvaWRYSnVPblYxYVdRNk1XUXdNemRqWmpRdE9EUTRPUzAwWW1JNExUaGpNakF0T0dZeVpUSmtNVE15T1RKa0lpd2lkSGx3WlNJNld5SldaWEpwWm1saFlteGxRM0psWkdWdWRHbGhiQ0lzSWxWdWFYWmxjbk5wZEhsRVpXZHlaV1VpWFN3aWFYTnpkV1Z5SWpwN0ltbGtJam9pWkdsa09tdGxlVHA2Ukc1aFpYRlFUa1phTkZsa1ZWSmFZM3BOYVV4d05XTk9jRkF6VTNWeVdGcEJOV0k0ZHpRMk1YaFNkekU1YzJoQ0luMHNJbU55WldSbGJuUnBZV3hUZFdKcVpXTjBJanA3SW1sa0lqb2laR2xrT21wM2F6cGxlVXB5WkVocmFVOXBTa1pSZVVselNXMU9lV1JwU1RaSmJFRjBUV3BWTWtscGQybGhNbXhyU1dwdmFWWnJjRFpSYkdNeVlsaFdWazFXT0hsa1IwWjZZMFJrVTFSR2FEWk9NblJ2WkRCNFYySXpSVEpPVkVKMFZUQndWMDVIZUhSV2VrcHRWRk5KYzBsdVoybFBhVWt3WWxWNE5rNHlVbmxOYTNod1ZEQktkbUZJUlhsaFZFa3dWMnM1Y1V4WFZsVldWMnhEWkVNd2VHUkZWblJUYlRsdFlrWkNRbVI2VG5KSmFYZHBaVk5KTmtsc09XMVRNRXBPWXpBMVYxUnVTalJhVlhjd1RURldNV1JXVWpCT1ZrWk5ZVlZaZDJOVmJIWlRSVVpWVTJ0ME1HUXhaSGxaV0VKb1dXeHJhV1pSSWl3aVpHVm5jbVZsSWpwN0luUjVjR1VpT2lKQ1lXTm9aV3h2Y2tSbFozSmxaU0lzSWw5elpDSTZXeUptWW14M2FFSnNWa3huYm1OdlQwVlFjMDVvVEU5RGJqRTFMVVZOTUMxcGFtdGpjakJRTlVoRFVHaG5JbDE5ZlN3aWFYTnpkV1Z5Ukdsa0lqb2laR2xrT210bGVUcDZSRzVoWlhGUVRrWmFORmxrVlZKYVkzcE5hVXh3TldOT2NGQXpVM1Z5V0ZwQk5XSTRkelEyTVhoU2R6RTVjMmhDSWl3aVpYaHdhWEpoZEdsdmJrUmhkR1VpT2lJeU1ESTJMVEEzTFRFMlZEQTJPalV5T2pBekxqQTJNemcyTkRZek4xb2lMQ0pmYzJRaU9sc2lObVV4WmpjeVZHWlZjRVZXVGtONVQzQkVTekpaZDFSeU4xSTBWVTFEYUZkU2EybEhUMHBDTW0xNFJTSmRmU3dpYW5ScElqb2lkWEp1T25WMWFXUTZNV1F3TXpkalpqUXRPRFE0T1MwMFltSTRMVGhqTWpBdE9HWXlaVEprTVRNeU9USmtJaXdpWlhod0lqb3hOemcwTVRnME56SXpMQ0pwWVhRaU9qRTNOVEkyTkRnM01qTXNJbTVpWmlJNk1UYzFNalkwT0RjeU0zMC5jbTJNdVc2eXFTQXpja0h3ekZzWVZnb0J1MWN3VnI4N3djcVBnWWFzSTZqVXZhYno2RHhJaHhNWGJNdVZ3MVpWWlN1eUZla1dfTFlDRVRmLVdkbUlRZ35XeUk1U3pGU01Gb3pORTF0VWsxbFVGUkdkWGxxYURkQklpd2lhWE56ZFdGdVkyVkVZWFJsSWl3aU1qQXlOUzB3TnkweE5sUXdOam8xTWpvd015NHdOak00TkRNNU5qWmFJbDB-V3lKWGMxcHFVbWRaWkc5NWRVdzVWVmhUUjJaU1NUbDNJaXdpYm1GdFpTSXNJa0poWTJobGJHOXlJRzltSUZOamFXVnVZMlVnWVc1a0lFRnlkSE1pWFEiXX19.XFz-Dio7lbM0BDgRJMKZ4yq9QooEFLzh-9dByeZcz9xLw4GUzb8hyFsaXYg6Mg7GZ3srzp52R-X4M6wFFewItg",
                            "header": {
                                "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ#0",
                                "typ": "JWT",
                                "alg": "ES256"
                            },
                            "payload": {
                                "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                "nbf": 1752648663,
                                "iat": 1752648723,
                                "jti": "9kYAagN5LHPq",
                                "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                "nonce": "5963886e-b2c2-496c-b373-270926a94642",
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "vp": {
                                    "@context": [
                                        "https://www.w3.org/2018/credentials/v1"
                                    ],
                                    "type": [
                                        "VerifiablePresentation"
                                    ],
                                    "id": "9kYAagN5LHPq",
                                    "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                    "verifiableCredential": [
                                        "eyJraWQiOiJkaWQ6a2V5OnpEbmFlcVBORlo0WWRVUlpjek1pTHA1Y05wUDNTdXJYWkE1Yjh3NDYxeFJ3MTlzaEIjekRuYWVxUE5GWjRZZFVSWmN6TWlMcDVjTnBQM1N1clhaQTViOHc0NjF4UncxOXNoQiIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlcVBORlo0WWRVUlpjek1pTHA1Y05wUDNTdXJYWkE1Yjh3NDYxeFJ3MTlzaEIiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVmtwNlFsYzJiWFZWTVY4eWRHRnpjRGRTVEZoNk4ydG9kMHhXYjNFMk5UQnRVMHBXTkd4dFZ6Sm1UU0lzSW5naU9pSTBiVXg2TjJSeU1reHBUMEp2YUhFeWFUSTBXazlxTFdWVVZXbENkQzB4ZEVWdFNtOW1iRkJCZHpOcklpd2llU0k2SWw5bVMwSk5jMDVXVG5KNFpVdzBNMVYxZFZSME5WRk1hVVl3Y1VsdlNFRlVTa3QwZDFkeVlYQmhZbGtpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoidXJuOnV1aWQ6MWQwMzdjZjQtODQ4OS00YmI4LThjMjAtOGYyZTJkMTMyOTJkIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWUiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6RG5hZXFQTkZaNFlkVVJaY3pNaUxwNWNOcFAzU3VyWFpBNWI4dzQ2MXhSdzE5c2hCIn0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVZrcDZRbGMyYlhWVk1WOHlkR0Z6Y0RkU1RGaDZOMnRvZDB4V2IzRTJOVEJ0VTBwV05HeHRWekptVFNJc0luZ2lPaUkwYlV4Nk4yUnlNa3hwVDBKdmFIRXlhVEkwV2s5cUxXVlVWV2xDZEMweGRFVnRTbTltYkZCQmR6TnJJaXdpZVNJNklsOW1TMEpOYzA1V1RuSjRaVXcwTTFWMWRWUjBOVkZNYVVZd2NVbHZTRUZVU2t0MGQxZHlZWEJoWWxraWZRIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIl9zZCI6WyJmYmx3aEJsVkxnbmNvT0VQc05oTE9DbjE1LUVNMC1pamtjcjBQNUhDUGhnIl19fSwiaXNzdWVyRGlkIjoiZGlkOmtleTp6RG5hZXFQTkZaNFlkVVJaY3pNaUxwNWNOcFAzU3VyWFpBNWI4dzQ2MXhSdzE5c2hCIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA3LTE2VDA2OjUyOjAzLjA2Mzg2NDYzN1oiLCJfc2QiOlsiNmUxZjcyVGZVcEVWTkN5T3BESzJZd1RyN1I0VU1DaFdSa2lHT0pCMm14RSJdfSwianRpIjoidXJuOnV1aWQ6MWQwMzdjZjQtODQ4OS00YmI4LThjMjAtOGYyZTJkMTMyOTJkIiwiZXhwIjoxNzg0MTg0NzIzLCJpYXQiOjE3NTI2NDg3MjMsIm5iZiI6MTc1MjY0ODcyM30.cm2MuW6yqSAzckHwzFsYVgoBu1cwVr87wcqPgYasI6jUvabz6DxIhxMXbMuVw1ZVZSuyFekW_LYCETf-WdmIQg~WyI5SzFSMFozNE1tUk1lUFRGdXlqaDdBIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xNlQwNjo1MjowMy4wNjM4NDM5NjZaIl0~WyJXc1pqUmdZZG95dUw5VVhTR2ZSSTl3IiwibmFtZSIsIkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMiXQ"
                                    ]
                                }
                            }
                        },
                        "verifiableCredentials": [
                            {
                                "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFlcVBORlo0WWRVUlpjek1pTHA1Y05wUDNTdXJYWkE1Yjh3NDYxeFJ3MTlzaEIjekRuYWVxUE5GWjRZZFVSWmN6TWlMcDVjTnBQM1N1clhaQTViOHc0NjF4UncxOXNoQiIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlcVBORlo0WWRVUlpjek1pTHA1Y05wUDNTdXJYWkE1Yjh3NDYxeFJ3MTlzaEIiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pVmtwNlFsYzJiWFZWTVY4eWRHRnpjRGRTVEZoNk4ydG9kMHhXYjNFMk5UQnRVMHBXTkd4dFZ6Sm1UU0lzSW5naU9pSTBiVXg2TjJSeU1reHBUMEp2YUhFeWFUSTBXazlxTFdWVVZXbENkQzB4ZEVWdFNtOW1iRkJCZHpOcklpd2llU0k2SWw5bVMwSk5jMDVXVG5KNFpVdzBNMVYxZFZSME5WRk1hVVl3Y1VsdlNFRlVTa3QwZDFkeVlYQmhZbGtpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoidXJuOnV1aWQ6MWQwMzdjZjQtODQ4OS00YmI4LThjMjAtOGYyZTJkMTMyOTJkIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWUiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6RG5hZXFQTkZaNFlkVVJaY3pNaUxwNWNOcFAzU3VyWFpBNWI4dzQ2MXhSdzE5c2hCIn0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVZrcDZRbGMyYlhWVk1WOHlkR0Z6Y0RkU1RGaDZOMnRvZDB4V2IzRTJOVEJ0VTBwV05HeHRWekptVFNJc0luZ2lPaUkwYlV4Nk4yUnlNa3hwVDBKdmFIRXlhVEkwV2s5cUxXVlVWV2xDZEMweGRFVnRTbTltYkZCQmR6TnJJaXdpZVNJNklsOW1TMEpOYzA1V1RuSjRaVXcwTTFWMWRWUjBOVkZNYVVZd2NVbHZTRUZVU2t0MGQxZHlZWEJoWWxraWZRIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIl9zZCI6WyJmYmx3aEJsVkxnbmNvT0VQc05oTE9DbjE1LUVNMC1pamtjcjBQNUhDUGhnIl19fSwiaXNzdWVyRGlkIjoiZGlkOmtleTp6RG5hZXFQTkZaNFlkVVJaY3pNaUxwNWNOcFAzU3VyWFpBNWI4dzQ2MXhSdzE5c2hCIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA3LTE2VDA2OjUyOjAzLjA2Mzg2NDYzN1oiLCJfc2QiOlsiNmUxZjcyVGZVcEVWTkN5T3BESzJZd1RyN1I0VU1DaFdSa2lHT0pCMm14RSJdfSwianRpIjoidXJuOnV1aWQ6MWQwMzdjZjQtODQ4OS00YmI4LThjMjAtOGYyZTJkMTMyOTJkIiwiZXhwIjoxNzg0MTg0NzIzLCJpYXQiOjE3NTI2NDg3MjMsIm5iZiI6MTc1MjY0ODcyM30.cm2MuW6yqSAzckHwzFsYVgoBu1cwVr87wcqPgYasI6jUvabz6DxIhxMXbMuVw1ZVZSuyFekW_LYCETf-WdmIQg~WyI5SzFSMFozNE1tUk1lUFRGdXlqaDdBIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xNlQwNjo1MjowMy4wNjM4NDM5NjZaIl0~WyJXc1pqUmdZZG95dUw5VVhTR2ZSSTl3IiwibmFtZSIsIkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMiXQ",
                                "header": {
                                    "kid": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB#zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "fullPayload": {
                                    "iss": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:1d037cf4-8489-4bb8-8c20-8f2e2d13292d",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB"
                                        },
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "name": "Bachelor of Science and Arts"
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB",
                                        "expirationDate": "2026-07-16T06:52:03.063864637Z",
                                        "issuanceDate": "2025-07-16T06:52:03.063843966Z"
                                    },
                                    "jti": "urn:uuid:1d037cf4-8489-4bb8-8c20-8f2e2d13292d",
                                    "exp": 1784184723,
                                    "iat": 1752648723,
                                    "nbf": 1752648723
                                },
                                "undisclosedPayload": {
                                    "iss": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:1d037cf4-8489-4bb8-8c20-8f2e2d13292d",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB"
                                        },
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiVkp6Qlc2bXVVMV8ydGFzcDdSTFh6N2tod0xWb3E2NTBtU0pWNGxtVzJmTSIsIngiOiI0bUx6N2RyMkxpT0JvaHEyaTI0Wk9qLWVUVWlCdC0xdEVtSm9mbFBBdzNrIiwieSI6Il9mS0JNc05WTnJ4ZUw0M1V1dVR0NVFMaUYwcUlvSEFUSkt0d1dyYXBhYlkifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "_sd": [
                                                    "fblwhBlVLgncoOEPsNhLOCn15-EM0-ijkcr0P5HCPhg"
                                                ]
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaeqPNFZ4YdURZczMiLp5cNpP3SurXZA5b8w461xRw19shB",
                                        "expirationDate": "2026-07-16T06:52:03.063864637Z",
                                        "_sd": [
                                            "6e1f72TfUpEVNCyOpDK2YwTr7R4UMChWRkiGOJB2mxE"
                                        ]
                                    },
                                    "jti": "urn:uuid:1d037cf4-8489-4bb8-8c20-8f2e2d13292d",
                                    "exp": 1784184723,
                                    "iat": 1752648723,
                                    "nbf": 1752648723
                                },
                                "disclosures": {
                                    "6e1f72TfUpEVNCyOpDK2YwTr7R4UMChWRkiGOJB2mxE": {
                                        "disclosure": "WyI5SzFSMFozNE1tUk1lUFRGdXlqaDdBIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xNlQwNjo1MjowMy4wNjM4NDM5NjZaIl0",
                                        "salt": "9K1R0Z34MmRMePTFuyjh7A",
                                        "key": "issuanceDate",
                                        "value": "2025-07-16T06:52:03.063843966Z"
                                    },
                                    "fblwhBlVLgncoOEPsNhLOCn15-EM0-ijkcr0P5HCPhg": {
                                        "disclosure": "WyJXc1pqUmdZZG95dUw5VVhTR2ZSSTl3IiwibmFtZSIsIkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMiXQ",
                                        "salt": "WsZjRgYdoyuL9UXSGfRI9w",
                                        "key": "name",
                                        "value": "Bachelor of Science and Arts"
                                    }
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "verbose"
        }
    """.trimIndent()
    )
    private val openBadgeTwoDisclosuresSimple = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_simple",
                        "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                        "verifiableCredentials": [
                            {
                                "header": {
                                    "kid": "did:key:zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7#zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "payload": {
                                    "iss": "did:key:zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:d6d84108-894b-46c5-937d-9da3a012431f",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "expirationDate": "2026-07-16T06:49:40.414301494Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        },
                                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                        "issuanceDate": "2025-07-16T06:49:40.414284954Z"
                                    },
                                    "jti": "urn:uuid:d6d84108-894b-46c5-937d-9da3a012431f",
                                    "exp": 1784184580,
                                    "iat": 1752648580,
                                    "nbf": 1752648580
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "simple"
        }
    """.trimIndent()
    )
    private val openBadgeTwoDisclosuresVerbose = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_verbose",
                        "vp": {
                            "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYkV0NE1HbEZSRzFMZEU1VGR6RlVVVFJPUkhOUVVEUnRSMW80Y0cxSFVFZDBXWGxTVGt0aVowVmZheUlzSW5naU9pSm9jRUpxVEdFd1ZWbzRVV0ZGY2xGVVFUTllTRVZoT1d0blJtRmZWVk5XYzA1TmJXVkJORVZUYjBaQklpd2llU0k2SW5Sc01rOWxabGQxVXpCeVgxSm1OM1JOVkhoRFZXWktkMUZMUkdoek1YQllOV3RCWTBjeU9XRTJRVzhpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYkV0NE1HbEZSRzFMZEU1VGR6RlVVVFJPUkhOUVVEUnRSMW80Y0cxSFVFZDBXWGxTVGt0aVowVmZheUlzSW5naU9pSm9jRUpxVEdFd1ZWbzRVV0ZGY2xGVVFUTllTRVZoT1d0blJtRmZWVk5XYzA1TmJXVkJORVZUYjBaQklpd2llU0k2SW5Sc01rOWxabGQxVXpCeVgxSm1OM1JOVkhoRFZXWktkMUZMUkdoek1YQllOV3RCWTBjeU9XRTJRVzhpZlEiLCJuYmYiOjE3NTI2NDg1MjAsImlhdCI6MTc1MjY0ODU4MCwianRpIjoiSWNvYkMxMmJ4RTBVIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaWJFdDRNR2xGUkcxTGRFNVRkekZVVVRST1JITlFVRFJ0UjFvNGNHMUhVRWQwV1hsU1RrdGlaMFZmYXlJc0luZ2lPaUpvY0VKcVRHRXdWVm80VVdGRmNsRlVRVE5ZU0VWaE9XdG5SbUZmVlZOV2MwNU5iV1ZCTkVWVGIwWkJJaXdpZVNJNkluUnNNazlsWmxkMVV6QnlYMUptTjNSTlZIaERWV1pLZDFGTFJHaHpNWEJZTld0QlkwY3lPV0UyUVc4aWZRIiwibm9uY2UiOiJlOTFiMGEzNS03MGFiLTQ4ZTYtOGU0Zi01MGI1OTI5ZGY0MTYiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJJY29iQzEyYnhFMFUiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYkV0NE1HbEZSRzFMZEU1VGR6RlVVVFJPUkhOUVVEUnRSMW80Y0cxSFVFZDBXWGxTVGt0aVowVmZheUlzSW5naU9pSm9jRUpxVEdFd1ZWbzRVV0ZGY2xGVVFUTllTRVZoT1d0blJtRmZWVk5XYzA1TmJXVkJORVZUYjBaQklpd2llU0k2SW5Sc01rOWxabGQxVXpCeVgxSm1OM1JOVkhoRFZXWktkMUZMUkdoek1YQllOV3RCWTBjeU9XRTJRVzhpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsV2pGb1NFVkhVMXAzWnpWV1ducEhTa1ZyUzNsVk5XNW1SVE14TjNGMFJrdDRWazFpZUc1NGNVaGljRGNqZWtSdVlXVmFNV2hJUlVkVFduZG5OVlphZWtkS1JXdExlVlUxYm1aRk16RTNjWFJHUzNoV1RXSjRibmh4U0dKd055SXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsV2pGb1NFVkhVMXAzWnpWV1ducEhTa1ZyUzNsVk5XNW1SVE14TjNGMFJrdDRWazFpZUc1NGNVaGljRGNpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBZa1YwTkUxSGJFWlNSekZNWkVVMVZHUjZSbFZWVkZKUFVraE9VVlZFVW5SU01XODBZMGN4U0ZWRlpEQlhXR3hUVkd0MGFWb3dWbVpoZVVselNXNW5hVTlwU205alJVcHhWRWRGZDFaV2J6UlZWMFpHWTJ4R1ZWRlVUbGxUUlZab1QxZDBibEp0Um1aV1ZrNVhZekExVG1KWFZrSk9SVlpVWWpCYVFrbHBkMmxsVTBrMlNXNVNjMDFyT1d4YWJHUXhWWHBDZVZneFNtMU9NMUpPVmtob1JGWlhXa3RrTVVaTVVrZG9lazFZUWxsT1YzUkNXVEJqZVU5WFJUSlJWemhwWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2Y0hWeWJDNXBiWE5uYkc5aVlXd3ViM0puTDNOd1pXTXZiMkl2ZGpOd01DOWpiMjUwWlhoMExtcHpiMjRpWFN3aWFXUWlPaUoxY200NmRYVnBaRHBrTm1RNE5ERXdPQzA0T1RSaUxUUTJZelV0T1RNM1pDMDVaR0V6WVRBeE1qUXpNV1lpTENKMGVYQmxJanBiSWxabGNtbG1hV0ZpYkdWRGNtVmtaVzUwYVdGc0lpd2lUM0JsYmtKaFpHZGxRM0psWkdWdWRHbGhiQ0pkTENKcGMzTjFaWElpT25zaWRIbHdaU0k2V3lKUWNtOW1hV3hsSWwwc0ltbGtJam9pWkdsa09tdGxlVHA2Ukc1aFpWb3hhRWhGUjFOYWQyYzFWbHA2UjBwRmEwdDVWVFZ1WmtVek1UZHhkRVpMZUZaTlluaHVlSEZJWW5BM0lpd2libUZ0WlNJNklrcHZZbk1nWm05eUlIUm9aU0JHZFhSMWNtVWdLRXBHUmlraUxDSjFjbXdpT2lKb2RIUndjem92TDNkM2R5NXFabVl1YjNKbkx5SXNJbWx0WVdkbElqb2lhSFIwY0hNNkx5OTNNMk10WTJObkxtZHBkR2gxWWk1cGJ5OTJZeTFsWkM5d2JIVm5abVZ6ZEMweExUSXdNakl2YVcxaFoyVnpMMHBHUmw5TWIyZHZURzlqYTNWd0xuQnVaeUo5TENKbGVIQnBjbUYwYVc5dVJHRjBaU0k2SWpJd01qWXRNRGN0TVRaVU1EWTZORGs2TkRBdU5ERTBNekF4TkRrMFdpSXNJbU55WldSbGJuUnBZV3hUZFdKcVpXTjBJanA3SW1sa0lqb2laR2xrT21wM2F6cGxlVXB5WkVocmFVOXBTa1pSZVVselNXMU9lV1JwU1RaSmJFRjBUV3BWTWtscGQybGhNbXhyU1dwdmFXSkZkRFJOUjJ4R1VrY3hUR1JGTlZSa2VrWlZWVlJTVDFKSVRsRlZSRkowVWpGdk5HTkhNVWhWUldRd1YxaHNVMVJyZEdsYU1GWm1ZWGxKYzBsdVoybFBhVXB2WTBWS2NWUkhSWGRXVm04MFZWZEdSbU5zUmxWUlZFNVpVMFZXYUU5WGRHNVNiVVptVmxaT1YyTXdOVTVpVjFaQ1RrVldWR0l3V2tKSmFYZHBaVk5KTmtsdVVuTk5hemxzV214a01WVjZRbmxZTVVwdFRqTlNUbFpJYUVSV1YxcExaREZHVEZKSGFIcE5XRUpaVGxkMFFsa3dZM2xQVjBVeVVWYzRhV1pSSWl3aWRIbHdaU0k2V3lKQlkyaHBaWFpsYldWdWRGTjFZbXBsWTNRaVhTd2lZV05vYVdWMlpXMWxiblFpT25zaWFXUWlPaUoxY200NmRYVnBaRHBoWXpJMU5HSmtOUzA0Wm1Ga0xUUmlZakV0T1dReU9TMWxabVE1TXpnMU16WTVNallpTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MElsMHNJbTVoYldVaU9pSktSa1lnZUNCMll5MWxaSFVnVUd4MVowWmxjM1FnTXlCSmJuUmxjbTl3WlhKaFltbHNhWFI1SWl3aVpHVnpZM0pwY0hScGIyNGlPaUpVYUdseklIZGhiR3hsZENCemRYQndiM0owY3lCMGFHVWdkWE5sSUc5bUlGY3pReUJXWlhKcFptbGhZbXhsSUVOeVpXUmxiblJwWVd4eklHRnVaQ0JvWVhNZ1pHVnRiMjV6ZEhKaGRHVmtJR2x1ZEdWeWIzQmxjbUZpYVd4cGRIa2daSFZ5YVc1bklIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z2NtVnhkV1Z6ZENCM2IzSnJabXh2ZHlCa2RYSnBibWNnU2taR0lIZ2dWa010UlVSVklGQnNkV2RHWlhOMElETXVJaXdpWTNKcGRHVnlhV0VpT25zaWRIbHdaU0k2SWtOeWFYUmxjbWxoSWl3aWJtRnljbUYwYVhabElqb2lWMkZzYkdWMElITnZiSFYwYVc5dWN5QndjbTkyYVdSbGNuTWdaV0Z5Ym1Wa0lIUm9hWE1nWW1Ga1oyVWdZbmtnWkdWdGIyNXpkSEpoZEdsdVp5QnBiblJsY205d1pYSmhZbWxzYVhSNUlHUjFjbWx1WnlCMGFHVWdjSEpsYzJWdWRHRjBhVzl1SUhKbGNYVmxjM1FnZDI5eWEyWnNiM2N1SUZSb2FYTWdhVzVqYkhWa1pYTWdjM1ZqWTJWemMyWjFiR3g1SUhKbFkyVnBkbWx1WnlCaElIQnlaWE5sYm5SaGRHbHZiaUJ5WlhGMVpYTjBMQ0JoYkd4dmQybHVaeUIwYUdVZ2FHOXNaR1Z5SUhSdklITmxiR1ZqZENCaGRDQnNaV0Z6ZENCMGQyOGdkSGx3WlhNZ2IyWWdkbVZ5YVdacFlXSnNaU0JqY21Wa1pXNTBhV0ZzY3lCMGJ5QmpjbVZoZEdVZ1lTQjJaWEpwWm1saFlteGxJSEJ5WlhObGJuUmhkR2x2Yml3Z2NtVjBkWEp1YVc1bklIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z2RHOGdkR2hsSUhKbGNYVmxjM1J2Y2l3Z1lXNWtJSEJoYzNOcGJtY2dkbVZ5YVdacFkyRjBhVzl1SUc5bUlIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z1lXNWtJSFJvWlNCcGJtTnNkV1JsWkNCamNtVmtaVzUwYVdGc2N5NGlmU3dpYVcxaFoyVWlPbnNpYVdRaU9pSm9kSFJ3Y3pvdkwzY3pZeTFqWTJjdVoybDBhSFZpTG1sdkwzWmpMV1ZrTDNCc2RXZG1aWE4wTFRNdE1qQXlNeTlwYldGblpYTXZTa1pHTFZaRExVVkVWUzFRVEZWSFJrVlRWRE10WW1Ga1oyVXRhVzFoWjJVdWNHNW5JaXdpZEhsd1pTSTZJa2x0WVdkbEluMTlmU3dpWDNOa0lqcGJJbWhvUkVOM04xcEhiV1JoTVhSdmJuRlhXVEZpT0hNdFkyaFlXak5aVFZNMlIxaEVjWGhyYmxsdVEyOGlMQ0pMZEZkNUxUQlZiRUpSTUdWTloxSkxVRzQxUnpsS2JuZEtUVVowTkV0cGExUlNRa2hwVjI5RVVtUTRJbDE5TENKcWRHa2lPaUoxY200NmRYVnBaRHBrTm1RNE5ERXdPQzA0T1RSaUxUUTJZelV0T1RNM1pDMDVaR0V6WVRBeE1qUXpNV1lpTENKbGVIQWlPakUzT0RReE9EUTFPREFzSW1saGRDSTZNVGMxTWpZME9EVTRNQ3dpYm1KbUlqb3hOelV5TmpRNE5UZ3dmUS5aLUpxbGdSbkhkMFAzcVJRUFBtdmJjaWtZYjFxMlNwZTBqaGJQc0xaMWhSWHJ4ZjhTOXNGbGFDcWQ2cnlkSExGdGZ3Q1RPLUk2cVdwaDBqbVRScldQd35XeUppYldkcU1tTXdia1oxYTFaUlRGQnJXREJSYW1kUklpd2libUZ0WlNJc0lrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpWFF-V3lKMWFITm5RbEJuVDJsU1dVb3pTMnRsZVZKaU5IcG5JaXdpYVhOemRXRnVZMlZFWVhSbElpd2lNakF5TlMwd055MHhObFF3TmpvME9UbzBNQzQwTVRReU9EUTVOVFJhSWwwIl19fQ.rn-8871YzIWQ0_r5LIlhgVz_uc4xsmSZrZH9d6AgGDy1HD3WdzjYj0EOT4Jm1DmeufBwY8V4hKxiH9a9_sgSoQ",
                            "header": {
                                "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ#0",
                                "typ": "JWT",
                                "alg": "ES256"
                            },
                            "payload": {
                                "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                "nbf": 1752648520,
                                "iat": 1752648580,
                                "jti": "IcobC12bxE0U",
                                "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                "nonce": "e91b0a35-70ab-48e6-8e4f-50b5929df416",
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "vp": {
                                    "@context": [
                                        "https://www.w3.org/2018/credentials/v1"
                                    ],
                                    "type": [
                                        "VerifiablePresentation"
                                    ],
                                    "id": "IcobC12bxE0U",
                                    "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                    "verifiableCredential": [
                                        "eyJraWQiOiJkaWQ6a2V5OnpEbmFlWjFoSEVHU1p3ZzVWWnpHSkVrS3lVNW5mRTMxN3F0Rkt4Vk1ieG54cUhicDcjekRuYWVaMWhIRUdTWndnNVZaekdKRWtLeVU1bmZFMzE3cXRGS3hWTWJ4bnhxSGJwNyIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlWjFoSEVHU1p3ZzVWWnpHSkVrS3lVNW5mRTMxN3F0Rkt4Vk1ieG54cUhicDciLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYkV0NE1HbEZSRzFMZEU1VGR6RlVVVFJPUkhOUVVEUnRSMW80Y0cxSFVFZDBXWGxTVGt0aVowVmZheUlzSW5naU9pSm9jRUpxVEdFd1ZWbzRVV0ZGY2xGVVFUTllTRVZoT1d0blJtRmZWVk5XYzA1TmJXVkJORVZUYjBaQklpd2llU0k2SW5Sc01rOWxabGQxVXpCeVgxSm1OM1JOVkhoRFZXWktkMUZMUkdoek1YQllOV3RCWTBjeU9XRTJRVzhpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDpkNmQ4NDEwOC04OTRiLTQ2YzUtOTM3ZC05ZGEzYTAxMjQzMWYiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sImlkIjoiZGlkOmtleTp6RG5hZVoxaEhFR1Nad2c1Vlp6R0pFa0t5VTVuZkUzMTdxdEZLeFZNYnhueHFIYnA3IiwibmFtZSI6IkpvYnMgZm9yIHRoZSBGdXR1cmUgKEpGRikiLCJ1cmwiOiJodHRwczovL3d3dy5qZmYub3JnLyIsImltYWdlIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0xLTIwMjIvaW1hZ2VzL0pGRl9Mb2dvTG9ja3VwLnBuZyJ9LCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDctMTZUMDY6NDk6NDAuNDE0MzAxNDk0WiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaWJFdDRNR2xGUkcxTGRFNVRkekZVVVRST1JITlFVRFJ0UjFvNGNHMUhVRWQwV1hsU1RrdGlaMFZmYXlJc0luZ2lPaUpvY0VKcVRHRXdWVm80VVdGRmNsRlVRVE5ZU0VWaE9XdG5SbUZmVlZOV2MwNU5iV1ZCTkVWVGIwWkJJaXdpZVNJNkluUnNNazlsWmxkMVV6QnlYMUptTjNSTlZIaERWV1pLZDFGTFJHaHpNWEJZTld0QlkwY3lPV0UyUVc4aWZRIiwidHlwZSI6WyJBY2hpZXZlbWVudFN1YmplY3QiXSwiYWNoaWV2ZW1lbnQiOnsiaWQiOiJ1cm46dXVpZDphYzI1NGJkNS04ZmFkLTRiYjEtOWQyOS1lZmQ5Mzg1MzY5MjYiLCJ0eXBlIjpbIkFjaGlldmVtZW50Il0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiZGVzY3JpcHRpb24iOiJUaGlzIHdhbGxldCBzdXBwb3J0cyB0aGUgdXNlIG9mIFczQyBWZXJpZmlhYmxlIENyZWRlbnRpYWxzIGFuZCBoYXMgZGVtb25zdHJhdGVkIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdyBkdXJpbmcgSkZGIHggVkMtRURVIFBsdWdGZXN0IDMuIiwiY3JpdGVyaWEiOnsidHlwZSI6IkNyaXRlcmlhIiwibmFycmF0aXZlIjoiV2FsbGV0IHNvbHV0aW9ucyBwcm92aWRlcnMgZWFybmVkIHRoaXMgYmFkZ2UgYnkgZGVtb25zdHJhdGluZyBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cuIFRoaXMgaW5jbHVkZXMgc3VjY2Vzc2Z1bGx5IHJlY2VpdmluZyBhIHByZXNlbnRhdGlvbiByZXF1ZXN0LCBhbGxvd2luZyB0aGUgaG9sZGVyIHRvIHNlbGVjdCBhdCBsZWFzdCB0d28gdHlwZXMgb2YgdmVyaWZpYWJsZSBjcmVkZW50aWFscyB0byBjcmVhdGUgYSB2ZXJpZmlhYmxlIHByZXNlbnRhdGlvbiwgcmV0dXJuaW5nIHRoZSBwcmVzZW50YXRpb24gdG8gdGhlIHJlcXVlc3RvciwgYW5kIHBhc3NpbmcgdmVyaWZpY2F0aW9uIG9mIHRoZSBwcmVzZW50YXRpb24gYW5kIHRoZSBpbmNsdWRlZCBjcmVkZW50aWFscy4ifSwiaW1hZ2UiOnsiaWQiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTMtMjAyMy9pbWFnZXMvSkZGLVZDLUVEVS1QTFVHRkVTVDMtYmFkZ2UtaW1hZ2UucG5nIiwidHlwZSI6IkltYWdlIn19fSwiX3NkIjpbImhoREN3N1pHbWRhMXRvbnFXWTFiOHMtY2hYWjNZTVM2R1hEcXhrblluQ28iLCJLdFd5LTBVbEJRMGVNZ1JLUG41RzlKbndKTUZ0NEtpa1RSQkhpV29EUmQ4Il19LCJqdGkiOiJ1cm46dXVpZDpkNmQ4NDEwOC04OTRiLTQ2YzUtOTM3ZC05ZGEzYTAxMjQzMWYiLCJleHAiOjE3ODQxODQ1ODAsImlhdCI6MTc1MjY0ODU4MCwibmJmIjoxNzUyNjQ4NTgwfQ.Z-JqlgRnHd0P3qRQPPmvbcikYb1q2Spe0jhbPsLZ1hRXrxf8S9sFlaCqd6rydHLFtfwCTO-I6qWph0jmTRrWPw~WyJibWdqMmMwbkZ1a1ZRTFBrWDBRamdRIiwibmFtZSIsIkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiXQ~WyJ1aHNnQlBnT2lSWUozS2tleVJiNHpnIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xNlQwNjo0OTo0MC40MTQyODQ5NTRaIl0"
                                    ]
                                }
                            }
                        },
                        "verifiableCredentials": [
                            {
                                "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFlWjFoSEVHU1p3ZzVWWnpHSkVrS3lVNW5mRTMxN3F0Rkt4Vk1ieG54cUhicDcjekRuYWVaMWhIRUdTWndnNVZaekdKRWtLeVU1bmZFMzE3cXRGS3hWTWJ4bnhxSGJwNyIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlWjFoSEVHU1p3ZzVWWnpHSkVrS3lVNW5mRTMxN3F0Rkt4Vk1ieG54cUhicDciLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYkV0NE1HbEZSRzFMZEU1VGR6RlVVVFJPUkhOUVVEUnRSMW80Y0cxSFVFZDBXWGxTVGt0aVowVmZheUlzSW5naU9pSm9jRUpxVEdFd1ZWbzRVV0ZGY2xGVVFUTllTRVZoT1d0blJtRmZWVk5XYzA1TmJXVkJORVZUYjBaQklpd2llU0k2SW5Sc01rOWxabGQxVXpCeVgxSm1OM1JOVkhoRFZXWktkMUZMUkdoek1YQllOV3RCWTBjeU9XRTJRVzhpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDpkNmQ4NDEwOC04OTRiLTQ2YzUtOTM3ZC05ZGEzYTAxMjQzMWYiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sImlkIjoiZGlkOmtleTp6RG5hZVoxaEhFR1Nad2c1Vlp6R0pFa0t5VTVuZkUzMTdxdEZLeFZNYnhueHFIYnA3IiwibmFtZSI6IkpvYnMgZm9yIHRoZSBGdXR1cmUgKEpGRikiLCJ1cmwiOiJodHRwczovL3d3dy5qZmYub3JnLyIsImltYWdlIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0xLTIwMjIvaW1hZ2VzL0pGRl9Mb2dvTG9ja3VwLnBuZyJ9LCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDctMTZUMDY6NDk6NDAuNDE0MzAxNDk0WiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaWJFdDRNR2xGUkcxTGRFNVRkekZVVVRST1JITlFVRFJ0UjFvNGNHMUhVRWQwV1hsU1RrdGlaMFZmYXlJc0luZ2lPaUpvY0VKcVRHRXdWVm80VVdGRmNsRlVRVE5ZU0VWaE9XdG5SbUZmVlZOV2MwNU5iV1ZCTkVWVGIwWkJJaXdpZVNJNkluUnNNazlsWmxkMVV6QnlYMUptTjNSTlZIaERWV1pLZDFGTFJHaHpNWEJZTld0QlkwY3lPV0UyUVc4aWZRIiwidHlwZSI6WyJBY2hpZXZlbWVudFN1YmplY3QiXSwiYWNoaWV2ZW1lbnQiOnsiaWQiOiJ1cm46dXVpZDphYzI1NGJkNS04ZmFkLTRiYjEtOWQyOS1lZmQ5Mzg1MzY5MjYiLCJ0eXBlIjpbIkFjaGlldmVtZW50Il0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiZGVzY3JpcHRpb24iOiJUaGlzIHdhbGxldCBzdXBwb3J0cyB0aGUgdXNlIG9mIFczQyBWZXJpZmlhYmxlIENyZWRlbnRpYWxzIGFuZCBoYXMgZGVtb25zdHJhdGVkIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdyBkdXJpbmcgSkZGIHggVkMtRURVIFBsdWdGZXN0IDMuIiwiY3JpdGVyaWEiOnsidHlwZSI6IkNyaXRlcmlhIiwibmFycmF0aXZlIjoiV2FsbGV0IHNvbHV0aW9ucyBwcm92aWRlcnMgZWFybmVkIHRoaXMgYmFkZ2UgYnkgZGVtb25zdHJhdGluZyBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cuIFRoaXMgaW5jbHVkZXMgc3VjY2Vzc2Z1bGx5IHJlY2VpdmluZyBhIHByZXNlbnRhdGlvbiByZXF1ZXN0LCBhbGxvd2luZyB0aGUgaG9sZGVyIHRvIHNlbGVjdCBhdCBsZWFzdCB0d28gdHlwZXMgb2YgdmVyaWZpYWJsZSBjcmVkZW50aWFscyB0byBjcmVhdGUgYSB2ZXJpZmlhYmxlIHByZXNlbnRhdGlvbiwgcmV0dXJuaW5nIHRoZSBwcmVzZW50YXRpb24gdG8gdGhlIHJlcXVlc3RvciwgYW5kIHBhc3NpbmcgdmVyaWZpY2F0aW9uIG9mIHRoZSBwcmVzZW50YXRpb24gYW5kIHRoZSBpbmNsdWRlZCBjcmVkZW50aWFscy4ifSwiaW1hZ2UiOnsiaWQiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTMtMjAyMy9pbWFnZXMvSkZGLVZDLUVEVS1QTFVHRkVTVDMtYmFkZ2UtaW1hZ2UucG5nIiwidHlwZSI6IkltYWdlIn19fSwiX3NkIjpbImhoREN3N1pHbWRhMXRvbnFXWTFiOHMtY2hYWjNZTVM2R1hEcXhrblluQ28iLCJLdFd5LTBVbEJRMGVNZ1JLUG41RzlKbndKTUZ0NEtpa1RSQkhpV29EUmQ4Il19LCJqdGkiOiJ1cm46dXVpZDpkNmQ4NDEwOC04OTRiLTQ2YzUtOTM3ZC05ZGEzYTAxMjQzMWYiLCJleHAiOjE3ODQxODQ1ODAsImlhdCI6MTc1MjY0ODU4MCwibmJmIjoxNzUyNjQ4NTgwfQ.Z-JqlgRnHd0P3qRQPPmvbcikYb1q2Spe0jhbPsLZ1hRXrxf8S9sFlaCqd6rydHLFtfwCTO-I6qWph0jmTRrWPw~WyJibWdqMmMwbkZ1a1ZRTFBrWDBRamdRIiwibmFtZSIsIkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiXQ~WyJ1aHNnQlBnT2lSWUozS2tleVJiNHpnIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xNlQwNjo0OTo0MC40MTQyODQ5NTRaIl0",
                                "header": {
                                    "kid": "did:key:zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7#zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "fullPayload": {
                                    "iss": "did:key:zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:d6d84108-894b-46c5-937d-9da3a012431f",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "expirationDate": "2026-07-16T06:49:40.414301494Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        },
                                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                        "issuanceDate": "2025-07-16T06:49:40.414284954Z"
                                    },
                                    "jti": "urn:uuid:d6d84108-894b-46c5-937d-9da3a012431f",
                                    "exp": 1784184580,
                                    "iat": 1752648580,
                                    "nbf": 1752648580
                                },
                                "undisclosedPayload": {
                                    "iss": "did:key:zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:d6d84108-894b-46c5-937d-9da3a012431f",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaeZ1hHEGSZwg5VZzGJEkKyU5nfE317qtFKxVMbxnxqHbp7",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "expirationDate": "2026-07-16T06:49:40.414301494Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoibEt4MGlFRG1LdE5TdzFUUTRORHNQUDRtR1o4cG1HUEd0WXlSTktiZ0VfayIsIngiOiJocEJqTGEwVVo4UWFFclFUQTNYSEVhOWtnRmFfVVNWc05NbWVBNEVTb0ZBIiwieSI6InRsMk9lZld1UzByX1JmN3RNVHhDVWZKd1FLRGhzMXBYNWtBY0cyOWE2QW8ifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        },
                                        "_sd": [
                                            "hhDCw7ZGmda1tonqWY1b8s-chXZ3YMS6GXDqxknYnCo",
                                            "KtWy-0UlBQ0eMgRKPn5G9JnwJMFt4KikTRBHiWoDRd8"
                                        ]
                                    },
                                    "jti": "urn:uuid:d6d84108-894b-46c5-937d-9da3a012431f",
                                    "exp": 1784184580,
                                    "iat": 1752648580,
                                    "nbf": 1752648580
                                },
                                "disclosures": {
                                    "hhDCw7ZGmda1tonqWY1b8s-chXZ3YMS6GXDqxknYnCo": {
                                        "disclosure": "WyJibWdqMmMwbkZ1a1ZRTFBrWDBRamdRIiwibmFtZSIsIkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiXQ",
                                        "salt": "bmgj2c0nFukVQLPkX0QjgQ",
                                        "key": "name",
                                        "value": "JFF x vc-edu PlugFest 3 Interoperability"
                                    },
                                    "KtWy-0UlBQ0eMgRKPn5G9JnwJMFt4KikTRBHiWoDRd8": {
                                        "disclosure": "WyJ1aHNnQlBnT2lSWUozS2tleVJiNHpnIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xNlQwNjo0OTo0MC40MTQyODQ5NTRaIl0",
                                        "salt": "uhsgBPgOiRYJ3KkeyRb4zg",
                                        "key": "issuanceDate",
                                        "value": "2025-07-16T06:49:40.414284954Z"
                                    }
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "verbose"
        }
    """.trimIndent()
    )

    private val sdJwtVcIdentityCredentialSimple = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "vc+sd-jwt": [
                    {
                        "type": "sd_jwt_vc_view_simple",
                        "vc": {
                            "header": {
                                "x5c": [
                                    "-----BEGIN CERTIFICATE-----\nMIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB\n-----END CERTIFICATE-----"
                                ],
                                "kid": "9vuaJyUxRx4KmHyoZ9kjJxMs_mjpnnf-mPM9nPMG51A",
                                "typ": "vc+sd-jwt",
                                "alg": "ES256"
                            },
                            "payload": {
                                "given_name": "John",
                                "email": "johndoe@example.com",
                                "phone_number": "+1-202-555-0101",
                                "address": {
                                    "street_address": "123 Main St",
                                    "locality": "Anytown",
                                    "region": "Anystate",
                                    "country": "US"
                                },
                                "is_over_18": true,
                                "is_over_21": true,
                                "is_over_65": true,
                                "id": "urn:uuid:f4411ed9-d3eb-4c5d-971a-ffb2872b616d",
                                "iat": 1752648454,
                                "nbf": 1752648454,
                                "exp": 1784184454,
                                "iss": "http://localhost:22222/draft13",
                                "cnf": {
                                    "jwk": {
                                        "kty": "EC",
                                        "crv": "P-256",
                                        "kid": "pE7QRzzJ3utoKynTja7JyeAzcQefzZO24rEdWwkGtT0",
                                        "x": "oRIMe5XVIcJGDSW10r19w5jym8W-btkxChzecsuXUhc",
                                        "y": "XQrS0p2BJf_WXIcn-RAdFoX17DJsefpED3Ct4t4W-X0"
                                    }
                                },
                                "vct": "http://localhost:22222/identity_credential",
                                "display": [],
                                "family_name": "Doe",
                                "birthdate": "1940-01-01"
                            }
                        },
                        "keyBinding": {
                            "header": {
                                "kid": "pE7QRzzJ3utoKynTja7JyeAzcQefzZO24rEdWwkGtT0",
                                "typ": "kb+jwt",
                                "alg": "ES256"
                            },
                            "payload": {
                                "iat": 1752648455,
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "nonce": "196b37da-77d0-463f-897c-5a0c1c6d5623",
                                "sd_hash": "iywnYcva2cWTVcclB1gECoI-x-y3IGz07snR_4QiWyE"
                            }
                        }
                    }
                ]
            },
            "viewMode": "simple"
        }
    """.trimIndent()
    )
    private val sdJwtVcIdentityCredentialVerbose = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "vc+sd-jwt": [
                    {
                        "type": "sd_jwt_vc_view_verbose",
                        "raw": "eyJ4NWMiOlsiLS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tXG5NSUlCZVRDQ0FSOENGSHJXZ3JHbDVLZGVmU3ZSUWhSK2FvcWRmNDgrTUFvR0NDcUdTTTQ5QkFNQ01CY3hGVEFUQmdOVkJBTU1ERTFFVDBNZ1VrOVBWQ0JEUVRBZ0Z3MHlOVEExTVRReE5EQTRNRGxhR0E4eU1EYzFNRFV3TWpFME1EZ3dPVm93WlRFTE1Ba0dBMVVFQmhNQ1FWUXhEekFOQmdOVkJBZ01CbFpwWlc1dVlURVBNQTBHQTFVRUJ3d0dWbWxsYm01aE1SQXdEZ1lEVlFRS0RBZDNZV3gwTG1sa01SQXdEZ1lEVlFRTERBZDNZV3gwTG1sa01SQXdEZ1lEVlFRRERBZDNZV3gwTG1sek1Ga3dFd1lIS29aSXpqMENBUVlJS29aSXpqMERBUWNEUWdBRUcwUklOQmlGK29RVUQzZDVER25lZ1F1WGVuSTI5SkRhTUdvTXZpb0tSQk41M2Q0VWF6YWtTMnVudThCbnNFdHh1dFMya3FSaFlCUFlrOVJBcmlVM2dUQUtCZ2dxaGtqT1BRUURBZ05JQURCRkFpQU9Nd003aEg3cTlEaSttVDZxQ2k0THZCK2tIOE94TWhlSXJaMmVSUHh0RFFJaEFMSHpUeHd2TjhVZHQwWjJDcG84SkJpaHFhY2ZlWGtJeFZBTzhYa3htWGhCXG4tLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tIl0sImtpZCI6Ijl2dWFKeVV4Ung0S21IeW9aOWtqSnhNc19tanBubmYtbVBNOW5QTUc1MUEiLCJ0eXAiOiJ2YytzZC1qd3QiLCJhbGciOiJFUzI1NiJ9.eyJnaXZlbl9uYW1lIjoiSm9obiIsImVtYWlsIjoiam9obmRvZUBleGFtcGxlLmNvbSIsInBob25lX251bWJlciI6IisxLTIwMi01NTUtMDEwMSIsImFkZHJlc3MiOnsic3RyZWV0X2FkZHJlc3MiOiIxMjMgTWFpbiBTdCIsImxvY2FsaXR5IjoiQW55dG93biIsInJlZ2lvbiI6IkFueXN0YXRlIiwiY291bnRyeSI6IlVTIn0sImlzX292ZXJfMTgiOnRydWUsImlzX292ZXJfMjEiOnRydWUsImlzX292ZXJfNjUiOnRydWUsImlkIjoidXJuOnV1aWQ6ZjQ0MTFlZDktZDNlYi00YzVkLTk3MWEtZmZiMjg3MmI2MTZkIiwiaWF0IjoxNzUyNjQ4NDU0LCJuYmYiOjE3NTI2NDg0NTQsImV4cCI6MTc4NDE4NDQ1NCwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDoyMjIyMi9kcmFmdDEzIiwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoicEU3UVJ6ekozdXRvS3luVGphN0p5ZUF6Y1FlZnpaTzI0ckVkV3drR3RUMCIsIngiOiJvUklNZTVYVkljSkdEU1cxMHIxOXc1anltOFctYnRreENoemVjc3VYVWhjIiwieSI6IlhRclMwcDJCSmZfV1hJY24tUkFkRm9YMTdESnNlZnBFRDNDdDR0NFctWDAifX0sInZjdCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvaWRlbnRpdHlfY3JlZGVudGlhbCIsImRpc3BsYXkiOltdLCJfc2QiOlsiV0NzTVZhMWxtUlBuRE1FSXVtSkg1UjJ3MDdLek5sRXlmLXFibS1sdlJlQSIsInZ5Q2trOG5rTlpjU05CcXpMeWhTekRwMUF1d25TOTExdWtyRmdtRlpQRkkiXX0.c2smAamo4kc6YxE5-SqF5ZIVIabdm5TZxHcESC3MjpoKaEF3ZL6HO8XeOid-0wD0XuPHscqjilmyiqFkk6yuaw~WyJjdFdidkpYeGJmbVRzMlJ3UFBXZ2d3IiwiZmFtaWx5X25hbWUiLCJEb2UiXQ~WyJ6cWRyTmZSUHp4WWl2YnZaZXZFaDF3IiwiYmlydGhkYXRlIiwiMTk0MC0wMS0wMSJd~eyJraWQiOiJwRTdRUnp6SjN1dG9LeW5UamE3SnllQXpjUWVmelpPMjRyRWRXd2tHdFQwIiwidHlwIjoia2Irand0IiwiYWxnIjoiRVMyNTYifQ.eyJpYXQiOjE3NTI2NDg0NTUsImF1ZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvb3BlbmlkNHZjL3ZlcmlmeSIsIm5vbmNlIjoiMTk2YjM3ZGEtNzdkMC00NjNmLTg5N2MtNWEwYzFjNmQ1NjIzIiwic2RfaGFzaCI6Iml5d25ZY3ZhMmNXVFZjY2xCMWdFQ29JLXgteTNJR3owN3NuUl80UWlXeUUifQ.XOZ_Y5n8OVuVS7OqkJz6O26ppf767ryIt8sVxxpQKFqYgLEMRd--WsVepvs6JQWiccavm4iGwlAokbw1ESPl6Q",
                        "vc": {
                            "header": {
                                "x5c": [
                                    "-----BEGIN CERTIFICATE-----\nMIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB\n-----END CERTIFICATE-----"
                                ],
                                "kid": "9vuaJyUxRx4KmHyoZ9kjJxMs_mjpnnf-mPM9nPMG51A",
                                "typ": "vc+sd-jwt",
                                "alg": "ES256"
                            },
                            "fullPayload": {
                                "given_name": "John",
                                "email": "johndoe@example.com",
                                "phone_number": "+1-202-555-0101",
                                "address": {
                                    "street_address": "123 Main St",
                                    "locality": "Anytown",
                                    "region": "Anystate",
                                    "country": "US"
                                },
                                "is_over_18": true,
                                "is_over_21": true,
                                "is_over_65": true,
                                "id": "urn:uuid:f4411ed9-d3eb-4c5d-971a-ffb2872b616d",
                                "iat": 1752648454,
                                "nbf": 1752648454,
                                "exp": 1784184454,
                                "iss": "http://localhost:22222/draft13",
                                "cnf": {
                                    "jwk": {
                                        "kty": "EC",
                                        "crv": "P-256",
                                        "kid": "pE7QRzzJ3utoKynTja7JyeAzcQefzZO24rEdWwkGtT0",
                                        "x": "oRIMe5XVIcJGDSW10r19w5jym8W-btkxChzecsuXUhc",
                                        "y": "XQrS0p2BJf_WXIcn-RAdFoX17DJsefpED3Ct4t4W-X0"
                                    }
                                },
                                "vct": "http://localhost:22222/identity_credential",
                                "display": [],
                                "family_name": "Doe",
                                "birthdate": "1940-01-01"
                            },
                            "undisclosedPayload": {
                                "given_name": "John",
                                "email": "johndoe@example.com",
                                "phone_number": "+1-202-555-0101",
                                "address": {
                                    "street_address": "123 Main St",
                                    "locality": "Anytown",
                                    "region": "Anystate",
                                    "country": "US"
                                },
                                "is_over_18": true,
                                "is_over_21": true,
                                "is_over_65": true,
                                "id": "urn:uuid:f4411ed9-d3eb-4c5d-971a-ffb2872b616d",
                                "iat": 1752648454,
                                "nbf": 1752648454,
                                "exp": 1784184454,
                                "iss": "http://localhost:22222/draft13",
                                "cnf": {
                                    "jwk": {
                                        "kty": "EC",
                                        "crv": "P-256",
                                        "kid": "pE7QRzzJ3utoKynTja7JyeAzcQefzZO24rEdWwkGtT0",
                                        "x": "oRIMe5XVIcJGDSW10r19w5jym8W-btkxChzecsuXUhc",
                                        "y": "XQrS0p2BJf_WXIcn-RAdFoX17DJsefpED3Ct4t4W-X0"
                                    }
                                },
                                "vct": "http://localhost:22222/identity_credential",
                                "display": [],
                                "_sd": [
                                    "WCsMVa1lmRPnDMEIumJH5R2w07KzNlEyf-qbm-lvReA",
                                    "vyCkk8nkNZcSNBqzLyhSzDp1AuwnS911ukrFgmFZPFI"
                                ]
                            },
                            "disclosures": {
                                "WCsMVa1lmRPnDMEIumJH5R2w07KzNlEyf-qbm-lvReA": {
                                    "disclosure": "WyJjdFdidkpYeGJmbVRzMlJ3UFBXZ2d3IiwiZmFtaWx5X25hbWUiLCJEb2UiXQ",
                                    "salt": "ctWbvJXxbfmTs2RwPPWggw",
                                    "key": "family_name",
                                    "value": "Doe"
                                },
                                "vyCkk8nkNZcSNBqzLyhSzDp1AuwnS911ukrFgmFZPFI": {
                                    "disclosure": "WyJ6cWRyTmZSUHp4WWl2YnZaZXZFaDF3IiwiYmlydGhkYXRlIiwiMTk0MC0wMS0wMSJd",
                                    "salt": "zqdrNfRPzxYivbvZevEh1w",
                                    "key": "birthdate",
                                    "value": "1940-01-01"
                                }
                            }
                        },
                        "keyBinding": {
                            "header": {
                                "kid": "pE7QRzzJ3utoKynTja7JyeAzcQefzZO24rEdWwkGtT0",
                                "typ": "kb+jwt",
                                "alg": "ES256"
                            },
                            "payload": {
                                "iat": 1752648455,
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "nonce": "196b37da-77d0-463f-897c-5a0c1c6d5623",
                                "sd_hash": "iywnYcva2cWTVcclB1gECoI-x-y3IGz07snR_4QiWyE"
                            }
                        }
                    }
                ]
            },
            "viewMode": "verbose"
        }
    """.trimIndent()
    )

    private val mDLSimple = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "mso_mdoc": [
                    {
                        "type": "mso_mdoc_view_simple",
                        "version": "1.0",
                        "status": 0,
                        "documents": [
                            {
                                "docType": "org.iso.18013.5.1.mDL",
                                "nameSpaces": {
                                    "org.iso.18013.5.1": {
                                        "family_name": "Doe",
                                        "given_name": "John",
                                        "birth_date": "1986-03-22",
                                        "issue_date": "2019-10-20",
                                        "expiry_date": "2024-10-20",
                                        "issuing_country": "AT",
                                        "issuing_authority": "AT DMV",
                                        "document_number": 123456789,
                                        "portrait": [
                                            141,
                                            182,
                                            121,
                                            111,
                                            238,
                                            50,
                                            120,
                                            94,
                                            54,
                                            111,
                                            113,
                                            13,
                                            241,
                                            12,
                                            12
                                        ],
                                        "driving_privileges": [
                                            {
                                                "vehicle_category_code": "A",
                                                "issue_date": "2018-08-09",
                                                "expiry_date": "2024-10-20"
                                            },
                                            {
                                                "vehicle_category_code": "B",
                                                "issue_date": "2017-02-23",
                                                "expiry_date": "2024-10-20"
                                            }
                                        ],
                                        "un_distinguishing_sign": "AT"
                                    }
                                },
                                "certificateChain": [
                                    "-----BEGIN CERTIFICATE-----\nMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M\n-----END CERTIFICATE-----"
                                ],
                                "validityInfo": {
                                    "signed": "2025-07-16T07:28:10.396597247Z",
                                    "validFrom": "2025-07-16T07:28:10.396597357Z",
                                    "validUntil": "2026-07-16T07:28:10.396597407Z"
                                },
                                "deviceKey": {
                                    "kty": "EC",
                                    "crv": "P-256",
                                    "x": "wDAbXvruEhxgchNp_M-lCaTU-oVbk4iz90wBah3RoN0",
                                    "y": "WHY287BApRNQkUrNQVEDUZUID5W0D8gi01EZpHG7e6I"
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "simple"
        }
    """.trimIndent()
    )
    private val mDLVerbose = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "mso_mdoc": [
                    {
                        "type": "mso_mdoc_view_verbose",
                        "raw": "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGxpc3N1ZXJTaWduZWSiam5hbWVTcGFjZXOhcW9yZy5pc28uMTgwMTMuNS4xi9gYWFKkaGRpZ2VzdElEAGZyYW5kb21Q6jtBNq_iT1tw9ackPuQNcHFlbGVtZW50SWRlbnRpZmllcmtmYW1pbHlfbmFtZWxlbGVtZW50VmFsdWVjRG9l2BhYUqRoZGlnZXN0SUQBZnJhbmRvbVAF97buvW0vtdvvyidYa3qQcWVsZW1lbnRJZGVudGlmaWVyamdpdmVuX25hbWVsZWxlbWVudFZhbHVlZEpvaG7YGFhYpGhkaWdlc3RJRAJmcmFuZG9tUIp69etSESKQMr4A3-eNsL9xZWxlbWVudElkZW50aWZpZXJqYmlydGhfZGF0ZWxlbGVtZW50VmFsdWVqMTk4Ni0wMy0yMtgYWFikaGRpZ2VzdElEA2ZyYW5kb21QHo_HvY6nN-lD-4p9Q2BvznFlbGVtZW50SWRlbnRpZmllcmppc3N1ZV9kYXRlbGVsZW1lbnRWYWx1ZWoyMDE5LTEwLTIw2BhYWaRoZGlnZXN0SUQEZnJhbmRvbVC3Us6PvyqxANfy7WZrwR-bcWVsZW1lbnRJZGVudGlmaWVya2V4cGlyeV9kYXRlbGVsZW1lbnRWYWx1ZWoyMDI0LTEwLTIw2BhYVaRoZGlnZXN0SUQFZnJhbmRvbVBQDUUmVFjsYhQcK7WZ4ApycWVsZW1lbnRJZGVudGlmaWVyb2lzc3VpbmdfY291bnRyeWxlbGVtZW50VmFsdWViQVTYGFhbpGhkaWdlc3RJRAZmcmFuZG9tUP1DNM51--gzzLH8I5Bjx7VxZWxlbWVudElkZW50aWZpZXJxaXNzdWluZ19hdXRob3JpdHlsZWxlbWVudFZhbHVlZkFUIERNVtgYWFekaGRpZ2VzdElEB2ZyYW5kb21QFgjaYCngk7V46uUeXTBX-XFlbGVtZW50SWRlbnRpZmllcm9kb2N1bWVudF9udW1iZXJsZWxlbWVudFZhbHVlGgdbzRXYGFhnpGhkaWdlc3RJRAhmcmFuZG9tUA-b-wZHW3ERzIb6hs3HAapxZWxlbWVudElkZW50aWZpZXJocG9ydHJhaXRsZWxlbWVudFZhbHVljxiNGLYYeRhvGO4YMhh4GF4YNhhvGHENGPEMDNgYWOKkaGRpZ2VzdElECWZyYW5kb21QGFSyeNBXnznFVEBHHpqZlXFlbGVtZW50SWRlbnRpZmllcnJkcml2aW5nX3ByaXZpbGVnZXNsZWxlbWVudFZhbHVlgqN1dmVoaWNsZV9jYXRlZ29yeV9jb2RlYUFqaXNzdWVfZGF0ZWoyMDE4LTA4LTA5a2V4cGlyeV9kYXRlajIwMjQtMTAtMjCjdXZlaGljbGVfY2F0ZWdvcnlfY29kZWFCamlzc3VlX2RhdGVqMjAxNy0wMi0yM2tleHBpcnlfZGF0ZWoyMDI0LTEwLTIw2BhYXKRoZGlnZXN0SUQKZnJhbmRvbVC7AKxX3rE-D3-Y0_MtrznwcWVsZW1lbnRJZGVudGlmaWVydnVuX2Rpc3Rpbmd1aXNoaW5nX3NpZ25sZWxlbWVudFZhbHVlYkFUamlzc3VlckF1dGiEQ6EBJqEYIVkCDTCCAgkwggGwoAMCAQICFH6sogKyWaF-zOtf-O91AFYtv1KYMAoGCCqGSM49BAMCMCgxCzAJBgNVBAYTAkFUMRkwFwYDVQQDDBBXYWx0aWQgVGVzdCBJQUNBMB4XDTI1MDYwMjA2NDExM1oXDTI2MDkwMjA2NDExM1owMzELMAkGA1UEBhMCQVQxJDAiBgNVBAMMG1dhbHRpZCBUZXN0IERvY3VtZW50IFNpZ25lcjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABD86enlUgHVxEagKfKvDrgxIZdiCxgGqGkF0ydrA9oOV6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZqjgawwgakwHwYDVR0jBBgwFoAU8Qp9p1jKxO9Kl2-teFNeAcHmNmswHQYDVR0OBBYEFMeapDiwuJaZdcaWGRphfRy8badIMA4GA1UdDwEB_wQEAwIHgDAaBgNVHRIEEzARhg9odHRwczovL3dhbHQuaWQwFQYDVR0lAQH_BAswCQYHKIGMXQUBAjAkBgNVHR8EHTAbMBmgF6AVhhNodHRwczovL3dhbHQuaWQvY3JsMAoGCCqGSM49BAMCA0cAMEQCIB02qd3OsglDYQ1X2VgTzCo_XQlmW6zBNN5IfTSU28NRAiAL1b7hpZfTttXkdekpCRxwANaT3KL4XE7xfX8_XHOujFkC29gYWQLWpmd2ZXJzaW9uYzEuMG9kaWdlc3RBbGdvcml0aG1nU0hBLTI1Nmx2YWx1ZURpZ2VzdHOhcW9yZy5pc28uMTgwMTMuNS4xqwBYIEhnRHmvup8TlsQ9hkEF4kH5KRKVxfjOpdadkGxgAqTUAVgg2PkypU9XEsKInFxUYaLdY95CQuR9WEx_uTKKMSakfDICWCCMWA5TlOdlv9OCYOPsUL7jvPQkOM8g6BQfZv6zcnE8AANYII5xio1q4XtRNUKeD-r3mD4rFVKeXzyJe_QHoDs2HcaPBFgg-Not5xT8jU-MMwi__otHq-A1HwvZto7K6gcNMHFe5NwFWCBXW91nys2OEHiJYr8TKTw0P9idVx0N9Xf384fmGnIKWQZYIBqK5uyNOCXvmTzRbl_SQSRapF5DIRtFxza-RfhKR14lB1ggbgAfyHWnOWRB0SCUFAj9He6XUdoJFYMVm-3cHEX-PjkIWCA-j1UB92LJnlr6BSdC3ZQ2pkzYz1zykCk28Baas-tShglYILn7TvLzLvTAFkS85WVyKnD74IW9CjNgaQn0KGAhfQ_uClggjOrfc9Ue1XVquGt2AUhlJrd1sUwrP2No5UZQi_ti5KBtZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCDAMBte-u4SHGByE2n8z6UJpNT6hVuTiLP3TAFqHdGg3SJYIFh2NvOwQKUTUJFKzUFRA1GVCA-VtA_IItNRGaRxu3uiZ2RvY1R5cGV1b3JnLmlzby4xODAxMy41LjEubURMbHZhbGlkaXR5SW5mb6Nmc2lnbmVkwHgeMjAyNS0wNy0xNlQwNzoyODoxMC4zOTY1OTcyNDdaaXZhbGlkRnJvbcB4HjIwMjUtMDctMTZUMDc6Mjg6MTAuMzk2NTk3MzU3Wmp2YWxpZFVudGlswHgeMjAyNi0wNy0xNlQwNzoyODoxMC4zOTY1OTc0MDdaWEDiemZef8Z-tVwEMtNPde_6ySjS37D5VCHm3WqbsnwGi87Y4H1HLrLQdkvonitJqjmP8WT5Vqt-wdAml82wlSmPbGRldmljZVNpZ25lZKJqbmFtZVNwYWNlc9gYQaBqZGV2aWNlQXV0aKFvZGV2aWNlU2lnbmF0dXJlhEOhASahGCGA9lhAU3IJNfF-abBEzwzEH0U1zJinbx_wbxXs0oaI3mPQJ_hWdb4WUO4GN1aLpPQ7McqNIozYxY7bQTmPbArqzSAIEWZzdGF0dXMA",
                        "version": "1.0",
                        "status": 0,
                        "documents": [
                            {
                                "docType": "org.iso.18013.5.1.mDL",
                                "issuerSigned": {
                                    "nameSpaces": {
                                        "org.iso.18013.5.1": {
                                            "family_name": "Doe",
                                            "given_name": "John",
                                            "birth_date": "1986-03-22",
                                            "issue_date": "2019-10-20",
                                            "expiry_date": "2024-10-20",
                                            "issuing_country": "AT",
                                            "issuing_authority": "AT DMV",
                                            "document_number": 123456789,
                                            "portrait": [
                                                141,
                                                182,
                                                121,
                                                111,
                                                238,
                                                50,
                                                120,
                                                94,
                                                54,
                                                111,
                                                113,
                                                13,
                                                241,
                                                12,
                                                12
                                            ],
                                            "driving_privileges": [
                                                {
                                                    "vehicle_category_code": "A",
                                                    "issue_date": "2018-08-09",
                                                    "expiry_date": "2024-10-20"
                                                },
                                                {
                                                    "vehicle_category_code": "B",
                                                    "issue_date": "2017-02-23",
                                                    "expiry_date": "2024-10-20"
                                                }
                                            ],
                                            "un_distinguishing_sign": "AT"
                                        }
                                    },
                                    "issuerAuth": {
                                        "x5c": [
                                            "-----BEGIN CERTIFICATE-----\nMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M\n-----END CERTIFICATE-----"
                                        ],
                                        "algorithm": -7,
                                        "protectedHeader": {
                                            "1": -7
                                        },
                                        "payload": {
                                            "docType": "org.iso.18013.5.1.mDL",
                                            "version": "1.0",
                                            "digestAlgorithm": "SHA-256",
                                            "valueDigests": {
                                                "org.iso.18013.5.1": {
                                                    "0": [
                                                        72,
                                                        103,
                                                        68,
                                                        121,
                                                        -81,
                                                        -70,
                                                        -97,
                                                        19,
                                                        -106,
                                                        -60,
                                                        61,
                                                        -122,
                                                        65,
                                                        5,
                                                        -30,
                                                        65,
                                                        -7,
                                                        41,
                                                        18,
                                                        -107,
                                                        -59,
                                                        -8,
                                                        -50,
                                                        -91,
                                                        -42,
                                                        -99,
                                                        -112,
                                                        108,
                                                        96,
                                                        2,
                                                        -92,
                                                        -44
                                                    ],
                                                    "1": [
                                                        -40,
                                                        -7,
                                                        50,
                                                        -91,
                                                        79,
                                                        87,
                                                        18,
                                                        -62,
                                                        -120,
                                                        -100,
                                                        92,
                                                        84,
                                                        97,
                                                        -94,
                                                        -35,
                                                        99,
                                                        -34,
                                                        66,
                                                        66,
                                                        -28,
                                                        125,
                                                        88,
                                                        76,
                                                        127,
                                                        -71,
                                                        50,
                                                        -118,
                                                        49,
                                                        38,
                                                        -92,
                                                        124,
                                                        50
                                                    ],
                                                    "2": [
                                                        -116,
                                                        88,
                                                        14,
                                                        83,
                                                        -108,
                                                        -25,
                                                        101,
                                                        -65,
                                                        -45,
                                                        -126,
                                                        96,
                                                        -29,
                                                        -20,
                                                        80,
                                                        -66,
                                                        -29,
                                                        -68,
                                                        -12,
                                                        36,
                                                        56,
                                                        -49,
                                                        32,
                                                        -24,
                                                        20,
                                                        31,
                                                        102,
                                                        -2,
                                                        -77,
                                                        114,
                                                        113,
                                                        60,
                                                        0
                                                    ],
                                                    "3": [
                                                        -114,
                                                        113,
                                                        -118,
                                                        -115,
                                                        106,
                                                        -31,
                                                        123,
                                                        81,
                                                        53,
                                                        66,
                                                        -98,
                                                        15,
                                                        -22,
                                                        -9,
                                                        -104,
                                                        62,
                                                        43,
                                                        21,
                                                        82,
                                                        -98,
                                                        95,
                                                        60,
                                                        -119,
                                                        123,
                                                        -12,
                                                        7,
                                                        -96,
                                                        59,
                                                        54,
                                                        29,
                                                        -58,
                                                        -113
                                                    ],
                                                    "4": [
                                                        -8,
                                                        -38,
                                                        45,
                                                        -25,
                                                        20,
                                                        -4,
                                                        -115,
                                                        79,
                                                        -116,
                                                        51,
                                                        8,
                                                        -65,
                                                        -2,
                                                        -117,
                                                        71,
                                                        -85,
                                                        -32,
                                                        53,
                                                        31,
                                                        11,
                                                        -39,
                                                        -74,
                                                        -114,
                                                        -54,
                                                        -22,
                                                        7,
                                                        13,
                                                        48,
                                                        113,
                                                        94,
                                                        -28,
                                                        -36
                                                    ],
                                                    "5": [
                                                        87,
                                                        91,
                                                        -35,
                                                        103,
                                                        -54,
                                                        -51,
                                                        -114,
                                                        16,
                                                        120,
                                                        -119,
                                                        98,
                                                        -65,
                                                        19,
                                                        41,
                                                        60,
                                                        52,
                                                        63,
                                                        -40,
                                                        -99,
                                                        87,
                                                        29,
                                                        13,
                                                        -11,
                                                        119,
                                                        -9,
                                                        -13,
                                                        -121,
                                                        -26,
                                                        26,
                                                        114,
                                                        10,
                                                        89
                                                    ],
                                                    "6": [
                                                        26,
                                                        -118,
                                                        -26,
                                                        -20,
                                                        -115,
                                                        56,
                                                        37,
                                                        -17,
                                                        -103,
                                                        60,
                                                        -47,
                                                        110,
                                                        95,
                                                        -46,
                                                        65,
                                                        36,
                                                        90,
                                                        -92,
                                                        94,
                                                        67,
                                                        33,
                                                        27,
                                                        69,
                                                        -57,
                                                        54,
                                                        -66,
                                                        69,
                                                        -8,
                                                        74,
                                                        71,
                                                        94,
                                                        37
                                                    ],
                                                    "7": [
                                                        110,
                                                        0,
                                                        31,
                                                        -56,
                                                        117,
                                                        -89,
                                                        57,
                                                        100,
                                                        65,
                                                        -47,
                                                        32,
                                                        -108,
                                                        20,
                                                        8,
                                                        -3,
                                                        29,
                                                        -18,
                                                        -105,
                                                        81,
                                                        -38,
                                                        9,
                                                        21,
                                                        -125,
                                                        21,
                                                        -101,
                                                        -19,
                                                        -36,
                                                        28,
                                                        69,
                                                        -2,
                                                        62,
                                                        57
                                                    ],
                                                    "8": [
                                                        62,
                                                        -113,
                                                        85,
                                                        1,
                                                        -9,
                                                        98,
                                                        -55,
                                                        -98,
                                                        90,
                                                        -6,
                                                        5,
                                                        39,
                                                        66,
                                                        -35,
                                                        -108,
                                                        54,
                                                        -90,
                                                        76,
                                                        -40,
                                                        -49,
                                                        92,
                                                        -14,
                                                        -112,
                                                        41,
                                                        54,
                                                        -16,
                                                        22,
                                                        -102,
                                                        -77,
                                                        -21,
                                                        82,
                                                        -122
                                                    ],
                                                    "9": [
                                                        -71,
                                                        -5,
                                                        78,
                                                        -14,
                                                        -13,
                                                        46,
                                                        -12,
                                                        -64,
                                                        22,
                                                        68,
                                                        -68,
                                                        -27,
                                                        101,
                                                        114,
                                                        42,
                                                        112,
                                                        -5,
                                                        -32,
                                                        -123,
                                                        -67,
                                                        10,
                                                        51,
                                                        96,
                                                        105,
                                                        9,
                                                        -12,
                                                        40,
                                                        96,
                                                        33,
                                                        125,
                                                        15,
                                                        -18
                                                    ],
                                                    "10": [
                                                        -116,
                                                        -22,
                                                        -33,
                                                        115,
                                                        -43,
                                                        30,
                                                        -43,
                                                        117,
                                                        106,
                                                        -72,
                                                        107,
                                                        118,
                                                        1,
                                                        72,
                                                        101,
                                                        38,
                                                        -73,
                                                        117,
                                                        -79,
                                                        76,
                                                        43,
                                                        63,
                                                        99,
                                                        104,
                                                        -27,
                                                        70,
                                                        80,
                                                        -117,
                                                        -5,
                                                        98,
                                                        -28,
                                                        -96
                                                    ]
                                                }
                                            },
                                            "validityInfo": {
                                                "signed": "2025-07-16T07:28:10.396597247Z",
                                                "validFrom": "2025-07-16T07:28:10.396597357Z",
                                                "validUntil": "2026-07-16T07:28:10.396597407Z"
                                            },
                                            "deviceKeyInfo": {
                                                "deviceKey": {
                                                    "kty": "EC",
                                                    "crv": "P-256",
                                                    "x": "wDAbXvruEhxgchNp_M-lCaTU-oVbk4iz90wBah3RoN0",
                                                    "y": "WHY287BApRNQkUrNQVEDUZUID5W0D8gi01EZpHG7e6I"
                                                }
                                            }
                                        }
                                    }
                                },
                                "deviceSigned": {
                                    "nameSpaces": {},
                                    "deviceAuth": {
                                        "deviceSignature": [
                                            [
                                                -95,
                                                1,
                                                38
                                            ],
                                            {
                                                "33": []
                                            },
                                            null,
                                            [
                                                83,
                                                114,
                                                9,
                                                53,
                                                -15,
                                                126,
                                                105,
                                                -80,
                                                68,
                                                -49,
                                                12,
                                                -60,
                                                31,
                                                69,
                                                53,
                                                -52,
                                                -104,
                                                -89,
                                                111,
                                                31,
                                                -16,
                                                111,
                                                21,
                                                -20,
                                                -46,
                                                -122,
                                                -120,
                                                -34,
                                                99,
                                                -48,
                                                39,
                                                -8,
                                                86,
                                                117,
                                                -66,
                                                22,
                                                80,
                                                -18,
                                                6,
                                                55,
                                                86,
                                                -117,
                                                -92,
                                                -12,
                                                59,
                                                49,
                                                -54,
                                                -115,
                                                34,
                                                -116,
                                                -40,
                                                -59,
                                                -114,
                                                -37,
                                                65,
                                                57,
                                                -113,
                                                108,
                                                10,
                                                -22,
                                                -51,
                                                32,
                                                8,
                                                17
                                            ]
                                        ]
                                    }
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "verbose"
        }
    """.trimIndent()
    )

    private val uniDegreeOpenBadgeWithDisclosuresSimple =
        Json.decodeFromString<PresentationSessionPresentedCredentials>(
            """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_simple",
                        "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                        "verifiableCredentials": [
                            {
                                "header": {
                                    "kid": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm#zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "payload": {
                                    "iss": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:4af8291a-7ac2-43ec-be21-b81237cbda7c",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm"
                                        },
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "name": "Bachelor of Science and Arts"
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                        "expirationDate": "2026-07-17T04:43:00.311322304Z",
                                        "issuanceDate": "2025-07-17T04:43:00.311298359Z"
                                    },
                                    "jti": "urn:uuid:4af8291a-7ac2-43ec-be21-b81237cbda7c",
                                    "exp": 1784263380,
                                    "iat": 1752727380,
                                    "nbf": 1752727380
                                }
                            },
                            {
                                "header": {
                                    "kid": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm#zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "payload": {
                                    "iss": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:95c49060-29e0-46fa-9aeb-16b46e2da15a",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "expirationDate": "2026-07-17T04:43:00.710540677Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        },
                                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                        "issuanceDate": "2025-07-17T04:43:00.710524386Z"
                                    },
                                    "jti": "urn:uuid:95c49060-29e0-46fa-9aeb-16b46e2da15a",
                                    "exp": 1784263380,
                                    "iat": 1752727380,
                                    "nbf": 1752727380
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "simple"
        }
    """.trimIndent()
        )
    private val uniDegreeOpenBadgeWithDisclosuresVerbose =
        Json.decodeFromString<PresentationSessionPresentedCredentials>(
            """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "type": "jwt_vc_json_view_verbose",
                        "vp": {
                            "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWlhsME1qRjZSVkV6YW1wWVZGOXVUa3BOYWtZMWRXbFBPVTA0V1VSblpVZGhjblJIU1Zkd2RFOUNSU0lzSW5naU9pSkZTVjlxZG1weE9YaEpSSFY0Um1Ga1IyaHNXWEJSWW1WVU1ERjFaV3RVYldzeVVYVlRlRkptUTFwaklpd2llU0k2SW5Jek5FbFFZMGhXUXpGSU9URm1VR2hJVm1sdWMzWkpOMmhwUkhkbGRFNUNPRkJHVmxGeU5EbEpTVUVpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWlhsME1qRjZSVkV6YW1wWVZGOXVUa3BOYWtZMWRXbFBPVTA0V1VSblpVZGhjblJIU1Zkd2RFOUNSU0lzSW5naU9pSkZTVjlxZG1weE9YaEpSSFY0Um1Ga1IyaHNXWEJSWW1WVU1ERjFaV3RVYldzeVVYVlRlRkptUTFwaklpd2llU0k2SW5Jek5FbFFZMGhXUXpGSU9URm1VR2hJVm1sdWMzWkpOMmhwUkhkbGRFNUNPRkJHVmxGeU5EbEpTVUVpZlEiLCJuYmYiOjE3NTI3MjczMjEsImlhdCI6MTc1MjcyNzM4MSwianRpIjoiWDFpMVRoN0ZCVHRqIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpYbDBNakY2UlZFemFtcFlWRjl1VGtwTmFrWTFkV2xQT1UwNFdVUm5aVWRoY25SSFNWZHdkRTlDUlNJc0luZ2lPaUpGU1Y5cWRtcHhPWGhKUkhWNFJtRmtSMmhzV1hCUlltVlVNREYxWld0VWJXc3lVWFZUZUZKbVExcGpJaXdpZVNJNkluSXpORWxRWTBoV1F6RklPVEZtVUdoSVZtbHVjM1pKTjJocFJIZGxkRTVDT0ZCR1ZsRnlORGxKU1VFaWZRIiwibm9uY2UiOiIzZTM0MGE3Yy03ZTBmLTQ2OWItYTJlMS1mODg5ZGRiN2Y4ZWYiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJYMWkxVGg3RkJUdGoiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWlhsME1qRjZSVkV6YW1wWVZGOXVUa3BOYWtZMWRXbFBPVTA0V1VSblpVZGhjblJIU1Zkd2RFOUNSU0lzSW5naU9pSkZTVjlxZG1weE9YaEpSSFY0Um1Ga1IyaHNXWEJSWW1WVU1ERjFaV3RVYldzeVVYVlRlRkptUTFwaklpd2llU0k2SW5Jek5FbFFZMGhXUXpGSU9URm1VR2hJVm1sdWMzWkpOMmhwUkhkbGRFNUNPRkJHVmxGeU5EbEpTVUVpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsZEVFeVozWnlhelJqWTIxRU1sUTNRVFY2ZVRSdFYyOU5Na05CZW01M1kzaElNblV4YURWdFJHNVlVbTBqZWtSdVlXVjBRVEpuZG5Kck5HTmpiVVF5VkRkQk5YcDVORzFYYjAweVEwRjZibmRqZUVneWRURm9OVzFFYmxoU2JTSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsZEVFeVozWnlhelJqWTIxRU1sUTNRVFY2ZVRSdFYyOU5Na05CZW01M1kzaElNblV4YURWdFJHNVlVbTBpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBXbGhzTUUxcVJqWlNWa1Y2WVcxd1dWWkdPWFZVYTNCT1lXdFpNV1JYYkZCUFZUQTBWMVZTYmxwVlpHaGpibEpJVTFaa2QyUkZPVU5TVTBselNXNW5hVTlwU2taVFZqbHhaRzF3ZUU5WWFFcFNTRlkwVW0xR2ExSXlhSE5YV0VKU1dXMVdWVTFFUmpGYVYzUlZZbGR6ZVZWWVZsUmxSa3B0VVRGd2FrbHBkMmxsVTBrMlNXNUplazVGYkZGWk1HaFhVWHBHU1U5VVJtMVZSMmhKVm0xc2RXTXpXa3BPTW1od1VraGtiR1JGTlVOUFJrSkhWbXhHZVU1RWJFcFRWVVZwWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2ZDNkM0xuY3pMbTl5Wnk4eU1ERTRMMk55WldSbGJuUnBZV3h6TDJWNFlXMXdiR1Z6TDNZeElsMHNJbWxrSWpvaWRYSnVPblYxYVdRNk5HRm1PREk1TVdFdE4yRmpNaTAwTTJWakxXSmxNakV0WWpneE1qTTNZMkprWVRkaklpd2lkSGx3WlNJNld5SldaWEpwWm1saFlteGxRM0psWkdWdWRHbGhiQ0lzSWxWdWFYWmxjbk5wZEhsRVpXZHlaV1VpWFN3aWFYTnpkV1Z5SWpwN0ltbGtJam9pWkdsa09tdGxlVHA2Ukc1aFpYUkJNbWQyY21zMFkyTnRSREpVTjBFMWVuazBiVmR2VFRKRFFYcHVkMk40U0RKMU1XZzFiVVJ1V0ZKdEluMHNJbU55WldSbGJuUnBZV3hUZFdKcVpXTjBJanA3SW1sa0lqb2laR2xrT21wM2F6cGxlVXB5WkVocmFVOXBTa1pSZVVselNXMU9lV1JwU1RaSmJFRjBUV3BWTWtscGQybGhNbXhyU1dwdmFWcFliREJOYWtZMlVsWkZlbUZ0Y0ZsV1JqbDFWR3R3VG1GcldURmtWMnhRVDFVd05GZFZVbTVhVldSb1kyNVNTRk5XWkhka1JUbERVbE5KYzBsdVoybFBhVXBHVTFZNWNXUnRjSGhQV0doS1VraFdORkp0Um10U01taHpWMWhDVWxsdFZsVk5SRVl4V2xkMFZXSlhjM2xWV0ZaVVpVWktiVkV4Y0dwSmFYZHBaVk5KTmtsdVNYcE9SV3hSV1RCb1YxRjZSa2xQVkVadFZVZG9TVlp0YkhWak0xcEtUakpvY0ZKSVpHeGtSVFZEVDBaQ1IxWnNSbmxPUkd4S1UxVkZhV1pSSWl3aVpHVm5jbVZsSWpwN0luUjVjR1VpT2lKQ1lXTm9aV3h2Y2tSbFozSmxaU0lzSWw5elpDSTZXeUpsYlhCYWVHZEhRbGxxVFdWUlpGUkxabXBZYUhwUFNGUmFVR0k0U0VoUFpGUXdZbE15VURoUVNtYzRJbDE5ZlN3aWFYTnpkV1Z5Ukdsa0lqb2laR2xrT210bGVUcDZSRzVoWlhSQk1tZDJjbXMwWTJOdFJESlVOMEUxZW5rMGJWZHZUVEpEUVhwdWQyTjRTREoxTVdnMWJVUnVXRkp0SWl3aVpYaHdhWEpoZEdsdmJrUmhkR1VpT2lJeU1ESTJMVEEzTFRFM1ZEQTBPalF6T2pBd0xqTXhNVE15TWpNd05Gb2lMQ0pmYzJRaU9sc2lkMHB5TldGblpVSkhRblJNZFdoYVdGZzJUME5QTUZGbWNYVkVjR0ZTY1RZemVuRTBlblJQWVRReVZTSmRmU3dpYW5ScElqb2lkWEp1T25WMWFXUTZOR0ZtT0RJNU1XRXROMkZqTWkwME0yVmpMV0psTWpFdFlqZ3hNak0zWTJKa1lUZGpJaXdpWlhod0lqb3hOemcwTWpZek16Z3dMQ0pwWVhRaU9qRTNOVEkzTWpjek9EQXNJbTVpWmlJNk1UYzFNamN5TnpNNE1IMC53QS1GLVN1SzA4WlZWMUo1Wi1ZWFNyNlFHb1pvczFMak43M3VrUkF4S0FKTmdvcFd3NXg5Ylc1cS0tZXN2R0VCWXdFVFBRUm5Fenh6TTB4aHp1VUdCZ35XeUpuVmxreGNHcGFZbmN3VUVsSlZEUnJMVU5YZHpOM0lpd2lhWE56ZFdGdVkyVkVZWFJsSWl3aU1qQXlOUzB3TnkweE4xUXdORG8wTXpvd01DNHpNVEV5T1Rnek5UbGFJbDB-V3lKR09IZFVURk5WYkhKelgyTXpOVzk2WjNaSmRGbFJJaXdpYm1GdFpTSXNJa0poWTJobGJHOXlJRzltSUZOamFXVnVZMlVnWVc1a0lFRnlkSE1pWFEiLCJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsZEVFeVozWnlhelJqWTIxRU1sUTNRVFY2ZVRSdFYyOU5Na05CZW01M1kzaElNblV4YURWdFJHNVlVbTBqZWtSdVlXVjBRVEpuZG5Kck5HTmpiVVF5VkRkQk5YcDVORzFYYjAweVEwRjZibmRqZUVneWRURm9OVzFFYmxoU2JTSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsZEVFeVozWnlhelJqWTIxRU1sUTNRVFY2ZVRSdFYyOU5Na05CZW01M1kzaElNblV4YURWdFJHNVlVbTBpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBXbGhzTUUxcVJqWlNWa1Y2WVcxd1dWWkdPWFZVYTNCT1lXdFpNV1JYYkZCUFZUQTBWMVZTYmxwVlpHaGpibEpJVTFaa2QyUkZPVU5TVTBselNXNW5hVTlwU2taVFZqbHhaRzF3ZUU5WWFFcFNTRlkwVW0xR2ExSXlhSE5YV0VKU1dXMVdWVTFFUmpGYVYzUlZZbGR6ZVZWWVZsUmxSa3B0VVRGd2FrbHBkMmxsVTBrMlNXNUplazVGYkZGWk1HaFhVWHBHU1U5VVJtMVZSMmhKVm0xc2RXTXpXa3BPTW1od1VraGtiR1JGTlVOUFJrSkhWbXhHZVU1RWJFcFRWVVZwWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2Y0hWeWJDNXBiWE5uYkc5aVlXd3ViM0puTDNOd1pXTXZiMkl2ZGpOd01DOWpiMjUwWlhoMExtcHpiMjRpWFN3aWFXUWlPaUoxY200NmRYVnBaRG81TldNME9UQTJNQzB5T1dVd0xUUTJabUV0T1dGbFlpMHhObUkwTm1VeVpHRXhOV0VpTENKMGVYQmxJanBiSWxabGNtbG1hV0ZpYkdWRGNtVmtaVzUwYVdGc0lpd2lUM0JsYmtKaFpHZGxRM0psWkdWdWRHbGhiQ0pkTENKcGMzTjFaWElpT25zaWRIbHdaU0k2V3lKUWNtOW1hV3hsSWwwc0ltbGtJam9pWkdsa09tdGxlVHA2Ukc1aFpYUkJNbWQyY21zMFkyTnRSREpVTjBFMWVuazBiVmR2VFRKRFFYcHVkMk40U0RKMU1XZzFiVVJ1V0ZKdElpd2libUZ0WlNJNklrcHZZbk1nWm05eUlIUm9aU0JHZFhSMWNtVWdLRXBHUmlraUxDSjFjbXdpT2lKb2RIUndjem92TDNkM2R5NXFabVl1YjNKbkx5SXNJbWx0WVdkbElqb2lhSFIwY0hNNkx5OTNNMk10WTJObkxtZHBkR2gxWWk1cGJ5OTJZeTFsWkM5d2JIVm5abVZ6ZEMweExUSXdNakl2YVcxaFoyVnpMMHBHUmw5TWIyZHZURzlqYTNWd0xuQnVaeUo5TENKbGVIQnBjbUYwYVc5dVJHRjBaU0k2SWpJd01qWXRNRGN0TVRkVU1EUTZORE02TURBdU56RXdOVFF3TmpjM1dpSXNJbU55WldSbGJuUnBZV3hUZFdKcVpXTjBJanA3SW1sa0lqb2laR2xrT21wM2F6cGxlVXB5WkVocmFVOXBTa1pSZVVselNXMU9lV1JwU1RaSmJFRjBUV3BWTWtscGQybGhNbXhyU1dwdmFWcFliREJOYWtZMlVsWkZlbUZ0Y0ZsV1JqbDFWR3R3VG1GcldURmtWMnhRVDFVd05GZFZVbTVhVldSb1kyNVNTRk5XWkhka1JUbERVbE5KYzBsdVoybFBhVXBHVTFZNWNXUnRjSGhQV0doS1VraFdORkp0Um10U01taHpWMWhDVWxsdFZsVk5SRVl4V2xkMFZXSlhjM2xWV0ZaVVpVWktiVkV4Y0dwSmFYZHBaVk5KTmtsdVNYcE9SV3hSV1RCb1YxRjZSa2xQVkVadFZVZG9TVlp0YkhWak0xcEtUakpvY0ZKSVpHeGtSVFZEVDBaQ1IxWnNSbmxPUkd4S1UxVkZhV1pSSWl3aWRIbHdaU0k2V3lKQlkyaHBaWFpsYldWdWRGTjFZbXBsWTNRaVhTd2lZV05vYVdWMlpXMWxiblFpT25zaWFXUWlPaUoxY200NmRYVnBaRHBoWXpJMU5HSmtOUzA0Wm1Ga0xUUmlZakV0T1dReU9TMWxabVE1TXpnMU16WTVNallpTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MElsMHNJbTVoYldVaU9pSktSa1lnZUNCMll5MWxaSFVnVUd4MVowWmxjM1FnTXlCSmJuUmxjbTl3WlhKaFltbHNhWFI1SWl3aVpHVnpZM0pwY0hScGIyNGlPaUpVYUdseklIZGhiR3hsZENCemRYQndiM0owY3lCMGFHVWdkWE5sSUc5bUlGY3pReUJXWlhKcFptbGhZbXhsSUVOeVpXUmxiblJwWVd4eklHRnVaQ0JvWVhNZ1pHVnRiMjV6ZEhKaGRHVmtJR2x1ZEdWeWIzQmxjbUZpYVd4cGRIa2daSFZ5YVc1bklIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z2NtVnhkV1Z6ZENCM2IzSnJabXh2ZHlCa2RYSnBibWNnU2taR0lIZ2dWa010UlVSVklGQnNkV2RHWlhOMElETXVJaXdpWTNKcGRHVnlhV0VpT25zaWRIbHdaU0k2SWtOeWFYUmxjbWxoSWl3aWJtRnljbUYwYVhabElqb2lWMkZzYkdWMElITnZiSFYwYVc5dWN5QndjbTkyYVdSbGNuTWdaV0Z5Ym1Wa0lIUm9hWE1nWW1Ga1oyVWdZbmtnWkdWdGIyNXpkSEpoZEdsdVp5QnBiblJsY205d1pYSmhZbWxzYVhSNUlHUjFjbWx1WnlCMGFHVWdjSEpsYzJWdWRHRjBhVzl1SUhKbGNYVmxjM1FnZDI5eWEyWnNiM2N1SUZSb2FYTWdhVzVqYkhWa1pYTWdjM1ZqWTJWemMyWjFiR3g1SUhKbFkyVnBkbWx1WnlCaElIQnlaWE5sYm5SaGRHbHZiaUJ5WlhGMVpYTjBMQ0JoYkd4dmQybHVaeUIwYUdVZ2FHOXNaR1Z5SUhSdklITmxiR1ZqZENCaGRDQnNaV0Z6ZENCMGQyOGdkSGx3WlhNZ2IyWWdkbVZ5YVdacFlXSnNaU0JqY21Wa1pXNTBhV0ZzY3lCMGJ5QmpjbVZoZEdVZ1lTQjJaWEpwWm1saFlteGxJSEJ5WlhObGJuUmhkR2x2Yml3Z2NtVjBkWEp1YVc1bklIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z2RHOGdkR2hsSUhKbGNYVmxjM1J2Y2l3Z1lXNWtJSEJoYzNOcGJtY2dkbVZ5YVdacFkyRjBhVzl1SUc5bUlIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z1lXNWtJSFJvWlNCcGJtTnNkV1JsWkNCamNtVmtaVzUwYVdGc2N5NGlmU3dpYVcxaFoyVWlPbnNpYVdRaU9pSm9kSFJ3Y3pvdkwzY3pZeTFqWTJjdVoybDBhSFZpTG1sdkwzWmpMV1ZrTDNCc2RXZG1aWE4wTFRNdE1qQXlNeTlwYldGblpYTXZTa1pHTFZaRExVVkVWUzFRVEZWSFJrVlRWRE10WW1Ga1oyVXRhVzFoWjJVdWNHNW5JaXdpZEhsd1pTSTZJa2x0WVdkbEluMTlmU3dpWDNOa0lqcGJJalphZGpkUGVuVndlbXA2ZVhwd2MweDBOSFZoTVdWd1NXOW5SbUZEV1VaV2EyeGtaRlpaV21oWU5UUWlMQ0poU0ZCTlJ6SnpiekpNV1ZsT2JrMXFVbEEyVFZKNk1HRnVNMUYwZHpFeFMybGpTbXQzV2poemN6WmpJbDE5TENKcWRHa2lPaUoxY200NmRYVnBaRG81TldNME9UQTJNQzB5T1dVd0xUUTJabUV0T1dGbFlpMHhObUkwTm1VeVpHRXhOV0VpTENKbGVIQWlPakUzT0RReU5qTXpPREFzSW1saGRDSTZNVGMxTWpjeU56TTRNQ3dpYm1KbUlqb3hOelV5TnpJM016Z3dmUS45cUI2c1M5TDV4UGdFaVgxVWVkdXZxUl9LMUc4bmJkb0VEWTZtcTVNTlJ5TjBRUXp1M1BDTkFzdEVpM1JwNmgteWJnZ0RBQlVpTTRoOGNMcHp2Smtnd35XeUpXUkVsUWJEVTJUMmcwWVUweFp6VlNja3huYTJaQklpd2libUZ0WlNJc0lrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpWFF-V3lKRVZtOXVYMVJ5TFRBelQwNXdZVFp0VUVob1puRm5JaXdpYVhOemRXRnVZMlZFWVhSbElpd2lNakF5TlMwd055MHhOMVF3TkRvME16b3dNQzQzTVRBMU1qUXpPRFphSWwwIl19fQ.uXETPEPSZlp2d61OUlBuuk0YK7Umyp0lD56HCaAoclot3pwOWvOkIUa6H5_x-PSvO_upfK9lNi7TU873RKvzCg",
                            "header": {
                                "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ#0",
                                "typ": "JWT",
                                "alg": "ES256"
                            },
                            "payload": {
                                "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                "nbf": 1752727321,
                                "iat": 1752727381,
                                "jti": "X1i1Th7FBTtj",
                                "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                "nonce": "3e340a7c-7e0f-469b-a2e1-f889ddb7f8ef",
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "vp": {
                                    "@context": [
                                        "https://www.w3.org/2018/credentials/v1"
                                    ],
                                    "type": [
                                        "VerifiablePresentation"
                                    ],
                                    "id": "X1i1Th7FBTtj",
                                    "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                    "verifiableCredential": [
                                        "eyJraWQiOiJkaWQ6a2V5OnpEbmFldEEyZ3ZyazRjY21EMlQ3QTV6eTRtV29NMkNBem53Y3hIMnUxaDVtRG5YUm0jekRuYWV0QTJndnJrNGNjbUQyVDdBNXp5NG1Xb00yQ0F6bndjeEgydTFoNW1EblhSbSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFldEEyZ3ZyazRjY21EMlQ3QTV6eTRtV29NMkNBem53Y3hIMnUxaDVtRG5YUm0iLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWlhsME1qRjZSVkV6YW1wWVZGOXVUa3BOYWtZMWRXbFBPVTA0V1VSblpVZGhjblJIU1Zkd2RFOUNSU0lzSW5naU9pSkZTVjlxZG1weE9YaEpSSFY0Um1Ga1IyaHNXWEJSWW1WVU1ERjFaV3RVYldzeVVYVlRlRkptUTFwaklpd2llU0k2SW5Jek5FbFFZMGhXUXpGSU9URm1VR2hJVm1sdWMzWkpOMmhwUkhkbGRFNUNPRkJHVmxGeU5EbEpTVUVpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoidXJuOnV1aWQ6NGFmODI5MWEtN2FjMi00M2VjLWJlMjEtYjgxMjM3Y2JkYTdjIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWUiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6RG5hZXRBMmd2cms0Y2NtRDJUN0E1enk0bVdvTTJDQXpud2N4SDJ1MWg1bURuWFJtIn0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpYbDBNakY2UlZFemFtcFlWRjl1VGtwTmFrWTFkV2xQT1UwNFdVUm5aVWRoY25SSFNWZHdkRTlDUlNJc0luZ2lPaUpGU1Y5cWRtcHhPWGhKUkhWNFJtRmtSMmhzV1hCUlltVlVNREYxWld0VWJXc3lVWFZUZUZKbVExcGpJaXdpZVNJNkluSXpORWxRWTBoV1F6RklPVEZtVUdoSVZtbHVjM1pKTjJocFJIZGxkRTVDT0ZCR1ZsRnlORGxKU1VFaWZRIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIl9zZCI6WyJlbXBaeGdHQllqTWVRZFRLZmpYaHpPSFRaUGI4SEhPZFQwYlMyUDhQSmc4Il19fSwiaXNzdWVyRGlkIjoiZGlkOmtleTp6RG5hZXRBMmd2cms0Y2NtRDJUN0E1enk0bVdvTTJDQXpud2N4SDJ1MWg1bURuWFJtIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA3LTE3VDA0OjQzOjAwLjMxMTMyMjMwNFoiLCJfc2QiOlsid0pyNWFnZUJHQnRMdWhaWFg2T0NPMFFmcXVEcGFScTYzenE0enRPYTQyVSJdfSwianRpIjoidXJuOnV1aWQ6NGFmODI5MWEtN2FjMi00M2VjLWJlMjEtYjgxMjM3Y2JkYTdjIiwiZXhwIjoxNzg0MjYzMzgwLCJpYXQiOjE3NTI3MjczODAsIm5iZiI6MTc1MjcyNzM4MH0.wA-F-SuK08ZVV1J5Z-YXSr6QGoZos1LjN73ukRAxKAJNgopWw5x9bW5q--esvGEBYwETPQRnEzxzM0xhzuUGBg~WyJnVlkxcGpaYncwUElJVDRrLUNXdzN3IiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xN1QwNDo0MzowMC4zMTEyOTgzNTlaIl0~WyJGOHdUTFNVbHJzX2MzNW96Z3ZJdFlRIiwibmFtZSIsIkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMiXQ",
                                        "eyJraWQiOiJkaWQ6a2V5OnpEbmFldEEyZ3ZyazRjY21EMlQ3QTV6eTRtV29NMkNBem53Y3hIMnUxaDVtRG5YUm0jekRuYWV0QTJndnJrNGNjbUQyVDdBNXp5NG1Xb00yQ0F6bndjeEgydTFoNW1EblhSbSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFldEEyZ3ZyazRjY21EMlQ3QTV6eTRtV29NMkNBem53Y3hIMnUxaDVtRG5YUm0iLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWlhsME1qRjZSVkV6YW1wWVZGOXVUa3BOYWtZMWRXbFBPVTA0V1VSblpVZGhjblJIU1Zkd2RFOUNSU0lzSW5naU9pSkZTVjlxZG1weE9YaEpSSFY0Um1Ga1IyaHNXWEJSWW1WVU1ERjFaV3RVYldzeVVYVlRlRkptUTFwaklpd2llU0k2SW5Jek5FbFFZMGhXUXpGSU9URm1VR2hJVm1sdWMzWkpOMmhwUkhkbGRFNUNPRkJHVmxGeU5EbEpTVUVpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDo5NWM0OTA2MC0yOWUwLTQ2ZmEtOWFlYi0xNmI0NmUyZGExNWEiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sImlkIjoiZGlkOmtleTp6RG5hZXRBMmd2cms0Y2NtRDJUN0E1enk0bVdvTTJDQXpud2N4SDJ1MWg1bURuWFJtIiwibmFtZSI6IkpvYnMgZm9yIHRoZSBGdXR1cmUgKEpGRikiLCJ1cmwiOiJodHRwczovL3d3dy5qZmYub3JnLyIsImltYWdlIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0xLTIwMjIvaW1hZ2VzL0pGRl9Mb2dvTG9ja3VwLnBuZyJ9LCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDctMTdUMDQ6NDM6MDAuNzEwNTQwNjc3WiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpYbDBNakY2UlZFemFtcFlWRjl1VGtwTmFrWTFkV2xQT1UwNFdVUm5aVWRoY25SSFNWZHdkRTlDUlNJc0luZ2lPaUpGU1Y5cWRtcHhPWGhKUkhWNFJtRmtSMmhzV1hCUlltVlVNREYxWld0VWJXc3lVWFZUZUZKbVExcGpJaXdpZVNJNkluSXpORWxRWTBoV1F6RklPVEZtVUdoSVZtbHVjM1pKTjJocFJIZGxkRTVDT0ZCR1ZsRnlORGxKU1VFaWZRIiwidHlwZSI6WyJBY2hpZXZlbWVudFN1YmplY3QiXSwiYWNoaWV2ZW1lbnQiOnsiaWQiOiJ1cm46dXVpZDphYzI1NGJkNS04ZmFkLTRiYjEtOWQyOS1lZmQ5Mzg1MzY5MjYiLCJ0eXBlIjpbIkFjaGlldmVtZW50Il0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiZGVzY3JpcHRpb24iOiJUaGlzIHdhbGxldCBzdXBwb3J0cyB0aGUgdXNlIG9mIFczQyBWZXJpZmlhYmxlIENyZWRlbnRpYWxzIGFuZCBoYXMgZGVtb25zdHJhdGVkIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdyBkdXJpbmcgSkZGIHggVkMtRURVIFBsdWdGZXN0IDMuIiwiY3JpdGVyaWEiOnsidHlwZSI6IkNyaXRlcmlhIiwibmFycmF0aXZlIjoiV2FsbGV0IHNvbHV0aW9ucyBwcm92aWRlcnMgZWFybmVkIHRoaXMgYmFkZ2UgYnkgZGVtb25zdHJhdGluZyBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cuIFRoaXMgaW5jbHVkZXMgc3VjY2Vzc2Z1bGx5IHJlY2VpdmluZyBhIHByZXNlbnRhdGlvbiByZXF1ZXN0LCBhbGxvd2luZyB0aGUgaG9sZGVyIHRvIHNlbGVjdCBhdCBsZWFzdCB0d28gdHlwZXMgb2YgdmVyaWZpYWJsZSBjcmVkZW50aWFscyB0byBjcmVhdGUgYSB2ZXJpZmlhYmxlIHByZXNlbnRhdGlvbiwgcmV0dXJuaW5nIHRoZSBwcmVzZW50YXRpb24gdG8gdGhlIHJlcXVlc3RvciwgYW5kIHBhc3NpbmcgdmVyaWZpY2F0aW9uIG9mIHRoZSBwcmVzZW50YXRpb24gYW5kIHRoZSBpbmNsdWRlZCBjcmVkZW50aWFscy4ifSwiaW1hZ2UiOnsiaWQiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTMtMjAyMy9pbWFnZXMvSkZGLVZDLUVEVS1QTFVHRkVTVDMtYmFkZ2UtaW1hZ2UucG5nIiwidHlwZSI6IkltYWdlIn19fSwiX3NkIjpbIjZadjdPenVwemp6eXpwc0x0NHVhMWVwSW9nRmFDWUZWa2xkZFZZWmhYNTQiLCJhSFBNRzJzbzJMWVlObk1qUlA2TVJ6MGFuM1F0dzExS2ljSmt3WjhzczZjIl19LCJqdGkiOiJ1cm46dXVpZDo5NWM0OTA2MC0yOWUwLTQ2ZmEtOWFlYi0xNmI0NmUyZGExNWEiLCJleHAiOjE3ODQyNjMzODAsImlhdCI6MTc1MjcyNzM4MCwibmJmIjoxNzUyNzI3MzgwfQ.9qB6sS9L5xPgEiX1UeduvqR_K1G8nbdoEDY6mq5MNRyN0QQzu3PCNAstEi3Rp6h-ybggDABUiM4h8cLpzvJkgw~WyJWRElQbDU2T2g0YU0xZzVSckxna2ZBIiwibmFtZSIsIkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiXQ~WyJEVm9uX1RyLTAzT05wYTZtUEhoZnFnIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xN1QwNDo0MzowMC43MTA1MjQzODZaIl0"
                                    ]
                                }
                            }
                        },
                        "verifiableCredentials": [
                            {
                                "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFldEEyZ3ZyazRjY21EMlQ3QTV6eTRtV29NMkNBem53Y3hIMnUxaDVtRG5YUm0jekRuYWV0QTJndnJrNGNjbUQyVDdBNXp5NG1Xb00yQ0F6bndjeEgydTFoNW1EblhSbSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFldEEyZ3ZyazRjY21EMlQ3QTV6eTRtV29NMkNBem53Y3hIMnUxaDVtRG5YUm0iLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWlhsME1qRjZSVkV6YW1wWVZGOXVUa3BOYWtZMWRXbFBPVTA0V1VSblpVZGhjblJIU1Zkd2RFOUNSU0lzSW5naU9pSkZTVjlxZG1weE9YaEpSSFY0Um1Ga1IyaHNXWEJSWW1WVU1ERjFaV3RVYldzeVVYVlRlRkptUTFwaklpd2llU0k2SW5Jek5FbFFZMGhXUXpGSU9URm1VR2hJVm1sdWMzWkpOMmhwUkhkbGRFNUNPRkJHVmxGeU5EbEpTVUVpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoidXJuOnV1aWQ6NGFmODI5MWEtN2FjMi00M2VjLWJlMjEtYjgxMjM3Y2JkYTdjIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWUiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6RG5hZXRBMmd2cms0Y2NtRDJUN0E1enk0bVdvTTJDQXpud2N4SDJ1MWg1bURuWFJtIn0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpYbDBNakY2UlZFemFtcFlWRjl1VGtwTmFrWTFkV2xQT1UwNFdVUm5aVWRoY25SSFNWZHdkRTlDUlNJc0luZ2lPaUpGU1Y5cWRtcHhPWGhKUkhWNFJtRmtSMmhzV1hCUlltVlVNREYxWld0VWJXc3lVWFZUZUZKbVExcGpJaXdpZVNJNkluSXpORWxRWTBoV1F6RklPVEZtVUdoSVZtbHVjM1pKTjJocFJIZGxkRTVDT0ZCR1ZsRnlORGxKU1VFaWZRIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIl9zZCI6WyJlbXBaeGdHQllqTWVRZFRLZmpYaHpPSFRaUGI4SEhPZFQwYlMyUDhQSmc4Il19fSwiaXNzdWVyRGlkIjoiZGlkOmtleTp6RG5hZXRBMmd2cms0Y2NtRDJUN0E1enk0bVdvTTJDQXpud2N4SDJ1MWg1bURuWFJtIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA3LTE3VDA0OjQzOjAwLjMxMTMyMjMwNFoiLCJfc2QiOlsid0pyNWFnZUJHQnRMdWhaWFg2T0NPMFFmcXVEcGFScTYzenE0enRPYTQyVSJdfSwianRpIjoidXJuOnV1aWQ6NGFmODI5MWEtN2FjMi00M2VjLWJlMjEtYjgxMjM3Y2JkYTdjIiwiZXhwIjoxNzg0MjYzMzgwLCJpYXQiOjE3NTI3MjczODAsIm5iZiI6MTc1MjcyNzM4MH0.wA-F-SuK08ZVV1J5Z-YXSr6QGoZos1LjN73ukRAxKAJNgopWw5x9bW5q--esvGEBYwETPQRnEzxzM0xhzuUGBg~WyJnVlkxcGpaYncwUElJVDRrLUNXdzN3IiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xN1QwNDo0MzowMC4zMTEyOTgzNTlaIl0~WyJGOHdUTFNVbHJzX2MzNW96Z3ZJdFlRIiwibmFtZSIsIkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMiXQ",
                                "header": {
                                    "kid": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm#zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "fullPayload": {
                                    "iss": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:4af8291a-7ac2-43ec-be21-b81237cbda7c",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm"
                                        },
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "name": "Bachelor of Science and Arts"
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                        "expirationDate": "2026-07-17T04:43:00.311322304Z",
                                        "issuanceDate": "2025-07-17T04:43:00.311298359Z"
                                    },
                                    "jti": "urn:uuid:4af8291a-7ac2-43ec-be21-b81237cbda7c",
                                    "exp": 1784263380,
                                    "iat": 1752727380,
                                    "nbf": 1752727380
                                },
                                "undisclosedPayload": {
                                    "iss": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://www.w3.org/2018/credentials/examples/v1"
                                        ],
                                        "id": "urn:uuid:4af8291a-7ac2-43ec-be21-b81237cbda7c",
                                        "type": [
                                            "VerifiableCredential",
                                            "UniversityDegree"
                                        ],
                                        "issuer": {
                                            "id": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm"
                                        },
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                            "degree": {
                                                "type": "BachelorDegree",
                                                "_sd": [
                                                    "empZxgGBYjMeQdTKfjXhzOHTZPb8HHOdT0bS2P8PJg8"
                                                ]
                                            }
                                        },
                                        "issuerDid": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                        "expirationDate": "2026-07-17T04:43:00.311322304Z",
                                        "_sd": [
                                            "wJr5ageBGBtLuhZXX6OCO0QfquDpaRq63zq4ztOa42U"
                                        ]
                                    },
                                    "jti": "urn:uuid:4af8291a-7ac2-43ec-be21-b81237cbda7c",
                                    "exp": 1784263380,
                                    "iat": 1752727380,
                                    "nbf": 1752727380
                                },
                                "disclosures": {
                                    "wJr5ageBGBtLuhZXX6OCO0QfquDpaRq63zq4ztOa42U": {
                                        "disclosure": "WyJnVlkxcGpaYncwUElJVDRrLUNXdzN3IiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xN1QwNDo0MzowMC4zMTEyOTgzNTlaIl0",
                                        "salt": "gVY1pjZbw0PIIT4k-CWw3w",
                                        "key": "issuanceDate",
                                        "value": "2025-07-17T04:43:00.311298359Z"
                                    },
                                    "empZxgGBYjMeQdTKfjXhzOHTZPb8HHOdT0bS2P8PJg8": {
                                        "disclosure": "WyJGOHdUTFNVbHJzX2MzNW96Z3ZJdFlRIiwibmFtZSIsIkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMiXQ",
                                        "salt": "F8wTLSUlrs_c35ozgvItYQ",
                                        "key": "name",
                                        "value": "Bachelor of Science and Arts"
                                    }
                                }
                            },
                            {
                                "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFldEEyZ3ZyazRjY21EMlQ3QTV6eTRtV29NMkNBem53Y3hIMnUxaDVtRG5YUm0jekRuYWV0QTJndnJrNGNjbUQyVDdBNXp5NG1Xb00yQ0F6bndjeEgydTFoNW1EblhSbSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFldEEyZ3ZyazRjY21EMlQ3QTV6eTRtV29NMkNBem53Y3hIMnUxaDVtRG5YUm0iLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWlhsME1qRjZSVkV6YW1wWVZGOXVUa3BOYWtZMWRXbFBPVTA0V1VSblpVZGhjblJIU1Zkd2RFOUNSU0lzSW5naU9pSkZTVjlxZG1weE9YaEpSSFY0Um1Ga1IyaHNXWEJSWW1WVU1ERjFaV3RVYldzeVVYVlRlRkptUTFwaklpd2llU0k2SW5Jek5FbFFZMGhXUXpGSU9URm1VR2hJVm1sdWMzWkpOMmhwUkhkbGRFNUNPRkJHVmxGeU5EbEpTVUVpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDo5NWM0OTA2MC0yOWUwLTQ2ZmEtOWFlYi0xNmI0NmUyZGExNWEiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sImlkIjoiZGlkOmtleTp6RG5hZXRBMmd2cms0Y2NtRDJUN0E1enk0bVdvTTJDQXpud2N4SDJ1MWg1bURuWFJtIiwibmFtZSI6IkpvYnMgZm9yIHRoZSBGdXR1cmUgKEpGRikiLCJ1cmwiOiJodHRwczovL3d3dy5qZmYub3JnLyIsImltYWdlIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0xLTIwMjIvaW1hZ2VzL0pGRl9Mb2dvTG9ja3VwLnBuZyJ9LCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDctMTdUMDQ6NDM6MDAuNzEwNTQwNjc3WiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpYbDBNakY2UlZFemFtcFlWRjl1VGtwTmFrWTFkV2xQT1UwNFdVUm5aVWRoY25SSFNWZHdkRTlDUlNJc0luZ2lPaUpGU1Y5cWRtcHhPWGhKUkhWNFJtRmtSMmhzV1hCUlltVlVNREYxWld0VWJXc3lVWFZUZUZKbVExcGpJaXdpZVNJNkluSXpORWxRWTBoV1F6RklPVEZtVUdoSVZtbHVjM1pKTjJocFJIZGxkRTVDT0ZCR1ZsRnlORGxKU1VFaWZRIiwidHlwZSI6WyJBY2hpZXZlbWVudFN1YmplY3QiXSwiYWNoaWV2ZW1lbnQiOnsiaWQiOiJ1cm46dXVpZDphYzI1NGJkNS04ZmFkLTRiYjEtOWQyOS1lZmQ5Mzg1MzY5MjYiLCJ0eXBlIjpbIkFjaGlldmVtZW50Il0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiZGVzY3JpcHRpb24iOiJUaGlzIHdhbGxldCBzdXBwb3J0cyB0aGUgdXNlIG9mIFczQyBWZXJpZmlhYmxlIENyZWRlbnRpYWxzIGFuZCBoYXMgZGVtb25zdHJhdGVkIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdyBkdXJpbmcgSkZGIHggVkMtRURVIFBsdWdGZXN0IDMuIiwiY3JpdGVyaWEiOnsidHlwZSI6IkNyaXRlcmlhIiwibmFycmF0aXZlIjoiV2FsbGV0IHNvbHV0aW9ucyBwcm92aWRlcnMgZWFybmVkIHRoaXMgYmFkZ2UgYnkgZGVtb25zdHJhdGluZyBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cuIFRoaXMgaW5jbHVkZXMgc3VjY2Vzc2Z1bGx5IHJlY2VpdmluZyBhIHByZXNlbnRhdGlvbiByZXF1ZXN0LCBhbGxvd2luZyB0aGUgaG9sZGVyIHRvIHNlbGVjdCBhdCBsZWFzdCB0d28gdHlwZXMgb2YgdmVyaWZpYWJsZSBjcmVkZW50aWFscyB0byBjcmVhdGUgYSB2ZXJpZmlhYmxlIHByZXNlbnRhdGlvbiwgcmV0dXJuaW5nIHRoZSBwcmVzZW50YXRpb24gdG8gdGhlIHJlcXVlc3RvciwgYW5kIHBhc3NpbmcgdmVyaWZpY2F0aW9uIG9mIHRoZSBwcmVzZW50YXRpb24gYW5kIHRoZSBpbmNsdWRlZCBjcmVkZW50aWFscy4ifSwiaW1hZ2UiOnsiaWQiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTMtMjAyMy9pbWFnZXMvSkZGLVZDLUVEVS1QTFVHRkVTVDMtYmFkZ2UtaW1hZ2UucG5nIiwidHlwZSI6IkltYWdlIn19fSwiX3NkIjpbIjZadjdPenVwemp6eXpwc0x0NHVhMWVwSW9nRmFDWUZWa2xkZFZZWmhYNTQiLCJhSFBNRzJzbzJMWVlObk1qUlA2TVJ6MGFuM1F0dzExS2ljSmt3WjhzczZjIl19LCJqdGkiOiJ1cm46dXVpZDo5NWM0OTA2MC0yOWUwLTQ2ZmEtOWFlYi0xNmI0NmUyZGExNWEiLCJleHAiOjE3ODQyNjMzODAsImlhdCI6MTc1MjcyNzM4MCwibmJmIjoxNzUyNzI3MzgwfQ.9qB6sS9L5xPgEiX1UeduvqR_K1G8nbdoEDY6mq5MNRyN0QQzu3PCNAstEi3Rp6h-ybggDABUiM4h8cLpzvJkgw~WyJWRElQbDU2T2g0YU0xZzVSckxna2ZBIiwibmFtZSIsIkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiXQ~WyJEVm9uX1RyLTAzT05wYTZtUEhoZnFnIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xN1QwNDo0MzowMC43MTA1MjQzODZaIl0",
                                "header": {
                                    "kid": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm#zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "typ": "JWT",
                                    "alg": "ES256"
                                },
                                "fullPayload": {
                                    "iss": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:95c49060-29e0-46fa-9aeb-16b46e2da15a",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "expirationDate": "2026-07-17T04:43:00.710540677Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        },
                                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                        "issuanceDate": "2025-07-17T04:43:00.710524386Z"
                                    },
                                    "jti": "urn:uuid:95c49060-29e0-46fa-9aeb-16b46e2da15a",
                                    "exp": 1784263380,
                                    "iat": 1752727380,
                                    "nbf": 1752727380
                                },
                                "undisclosedPayload": {
                                    "iss": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                    "vc": {
                                        "@context": [
                                            "https://www.w3.org/2018/credentials/v1",
                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                        ],
                                        "id": "urn:uuid:95c49060-29e0-46fa-9aeb-16b46e2da15a",
                                        "type": [
                                            "VerifiableCredential",
                                            "OpenBadgeCredential"
                                        ],
                                        "issuer": {
                                            "type": [
                                                "Profile"
                                            ],
                                            "id": "did:key:zDnaetA2gvrk4ccmD2T7A5zy4mWoM2CAznwcxH2u1h5mDnXRm",
                                            "name": "Jobs for the Future (JFF)",
                                            "url": "https://www.jff.org/",
                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                        },
                                        "expirationDate": "2026-07-17T04:43:00.710540677Z",
                                        "credentialSubject": {
                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZXl0MjF6RVEzampYVF9uTkpNakY1dWlPOU04WURnZUdhcnRHSVdwdE9CRSIsIngiOiJFSV9qdmpxOXhJRHV4RmFkR2hsWXBRYmVUMDF1ZWtUbWsyUXVTeFJmQ1pjIiwieSI6InIzNElQY0hWQzFIOTFmUGhIVmluc3ZJN2hpRHdldE5COFBGVlFyNDlJSUEifQ",
                                            "type": [
                                                "AchievementSubject"
                                            ],
                                            "achievement": {
                                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                                "type": [
                                                    "Achievement"
                                                ],
                                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                                "criteria": {
                                                    "type": "Criteria",
                                                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                                },
                                                "image": {
                                                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                                    "type": "Image"
                                                }
                                            }
                                        },
                                        "_sd": [
                                            "6Zv7OzupzjzyzpsLt4ua1epIogFaCYFVklddVYZhX54",
                                            "aHPMG2so2LYYNnMjRP6MRz0an3Qtw11KicJkwZ8ss6c"
                                        ]
                                    },
                                    "jti": "urn:uuid:95c49060-29e0-46fa-9aeb-16b46e2da15a",
                                    "exp": 1784263380,
                                    "iat": 1752727380,
                                    "nbf": 1752727380
                                },
                                "disclosures": {
                                    "6Zv7OzupzjzyzpsLt4ua1epIogFaCYFVklddVYZhX54": {
                                        "disclosure": "WyJWRElQbDU2T2g0YU0xZzVSckxna2ZBIiwibmFtZSIsIkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiXQ",
                                        "salt": "VDIPl56Oh4aM1g5RrLgkfA",
                                        "key": "name",
                                        "value": "JFF x vc-edu PlugFest 3 Interoperability"
                                    },
                                    "aHPMG2so2LYYNnMjRP6MRz0an3Qtw11KicJkwZ8ss6c": {
                                        "disclosure": "WyJEVm9uX1RyLTAzT05wYTZtUEhoZnFnIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0xN1QwNDo0MzowMC43MTA1MjQzODZaIl0",
                                        "salt": "DVon_Tr-03ONpa6mPHhfqg",
                                        "key": "issuanceDate",
                                        "value": "2025-07-17T04:43:00.710524386Z"
                                    }
                                }
                            }
                        ]
                    }
                ]
            },
            "viewMode": "verbose"
        }
    """.trimIndent()
        )
}