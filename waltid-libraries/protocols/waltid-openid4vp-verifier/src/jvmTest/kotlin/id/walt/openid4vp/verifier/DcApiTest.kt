package id.walt.openid4vp.verifier

import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.handlers.vpresponse.Verifier2VPDirectPostHandler
import id.walt.openid4vp.verifier.handlers.vpresponse.Verifier2VPDirectPostHandler.DcApiJsonDirectPostResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test

class DcApiTest {

    // language=JSON
    val request1 = """
    {
      "id": "610965bb-3b17-4e70-90c7-45bd4c26b282",
      "creationDate": "2025-11-25T10:06:42.799351130Z",
      "expirationDate": "2025-11-25T10:11:42.799351130Z",
      "retentionDate": "2035-11-25T10:06:42.799351130Z",
      "status": "UNUSED",
      "attempted": false,
      "reattemptable": true,
      "authorizationRequest": {
        "response_type": "vp_token",
        "client_id": "x509_hash:abc-xyz-base64url-sha256-hash-of-der-x509-leaf",
        "response_mode": "dc_api",
        "nonce": "b9cc2837-fbe2-4c2c-a047-750071aa0063",
        "dcql_query": {
          "credentials": [
            {
              "id": "my_mdl",
              "format": "mso_mdoc",
              "multiple": false,
              "meta": {
                "doctype_value": "org.iso.18013.5.1.mDL",
                "format": "mso_mdoc"
              },
              "require_cryptographic_holder_binding": true,
              "claims": [
                {
                  "path": [
                    "org.iso.18013.5.1",
                    "family_name"
                  ]
                },
                {
                  "path": [
                    "org.iso.18013.5.1",
                    "given_name"
                  ]
                },
                {
                  "path": [
                    "org.iso.18013.5.1",
                    "age_over_21"
                  ]
                }
              ]
            }
          ]
        },
        "client_metadata": {},
        "expected_origins": [
          "https://digital-credentials.walt.id"
        ]
      },
      "authorizationRequestUrl": "https://digital-credentials.walt.id?response_type=vp_token&client_id=x509_hash%3Aabc-xyz-base64url-sha256-hash-of-der-x509-leaf&state=9f0a99ba-68d8-4de3-92b3-6f236d9a1586&response_mode=dc_api&nonce=b9cc2837-fbe2-4c2c-a047-750071aa0063&dcql_query=%7B%22credentials%22%3A%5B%7B%22id%22%3A%22my_mdl%22%2C%22format%22%3A%22mso_mdoc%22%2C%22meta%22%3A%7B%22doctype_value%22%3A%22org.iso.18013.5.1.mDL%22%7D%2C%22claims%22%3A%5B%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22family_name%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22given_name%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22age_over_21%22%5D%7D%5D%7D%5D%7D&client_metadata=%7B%7D&expected_origins=%5B%22https%3A%2F%2Fdigital-credentials.walt.id%22%5D",
      "signedAuthorizationRequestJwt": "eyJ0eXAiOiJvYXV0aC1hdXRoei1yZXErand0IiwiZXhwIjoxNzY0MDY1NTAyLCJpYXQiOjE3NjQwNjUyMDIsImFsZyI6IkVTMjU2IiwieDVjIjpbIk1JSUJWekNCL2FBREFnRUNBZ2dOS1pBdlVydGltekFLQmdncWhrak9QUVFEQWpBZk1SMHdHd1lEVlFRRERCUjJaWEpwWm1sbGNpNWxlR0Z0Y0d4bExtTnZiVEFlRncweU5URXdNVFF3TmpJME1qQmFGdzB5TmpFd01UUXdOakkwTWpCYU1COHhIVEFiQmdOVkJBTU1GSFpsY21sbWFXVnlMbVY0WVcxd2JHVXVZMjl0TUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFRy9UZ0JjMEJrbU1pcGlRLzZna2FtSW4zbW1wN2hjVHJadXlyTFRta25QMVdSRXhsMWRoZEl4OS9rQWt1dWNlSTNUSGt4WHE3L3krc0J6SzBaUjdqUHFNak1DRXdId1lEVlIwUkJCZ3dGb0lVZG1WeWFXWnBaWEl1WlhoaGJYQnNaUzVqYjIwd0NnWUlLb1pJemowRUF3SURTUUF3UmdJaEFPdTBSR002QmpWUVVlcGVMQm9ndytaRDNNUTl2RnBwYlBJR01QanRuL3FkQWlFQXR0ZmRmeVhIZnpKMnRyK1Bjenlja3p2M05sTTQzNDYxY3ZQOTZzSXpPUUE9Il19.eyJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJjbGllbnRfaWQiOiJ4NTA5X2hhc2g6YWJjLXh5ei1iYXNlNjR1cmwtc2hhMjU2LWhhc2gtb2YtZGVyLXg1MDktbGVhZiIsInN0YXRlIjoiOWYwYTk5YmEtNjhkOC00ZGUzLTkyYjMtNmYyMzZkOWExNTg2IiwicmVzcG9uc2VfbW9kZSI6ImRjX2FwaSIsIm5vbmNlIjoiYjljYzI4MzctZmJlMi00YzJjLWEwNDctNzUwMDcxYWEwMDYzIiwiZGNxbF9xdWVyeSI6eyJjcmVkZW50aWFscyI6W3siaWQiOiJteV9tZGwiLCJmb3JtYXQiOiJtc29fbWRvYyIsIm1ldGEiOnsiZG9jdHlwZV92YWx1ZSI6Im9yZy5pc28uMTgwMTMuNS4xLm1ETCJ9LCJjbGFpbXMiOlt7InBhdGgiOlsib3JnLmlzby4xODAxMy41LjEiLCJmYW1pbHlfbmFtZSJdfSx7InBhdGgiOlsib3JnLmlzby4xODAxMy41LjEiLCJnaXZlbl9uYW1lIl19LHsicGF0aCI6WyJvcmcuaXNvLjE4MDEzLjUuMSIsImFnZV9vdmVyXzIxIl19XX1dfSwiY2xpZW50X21ldGFkYXRhIjp7fSwiZXhwZWN0ZWRfb3JpZ2lucyI6WyJodHRwczovL2RpZ2l0YWwtY3JlZGVudGlhbHMud2FsdC5pZCJdfQ.W3DkFQwBfsb7qcuDiCr9G4Po0BSSsVgIuevsYPsCz0aFKOriq1KepLAnFb2VxZdDiMraxrvjbTbNBFqSP6JBvA",
      "requestMode": "REQUEST_URI_SIGNED",
      "policies": {
        "vp_policies": {
          "jwt_vc_json": [
            "jwt_vc_json/audience-check",
            "jwt_vc_json/nonce-check",
            "jwt_vc_json/envelope_signature"
          ],
          "dc+sd-jwt": [
            "dc+sd-jwt/audience-check",
            "dc+sd-jwt/kb-jwt_signature",
            "dc+sd-jwt/nonce-check",
            "dc+sd-jwt/sd_hash-check"
          ],
          "mso_mdoc": [
            "mso_mdoc/device-auth",
            "mso_mdoc/device_key_auth",
            "mso_mdoc/issuer_auth",
            "mso_mdoc/issuer_signed_integrity",
            "mso_mdoc/mso"
          ]
        },
        "vc_policies": [
          {
            "policy": "signature",
            "id": "signature"
          }
        ],
        "specific_vc_policies": {}
      }
    }
    """.trimIndent()

