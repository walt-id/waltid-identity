package id.walt.verifier.openapi

import id.walt.verifier.oidc.models.PresentationSessionPresentedCredentials
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
        }

        response {

            HttpStatusCode.OK to {
                body<PresentationSessionPresentedCredentials> {
                    description = "Map of credential formats to lists of decoded presented credentials."
                    required = true

                    example(
                        name = "OpenBadge W3C VC (no disclosures)"
                    ) {
                        value = openBadgeNoDisclosuresResponse
                    }

                    example(
                        name = "OpenBadge W3C VC with disclosures"
                    ) {
                        value = openBadgeWithDisclosuresResponse
                    }

                    example(
                        name = "University Degree W3C VC (no disclosures)"
                    ) {
                        value = uniDegreeNoDisclosuresResponse
                    }

                    example(
                        name = "University Degree W3C VC with disclosures"
                    ) {
                        value = uniDegreeWithDisclosuresResponse
                    }

                    example(
                        name = "Sd Jwt VC"
                    ) {
                        value = sdJwtVcResponse
                    }

                    example(
                        name = "mDL with all required fields"
                    ) {
                        value = mDLExampleResponse
                    }

                }
            }
        }
    }

    private val sdJwtVcResponse = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "vc+sd-jwt": [
                    {
                        "raw": "eyJ4NWMiOlsiLS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tXG5NSUlCZVRDQ0FSOENGSHJXZ3JHbDVLZGVmU3ZSUWhSK2FvcWRmNDgrTUFvR0NDcUdTTTQ5QkFNQ01CY3hGVEFUQmdOVkJBTU1ERTFFVDBNZ1VrOVBWQ0JEUVRBZ0Z3MHlOVEExTVRReE5EQTRNRGxhR0E4eU1EYzFNRFV3TWpFME1EZ3dPVm93WlRFTE1Ba0dBMVVFQmhNQ1FWUXhEekFOQmdOVkJBZ01CbFpwWlc1dVlURVBNQTBHQTFVRUJ3d0dWbWxsYm01aE1SQXdEZ1lEVlFRS0RBZDNZV3gwTG1sa01SQXdEZ1lEVlFRTERBZDNZV3gwTG1sa01SQXdEZ1lEVlFRRERBZDNZV3gwTG1sek1Ga3dFd1lIS29aSXpqMENBUVlJS29aSXpqMERBUWNEUWdBRUcwUklOQmlGK29RVUQzZDVER25lZ1F1WGVuSTI5SkRhTUdvTXZpb0tSQk41M2Q0VWF6YWtTMnVudThCbnNFdHh1dFMya3FSaFlCUFlrOVJBcmlVM2dUQUtCZ2dxaGtqT1BRUURBZ05JQURCRkFpQU9Nd003aEg3cTlEaSttVDZxQ2k0THZCK2tIOE94TWhlSXJaMmVSUHh0RFFJaEFMSHpUeHd2TjhVZHQwWjJDcG84SkJpaHFhY2ZlWGtJeFZBTzhYa3htWGhCXG4tLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tIl0sImtpZCI6Ijl2dWFKeVV4Ung0S21IeW9aOWtqSnhNc19tanBubmYtbVBNOW5QTUc1MUEiLCJ0eXAiOiJ2YytzZC1qd3QiLCJhbGciOiJFUzI1NiJ9.eyJnaXZlbl9uYW1lIjoiSm9obiIsImVtYWlsIjoiam9obmRvZUBleGFtcGxlLmNvbSIsInBob25lX251bWJlciI6IisxLTIwMi01NTUtMDEwMSIsImFkZHJlc3MiOnsic3RyZWV0X2FkZHJlc3MiOiIxMjMgTWFpbiBTdCIsImxvY2FsaXR5IjoiQW55dG93biIsInJlZ2lvbiI6IkFueXN0YXRlIiwiY291bnRyeSI6IlVTIn0sImlzX292ZXJfMTgiOnRydWUsImlzX292ZXJfMjEiOnRydWUsImlzX292ZXJfNjUiOnRydWUsImlkIjoidXJuOnV1aWQ6Mzc0OGNhNTAtNzg2MC00NzU1LWE1ZGEtYzNhODM3MjhlMzc5IiwiaWF0IjoxNzUyMDYyMDE1LCJuYmYiOjE3NTIwNjIwMTUsImV4cCI6MTc4MzU5ODAxNSwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDoyMjIyMi9kcmFmdDEzIiwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiUzZKUm05a3R0VlQ1TzkzVlhFWERVbzNvRW5rZy00Z1hHTGxOd3NieWt1MCIsIngiOiJrdldoSHlORDcwdFJ0T1N0aTdsRkVaUkQ5Vm1oaE50SmktVXpGSDU2VEVZIiwieSI6Im03M0VUNlNTV2lzTkJHRTYzQTZlMUtiSWV5UGNEWElNQ2xDR3FINDNrdHcifX0sInZjdCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvaWRlbnRpdHlfY3JlZGVudGlhbCIsImRpc3BsYXkiOltdLCJfc2QiOlsiRmkzN1NOQk9rblFzLU8zdzZTNi10V3EtczhycklJSGdSbEZMbTY1eWx1MCIsImVsWThXQXBrTlpRVktrcFoxc0N2TFUta3dEby12YTNkN1FheGxnODVXZXciXX0.kPA76rwKhWttZIvbp8DP1uxfNQRrTWFcyevTCbfansPtYaNlR-OeEw7eM_wVxWYz2XwSPkm6a_81ubmEkhsWvw~WyJOejJMWGlMR3M1Zkd0UzRmYVF0WUtnIiwiZmFtaWx5X25hbWUiLCJEb2UiXQ~WyI0bW13ZTJzQmZ5Z2wyN3BQdElYYVhBIiwiYmlydGhkYXRlIiwiMTk0MC0wMS0wMSJd~eyJraWQiOiJTNkpSbTlrdHRWVDVPOTNWWEVYRFVvM29FbmtnLTRnWEdMbE53c2J5a3UwIiwidHlwIjoia2Irand0IiwiYWxnIjoiRVMyNTYifQ.eyJpYXQiOjE3NTIwNjIwMTYsImF1ZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvb3BlbmlkNHZjL3ZlcmlmeSIsIm5vbmNlIjoiMWYwY2RkOTUtMGNhNC00MzNhLWI2NWItMDQzNGEwZjdlMmI4Iiwic2RfaGFzaCI6ImQ0Mmpac1U1V095NldXdk4zcDdwTWcwSU9mZUNGVWRMWnVvR3Jyc3F1aXcifQ.ihGpmtNZRZ7Aufa4XOeKFwA2cc-ch-ZYGAAi0mWI_MrwUi91LuoptNaP-zWuedXUb_-Hp1pEdl0tT-wz2ESOFA",
                        "decoded": {
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
                                "id": "urn:uuid:3748ca50-7860-4755-a5da-c3a83728e379",
                                "iat": 1752062015,
                                "nbf": 1752062015,
                                "exp": 1783598015,
                                "iss": "http://localhost:22222/draft13",
                                "cnf": {
                                    "jwk": {
                                        "kty": "EC",
                                        "crv": "P-256",
                                        "kid": "S6JRm9kttVT5O93VXEXDUo3oEnkg-4gXGLlNwsbyku0",
                                        "x": "kvWhHyND70tRtOSti7lFEZRD9VmhhNtJi-UzFH56TEY",
                                        "y": "m73ET6SSWisNBGE63A6e1KbIeyPcDXIMClCGqH43ktw"
                                    }
                                },
                                "vct": "http://localhost:22222/identity_credential",
                                "display": [],
                                "family_name": "Doe",
                                "birthdate": "1940-01-01"
                            },
                            "disclosures": {
                                "Fi37SNBOknQs-O3w6S6-tWq-s8rrIIHgRlFLm65ylu0": {
                                    "disclosure": "WyJOejJMWGlMR3M1Zkd0UzRmYVF0WUtnIiwiZmFtaWx5X25hbWUiLCJEb2UiXQ",
                                    "salt": "Nz2LXiLGs5fGtS4faQtYKg",
                                    "key": "family_name",
                                    "value": "Doe"
                                },
                                "elY8WApkNZQVKkpZ1sCvLU-kwDo-va3d7Qaxlg85Wew": {
                                    "disclosure": "WyI0bW13ZTJzQmZ5Z2wyN3BQdElYYVhBIiwiYmlydGhkYXRlIiwiMTk0MC0wMS0wMSJd",
                                    "salt": "4mmwe2sBfygl27pPtIXaXA",
                                    "key": "birthdate",
                                    "value": "1940-01-01"
                                }
                            }
                        }
                    }
                ]
            }
        }
    """.trimIndent()
    )

    private val uniDegreeWithDisclosuresResponse = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJuYmYiOjE3NTIwNjExMzYsImlhdCI6MTc1MjA2MTE5NiwianRpIjoic01Iak9IMFhyUzdIIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpsRjNjRmRWUW5kUFQySnRkazVMVFdGWWVYUnRSVkpvT1ZjNGEzZFBWRFpEUWpGbGJYTTNlRUZ5WnlJc0luZ2lPaUprY1ZadU5Ua3pWa0p2T1hORFFYRTVNbXBaYkdOUWVsRXphbkZtVFdScVMxbHRNbWRCZG1ndFJXUjNJaXdpZVNJNklqRlpUR0pNT1d4VWRWZzBUekJRWVZONmVVSk1ka3BsU2pCSmFFeE9jalZUUVVOemIxYzFSMWgzVldjaWZRIiwibm9uY2UiOiI4ZjNiZGM0Zi0xMDUxLTRmZDktOWEzOC05NjgzZWQ1NDI2MjQiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJzTUhqT0gwWHJTN0giLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtqZWtSdVlXVnRVbWRvUjFSNGRYVm1TMmhFZUhSVE9WQjNja3hOZW5jNVdVSkJUVXBoTWpZeVdIbExORkpZVUhKbU9TSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBXbXhHTTJOR1pGWlJibVJRVkRKS2RHUnJOVXhVVjBaWlpWaFNkRkpXU205UFZtTTBZVE5rVUZaRVdrUlJha1pzWWxoTk0yVkZSbmxhZVVselNXNW5hVTlwU210alZscDFUbFJyZWxaclNuWlBXRTVFVVZoRk5VMXRjRnBpUjA1UlpXeEZlbUZ1Um0xVVYxSnhVekZzZEUxdFpFSmtiV2QwVWxkU00wbHBkMmxsVTBrMlNXcEdXbFJIU2sxUFYzaFZaRlpuTUZSNlFsRlpWazQyWlZWS1RXUnJjR3hUYWtKS1lVVjRUMk5xVmxSUlZVNTZZakZqTVZJeGFETldWMk5wWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2ZDNkM0xuY3pMbTl5Wnk4eU1ERTRMMk55WldSbGJuUnBZV3h6TDJWNFlXMXdiR1Z6TDNZeElsMHNJbWxrSWpvaWRYSnVPblYxYVdRNk5qZzBaamN4WmprdE1EVXhOUzAwTURGbExUaGxOMk10TURJek5tRm1ORFZoT0RaaUlpd2lkSGx3WlNJNld5SldaWEpwWm1saFlteGxRM0psWkdWdWRHbGhiQ0lzSWxWdWFYWmxjbk5wZEhsRVpXZHlaV1VpWFN3aWFYTnpkV1Z5SWpwN0ltbGtJam9pWkdsa09tdGxlVHA2Ukc1aFpXMVNaMmhIVkhoMWRXWkxhRVI0ZEZNNVVIZHlURTE2ZHpsWlFrRk5TbUV5TmpKWWVVczBVbGhRY21ZNUluMHNJbU55WldSbGJuUnBZV3hUZFdKcVpXTjBJanA3SW1sa0lqb2laR2xrT21wM2F6cGxlVXB5WkVocmFVOXBTa1pSZVVselNXMU9lV1JwU1RaSmJFRjBUV3BWTWtscGQybGhNbXhyU1dwdmFWcHNSak5qUm1SV1VXNWtVRlF5U25Sa2F6Vk1WRmRHV1dWWVVuUlNWa3B2VDFaak5HRXpaRkJXUkZwRVVXcEdiR0pZVFRObFJVWjVXbmxKYzBsdVoybFBhVXByWTFaYWRVNVVhM3BXYTBwMlQxaE9SRkZZUlRWTmJYQmFZa2RPVVdWc1JYcGhia1p0VkZkU2NWTXhiSFJOYldSQ1pHMW5kRkpYVWpOSmFYZHBaVk5KTmtscVJscFVSMHBOVDFkNFZXUldaekJVZWtKUldWWk9ObVZWU2sxa2EzQnNVMnBDU21GRmVFOWphbFpVVVZWT2VtSXhZekZTTVdnelZsZGphV1pSSWl3aVpHVm5jbVZsSWpwN0luUjVjR1VpT2lKQ1lXTm9aV3h2Y2tSbFozSmxaU0lzSWw5elpDSTZXeUpxTUVGT1FXOVdlREZOVVZsV2MweHlTM1pDWjFVMmFuRTBUM290WjNkeGJteFhVMU5oTm1oTFlXWnZJbDE5ZlN3aWFYTnpkV1Z5Ukdsa0lqb2laR2xrT210bGVUcDZSRzVoWlcxU1oyaEhWSGgxZFdaTGFFUjRkRk01VUhkeVRFMTZkemxaUWtGTlNtRXlOakpZZVVzMFVsaFFjbVk1SWl3aVpYaHdhWEpoZEdsdmJrUmhkR1VpT2lJeU1ESTJMVEEzTFRBNVZERXhPak00T2pBM0xqVTFORE0yT1RRNU5Gb2lMQ0pmYzJRaU9sc2liSFZ0UW1WeFp6UnBNMHRST0VSQmRtWTFVbFJoU0dSWFUwOHpZMHRvYWw5dE4yaFVRbXRTWTB0TlJTSmRmU3dpYW5ScElqb2lkWEp1T25WMWFXUTZOamcwWmpjeFpqa3RNRFV4TlMwME1ERmxMVGhsTjJNdE1ESXpObUZtTkRWaE9EWmlJaXdpWlhod0lqb3hOemd6TlRrM01EZzNMQ0pwWVhRaU9qRTNOVEl3TmpFd09EY3NJbTVpWmlJNk1UYzFNakEyTVRBNE4zMC5qWDJqeFdMcVdUTjNnRHM2VlJZVXFoeDRoakRfV1VqOTI1UW1tQjQ5YTJtaHY1QTI2LU5wakRuTzA1dEhRVDVZNHBEZ0V5WWRfb000a3lMc3VJVDQtd35XeUprU1VjM09XWmpVblY0UjJsZk1TMXhaVXQwTUhaUklpd2lhWE56ZFdGdVkyVkVZWFJsSWl3aU1qQXlOUzB3Tnkwd09WUXhNVG96T0Rvd055NDFOVFF6TVRnNE9EaGFJbDB-V3lKd1RHVmtlbFZUV0dnelVFRTBNMEZ2WWpGM1N6WlJJaXdpYm1GdFpTSXNJa0poWTJobGJHOXlJRzltSUZOamFXVnVZMlVnWVc1a0lFRnlkSE1pWFEiXX19.-4pzurzoKGK2vDk4Oi7jzwWFUToOtObPPo6tFM96e3Jr8_MvleIjk4_4Uk5_A7U2LOcfCxMIBSWNsROnFHRTAQ",
                        "decoded": {
                            "header": {
                                "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ#0",
                                "typ": "JWT",
                                "alg": "ES256"
                            },
                            "payload": {
                                "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                "nbf": 1752061136,
                                "iat": 1752061196,
                                "jti": "sMHjOH0XrS7H",
                                "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                "nonce": "8f3bdc4f-1051-4fd9-9a38-9683ed542624",
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "vp": {
                                    "@context": [
                                        "https://www.w3.org/2018/credentials/v1"
                                    ],
                                    "type": [
                                        "VerifiablePresentation"
                                    ],
                                    "id": "sMHjOH0XrS7H",
                                    "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                    "verifiableCredential": [
                                        {
                                            "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkjekRuYWVtUmdoR1R4dXVmS2hEeHRTOVB3ckxNenc5WUJBTUphMjYyWHlLNFJYUHJmOSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoidXJuOnV1aWQ6Njg0ZjcxZjktMDUxNS00MDFlLThlN2MtMDIzNmFmNDVhODZiIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWUiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6RG5hZW1SZ2hHVHh1dWZLaER4dFM5UHdyTE16dzlZQkFNSmEyNjJYeUs0UlhQcmY5In0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpsRjNjRmRWUW5kUFQySnRkazVMVFdGWWVYUnRSVkpvT1ZjNGEzZFBWRFpEUWpGbGJYTTNlRUZ5WnlJc0luZ2lPaUprY1ZadU5Ua3pWa0p2T1hORFFYRTVNbXBaYkdOUWVsRXphbkZtVFdScVMxbHRNbWRCZG1ndFJXUjNJaXdpZVNJNklqRlpUR0pNT1d4VWRWZzBUekJRWVZONmVVSk1ka3BsU2pCSmFFeE9jalZUUVVOemIxYzFSMWgzVldjaWZRIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIl9zZCI6WyJqMEFOQW9WeDFNUVlWc0xyS3ZCZ1U2anE0T3otZ3dxbmxXU1NhNmhLYWZvIl19fSwiaXNzdWVyRGlkIjoiZGlkOmtleTp6RG5hZW1SZ2hHVHh1dWZLaER4dFM5UHdyTE16dzlZQkFNSmEyNjJYeUs0UlhQcmY5IiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA3LTA5VDExOjM4OjA3LjU1NDM2OTQ5NFoiLCJfc2QiOlsibHVtQmVxZzRpM0tROERBdmY1UlRhSGRXU08zY0toal9tN2hUQmtSY0tNRSJdfSwianRpIjoidXJuOnV1aWQ6Njg0ZjcxZjktMDUxNS00MDFlLThlN2MtMDIzNmFmNDVhODZiIiwiZXhwIjoxNzgzNTk3MDg3LCJpYXQiOjE3NTIwNjEwODcsIm5iZiI6MTc1MjA2MTA4N30.jX2jxWLqWTN3gDs6VRYUqhx4hjD_WUj925QmmB49a2mhv5A26-NpjDnO05tHQT5Y4pDgEyYd_oM4kyLsuIT4-w~WyJkSUc3OWZjUnV4R2lfMS1xZUt0MHZRIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0wOVQxMTozODowNy41NTQzMTg4ODhaIl0~WyJwTGVkelVTWGgzUEE0M0FvYjF3SzZRIiwibmFtZSIsIkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMiXQ",
                                            "decoded": {
                                                "header": {
                                                    "kid": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9#zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                    "typ": "JWT",
                                                    "alg": "ES256"
                                                },
                                                "payload": {
                                                    "iss": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                                    "vc": {
                                                        "@context": [
                                                            "https://www.w3.org/2018/credentials/v1",
                                                            "https://www.w3.org/2018/credentials/examples/v1"
                                                        ],
                                                        "id": "urn:uuid:684f71f9-0515-401e-8e7c-0236af45a86b",
                                                        "type": [
                                                            "VerifiableCredential",
                                                            "UniversityDegree"
                                                        ],
                                                        "issuer": {
                                                            "id": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9"
                                                        },
                                                        "credentialSubject": {
                                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                                            "degree": {
                                                                "type": "BachelorDegree",
                                                                "name": "Bachelor of Science and Arts"
                                                            }
                                                        },
                                                        "issuerDid": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                        "expirationDate": "2026-07-09T11:38:07.554369494Z",
                                                        "issuanceDate": "2025-07-09T11:38:07.554318888Z"
                                                    },
                                                    "jti": "urn:uuid:684f71f9-0515-401e-8e7c-0236af45a86b",
                                                    "exp": 1783597087,
                                                    "iat": 1752061087,
                                                    "nbf": 1752061087
                                                },
                                                "disclosures": {
                                                    "lumBeqg4i3KQ8DAvf5RTaHdWSO3cKhj_m7hTBkRcKME": {
                                                        "disclosure": "WyJkSUc3OWZjUnV4R2lfMS1xZUt0MHZRIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0wOVQxMTozODowNy41NTQzMTg4ODhaIl0",
                                                        "salt": "dIG79fcRuxGi_1-qeKt0vQ",
                                                        "key": "issuanceDate",
                                                        "value": "2025-07-09T11:38:07.554318888Z"
                                                    },
                                                    "j0ANAoVx1MQYVsLrKvBgU6jq4Oz-gwqnlWSSa6hKafo": {
                                                        "disclosure": "WyJwTGVkelVTWGgzUEE0M0FvYjF3SzZRIiwibmFtZSIsIkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMiXQ",
                                                        "salt": "pLedzUSXh3PA43Aob1wK6Q",
                                                        "key": "name",
                                                        "value": "Bachelor of Science and Arts"
                                                    }
                                                }
                                            }
                                        }
                                    ]
                                }
                            }
                        }
                    }
                ]
            }
        }
    """.trimIndent()
    )

    private val uniDegreeNoDisclosuresResponse = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJuYmYiOjE3NTIwNjEwMjgsImlhdCI6MTc1MjA2MTA4OCwianRpIjoiY0xKVGsxTWNBRlRyIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpsRjNjRmRWUW5kUFQySnRkazVMVFdGWWVYUnRSVkpvT1ZjNGEzZFBWRFpEUWpGbGJYTTNlRUZ5WnlJc0luZ2lPaUprY1ZadU5Ua3pWa0p2T1hORFFYRTVNbXBaYkdOUWVsRXphbkZtVFdScVMxbHRNbWRCZG1ndFJXUjNJaXdpZVNJNklqRlpUR0pNT1d4VWRWZzBUekJRWVZONmVVSk1ka3BsU2pCSmFFeE9jalZUUVVOemIxYzFSMWgzVldjaWZRIiwibm9uY2UiOiJjM2U4MWI1Mi04Y2IxLTQ2ZTEtODZjNy02MWFhNjlhZDE1OWMiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJjTEpUazFNY0FGVHIiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtqZWtSdVlXVnRVbWRvUjFSNGRYVm1TMmhFZUhSVE9WQjNja3hOZW5jNVdVSkJUVXBoTWpZeVdIbExORkpZVUhKbU9TSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBXbXhHTTJOR1pGWlJibVJRVkRKS2RHUnJOVXhVVjBaWlpWaFNkRkpXU205UFZtTTBZVE5rVUZaRVdrUlJha1pzWWxoTk0yVkZSbmxhZVVselNXNW5hVTlwU210alZscDFUbFJyZWxaclNuWlBXRTVFVVZoRk5VMXRjRnBpUjA1UlpXeEZlbUZ1Um0xVVYxSnhVekZzZEUxdFpFSmtiV2QwVWxkU00wbHBkMmxsVTBrMlNXcEdXbFJIU2sxUFYzaFZaRlpuTUZSNlFsRlpWazQyWlZWS1RXUnJjR3hUYWtKS1lVVjRUMk5xVmxSUlZVNTZZakZqTVZJeGFETldWMk5wWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2ZDNkM0xuY3pMbTl5Wnk4eU1ERTRMMk55WldSbGJuUnBZV3h6TDJWNFlXMXdiR1Z6TDNZeElsMHNJbWxrSWpvaWRYSnVPblYxYVdRNlpHSXhOemxpTlRRdE5qUTRPUzAwT1dabExUZ3hNRFF0WmpSaVpUZ3lNR1poTWpSbUlpd2lkSGx3WlNJNld5SldaWEpwWm1saFlteGxRM0psWkdWdWRHbGhiQ0lzSWxWdWFYWmxjbk5wZEhsRVpXZHlaV1VpWFN3aWFYTnpkV1Z5SWpwN0ltbGtJam9pWkdsa09tdGxlVHA2Ukc1aFpXMVNaMmhIVkhoMWRXWkxhRVI0ZEZNNVVIZHlURTE2ZHpsWlFrRk5TbUV5TmpKWWVVczBVbGhRY21ZNUluMHNJbWx6YzNWaGJtTmxSR0YwWlNJNklqSXdNalV0TURjdE1EbFVNVEU2TXpnNk1EY3VOREl5TlRjd05EZ3pXaUlzSW1OeVpXUmxiblJwWVd4VGRXSnFaV04wSWpwN0ltbGtJam9pWkdsa09tcDNhenBsZVVweVpFaHJhVTlwU2taUmVVbHpTVzFPZVdScFNUWkpiRUYwVFdwVk1rbHBkMmxoTW14clNXcHZhVnBzUmpOalJtUldVVzVrVUZReVNuUmthelZNVkZkR1dXVllVblJTVmtwdlQxWmpOR0V6WkZCV1JGcEVVV3BHYkdKWVRUTmxSVVo1V25sSmMwbHVaMmxQYVVwclkxWmFkVTVVYTNwV2EwcDJUMWhPUkZGWVJUVk5iWEJhWWtkT1VXVnNSWHBoYmtadFZGZFNjVk14YkhSTmJXUkNaRzFuZEZKWFVqTkphWGRwWlZOSk5rbHFSbHBVUjBwTlQxZDRWV1JXWnpCVWVrSlJXVlpPTm1WVlNrMWthM0JzVTJwQ1NtRkZlRTlqYWxaVVVWVk9lbUl4WXpGU01XZ3pWbGRqYVdaUklpd2laR1ZuY21WbElqcDdJblI1Y0dVaU9pSkNZV05vWld4dmNrUmxaM0psWlNJc0ltNWhiV1VpT2lKQ1lXTm9aV3h2Y2lCdlppQlRZMmxsYm1ObElHRnVaQ0JCY25SekluMTlMQ0pwYzNOMVpYSkVhV1FpT2lKa2FXUTZhMlY1T25wRWJtRmxiVkpuYUVkVWVIVjFaa3RvUkhoMFV6bFFkM0pNVFhwM09WbENRVTFLWVRJMk1saDVTelJTV0ZCeVpqa2lMQ0psZUhCcGNtRjBhVzl1UkdGMFpTSTZJakl3TWpZdE1EY3RNRGxVTVRFNk16ZzZNRGN1TkRJeU5qQTVOalk0V2lKOUxDSnFkR2tpT2lKMWNtNDZkWFZwWkRwa1lqRTNPV0kxTkMwMk5EZzVMVFE1Wm1VdE9ERXdOQzFtTkdKbE9ESXdabUV5TkdZaUxDSmxlSEFpT2pFM09ETTFPVGN3T0Rjc0ltbGhkQ0k2TVRjMU1qQTJNVEE0Tnl3aWJtSm1Jam94TnpVeU1EWXhNRGczZlEubDVfamhudVBPanJaR3BkaXRZWDg4SUQyS0J6dTZZYW9YOFVyODdQY0lrV1V6MkJfeTFwMWhpVnlGRENXUmQ4WkItRWFrYnBwOF9MVWZEMHZmazJFU0EiXX19.q_57ySo70ShyzqEHaR1GvTdezenUmbuMD4Mdb60P7GA_mS6P8h_Nl5wbIRJAZJkOqusLmfzgLS6fNoeF-pUtBA",
                        "decoded": {
                            "header": {
                                "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ#0",
                                "typ": "JWT",
                                "alg": "ES256"
                            },
                            "payload": {
                                "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                "nbf": 1752061028,
                                "iat": 1752061088,
                                "jti": "cLJTk1McAFTr",
                                "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                "nonce": "c3e81b52-8cb1-46e1-86c7-61aa69ad159c",
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "vp": {
                                    "@context": [
                                        "https://www.w3.org/2018/credentials/v1"
                                    ],
                                    "type": [
                                        "VerifiablePresentation"
                                    ],
                                    "id": "cLJTk1McAFTr",
                                    "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                    "verifiableCredential": [
                                        {
                                            "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkjekRuYWVtUmdoR1R4dXVmS2hEeHRTOVB3ckxNenc5WUJBTUphMjYyWHlLNFJYUHJmOSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoidXJuOnV1aWQ6ZGIxNzliNTQtNjQ4OS00OWZlLTgxMDQtZjRiZTgyMGZhMjRmIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWUiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6RG5hZW1SZ2hHVHh1dWZLaER4dFM5UHdyTE16dzlZQkFNSmEyNjJYeUs0UlhQcmY5In0sImlzc3VhbmNlRGF0ZSI6IjIwMjUtMDctMDlUMTE6Mzg6MDcuNDIyNTcwNDgzWiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpsRjNjRmRWUW5kUFQySnRkazVMVFdGWWVYUnRSVkpvT1ZjNGEzZFBWRFpEUWpGbGJYTTNlRUZ5WnlJc0luZ2lPaUprY1ZadU5Ua3pWa0p2T1hORFFYRTVNbXBaYkdOUWVsRXphbkZtVFdScVMxbHRNbWRCZG1ndFJXUjNJaXdpZVNJNklqRlpUR0pNT1d4VWRWZzBUekJRWVZONmVVSk1ka3BsU2pCSmFFeE9jalZUUVVOemIxYzFSMWgzVldjaWZRIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIm5hbWUiOiJCYWNoZWxvciBvZiBTY2llbmNlIGFuZCBBcnRzIn19LCJpc3N1ZXJEaWQiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDctMDlUMTE6Mzg6MDcuNDIyNjA5NjY4WiJ9LCJqdGkiOiJ1cm46dXVpZDpkYjE3OWI1NC02NDg5LTQ5ZmUtODEwNC1mNGJlODIwZmEyNGYiLCJleHAiOjE3ODM1OTcwODcsImlhdCI6MTc1MjA2MTA4NywibmJmIjoxNzUyMDYxMDg3fQ.l5_jhnuPOjrZGpditYX88ID2KBzu6YaoX8Ur87PcIkWUz2B_y1p1hiVyFDCWRd8ZB-Eakbpp8_LUfD0vfk2ESA",
                                            "decoded": {
                                                "header": {
                                                    "kid": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9#zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                    "typ": "JWT",
                                                    "alg": "ES256"
                                                },
                                                "payload": {
                                                    "iss": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                                    "vc": {
                                                        "@context": [
                                                            "https://www.w3.org/2018/credentials/v1",
                                                            "https://www.w3.org/2018/credentials/examples/v1"
                                                        ],
                                                        "id": "urn:uuid:db179b54-6489-49fe-8104-f4be820fa24f",
                                                        "type": [
                                                            "VerifiableCredential",
                                                            "UniversityDegree"
                                                        ],
                                                        "issuer": {
                                                            "id": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9"
                                                        },
                                                        "issuanceDate": "2025-07-09T11:38:07.422570483Z",
                                                        "credentialSubject": {
                                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                                            "degree": {
                                                                "type": "BachelorDegree",
                                                                "name": "Bachelor of Science and Arts"
                                                            }
                                                        },
                                                        "issuerDid": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                        "expirationDate": "2026-07-09T11:38:07.422609668Z"
                                                    },
                                                    "jti": "urn:uuid:db179b54-6489-49fe-8104-f4be820fa24f",
                                                    "exp": 1783597087,
                                                    "iat": 1752061087,
                                                    "nbf": 1752061087
                                                }
                                            }
                                        }
                                    ]
                                }
                            }
                        }
                    }
                ]
            }
        }
    """.trimIndent()
    )

    private val openBadgeWithDisclosuresResponse = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "jwt_vc_json": [
                    {
                        "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJuYmYiOjE3NTIwNjExMDksImlhdCI6MTc1MjA2MTE2OSwianRpIjoiRUZHS3M3Vm1IeXNHIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpsRjNjRmRWUW5kUFQySnRkazVMVFdGWWVYUnRSVkpvT1ZjNGEzZFBWRFpEUWpGbGJYTTNlRUZ5WnlJc0luZ2lPaUprY1ZadU5Ua3pWa0p2T1hORFFYRTVNbXBaYkdOUWVsRXphbkZtVFdScVMxbHRNbWRCZG1ndFJXUjNJaXdpZVNJNklqRlpUR0pNT1d4VWRWZzBUekJRWVZONmVVSk1ka3BsU2pCSmFFeE9jalZUUVVOemIxYzFSMWgzVldjaWZRIiwibm9uY2UiOiI4MGIyZmI2NS02M2NkLTQwNTctYWZiNi0wZjA2MmFlYzMyYmIiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJFRkdLczdWbUh5c0ciLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtqZWtSdVlXVnRVbWRvUjFSNGRYVm1TMmhFZUhSVE9WQjNja3hOZW5jNVdVSkJUVXBoTWpZeVdIbExORkpZVUhKbU9TSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBXbXhHTTJOR1pGWlJibVJRVkRKS2RHUnJOVXhVVjBaWlpWaFNkRkpXU205UFZtTTBZVE5rVUZaRVdrUlJha1pzWWxoTk0yVkZSbmxhZVVselNXNW5hVTlwU210alZscDFUbFJyZWxaclNuWlBXRTVFVVZoRk5VMXRjRnBpUjA1UlpXeEZlbUZ1Um0xVVYxSnhVekZzZEUxdFpFSmtiV2QwVWxkU00wbHBkMmxsVTBrMlNXcEdXbFJIU2sxUFYzaFZaRlpuTUZSNlFsRlpWazQyWlZWS1RXUnJjR3hUYWtKS1lVVjRUMk5xVmxSUlZVNTZZakZqTVZJeGFETldWMk5wWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2Y0hWeWJDNXBiWE5uYkc5aVlXd3ViM0puTDNOd1pXTXZiMkl2ZGpOd01DOWpiMjUwWlhoMExtcHpiMjRpWFN3aWFXUWlPaUoxY200NmRYVnBaRG8wT1dOaU1qRTNZaTFsTW1RNUxUUmhOR0V0WW1OaU55MWpZamxpWW1Ka1pHWTBNREVpTENKMGVYQmxJanBiSWxabGNtbG1hV0ZpYkdWRGNtVmtaVzUwYVdGc0lpd2lUM0JsYmtKaFpHZGxRM0psWkdWdWRHbGhiQ0pkTENKcGMzTjFaWElpT25zaWRIbHdaU0k2V3lKUWNtOW1hV3hsSWwwc0ltbGtJam9pWkdsa09tdGxlVHA2Ukc1aFpXMVNaMmhIVkhoMWRXWkxhRVI0ZEZNNVVIZHlURTE2ZHpsWlFrRk5TbUV5TmpKWWVVczBVbGhRY21ZNUlpd2libUZ0WlNJNklrcHZZbk1nWm05eUlIUm9aU0JHZFhSMWNtVWdLRXBHUmlraUxDSjFjbXdpT2lKb2RIUndjem92TDNkM2R5NXFabVl1YjNKbkx5SXNJbWx0WVdkbElqb2lhSFIwY0hNNkx5OTNNMk10WTJObkxtZHBkR2gxWWk1cGJ5OTJZeTFsWkM5d2JIVm5abVZ6ZEMweExUSXdNakl2YVcxaFoyVnpMMHBHUmw5TWIyZHZURzlqYTNWd0xuQnVaeUo5TENKbGVIQnBjbUYwYVc5dVJHRjBaU0k2SWpJd01qWXRNRGN0TURsVU1URTZNemc2TURndU1ERTNPVGt5TmpVMFdpSXNJbU55WldSbGJuUnBZV3hUZFdKcVpXTjBJanA3SW1sa0lqb2laR2xrT21wM2F6cGxlVXB5WkVocmFVOXBTa1pSZVVselNXMU9lV1JwU1RaSmJFRjBUV3BWTWtscGQybGhNbXhyU1dwdmFWcHNSak5qUm1SV1VXNWtVRlF5U25Sa2F6Vk1WRmRHV1dWWVVuUlNWa3B2VDFaak5HRXpaRkJXUkZwRVVXcEdiR0pZVFRObFJVWjVXbmxKYzBsdVoybFBhVXByWTFaYWRVNVVhM3BXYTBwMlQxaE9SRkZZUlRWTmJYQmFZa2RPVVdWc1JYcGhia1p0VkZkU2NWTXhiSFJOYldSQ1pHMW5kRkpYVWpOSmFYZHBaVk5KTmtscVJscFVSMHBOVDFkNFZXUldaekJVZWtKUldWWk9ObVZWU2sxa2EzQnNVMnBDU21GRmVFOWphbFpVVVZWT2VtSXhZekZTTVdnelZsZGphV1pSSWl3aWRIbHdaU0k2V3lKQlkyaHBaWFpsYldWdWRGTjFZbXBsWTNRaVhTd2lZV05vYVdWMlpXMWxiblFpT25zaWFXUWlPaUoxY200NmRYVnBaRHBoWXpJMU5HSmtOUzA0Wm1Ga0xUUmlZakV0T1dReU9TMWxabVE1TXpnMU16WTVNallpTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MElsMHNJbTVoYldVaU9pSktSa1lnZUNCMll5MWxaSFVnVUd4MVowWmxjM1FnTXlCSmJuUmxjbTl3WlhKaFltbHNhWFI1SWl3aVpHVnpZM0pwY0hScGIyNGlPaUpVYUdseklIZGhiR3hsZENCemRYQndiM0owY3lCMGFHVWdkWE5sSUc5bUlGY3pReUJXWlhKcFptbGhZbXhsSUVOeVpXUmxiblJwWVd4eklHRnVaQ0JvWVhNZ1pHVnRiMjV6ZEhKaGRHVmtJR2x1ZEdWeWIzQmxjbUZpYVd4cGRIa2daSFZ5YVc1bklIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z2NtVnhkV1Z6ZENCM2IzSnJabXh2ZHlCa2RYSnBibWNnU2taR0lIZ2dWa010UlVSVklGQnNkV2RHWlhOMElETXVJaXdpWTNKcGRHVnlhV0VpT25zaWRIbHdaU0k2SWtOeWFYUmxjbWxoSWl3aWJtRnljbUYwYVhabElqb2lWMkZzYkdWMElITnZiSFYwYVc5dWN5QndjbTkyYVdSbGNuTWdaV0Z5Ym1Wa0lIUm9hWE1nWW1Ga1oyVWdZbmtnWkdWdGIyNXpkSEpoZEdsdVp5QnBiblJsY205d1pYSmhZbWxzYVhSNUlHUjFjbWx1WnlCMGFHVWdjSEpsYzJWdWRHRjBhVzl1SUhKbGNYVmxjM1FnZDI5eWEyWnNiM2N1SUZSb2FYTWdhVzVqYkhWa1pYTWdjM1ZqWTJWemMyWjFiR3g1SUhKbFkyVnBkbWx1WnlCaElIQnlaWE5sYm5SaGRHbHZiaUJ5WlhGMVpYTjBMQ0JoYkd4dmQybHVaeUIwYUdVZ2FHOXNaR1Z5SUhSdklITmxiR1ZqZENCaGRDQnNaV0Z6ZENCMGQyOGdkSGx3WlhNZ2IyWWdkbVZ5YVdacFlXSnNaU0JqY21Wa1pXNTBhV0ZzY3lCMGJ5QmpjbVZoZEdVZ1lTQjJaWEpwWm1saFlteGxJSEJ5WlhObGJuUmhkR2x2Yml3Z2NtVjBkWEp1YVc1bklIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z2RHOGdkR2hsSUhKbGNYVmxjM1J2Y2l3Z1lXNWtJSEJoYzNOcGJtY2dkbVZ5YVdacFkyRjBhVzl1SUc5bUlIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z1lXNWtJSFJvWlNCcGJtTnNkV1JsWkNCamNtVmtaVzUwYVdGc2N5NGlmU3dpYVcxaFoyVWlPbnNpYVdRaU9pSm9kSFJ3Y3pvdkwzY3pZeTFqWTJjdVoybDBhSFZpTG1sdkwzWmpMV1ZrTDNCc2RXZG1aWE4wTFRNdE1qQXlNeTlwYldGblpYTXZTa1pHTFZaRExVVkVWUzFRVEZWSFJrVlRWRE10WW1Ga1oyVXRhVzFoWjJVdWNHNW5JaXdpZEhsd1pTSTZJa2x0WVdkbEluMTlmU3dpWDNOa0lqcGJJbFpIVm5Cb1VGSjZhVGh3WVhFMlIxRlpOamxtVVZvMmIzcGFWMjFvYmxVeExXWXlPVTFhVTFaSVZXc2lMQ0o0YUVwaVlrUlJaVXgyZEZaa05scE5UV2hTT1ZGUmVIaHhURk5sTm1KeFJFNU5jMFpKVVcxa1NqVXdJbDE5TENKcWRHa2lPaUoxY200NmRYVnBaRG8wT1dOaU1qRTNZaTFsTW1RNUxUUmhOR0V0WW1OaU55MWpZamxpWW1Ka1pHWTBNREVpTENKbGVIQWlPakUzT0RNMU9UY3dPRGdzSW1saGRDSTZNVGMxTWpBMk1UQTRPQ3dpYm1KbUlqb3hOelV5TURZeE1EZzRmUS5RakRPSmxVc293SDVSdUUyX0dQdFpPWUxVSUJkeTRqZVdLeWZJemRBWGhENGl4R2NSaTJnM18tU0hYNHZkc1lsV2tmTXZTb2QtRk92LS05eTlkMnBRZ35XeUptZDJ0bVNsOXRRbE0zWkc1WmF6SjJhMHhmU0ZsM0lpd2libUZ0WlNJc0lrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpWFF-V3lKUWJsZzFTa0pmVDBodk9WVnBTVmc0YURkZmRtRlJJaXdpYVhOemRXRnVZMlZFWVhSbElpd2lNakF5TlMwd055MHdPVlF4TVRvek9Eb3dPQzR3TVRjNU5UWTFNelZhSWwwIl19fQ.0-gBw2MX6L1jKOYh4e-ByNyYP5ihLxHBE796XLQM8sAPgwCmMEL-DCW83tMazbjFS9DEgRzytbRCO-e9IZe3Pw",
                        "decoded": {
                            "header": {
                                "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ#0",
                                "typ": "JWT",
                                "alg": "ES256"
                            },
                            "payload": {
                                "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                "nbf": 1752061109,
                                "iat": 1752061169,
                                "jti": "EFGKs7VmHysG",
                                "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                "nonce": "80b2fb65-63cd-4057-afb6-0f062aec32bb",
                                "aud": "http://localhost:22222/openid4vc/verify",
                                "vp": {
                                    "@context": [
                                        "https://www.w3.org/2018/credentials/v1"
                                    ],
                                    "type": [
                                        "VerifiablePresentation"
                                    ],
                                    "id": "EFGKs7VmHysG",
                                    "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                    "verifiableCredential": [
                                        {
                                            "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkjekRuYWVtUmdoR1R4dXVmS2hEeHRTOVB3ckxNenc5WUJBTUphMjYyWHlLNFJYUHJmOSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDo0OWNiMjE3Yi1lMmQ5LTRhNGEtYmNiNy1jYjliYmJkZGY0MDEiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sImlkIjoiZGlkOmtleTp6RG5hZW1SZ2hHVHh1dWZLaER4dFM5UHdyTE16dzlZQkFNSmEyNjJYeUs0UlhQcmY5IiwibmFtZSI6IkpvYnMgZm9yIHRoZSBGdXR1cmUgKEpGRikiLCJ1cmwiOiJodHRwczovL3d3dy5qZmYub3JnLyIsImltYWdlIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0xLTIwMjIvaW1hZ2VzL0pGRl9Mb2dvTG9ja3VwLnBuZyJ9LCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDctMDlUMTE6Mzg6MDguMDE3OTkyNjU0WiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpsRjNjRmRWUW5kUFQySnRkazVMVFdGWWVYUnRSVkpvT1ZjNGEzZFBWRFpEUWpGbGJYTTNlRUZ5WnlJc0luZ2lPaUprY1ZadU5Ua3pWa0p2T1hORFFYRTVNbXBaYkdOUWVsRXphbkZtVFdScVMxbHRNbWRCZG1ndFJXUjNJaXdpZVNJNklqRlpUR0pNT1d4VWRWZzBUekJRWVZONmVVSk1ka3BsU2pCSmFFeE9jalZUUVVOemIxYzFSMWgzVldjaWZRIiwidHlwZSI6WyJBY2hpZXZlbWVudFN1YmplY3QiXSwiYWNoaWV2ZW1lbnQiOnsiaWQiOiJ1cm46dXVpZDphYzI1NGJkNS04ZmFkLTRiYjEtOWQyOS1lZmQ5Mzg1MzY5MjYiLCJ0eXBlIjpbIkFjaGlldmVtZW50Il0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiZGVzY3JpcHRpb24iOiJUaGlzIHdhbGxldCBzdXBwb3J0cyB0aGUgdXNlIG9mIFczQyBWZXJpZmlhYmxlIENyZWRlbnRpYWxzIGFuZCBoYXMgZGVtb25zdHJhdGVkIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdyBkdXJpbmcgSkZGIHggVkMtRURVIFBsdWdGZXN0IDMuIiwiY3JpdGVyaWEiOnsidHlwZSI6IkNyaXRlcmlhIiwibmFycmF0aXZlIjoiV2FsbGV0IHNvbHV0aW9ucyBwcm92aWRlcnMgZWFybmVkIHRoaXMgYmFkZ2UgYnkgZGVtb25zdHJhdGluZyBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cuIFRoaXMgaW5jbHVkZXMgc3VjY2Vzc2Z1bGx5IHJlY2VpdmluZyBhIHByZXNlbnRhdGlvbiByZXF1ZXN0LCBhbGxvd2luZyB0aGUgaG9sZGVyIHRvIHNlbGVjdCBhdCBsZWFzdCB0d28gdHlwZXMgb2YgdmVyaWZpYWJsZSBjcmVkZW50aWFscyB0byBjcmVhdGUgYSB2ZXJpZmlhYmxlIHByZXNlbnRhdGlvbiwgcmV0dXJuaW5nIHRoZSBwcmVzZW50YXRpb24gdG8gdGhlIHJlcXVlc3RvciwgYW5kIHBhc3NpbmcgdmVyaWZpY2F0aW9uIG9mIHRoZSBwcmVzZW50YXRpb24gYW5kIHRoZSBpbmNsdWRlZCBjcmVkZW50aWFscy4ifSwiaW1hZ2UiOnsiaWQiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTMtMjAyMy9pbWFnZXMvSkZGLVZDLUVEVS1QTFVHRkVTVDMtYmFkZ2UtaW1hZ2UucG5nIiwidHlwZSI6IkltYWdlIn19fSwiX3NkIjpbIlZHVnBoUFJ6aThwYXE2R1FZNjlmUVo2b3paV21oblUxLWYyOU1aU1ZIVWsiLCJ4aEpiYkRRZUx2dFZkNlpNTWhSOVFReHhxTFNlNmJxRE5Nc0ZJUW1kSjUwIl19LCJqdGkiOiJ1cm46dXVpZDo0OWNiMjE3Yi1lMmQ5LTRhNGEtYmNiNy1jYjliYmJkZGY0MDEiLCJleHAiOjE3ODM1OTcwODgsImlhdCI6MTc1MjA2MTA4OCwibmJmIjoxNzUyMDYxMDg4fQ.QjDOJlUsowH5RuE2_GPtZOYLUIBdy4jeWKyfIzdAXhD4ixGcRi2g3_-SHX4vdsYlWkfMvSod-FOv--9y9d2pQg~WyJmd2tmSl9tQlM3ZG5ZazJ2a0xfSFl3IiwibmFtZSIsIkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiXQ~WyJQblg1SkJfT0hvOVVpSVg4aDdfdmFRIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0wOVQxMTozODowOC4wMTc5NTY1MzVaIl0",
                                            "decoded": {
                                                "header": {
                                                    "kid": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9#zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                    "typ": "JWT",
                                                    "alg": "ES256"
                                                },
                                                "payload": {
                                                    "iss": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                                                    "vc": {
                                                        "@context": [
                                                            "https://www.w3.org/2018/credentials/v1",
                                                            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                                        ],
                                                        "id": "urn:uuid:49cb217b-e2d9-4a4a-bcb7-cb9bbbddf401",
                                                        "type": [
                                                            "VerifiableCredential",
                                                            "OpenBadgeCredential"
                                                        ],
                                                        "issuer": {
                                                            "type": [
                                                                "Profile"
                                                            ],
                                                            "id": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                                            "name": "Jobs for the Future (JFF)",
                                                            "url": "https://www.jff.org/",
                                                            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                                        },
                                                        "expirationDate": "2026-07-09T11:38:08.017992654Z",
                                                        "credentialSubject": {
                                                            "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
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
                                                        "issuanceDate": "2025-07-09T11:38:08.017956535Z"
                                                    },
                                                    "jti": "urn:uuid:49cb217b-e2d9-4a4a-bcb7-cb9bbbddf401",
                                                    "exp": 1783597088,
                                                    "iat": 1752061088,
                                                    "nbf": 1752061088
                                                },
                                                "disclosures": {
                                                    "VGVphPRzi8paq6GQY69fQZ6ozZWmhnU1-f29MZSVHUk": {
                                                        "disclosure": "WyJmd2tmSl9tQlM3ZG5ZazJ2a0xfSFl3IiwibmFtZSIsIkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiXQ",
                                                        "salt": "fwkfJ_mBS7dnYk2vkL_HYw",
                                                        "key": "name",
                                                        "value": "JFF x vc-edu PlugFest 3 Interoperability"
                                                    },
                                                    "xhJbbDQeLvtVd6ZMMhR9QQxxqLSe6bqDNMsFIQmdJ50": {
                                                        "disclosure": "WyJQblg1SkJfT0hvOVVpSVg4aDdfdmFRIiwiaXNzdWFuY2VEYXRlIiwiMjAyNS0wNy0wOVQxMTozODowOC4wMTc5NTY1MzVaIl0",
                                                        "salt": "PnX5JB_OHo9UiIX8h7_vaQ",
                                                        "key": "issuanceDate",
                                                        "value": "2025-07-09T11:38:08.017956535Z"
                                                    }
                                                }
                                            }
                                        }
                                    ]
                                }
                            }
                        }
                    }
                ]
            }
        }
    """.trimIndent()
    )

    private val openBadgeNoDisclosuresResponse = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
          "credentialsByFormat": {
            "jwt_vc_json": [
              {
                "raw": "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJuYmYiOjE3NTIwNjEwODUsImlhdCI6MTc1MjA2MTE0NSwianRpIjoiaEtDemRZZ3lSaEltIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVpsRjNjRmRWUW5kUFQySnRkazVMVFdGWWVYUnRSVkpvT1ZjNGEzZFBWRFpEUWpGbGJYTTNlRUZ5WnlJc0luZ2lPaUprY1ZadU5Ua3pWa0p2T1hORFFYRTVNbXBaYkdOUWVsRXphbkZtVFdScVMxbHRNbWRCZG1ndFJXUjNJaXdpZVNJNklqRlpUR0pNT1d4VWRWZzBUekJRWVZONmVVSk1ka3BsU2pCSmFFeE9jalZUUVVOemIxYzFSMWgzVldjaWZRIiwibm9uY2UiOiI4Mzc0YmYwZC1iMzMwLTRmYWQtOTFmZi1mYTNhNWMyNmJjMmUiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJoS0N6ZFlneVJoSW0iLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtqZWtSdVlXVnRVbWRvUjFSNGRYVm1TMmhFZUhSVE9WQjNja3hOZW5jNVdVSkJUVXBoTWpZeVdIbExORkpZVUhKbU9TSXNJblI1Y0NJNklrcFhWQ0lzSW1Gc1p5STZJa1ZUTWpVMkluMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtpTENKemRXSWlPaUprYVdRNmFuZHJPbVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHNRWFJOYWxVeVNXbDNhV0V5Ykd0SmFtOXBXbXhHTTJOR1pGWlJibVJRVkRKS2RHUnJOVXhVVjBaWlpWaFNkRkpXU205UFZtTTBZVE5rVUZaRVdrUlJha1pzWWxoTk0yVkZSbmxhZVVselNXNW5hVTlwU210alZscDFUbFJyZWxaclNuWlBXRTVFVVZoRk5VMXRjRnBpUjA1UlpXeEZlbUZ1Um0xVVYxSnhVekZzZEUxdFpFSmtiV2QwVWxkU00wbHBkMmxsVTBrMlNXcEdXbFJIU2sxUFYzaFZaRlpuTUZSNlFsRlpWazQyWlZWS1RXUnJjR3hUYWtKS1lVVjRUMk5xVmxSUlZVNTZZakZqTVZJeGFETldWMk5wWmxFaUxDSjJZeUk2ZXlKQVkyOXVkR1Y0ZENJNld5Sm9kSFJ3Y3pvdkwzZDNkeTUzTXk1dmNtY3ZNakF4T0M5amNtVmtaVzUwYVdGc2N5OTJNU0lzSW1oMGRIQnpPaTh2Y0hWeWJDNXBiWE5uYkc5aVlXd3ViM0puTDNOd1pXTXZiMkl2ZGpOd01DOWpiMjUwWlhoMExtcHpiMjRpWFN3aWFXUWlPaUoxY200NmRYVnBaRG93T1dNek1qRXhZaTFoWm1ZekxUUTJNMk10WWpBMk5TMDFaalJsTWpRNFpUUTBaRE1pTENKMGVYQmxJanBiSWxabGNtbG1hV0ZpYkdWRGNtVmtaVzUwYVdGc0lpd2lUM0JsYmtKaFpHZGxRM0psWkdWdWRHbGhiQ0pkTENKdVlXMWxJam9pU2taR0lIZ2dkbU10WldSMUlGQnNkV2RHWlhOMElETWdTVzUwWlhKdmNHVnlZV0pwYkdsMGVTSXNJbWx6YzNWbGNpSTZleUowZVhCbElqcGJJbEJ5YjJacGJHVWlYU3dpYVdRaU9pSmthV1E2YTJWNU9ucEVibUZsYlZKbmFFZFVlSFYxWmt0b1JIaDBVemxRZDNKTVRYcDNPVmxDUVUxS1lUSTJNbGg1U3pSU1dGQnlaamtpTENKdVlXMWxJam9pU205aWN5Qm1iM0lnZEdobElFWjFkSFZ5WlNBb1NrWkdLU0lzSW5WeWJDSTZJbWgwZEhCek9pOHZkM2QzTG1wbVppNXZjbWN2SWl3aWFXMWhaMlVpT2lKb2RIUndjem92TDNjell5MWpZMmN1WjJsMGFIVmlMbWx2TDNaakxXVmtMM0JzZFdkbVpYTjBMVEV0TWpBeU1pOXBiV0ZuWlhNdlNrWkdYMHh2WjI5TWIyTnJkWEF1Y0c1bkluMHNJbWx6YzNWaGJtTmxSR0YwWlNJNklqSXdNalV0TURjdE1EbFVNVEU2TXpnNk1EY3VOalk1TURReU9EQTFXaUlzSW1WNGNHbHlZWFJwYjI1RVlYUmxJam9pTWpBeU5pMHdOeTB3T1ZReE1Ub3pPRG93Tnk0Mk5qa3dPRGsxTWpWYUlpd2lZM0psWkdWdWRHbGhiRk4xWW1wbFkzUWlPbnNpYVdRaU9pSmthV1E2YW5kck9tVjVTbkprU0d0cFQybEtSbEY1U1hOSmJVNTVaR2xKTmtsc1FYUk5hbFV5U1dsM2FXRXliR3RKYW05cFdteEdNMk5HWkZaUmJtUlFWREpLZEdSck5VeFVWMFpaWlZoU2RGSldTbTlQVm1NMFlUTmtVRlpFV2tSUmFrWnNZbGhOTTJWRlJubGFlVWx6U1c1bmFVOXBTbXRqVmxwMVRsUnJlbFpyU25aUFdFNUVVVmhGTlUxdGNGcGlSMDVSWld4RmVtRnVSbTFVVjFKeFV6RnNkRTF0WkVKa2JXZDBVbGRTTTBscGQybGxVMGsyU1dwR1dsUkhTazFQVjNoVlpGWm5NRlI2UWxGWlZrNDJaVlZLVFdScmNHeFRha0pLWVVWNFQyTnFWbFJSVlU1NllqRmpNVkl4YUROV1YyTnBabEVpTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MFUzVmlhbVZqZENKZExDSmhZMmhwWlhabGJXVnVkQ0k2ZXlKcFpDSTZJblZ5YmpwMWRXbGtPbUZqTWpVMFltUTFMVGhtWVdRdE5HSmlNUzA1WkRJNUxXVm1aRGt6T0RVek5qa3lOaUlzSW5SNWNHVWlPbHNpUVdOb2FXVjJaVzFsYm5RaVhTd2libUZ0WlNJNklrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpTENKa1pYTmpjbWx3ZEdsdmJpSTZJbFJvYVhNZ2QyRnNiR1YwSUhOMWNIQnZjblJ6SUhSb1pTQjFjMlVnYjJZZ1Z6TkRJRlpsY21sbWFXRmliR1VnUTNKbFpHVnVkR2xoYkhNZ1lXNWtJR2hoY3lCa1pXMXZibk4wY21GMFpXUWdhVzUwWlhKdmNHVnlZV0pwYkdsMGVTQmtkWEpwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCeVpYRjFaWE4wSUhkdmNtdG1iRzkzSUdSMWNtbHVaeUJLUmtZZ2VDQldReTFGUkZVZ1VHeDFaMFpsYzNRZ015NGlMQ0pqY21sMFpYSnBZU0k2ZXlKMGVYQmxJam9pUTNKcGRHVnlhV0VpTENKdVlYSnlZWFJwZG1VaU9pSlhZV3hzWlhRZ2MyOXNkWFJwYjI1eklIQnliM1pwWkdWeWN5QmxZWEp1WldRZ2RHaHBjeUJpWVdSblpTQmllU0JrWlcxdmJuTjBjbUYwYVc1bklHbHVkR1Z5YjNCbGNtRmlhV3hwZEhrZ1pIVnlhVzVuSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnY21WeGRXVnpkQ0IzYjNKclpteHZkeTRnVkdocGN5QnBibU5zZFdSbGN5QnpkV05qWlhOelpuVnNiSGtnY21WalpXbDJhVzVuSUdFZ2NISmxjMlZ1ZEdGMGFXOXVJSEpsY1hWbGMzUXNJR0ZzYkc5M2FXNW5JSFJvWlNCb2IyeGtaWElnZEc4Z2MyVnNaV04wSUdGMElHeGxZWE4wSUhSM2J5QjBlWEJsY3lCdlppQjJaWEpwWm1saFlteGxJR055WldSbGJuUnBZV3h6SUhSdklHTnlaV0YwWlNCaElIWmxjbWxtYVdGaWJHVWdjSEpsYzJWdWRHRjBhVzl1TENCeVpYUjFjbTVwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCMGJ5QjBhR1VnY21WeGRXVnpkRzl5TENCaGJtUWdjR0Z6YzJsdVp5QjJaWEpwWm1sallYUnBiMjRnYjJZZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCaGJtUWdkR2hsSUdsdVkyeDFaR1ZrSUdOeVpXUmxiblJwWVd4ekxpSjlMQ0pwYldGblpTSTZleUpwWkNJNkltaDBkSEJ6T2k4dmR6TmpMV05qWnk1bmFYUm9kV0l1YVc4dmRtTXRaV1F2Y0d4MVoyWmxjM1F0TXkweU1ESXpMMmx0WVdkbGN5OUtSa1l0VmtNdFJVUlZMVkJNVlVkR1JWTlVNeTFpWVdSblpTMXBiV0ZuWlM1d2JtY2lMQ0owZVhCbElqb2lTVzFoWjJVaWZYMTlmU3dpYW5ScElqb2lkWEp1T25WMWFXUTZNRGxqTXpJeE1XSXRZV1ptTXkwME5qTmpMV0l3TmpVdE5XWTBaVEkwT0dVME5HUXpJaXdpWlhod0lqb3hOemd6TlRrM01EZzNMQ0pwWVhRaU9qRTNOVEl3TmpFd09EY3NJbTVpWmlJNk1UYzFNakEyTVRBNE4zMC5MRmxVeGlFa254LTVOdWZhTlMwMlQyXzE1WU9tbF9DelBpOFkzelM3NGUwZnFHVXM0My0yR2dtR21FWFgtRkVRRFJpQjROUUVRNVJYZFR2SzB4QkMyUSJdfX0.yKwv535wcXLmlSt7UwT2wrevg_WpXQicU1alLsozZ_phIJTh5jBSAgboOhL_AJ00q-yVP6rxfRBzKd8_djh0Uw",
                "decoded": {
                  "header": {
                    "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ#0",
                    "typ": "JWT",
                    "alg": "ES256"
                  },
                  "payload": {
                    "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                    "nbf": 1752061085,
                    "iat": 1752061145,
                    "jti": "hKCzdYgyRhIm",
                    "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                    "nonce": "8374bf0d-b330-4fad-91ff-fa3a5c26bc2e",
                    "aud": "http://localhost:22222/openid4vc/verify",
                    "vp": {
                      "@context": [
                        "https://www.w3.org/2018/credentials/v1"
                      ],
                      "type": [
                        "VerifiablePresentation"
                      ],
                      "id": "hKCzdYgyRhIm",
                      "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                      "verifiableCredential": [
                        {
                          "raw": "eyJraWQiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkjekRuYWVtUmdoR1R4dXVmS2hEeHRTOVB3ckxNenc5WUJBTUphMjYyWHlLNFJYUHJmOSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDowOWMzMjExYi1hZmYzLTQ2M2MtYjA2NS01ZjRlMjQ4ZTQ0ZDMiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJuYW1lIjoiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSIsImlzc3VlciI6eyJ0eXBlIjpbIlByb2ZpbGUiXSwiaWQiOiJkaWQ6a2V5OnpEbmFlbVJnaEdUeHV1ZktoRHh0UzlQd3JMTXp3OVlCQU1KYTI2Mlh5SzRSWFByZjkiLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIn0sImlzc3VhbmNlRGF0ZSI6IjIwMjUtMDctMDlUMTE6Mzg6MDcuNjY5MDQyODA1WiIsImV4cGlyYXRpb25EYXRlIjoiMjAyNi0wNy0wOVQxMTozODowNy42NjkwODk1MjVaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pWmxGM2NGZFZRbmRQVDJKdGRrNUxUV0ZZZVhSdFJWSm9PVmM0YTNkUFZEWkRRakZsYlhNM2VFRnlaeUlzSW5naU9pSmtjVlp1TlRrelZrSnZPWE5EUVhFNU1tcFpiR05RZWxFemFuRm1UV1JxUzFsdE1tZEJkbWd0UldSM0lpd2llU0k2SWpGWlRHSk1PV3hVZFZnMFR6QlFZVk42ZVVKTWRrcGxTakJKYUV4T2NqVlRRVU56YjFjMVIxaDNWV2NpZlEiLCJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX19fSwianRpIjoidXJuOnV1aWQ6MDljMzIxMWItYWZmMy00NjNjLWIwNjUtNWY0ZTI0OGU0NGQzIiwiZXhwIjoxNzgzNTk3MDg3LCJpYXQiOjE3NTIwNjEwODcsIm5iZiI6MTc1MjA2MTA4N30.LFlUxiEknx-5NufaNS02T2_15YOml_CzPi8Y3zS74e0fqGUs43-2GgmGmEXX-FEQDRiB4NQEQ5RXdTvK0xBC2Q",
                          "decoded": {
                            "header": {
                              "kid": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9#zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                              "typ": "JWT",
                              "alg": "ES256"
                            },
                            "payload": {
                              "iss": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                              "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
                              "vc": {
                                "@context": [
                                  "https://www.w3.org/2018/credentials/v1",
                                  "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                                ],
                                "id": "urn:uuid:09c3211b-aff3-463c-b065-5f4e248e44d3",
                                "type": [
                                  "VerifiableCredential",
                                  "OpenBadgeCredential"
                                ],
                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                "issuer": {
                                  "type": [
                                    "Profile"
                                  ],
                                  "id": "did:key:zDnaemRghGTxuufKhDxtS9PwrLMzw9YBAMJa262XyK4RXPrf9",
                                  "name": "Jobs for the Future (JFF)",
                                  "url": "https://www.jff.org/",
                                  "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                                },
                                "issuanceDate": "2025-07-09T11:38:07.669042805Z",
                                "expirationDate": "2026-07-09T11:38:07.669089525Z",
                                "credentialSubject": {
                                  "id": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZlF3cFdVQndPT2Jtdk5LTWFYeXRtRVJoOVc4a3dPVDZDQjFlbXM3eEFyZyIsIngiOiJkcVZuNTkzVkJvOXNDQXE5MmpZbGNQelEzanFmTWRqS1ltMmdBdmgtRWR3IiwieSI6IjFZTGJMOWxUdVg0TzBQYVN6eUJMdkplSjBJaExOcjVTQUNzb1c1R1h3VWcifQ",
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
                              "jti": "urn:uuid:09c3211b-aff3-463c-b065-5f4e248e44d3",
                              "exp": 1783597087,
                              "iat": 1752061087,
                              "nbf": 1752061087
                            }
                          }
                        }
                      ]
                    }
                  }
                }
              }
            ]
          }
        }
    """.trimIndent()
    )

    private val mDLExampleResponse = Json.decodeFromString<PresentationSessionPresentedCredentials>(
        """
        {
            "credentialsByFormat": {
                "mso_mdoc": [
                    {
                        "raw": "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGxpc3N1ZXJTaWduZWSiam5hbWVTcGFjZXOhcW9yZy5pc28uMTgwMTMuNS4xi9gYWFKkaGRpZ2VzdElEAGZyYW5kb21QL2D7ORKvTsTRQZOxjQ1Lh3FlbGVtZW50SWRlbnRpZmllcmtmYW1pbHlfbmFtZWxlbGVtZW50VmFsdWVjRG9l2BhYUqRoZGlnZXN0SUQBZnJhbmRvbVD9VQS_Pz_iFa09Tq_17wI9cWVsZW1lbnRJZGVudGlmaWVyamdpdmVuX25hbWVsZWxlbWVudFZhbHVlZEpvaG7YGFhYpGhkaWdlc3RJRAJmcmFuZG9tUL0xL2dGVmTQJZugHmvV9GxxZWxlbWVudElkZW50aWZpZXJqYmlydGhfZGF0ZWxlbGVtZW50VmFsdWVqMTk4Ni0wMy0yMtgYWFikaGRpZ2VzdElEA2ZyYW5kb21Q_51BC1IKi_Lf-04JqhKV53FlbGVtZW50SWRlbnRpZmllcmppc3N1ZV9kYXRlbGVsZW1lbnRWYWx1ZWoyMDE5LTEwLTIw2BhYWaRoZGlnZXN0SUQEZnJhbmRvbVB-d57Eqz91eukf2I_YAe2xcWVsZW1lbnRJZGVudGlmaWVya2V4cGlyeV9kYXRlbGVsZW1lbnRWYWx1ZWoyMDI0LTEwLTIw2BhYVaRoZGlnZXN0SUQFZnJhbmRvbVAl5UZaPSiMXSO1NIYgNPBrcWVsZW1lbnRJZGVudGlmaWVyb2lzc3VpbmdfY291bnRyeWxlbGVtZW50VmFsdWViQVTYGFhbpGhkaWdlc3RJRAZmcmFuZG9tULVjFRBp0eatNdx6Q8VafPtxZWxlbWVudElkZW50aWZpZXJxaXNzdWluZ19hdXRob3JpdHlsZWxlbWVudFZhbHVlZkFUIERNVtgYWFekaGRpZ2VzdElEB2ZyYW5kb21QmxWvd2KMWfeKRFh9NQWR9HFlbGVtZW50SWRlbnRpZmllcm9kb2N1bWVudF9udW1iZXJsZWxlbWVudFZhbHVlGgdbzRXYGFhnpGhkaWdlc3RJRAhmcmFuZG9tUDDsFX8yX4sFhj6302Ik5yFxZWxlbWVudElkZW50aWZpZXJocG9ydHJhaXRsZWxlbWVudFZhbHVljxiNGLYYeRhvGO4YMhh4GF4YNhhvGHENGPEMDNgYWOKkaGRpZ2VzdElECWZyYW5kb21QGlMHK7qtBEHJewJS04ima3FlbGVtZW50SWRlbnRpZmllcnJkcml2aW5nX3ByaXZpbGVnZXNsZWxlbWVudFZhbHVlgqN1dmVoaWNsZV9jYXRlZ29yeV9jb2RlYUFqaXNzdWVfZGF0ZWoyMDE4LTA4LTA5a2V4cGlyeV9kYXRlajIwMjQtMTAtMjCjdXZlaGljbGVfY2F0ZWdvcnlfY29kZWFCamlzc3VlX2RhdGVqMjAxNy0wMi0yM2tleHBpcnlfZGF0ZWoyMDI0LTEwLTIw2BhYXKRoZGlnZXN0SUQKZnJhbmRvbVAtuF4wvBZRC28CXg0VSL1ecWVsZW1lbnRJZGVudGlmaWVydnVuX2Rpc3Rpbmd1aXNoaW5nX3NpZ25sZWxlbWVudFZhbHVlYkFUamlzc3VlckF1dGiEQ6EBJqEYIVkCDTCCAgkwggGwoAMCAQICFH6sogKyWaF-zOtf-O91AFYtv1KYMAoGCCqGSM49BAMCMCgxCzAJBgNVBAYTAkFUMRkwFwYDVQQDDBBXYWx0aWQgVGVzdCBJQUNBMB4XDTI1MDYwMjA2NDExM1oXDTI2MDkwMjA2NDExM1owMzELMAkGA1UEBhMCQVQxJDAiBgNVBAMMG1dhbHRpZCBUZXN0IERvY3VtZW50IFNpZ25lcjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABD86enlUgHVxEagKfKvDrgxIZdiCxgGqGkF0ydrA9oOV6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZqjgawwgakwHwYDVR0jBBgwFoAU8Qp9p1jKxO9Kl2-teFNeAcHmNmswHQYDVR0OBBYEFMeapDiwuJaZdcaWGRphfRy8badIMA4GA1UdDwEB_wQEAwIHgDAaBgNVHRIEEzARhg9odHRwczovL3dhbHQuaWQwFQYDVR0lAQH_BAswCQYHKIGMXQUBAjAkBgNVHR8EHTAbMBmgF6AVhhNodHRwczovL3dhbHQuaWQvY3JsMAoGCCqGSM49BAMCA0cAMEQCIB02qd3OsglDYQ1X2VgTzCo_XQlmW6zBNN5IfTSU28NRAiAL1b7hpZfTttXkdekpCRxwANaT3KL4XE7xfX8_XHOujFkC29gYWQLWpmd2ZXJzaW9uYzEuMG9kaWdlc3RBbGdvcml0aG1nU0hBLTI1Nmx2YWx1ZURpZ2VzdHOhcW9yZy5pc28uMTgwMTMuNS4xqwBYIEtIndBMMAw9KxA55be0DQ3pcPPZu7SInKQ0rljx4cwmAVggjPDWEccjSXuAYTsrZ-8BVpw8wBk2RBj4Yee7_MfTSE0CWCDlbIWRitEZx0iICEEUBa9ohvcbqKt5eiFd2MXL_WV5VANYICcwGzNyjJx9ouok7JnHJVXsOZ_y-zC0mM7hrbd7XUbVBFggCVpr5S4jjyThoD4Yv_zlyhl-b2hUbbHAf1FzoG_gim8FWCAP80J377LC1Lj7WKUSez9VErNI8Hxa-RaruKQwLwPmwgZYIKYYEchq20ris0NJFMA5N6vhrQIjO3sxmoS7uhJo2s2SB1gg0id5Zuo59GLZBSq6CyXsJTXN5DKg_tZqBL82kxQ4FsEIWCC42t4cx79_IR4kz09M2TL8iTs9VcRGoekEcjvedajoaglYIJ764cxz69mngNkGfA5k_k5AL-fYsd631ehlVKV4uyVRClgg0cGo48LQL3FYSfB0M_bJIOdIMuQ1MtMJ8pzY4w2L4u9tZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCA4BRdUHQZPZDH-0ECIEFko5bCuXS5nejmCG6dHw2T3RSJYIJP8cJCTiHdT10i1GLOamFWpt4TRf1hMSQ56tBuKitlGZ2RvY1R5cGV1b3JnLmlzby4xODAxMy41LjEubURMbHZhbGlkaXR5SW5mb6Nmc2lnbmVkwHgeMjAyNS0wNy0wOVQwODozMTozMi42NzY4Nzg3NDZaaXZhbGlkRnJvbcB4HjIwMjUtMDctMDlUMDg6MzE6MzIuNjc2ODc5MTQ3Wmp2YWxpZFVudGlswHgeMjAyNi0wNy0wOVQwODozMTozMi42NzY4NzkxOTdaWEAoBqCuKqn9NE_oux0vreYWrQwSGI_dxKAdrcEzBpq1NvHSna5L2QGhhKnDolkijZfriL_caTeyxv_U36N3ErQebGRldmljZVNpZ25lZKJqbmFtZVNwYWNlc9gYQaBqZGV2aWNlQXV0aKFvZGV2aWNlU2lnbmF0dXJlhEOhASahGCGA9lhAfCDHYoPNapcJk--34zhLYb-I2L6RLbFwmygNN8Nx4gCdecNGeW8n9b-gOuCa5eB2-IMTIlLNTW99IZ4g3suKUGZzdGF0dXMA",
                        "decoded": {
                            "version": "1.0",
                            "status": 0,
                            "documents": [
                                {
                                    "docType": "org.iso.18013.5.1.mDL",
                                    "issuerSigned": {
                                        "nameSpaces": {
                                            "org.iso.18013.5.1": [
                                                {
                                                    "digestID": 0,
                                                    "random": [
                                                        47,
                                                        96,
                                                        -5,
                                                        57,
                                                        18,
                                                        -81,
                                                        78,
                                                        -60,
                                                        -47,
                                                        65,
                                                        -109,
                                                        -79,
                                                        -115,
                                                        13,
                                                        75,
                                                        -121
                                                    ],
                                                    "elementIdentifier": "family_name",
                                                    "elementValue": "Doe"
                                                },
                                                {
                                                    "digestID": 1,
                                                    "random": [
                                                        -3,
                                                        85,
                                                        4,
                                                        -65,
                                                        63,
                                                        63,
                                                        -30,
                                                        21,
                                                        -83,
                                                        61,
                                                        78,
                                                        -81,
                                                        -11,
                                                        -17,
                                                        2,
                                                        61
                                                    ],
                                                    "elementIdentifier": "given_name",
                                                    "elementValue": "John"
                                                },
                                                {
                                                    "digestID": 2,
                                                    "random": [
                                                        -67,
                                                        49,
                                                        47,
                                                        103,
                                                        70,
                                                        86,
                                                        100,
                                                        -48,
                                                        37,
                                                        -101,
                                                        -96,
                                                        30,
                                                        107,
                                                        -43,
                                                        -12,
                                                        108
                                                    ],
                                                    "elementIdentifier": "birth_date",
                                                    "elementValue": "1986-03-22"
                                                },
                                                {
                                                    "digestID": 3,
                                                    "random": [
                                                        -1,
                                                        -99,
                                                        65,
                                                        11,
                                                        82,
                                                        10,
                                                        -117,
                                                        -14,
                                                        -33,
                                                        -5,
                                                        78,
                                                        9,
                                                        -86,
                                                        18,
                                                        -107,
                                                        -25
                                                    ],
                                                    "elementIdentifier": "issue_date",
                                                    "elementValue": "2019-10-20"
                                                },
                                                {
                                                    "digestID": 4,
                                                    "random": [
                                                        126,
                                                        119,
                                                        -98,
                                                        -60,
                                                        -85,
                                                        63,
                                                        117,
                                                        122,
                                                        -23,
                                                        31,
                                                        -40,
                                                        -113,
                                                        -40,
                                                        1,
                                                        -19,
                                                        -79
                                                    ],
                                                    "elementIdentifier": "expiry_date",
                                                    "elementValue": "2024-10-20"
                                                },
                                                {
                                                    "digestID": 5,
                                                    "random": [
                                                        37,
                                                        -27,
                                                        70,
                                                        90,
                                                        61,
                                                        40,
                                                        -116,
                                                        93,
                                                        35,
                                                        -75,
                                                        52,
                                                        -122,
                                                        32,
                                                        52,
                                                        -16,
                                                        107
                                                    ],
                                                    "elementIdentifier": "issuing_country",
                                                    "elementValue": "AT"
                                                },
                                                {
                                                    "digestID": 6,
                                                    "random": [
                                                        -75,
                                                        99,
                                                        21,
                                                        16,
                                                        105,
                                                        -47,
                                                        -26,
                                                        -83,
                                                        53,
                                                        -36,
                                                        122,
                                                        67,
                                                        -59,
                                                        90,
                                                        124,
                                                        -5
                                                    ],
                                                    "elementIdentifier": "issuing_authority",
                                                    "elementValue": "AT DMV"
                                                },
                                                {
                                                    "digestID": 7,
                                                    "random": [
                                                        -101,
                                                        21,
                                                        -81,
                                                        119,
                                                        98,
                                                        -116,
                                                        89,
                                                        -9,
                                                        -118,
                                                        68,
                                                        88,
                                                        125,
                                                        53,
                                                        5,
                                                        -111,
                                                        -12
                                                    ],
                                                    "elementIdentifier": "document_number",
                                                    "elementValue": 123456789
                                                },
                                                {
                                                    "digestID": 8,
                                                    "random": [
                                                        48,
                                                        -20,
                                                        21,
                                                        127,
                                                        50,
                                                        95,
                                                        -117,
                                                        5,
                                                        -122,
                                                        62,
                                                        -73,
                                                        -45,
                                                        98,
                                                        36,
                                                        -25,
                                                        33
                                                    ],
                                                    "elementIdentifier": "portrait",
                                                    "elementValue": [
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
                                                    ]
                                                },
                                                {
                                                    "digestID": 9,
                                                    "random": [
                                                        26,
                                                        83,
                                                        7,
                                                        43,
                                                        -70,
                                                        -83,
                                                        4,
                                                        65,
                                                        -55,
                                                        123,
                                                        2,
                                                        82,
                                                        -45,
                                                        -120,
                                                        -90,
                                                        107
                                                    ],
                                                    "elementIdentifier": "driving_privileges",
                                                    "elementValue": [
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
                                                    ]
                                                },
                                                {
                                                    "digestID": 10,
                                                    "random": [
                                                        45,
                                                        -72,
                                                        94,
                                                        48,
                                                        -68,
                                                        22,
                                                        81,
                                                        11,
                                                        111,
                                                        2,
                                                        94,
                                                        13,
                                                        21,
                                                        72,
                                                        -67,
                                                        94
                                                    ],
                                                    "elementIdentifier": "un_distinguishing_sign",
                                                    "elementValue": "AT"
                                                }
                                            ]
                                        },
                                        "issuerAuth": {
                                            "x5c": [
                                                "-----BEGIN CERTIFICATE-----\nMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M\n-----END CERTIFICATE-----"
                                            ],
                                            "mso": {
                                                "docType": "org.iso.18013.5.1.mDL",
                                                "version": "1.0",
                                                "digestAlgorithm": "SHA-256",
                                                "valueDigests": {
                                                    "org.iso.18013.5.1": {
                                                        "0": [
                                                            75,
                                                            72,
                                                            -99,
                                                            -48,
                                                            76,
                                                            48,
                                                            12,
                                                            61,
                                                            43,
                                                            16,
                                                            57,
                                                            -27,
                                                            -73,
                                                            -76,
                                                            13,
                                                            13,
                                                            -23,
                                                            112,
                                                            -13,
                                                            -39,
                                                            -69,
                                                            -76,
                                                            -120,
                                                            -100,
                                                            -92,
                                                            52,
                                                            -82,
                                                            88,
                                                            -15,
                                                            -31,
                                                            -52,
                                                            38
                                                        ],
                                                        "1": [
                                                            -116,
                                                            -16,
                                                            -42,
                                                            17,
                                                            -57,
                                                            35,
                                                            73,
                                                            123,
                                                            -128,
                                                            97,
                                                            59,
                                                            43,
                                                            103,
                                                            -17,
                                                            1,
                                                            86,
                                                            -100,
                                                            60,
                                                            -64,
                                                            25,
                                                            54,
                                                            68,
                                                            24,
                                                            -8,
                                                            97,
                                                            -25,
                                                            -69,
                                                            -4,
                                                            -57,
                                                            -45,
                                                            72,
                                                            77
                                                        ],
                                                        "2": [
                                                            -27,
                                                            108,
                                                            -123,
                                                            -111,
                                                            -118,
                                                            -47,
                                                            25,
                                                            -57,
                                                            72,
                                                            -120,
                                                            8,
                                                            65,
                                                            20,
                                                            5,
                                                            -81,
                                                            104,
                                                            -122,
                                                            -9,
                                                            27,
                                                            -88,
                                                            -85,
                                                            121,
                                                            122,
                                                            33,
                                                            93,
                                                            -40,
                                                            -59,
                                                            -53,
                                                            -3,
                                                            101,
                                                            121,
                                                            84
                                                        ],
                                                        "3": [
                                                            39,
                                                            48,
                                                            27,
                                                            51,
                                                            114,
                                                            -116,
                                                            -100,
                                                            125,
                                                            -94,
                                                            -22,
                                                            36,
                                                            -20,
                                                            -103,
                                                            -57,
                                                            37,
                                                            85,
                                                            -20,
                                                            57,
                                                            -97,
                                                            -14,
                                                            -5,
                                                            48,
                                                            -76,
                                                            -104,
                                                            -50,
                                                            -31,
                                                            -83,
                                                            -73,
                                                            123,
                                                            93,
                                                            70,
                                                            -43
                                                        ],
                                                        "4": [
                                                            9,
                                                            90,
                                                            107,
                                                            -27,
                                                            46,
                                                            35,
                                                            -113,
                                                            36,
                                                            -31,
                                                            -96,
                                                            62,
                                                            24,
                                                            -65,
                                                            -4,
                                                            -27,
                                                            -54,
                                                            25,
                                                            126,
                                                            111,
                                                            104,
                                                            84,
                                                            109,
                                                            -79,
                                                            -64,
                                                            127,
                                                            81,
                                                            115,
                                                            -96,
                                                            111,
                                                            -32,
                                                            -118,
                                                            111
                                                        ],
                                                        "5": [
                                                            15,
                                                            -13,
                                                            66,
                                                            119,
                                                            -17,
                                                            -78,
                                                            -62,
                                                            -44,
                                                            -72,
                                                            -5,
                                                            88,
                                                            -91,
                                                            18,
                                                            123,
                                                            63,
                                                            85,
                                                            18,
                                                            -77,
                                                            72,
                                                            -16,
                                                            124,
                                                            90,
                                                            -7,
                                                            22,
                                                            -85,
                                                            -72,
                                                            -92,
                                                            48,
                                                            47,
                                                            3,
                                                            -26,
                                                            -62
                                                        ],
                                                        "6": [
                                                            -90,
                                                            24,
                                                            17,
                                                            -56,
                                                            106,
                                                            -37,
                                                            74,
                                                            -30,
                                                            -77,
                                                            67,
                                                            73,
                                                            20,
                                                            -64,
                                                            57,
                                                            55,
                                                            -85,
                                                            -31,
                                                            -83,
                                                            2,
                                                            35,
                                                            59,
                                                            123,
                                                            49,
                                                            -102,
                                                            -124,
                                                            -69,
                                                            -70,
                                                            18,
                                                            104,
                                                            -38,
                                                            -51,
                                                            -110
                                                        ],
                                                        "7": [
                                                            -46,
                                                            39,
                                                            121,
                                                            102,
                                                            -22,
                                                            57,
                                                            -12,
                                                            98,
                                                            -39,
                                                            5,
                                                            42,
                                                            -70,
                                                            11,
                                                            37,
                                                            -20,
                                                            37,
                                                            53,
                                                            -51,
                                                            -28,
                                                            50,
                                                            -96,
                                                            -2,
                                                            -42,
                                                            106,
                                                            4,
                                                            -65,
                                                            54,
                                                            -109,
                                                            20,
                                                            56,
                                                            22,
                                                            -63
                                                        ],
                                                        "8": [
                                                            -72,
                                                            -38,
                                                            -34,
                                                            28,
                                                            -57,
                                                            -65,
                                                            127,
                                                            33,
                                                            30,
                                                            36,
                                                            -49,
                                                            79,
                                                            76,
                                                            -39,
                                                            50,
                                                            -4,
                                                            -119,
                                                            59,
                                                            61,
                                                            85,
                                                            -60,
                                                            70,
                                                            -95,
                                                            -23,
                                                            4,
                                                            114,
                                                            59,
                                                            -34,
                                                            117,
                                                            -88,
                                                            -24,
                                                            106
                                                        ],
                                                        "9": [
                                                            -98,
                                                            -6,
                                                            -31,
                                                            -52,
                                                            115,
                                                            -21,
                                                            -39,
                                                            -89,
                                                            -128,
                                                            -39,
                                                            6,
                                                            124,
                                                            14,
                                                            100,
                                                            -2,
                                                            78,
                                                            64,
                                                            47,
                                                            -25,
                                                            -40,
                                                            -79,
                                                            -34,
                                                            -73,
                                                            -43,
                                                            -24,
                                                            101,
                                                            84,
                                                            -91,
                                                            120,
                                                            -69,
                                                            37,
                                                            81
                                                        ],
                                                        "10": [
                                                            -47,
                                                            -63,
                                                            -88,
                                                            -29,
                                                            -62,
                                                            -48,
                                                            47,
                                                            113,
                                                            88,
                                                            73,
                                                            -16,
                                                            116,
                                                            51,
                                                            -10,
                                                            -55,
                                                            32,
                                                            -25,
                                                            72,
                                                            50,
                                                            -28,
                                                            53,
                                                            50,
                                                            -45,
                                                            9,
                                                            -14,
                                                            -100,
                                                            -40,
                                                            -29,
                                                            13,
                                                            -117,
                                                            -30,
                                                            -17
                                                        ]
                                                    }
                                                },
                                                "validityInfo": {
                                                    "signed": 1752049892,
                                                    "validFrom": 1752049892,
                                                    "validUntil": 1783585892
                                                },
                                                "deviceKeyInfo": {
                                                    "deviceKey": {
                                                        "kty": "EC",
                                                        "crv": "P-256",
                                                        "x": "OAUXVB0GT2Qx_tBAiBBZKOWwrl0uZ3o5ghunR8Nk90U",
                                                        "y": "k_xwkJOId1PXSLUYs5qYVam3hNF_WExJDnq0G4qK2UY"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            ]
                        }
                    }
                ]
            }
        }
    """.trimIndent()
    )
}