    // language=JSON
    val response1 = Json.decodeFromString<JsonObject>(
        """
    {
      "protocol": "openid4vp-v1-signed",
      "data": {
        "vp_token": {
          "my_mdl": [
            "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGxpc3N1ZXJTaWduZWSiam5hbWVTcGFjZXOhcW9yZy5pc28uMTgwMTMuNS4xg9gYWFSkaGRpZ2VzdElEAGZyYW5kb21QEloNL96oPMkJmnXv9ESbW3FlbGVtZW50SWRlbnRpZmllcmtmYW1pbHlfbmFtZWxlbGVtZW50VmFsdWVlU21pdGjYGFhRpGhkaWdlc3RJRAFmcmFuZG9tUKQwy6zsJN0wZkMPQxjbyWJxZWxlbWVudElkZW50aWZpZXJqZ2l2ZW5fbmFtZWxlbGVtZW50VmFsdWVjSm9u2BhYT6RoZGlnZXN0SUQCZnJhbmRvbVBfiTVUPq1IQvzu0cugg7b9cWVsZW1lbnRJZGVudGlmaWVya2FnZV9vdmVyXzIxbGVsZW1lbnRWYWx1ZfVqaXNzdWVyQXV0aIRDoQEmoRghWQLEMIICwDCCAmegAwIBAgIUHn8bMq1PNO_ksMwHt7DjM6cLGE0wCgYIKoZIzj0EAwIweTELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFjAUBgNVBAcMDU1vdW50YWluIFZpZXcxHDAaBgNVBAoME0RpZ2l0YWwgQ3JlZGVudGlhbHMxHzAdBgNVBAMMFmRpZ2l0YWxjcmVkZW50aWFscy5kZXYwHhcNMjUwMjE5MjMzMDE4WhcNMjYwMjE5MjMzMDE4WjB5MQswCQYDVQQGEwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzEcMBoGA1UECgwTRGlnaXRhbCBDcmVkZW50aWFsczEfMB0GA1UEAwwWZGlnaXRhbGNyZWRlbnRpYWxzLmRldjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOt5Nivi1_OXw1AEfYPh42Is41VrNg9qaMdYuw3cavhsCa-aXV0NmTl2EsNaJ5GWmMoAD8ikwAFszYhIeNgF42mjgcwwgckwHwYDVR0jBBgwFoAUok_0idl8Ruhuo4bZR0jOzL7cz_UwHQYDVR0OBBYEFN_-aloS6cBixLyYpyXS2XD3emAoMDQGA1UdHwQtMCswKaAnoCWGI2h0dHBzOi8vZGlnaXRhbC1jcmVkZW50aWFscy5kZXYvY3JsMCoGA1UdEgQjMCGGH2h0dHBzOi8vZGlnaXRhbC1jcmVkZW50aWFscy5kZXYwDgYDVR0PAQH_BAQDAgeAMBUGA1UdJQEB_wQLMAkGByiBjF0FAQIwCgYIKoZIzj0EAwIDRwAwRAIgYcXL9XzB43vy4LEz2h8gMQRdcJtaIRQOemgwm8sHQucCIHCvouHEm_unjBXMCeUZ7QR_ympjGyHITw25_B9H9QsCWQOk2BhZA5-mZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2Z2RvY1R5cGV1b3JnLmlzby4xODAxMy41LjEubURMbHZhbHVlRGlnZXN0c6Fxb3JnLmlzby4xODAxMy41LjGxAFggng2tWJR7fp49froXSRnsklR_sI-cX_vtNAgkCpdH7KcBWCC0FPEWRP0z6_Rt-ttqzJN5g6hoLJ3nrVljFBcO7RybfwJYIO6B7hZWOTAt0Kz_o7zCJclTcb6SJr_404PWx8RAzjN_A1ggeLRVnN98xkCw3ysIv4PDTCAhZkTqgDSVi-jkN9poMYUEWCDowY3mzymtYR69jFIoHo-NrNdCCxD9k9OogqIHrU3m0QVYILN8AZMmJ9Qq9bwUbsVAQVUP-QtoPmlYdIBTmXJNbr1oBlggzqXSfF8t4hF5hU5wGmxZa0he4VTpAQOCeUTNKP5rA5wHWCATqvZD_qvPdBVcFyUxmIRAK3CgDFrAdzfLMkkRyIIDzAhYIKzeNnZGdWcMC2mXpck7pWMHiVOMDGqSA5M_1lfqO7-wCVggVOyCZq3s73xJMoLhL4b1zoa_hV_twW4xUtyyoWm98nMKWCAzTGC3kG1f25U5RLQUOPNZ1fslnEvfMeLrRXgj19yFxgtYII0QK_PhhkC_VYFaCysXkdtwBGJb6Z3I8fFufwgIGo2NDFggQHxV4YQJLdbpxcfZiPIuocbCZDozc1f0fe9m4-lwVnENWCCY0f4Pabks2V2efyNUpA-Bc0qSG88o0gYgg6mZ-d48ww5YIHDHCfY18n5O6_740xNe_5HMn7D0jHFFymsk5FCNRSQ2D1gg4Qu7JMWCmI1bO1L8kN59jpJyZ9QXNgsG2p679UzAomAQWCAbGPycoabYvTGW6hX0onS3jiCLdi874pBb-hzB1STJ4W1kZXZpY2VLZXlJbmZvoWlkZXZpY2VLZXmkAQIgASFYIEgd6IsLll1JJy7WL7pRu_fcGKaJ8xCKt6klhXBcQJ9PIlggni7iZtdNwuuORu3f_5AeypnVHIu_U5rm4FtrGy-YyIJsdmFsaWRpdHlJbmZvo2ZzaWduZWTAeBsyMDI1LTA5LTI5VDAwOjE5OjUzLjYwODQ3M1ppdmFsaWRGcm9twHgbMjAyNS0wOS0yOVQwMDoxOTo1My42MDg0OTFaanZhbGlkVW50aWzAeBsyMDM1LTA5LTE3VDAwOjE5OjUzLjYwODQ5MlpYQCm_oEaEsbGd7vTDkT1DyLfJ8AYw4AVYYH5mvypCWe09qV2-iLNWF_Q5QMUwOotaJrblStdSZQK-HQydqYXumaxsZGV2aWNlU2lnbmVkompuYW1lU3BhY2Vz2BhBoGpkZXZpY2VBdXRooW9kZXZpY2VTaWduYXR1cmWEQ6EBJqD2WEDUIjLF0cRHm_nuj7M_8odnkqlu8VazITigyatdoN4CciMvQZA2_ncSXvXIplss7its81MZuCB6gSXPM8SFz2EWZnN0YXR1cwA"
          ]
        }
      }
    }
    """.trimIndent()
    )

    @Test
    fun testDcApi1() = runTest {
        val verificationSession = Json.decodeFromString<Verification2Session>(request1)

        Verifier2VPDirectPostHandler.handleDirectPost(
            verificationSession = verificationSession,
            responseData = DcApiJsonDirectPostResponse(response1),

            updateSessionCallback = { session, event, sessionBlock ->
                println(">> Called callback for update session due to $event: $session")
            },
            failSessionCallback = { session, event, _ ->
                println(">> Called callback for fail session due to $event: $session")
            }
        )
    }

}
