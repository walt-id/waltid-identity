package id.walt.credentials.examples

import id.walt.credentials.formats.AbstractW3C
import kotlinx.serialization.json.Json

object W3CExamples {
    //language=JSON
    val w3cCredential = """
        {
          "@context": [
            "https://www.w3.org/ns/credentials/v2",
            "https://www.w3.org/ns/credentials/examples/v2"
          ],
          "id": "http://university.example/credentials/3732",
          "type": ["VerifiableCredential", "ExampleDegreeCredential"],
          "issuer": "https://university.example/issuers/565049",
          "validFrom": "2010-01-01T00:00:00Z",
          "credentialSubject": {
            "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
            "degree": {
              "type": "ExampleBachelorDegree",
              "name": "Bachelor of Arts"
            }
          }
        }
    """.trimIndent()
    val w3cCredentialValues = mapOf(
        "issuer" to "https://university.example/issuers/565049",
        "subject" to "did:example:ebfeb1f712ebc6f1c276e12ec21"
    )

    // application/vc
    //language=JSON
    val dipEcdsaSignedW3CCredential = """
        {
          "@context": [
            "https://www.w3.org/ns/credentials/v2",
            "https://www.w3.org/ns/credentials/examples/v2"
          ],
          "id": "http://university.example/credentials/3732",
          "type": [
            "VerifiableCredential",
            "ExampleDegreeCredential"
          ],
          "issuer": "https://university.example/issuers/565049",
          "validFrom": "2010-01-01T00:00:00Z",
          "credentialSubject": {
            "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
            "degree": {
              "type": "ExampleBachelorDegree",
              "name": "Bachelor of Science and Arts"
            }
          },
          "proof": {
            "type": "DataIntegrityProof",
            "created": "2025-02-25T01:47:38Z",
            "verificationMethod": "did:key:zDnaehK1RvsyuH7E1qSEp17iUwzYUvMnaz4vXTtXunvBzNZNk",
            "cryptosuite": "ecdsa-rdfc-2019",
            "proofPurpose": "assertionMethod",
            "proofValue": "zjV78p6RWm6uZCoFyx4D5xLg5GMdEnhtrG4LJU5PH6Tf3kHAHoSmqvXNipsZbRtV1KuCwt83zr9qLjX3zuNkHCwW"
          }
        }
    """.trimIndent()
    val dipEcdsaSignedW3CCredentialValues = mapOf(
        "issuer" to "https://university.example/issuers/565049",
        "subject" to "did:example:ebfeb1f712ebc6f1c276e12ec21"
    )

    // application/vc
    //language=JSON
    val dipEddsaSignedW3CCredential = """
        {
          "@context": [
            "https://www.w3.org/ns/credentials/v2",
            "https://www.w3.org/ns/credentials/examples/v2"
          ],
          "id": "http://university.example/credentials/3732",
          "type": [
            "VerifiableCredential",
            "ExampleDegreeCredential"
          ],
          "issuer": "https://university.example/issuers/565049",
          "validFrom": "2010-01-01T00:00:00Z",
          "credentialSubject": {
            "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
            "degree": {
              "type": "ExampleBachelorDegree",
              "name": "Bachelor of Science and Arts"
            }
          },
          "proof": {
            "type": "DataIntegrityProof",
            "created": "2025-02-25T01:47:38Z",
            "verificationMethod": "did:key:z6MkkZwCZavHWfRQAu9WbrDGJrSyxQV6Y3v44GdJeX8X3Vtu",
            "cryptosuite": "eddsa-rdfc-2022",
            "proofPurpose": "assertionMethod",
            "proofValue": "z2SLLvvKkaiM23MphyzXZ3AMiWRHop7VJR8mtWDnH2YUC6K33QFE3rxaJhuLrdAfCVFAiajRY3FyiDRqKmTM2C8rk"
          }
        }
    """.trimIndent()
    val dipEddsaSignedW3CCredentialValues = mapOf(
        "issuer" to "https://university.example/issuers/565049",
        "subject" to "did:example:ebfeb1f712ebc6f1c276e12ec21"
    )

    // application/vc
    //language=JSON
    val dipEcdsaSdSignedW3CCredential = """
        {
          "@context": [
            "https://www.w3.org/ns/credentials/v2",
            "https://www.w3.org/ns/credentials/examples/v2"
          ],
          "id": "http://university.example/credentials/3732",
          "type": [
            "VerifiableCredential",
            "ExampleDegreeCredential"
          ],
          "issuer": "https://university.example/issuers/565049",
          "validFrom": "2010-01-01T00:00:00Z",
          "credentialSubject": {
            "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
            "degree": {
              "type": "ExampleBachelorDegree",
              "name": "Bachelor of Science and Arts"
            }
          },
          "proof": {
            "type": "DataIntegrityProof",
            "created": "2025-02-25T01:47:38Z",
            "verificationMethod": "did:key:zDnaebicRP8aW7WnNkaGHGzkDYxfKCPUt7HZoApNuK7oujVb6",
            "cryptosuite": "ecdsa-sd-2023",
            "proofPurpose": "assertionMethod",
            "proofValue": "u2V0AhVhAS12OevQhqdbbEziQ1Wip3RxIiqmGGlFeXeAsDty6iVxMUYAggXKJcTBFUUg4Y-9x5xzQIDvwPfubgbuqdY1WUVgjgCQDWZSts_2-amKm0AgVHwQZGm8UAU_g5B1auCaZKXXLXz5YIE1yeLHN6kPlHYT0uheFfoeVYeix6LO9nOcxDJuE3HS3hVhAOyy2BS2S8idjuuJsJ_SNk49SB9_MNYU65HlWcQn9L5UT8mtGkzDmJnvSuUFciAEwFiIvIIHZT5vhlZPh05Q2lFhAR4jiF5sVuxk7rGZg0Ln01iSEVjGnikPqPP0myHYX0d1iU7VIxLgCZ7d5PBC3OOJDFlL4DZSmG2N-A0r_v2NqHlhAy9Zi_6GntrZqP5h-DvaSNojdDMg2zYA9OMyOfBCdbRU0uR4np5IoTs0ianIGylcInbTEyEpdvvl0p017MxEATFhAU_mXVx5WK_bGNwExqmNNvXOTsz7-D-sVO_66pbqlIt9f0xC-L5p0T-UBIvDfJImAtwX6GEQmIqGwjMHwKPc3ylhAFtKzqq65D5o6bAlOm3xcJqYD_IXI9BvUSQeVENYAVeMuSUWv2wUkkoifO5MJL2OoiJiT6a1-nwR6JlE-PtGpwYFnL2lzc3Vlcg"
          }
        }
    """.trimIndent()
    val dipEcdsaSdSignedW3CCredentialValues = mapOf(
        "issuer" to "https://university.example/issuers/565049",
        "subject" to "did:example:ebfeb1f712ebc6f1c276e12ec21"
    )

    // application/vc
    //language=JSON
    val dipBbsSignedW3CCredential = """
        {
          "@context": [
            "https://www.w3.org/ns/credentials/v2",
            "https://www.w3.org/ns/credentials/examples/v2"
          ],
          "id": "http://university.example/credentials/3732",
          "type": [
            "VerifiableCredential",
            "ExampleDegreeCredential"
          ],
          "issuer": "https://university.example/issuers/565049",
          "validFrom": "2010-01-01T00:00:00Z",
          "credentialSubject": {
            "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
            "degree": {
              "type": "ExampleBachelorDegree",
              "name": "Bachelor of Science and Arts"
            }
          },
          "proof": {
            "type": "DataIntegrityProof",
            "verificationMethod": "did:key:zUC7Go51G8npR5NoC6nezg3qFo59VsL214zBurCJEyWp8ADY8Q4SVb64P4cBPKxDnAgQScHYfX44Qz3jU6dW8y974FUr9LG8hQ5A1zuBvYU276mzQDoGEQZmAjRQ89zhq34yoQq",
            "cryptosuite": "bbs-2023",
            "proofPurpose": "assertionMethod",
            "proofValue": "u2V0ChVhQqkCLI-OKmq2CmS913Cgty5UH_E-jLS-dsDhhvWH2KwQh5CPm4ljyuOQMsU3h3AfwOI_i4YMzYywVCu89xHz2TY-ftxP1FWX4TfCZV9sEpaxYQKIPylydjNOWM-rVmQ85NDHey_RND9gCFXQKlcj21GDfS1r7HKFDPyyrvPGqNF8bjgNELvoomOjpbD9JEvaGI1pYYK4N01rwFzDhhpSCTdypDTU_9jJNNTFV1tBYvbJqR-hEMB08LfGkh1jQ1kvP_zAEQQnoY7o9U820WvCN0Hr8MP0SEdU8gBBHAHgpBKIbkgU6hpJi_hOw85-Gp1bBFH0aDlggHQDQxT-x2GmzDJApHlWz2yP7DDsfVyqnmELikJvQ4XeBZy9pc3N1ZXI"
          }
        }
    """.trimIndent()
    val dipBbsSignedW3CCredentialValues = mapOf(
        "issuer" to "https://university.example/issuers/565049",
        "subject" to "did:example:ebfeb1f712ebc6f1c276e12ec21"
    )

    // application/vc-ld+jwt
    val joseSignedW3CCredential = """
        eyJraWQiOiJFeEhrQk1XOWZtYmt2VjI2Nm1ScHVQMnNVWV9OX0VXSU4xbGFwVXpPOHJvIiwiYWxnIjoiRVMyNTYifQ.eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvbnMvY3JlZGVudGlhbHMvdjIiLCJodHRwczovL3d3dy53My5vcmcvbnMvY3JlZGVudGlhbHMvZXhhbXBsZXMvdjIiXSwiaWQiOiJodHRwOi8vdW5pdmVyc2l0eS5leGFtcGxlL2NyZWRlbnRpYWxzLzM3MzIiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiRXhhbXBsZURlZ3JlZUNyZWRlbnRpYWwiXSwiaXNzdWVyIjoiaHR0cHM6Ly91bml2ZXJzaXR5LmV4YW1wbGUvaXNzdWVycy81NjUwNDkiLCJ2YWxpZEZyb20iOiIyMDEwLTAxLTAxVDAwOjAwOjAwWiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmV4YW1wbGU6ZWJmZWIxZjcxMmViYzZmMWMyNzZlMTJlYzIxIiwiZGVncmVlIjp7InR5cGUiOiJFeGFtcGxlQmFjaGVsb3JEZWdyZWUiLCJuYW1lIjoiQmFjaGVsb3Igb2YgU2NpZW5jZSBhbmQgQXJ0cyJ9fX0.MA98XcfFDHT1NbcrNqxKRokCHvyqh3sUutKf8Eo69wRK0Bagn_kMfda62RUpTCIWymNo4IJCLlDQyBnLLJ4GUQ
    """.trimIndent()
    val joseSignedW3CCredentialValues = mapOf(
        "issuer" to "https://university.example/issuers/565049",
        "subject" to "did:example:ebfeb1f712ebc6f1c276e12ec21"
    )

    //  application/vc-ld+cose
    val coseSignedW3CCredential = """
         d28444a1013822a05901be7b2240636f6e74657874223a5b2268747470733a2f2f7777772e77332e6f72672f6e732f63726564656e7469616c732f7632222c2268747470733a2f2f7777772e77332e6f72672f6e732f63726564656e7469616c732f6578616d706c65732f7632225d2c226964223a22687474703a2f2f756e69766572736974792e6578616d706c652f63726564656e7469616c732f33373332222c2274797065223a5b2256657269666961626c6543726564656e7469616c222c224578616d706c6544656772656543726564656e7469616c225d2c22697373756572223a2268747470733a2f2f756e69766572736974792e6578616d706c652f697373756572732f353635303439222c2276616c696446726f6d223a22323031302d30312d30315430303a30303a30305a222c2263726564656e7469616c5375626a656374223a7b226964223a226469643a6578616d706c653a656266656231663731326562633666316332373665313265633231222c22646567726565223a7b2274797065223a224578616d706c6542616368656c6f72446567726565222c226e616d65223a2242616368656c6f72206f6620536369656e636520616e642041727473227d7d7d5840ceda2a8f1de05e489b63d33fb20d5136098671b4aadf538a9694dc70232886d1f52421756f5f443a9e6a2d4ce12e2cdab58c421d5a96ca162a0b69825d559164
    """.trimIndent()

    /*val sdJwtSignedW3CCredential = """
        eyJraWQiOiJFeEhrQk1XOWZtYmt2VjI2Nm1ScHVQMnNVWV9OX0VXSU4xbGFwVXpPOHJvIiwiYWxnIjoiRVMyNTYifQ.eyJfc2RfYWxnIjoic2hhLTI1NiIsIkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy9ucy9jcmVkZW50aWFscy92MiIsImh0dHBzOi8vd3d3LnczLm9yZy9ucy9jcmVkZW50aWFscy9leGFtcGxlcy92MiJdLCJpc3N1ZXIiOiJodHRwczovL3VuaXZlcnNpdHkuZXhhbXBsZS9pc3N1ZXJzLzU2NTA0OSIsInZhbGlkRnJvbSI6IjIwMTAtMDEtMDFUMDA6MDA6MDBaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiZGVncmVlIjp7Im5hbWUiOiJCYWNoZWxvciBvZiBTY2llbmNlIGFuZCBBcnRzIiwiX3NkIjpbImNJTmtwVWxHYmJjTzRPUWVvcS05SE9VaDhPV0gzOEI1NnNISjY4VXRodnciXX0sIl9zZCI6WyIwb1RsT0JodnRMZnFLeU5yUkxEeDRfV21pbkJIdC16aHNnb3g0N25LR1I0Il19LCJfc2QiOlsiU0NnWTFsc191dG1oY3VOQm9nYjBvQ1BhRnVwWTg4bklMTVVTdDdvRlZJQSIsIl9FYzFhZkI4R3JGRUd2bDF0ZWpCTEdQVmR2WlZkM3pfczRGN3hfeUYtLWsiXX0.n0PYVtaVBkdc_csD5ByCVHpBLaiMy3VAMKkfLNrcAzq5I1vaaet2SZO-S6nuXIWunQTIcNkgZ64535ayeEnDZQ~WyJTSXpzaW4zSVZhckxnV0VXMmtOSDFRIiwgImlkIiwgImh0dHA6Ly91bml2ZXJzaXR5LmV4YW1wbGUvY3JlZGVudGlhbHMvMzczMiJd~WyJSYTVxSUt6TUgtQnF0cmNIT01TdENnIiwgInR5cGUiLCBbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwgIkV4YW1wbGVEZWdyZWVDcmVkZW50aWFsIl1d~WyIwRGlWcEhPT1RMeUxxSkxzRjQ0MHZBIiwgImlkIiwgImRpZDpleGFtcGxlOmViZmViMWY3MTJlYmM2ZjFjMjc2ZTEyZWMyMSJd~WyJSYWhaSVFkdWVuOUhIM0hpcmtCRDdRIiwgInR5cGUiLCAiRXhhbXBsZUJhY2hlbG9yRGVncmVlIl0
    """.trimIndent()*/

    val waltidIssuedJoseSignedW3CCredential = """
        eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3B1cmwuaW1zZ2xvYmFsLm9yZy9zcGVjL29iL3YzcDAvY29udGV4dC5qc29uIl0sImlkIjoidXJuOnV1aWQ6ZjdmNDMzMTItMWRmMi00YzNiLWI5NWUtNzIxNTBiMzlhZjQyIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk9wZW5CYWRnZUNyZWRlbnRpYWwiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sIm5hbWUiOiJKb2JzIGZvciB0aGUgRnV0dXJlIChKRkYpIiwidXJsIjoiaHR0cHM6Ly93d3cuamZmLm9yZy8iLCJpbWFnZSI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMS0yMDIyL2ltYWdlcy9KRkZfTG9nb0xvY2t1cC5wbmciLCJpZCI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIn0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7InR5cGUiOlsiQWNoaWV2ZW1lbnRTdWJqZWN0Il0sImFjaGlldmVtZW50Ijp7ImlkIjoidXJuOnV1aWQ6YWMyNTRiZDUtOGZhZC00YmIxLTlkMjktZWZkOTM4NTM2OTI2IiwidHlwZSI6WyJBY2hpZXZlbWVudCJdLCJuYW1lIjoiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSIsImRlc2NyaXB0aW9uIjoiVGhpcyB3YWxsZXQgc3VwcG9ydHMgdGhlIHVzZSBvZiBXM0MgVmVyaWZpYWJsZSBDcmVkZW50aWFscyBhbmQgaGFzIGRlbW9uc3RyYXRlZCBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cgZHVyaW5nIEpGRiB4IFZDLUVEVSBQbHVnRmVzdCAzLiIsImNyaXRlcmlhIjp7InR5cGUiOiJDcml0ZXJpYSIsIm5hcnJhdGl2ZSI6IldhbGxldCBzb2x1dGlvbnMgcHJvdmlkZXJzIGVhcm5lZCB0aGlzIGJhZGdlIGJ5IGRlbW9uc3RyYXRpbmcgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93LiBUaGlzIGluY2x1ZGVzIHN1Y2Nlc3NmdWxseSByZWNlaXZpbmcgYSBwcmVzZW50YXRpb24gcmVxdWVzdCwgYWxsb3dpbmcgdGhlIGhvbGRlciB0byBzZWxlY3QgYXQgbGVhc3QgdHdvIHR5cGVzIG9mIHZlcmlmaWFibGUgY3JlZGVudGlhbHMgdG8gY3JlYXRlIGEgdmVyaWZpYWJsZSBwcmVzZW50YXRpb24sIHJldHVybmluZyB0aGUgcHJlc2VudGF0aW9uIHRvIHRoZSByZXF1ZXN0b3IsIGFuZCBwYXNzaW5nIHZlcmlmaWNhdGlvbiBvZiB0aGUgcHJlc2VudGF0aW9uIGFuZCB0aGUgaW5jbHVkZWQgY3JlZGVudGlhbHMuIn0sImltYWdlIjp7ImlkIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0zLTIwMjMvaW1hZ2VzL0pGRi1WQy1FRFUtUExVR0ZFU1QzLWJhZGdlLWltYWdlLnBuZyIsInR5cGUiOiJJbWFnZSJ9fSwiaWQiOiJkaWQ6a2V5Ono2TWtrdlhTVFlhMWZ0aVNhOVpZdmlhZjNURVBIVnlWUDFWaEI3TXNqdTlFcjdMRyJ9LCJpc3N1YW5jZURhdGUiOiIyMDI1LTAzLTE5VDEzOjU0OjA1LjkwMTk2ODAzN1oiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDMtMTlUMTM6NTQ6MDUuOTAyMDIwMDM4WiJ9LCJqdGkiOiJ1cm46dXVpZDpmN2Y0MzMxMi0xZGYyLTRjM2ItYjk1ZS03MjE1MGIzOWFmNDIiLCJleHAiOjE3NzM5Mjg0NDUsImlhdCI6MTc0MjM5MjQ0NSwibmJmIjoxNzQyMzkyNDQ1fQ.eppq-IFQ5LW7UZ514jvn9rgrHQ23iDTsCGDq3EH6FbYf-RkN763eAEmuYIzXykgc5bRqdHOrmV_IJ4VUJedkBg
    """.trimIndent()

    val waltidIssuedJoseSignedW3CCredentialValues = mapOf(
        "issuer" to "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
        "subject" to "did:key:z6MkkvXSTYa1ftiSa9ZYviaf3TEPHVyVP1VhB7Msju9Er7LG"
    )

    val waltidIssuedJoseSignedW3CCredentialInvalidSignature = """
        eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3B1cmwuaW1zZ2xvYmFsLm9yZy9zcGVjL29iL3YzcDAvY29udGV4dC5qc29uIl0sImlkIjoidXJuOnV1aWQ6ZjdmNDMzMTItMWRmMi00YzNiLWI5NWUtNzIxNTBiMzlhZjQyIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk9wZW5CYWRnZUNyZWRlbnRpYWwiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sIm5hbWUiOiJKb2JzIGZvciB0aGUgRnV0dXJlIChKRkYpIiwidXJsIjoiaHR0cHM6Ly93d3cuamZmLm9yZy8iLCJpbWFnZSI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMS0yMDIyL2ltYWdlcy9KRkZfTG9nb0xvY2t1cC5wbmciLCJpZCI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIn0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7InR5cGUiOlsiQWNoaWV2ZW1lbnRTdWJqZWN0Il0sImFjaGlldmVtZW50Ijp7ImlkIjoidXJuOnV1aWQ6YWMyNTRiZDUtOGZhZC00YmIxLTlkMjktZWZkOTM4NTM2OTI2IiwidHlwZSI6WyJBY2hpZXZlbWVudCJdLCJuYW1lIjoiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSIsImRlc2NyaXB0aW9uIjoiVGhpcyB3YWxsZXQgc3VwcG9ydHMgdGhlIHVzZSBvZiBXM0MgVmVyaWZpYWJsZSBDcmVkZW50aWFscyBhbmQgaGFzIGRlbW9uc3RyYXRlZCBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cgZHVyaW5nIEpGRiB4IFZDLUVEVSBQbHVnRmVzdCAzLiIsImNyaXRlcmlhIjp7InR5cGUiOiJDcml0ZXJpYSIsIm5hcnJhdGl2ZSI6IldhbGxldCBzb2x1dGlvbnMgcHJvdmlkZXJzIGVhcm5lZCB0aGlzIGJhZGdlIGJ5IGRlbW9uc3RyYXRpbmcgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93LiBUaGlzIGluY2x1ZGVzIHN1Y2Nlc3NmdWxseSByZWNlaXZpbmcgYSBwcmVzZW50YXRpb24gcmVxdWVzdCwgYWxsb3dpbmcgdGhlIGhvbGRlciB0byBzZWxlY3QgYXQgbGVhc3QgdHdvIHR5cGVzIG9mIHZlcmlmaWFibGUgY3JlZGVudGlhbHMgdG8gY3JlYXRlIGEgdmVyaWZpYWJsZSBwcmVzZW50YXRpb24sIHJldHVybmluZyB0aGUgcHJlc2VudGF0aW9uIHRvIHRoZSByZXF1ZXN0b3IsIGFuZCBwYXNzaW5nIHZlcmlmaWNhdGlvbiBvZiB0aGUgcHJlc2VudGF0aW9uIGFuZCB0aGUgaW5jbHVkZWQgY3JlZGVudGlhbHMuIn0sImltYWdlIjp7ImlkIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0zLTIwMjMvaW1hZ2VzL0pGRi1WQy1FRFUtUExVR0ZFU1QzLWJhZGdlLWltYWdlLnBuZyIsInR5cGUiOiJJbWFnZSJ9fSwiaWQiOiJkaWQ6a2V5Ono2TWtrdlhTVFlhMWZ0aVNhOVpZdmlhZjNURVBIVnlWUDFWaEI3TXNqdTlFcjdMRyJ9LCJpc3N1YW5jZURhdGUiOiIyMDI1LTAzLTE5VDEzOjU0OjA1LjkwMTk2ODAzN1oiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDMtMTlUMTM6NTQ6MDUuOTAyMDIwMDM4WiJ9LCJqdGkiOiJ1cm46dXVpZDpmN2Y0MzMxMi0xZGYyLTRjM2ItYjk1ZS03MjE1MGIzOWFmNDIiLCJleHAiOjE3NzM5Mjg0NDUsImlhdCI6MTc0MjM5MjQ0NSwibmJmIjoxNzQyMzkyNDQ1fQ.AbcdefFQ5LW7UZ514jvn9rgrHQ23iDTsCGDq3EH6FbYf-RkN763eAEmuYIzXykgc5bRqdHOrmV_ABCDEFabcde
    """.trimIndent()

    val ossIssuedW3CSdJwt = """
        eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3B1cmwuaW1zZ2xvYmFsLm9yZy9zcGVjL29iL3YzcDAvY29udGV4dC5qc29uIl0sImlkIjoidXJuOnV1aWQ6YmRmMjE2ZGItOTJmOC00MTYwLThiYTYtNmRlNDliODRhNGQ5IiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk9wZW5CYWRnZUNyZWRlbnRpYWwiXSwiaXNzdWVyIjp7InR5cGUiOlsiUHJvZmlsZSJdLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIiwiaWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCJ9LCJjcmVkZW50aWFsU3ViamVjdCI6eyJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX0sImlkIjoiZGlkOmtleTp6Nk1ra3ZYU1RZYTFmdGlTYTlaWXZpYWYzVEVQSFZ5VlAxVmhCN01zanU5RXI3TEcifSwiaXNzdWFuY2VEYXRlIjoiMjAyNS0wNC0wOFQxOTo1MzozOS4zMjUzODIyMTdaIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA0LTA4VDE5OjUzOjM5LjMyNTQwMzkxN1oiLCJfc2QiOlsibVZkTkExaS1ZSjdQT0FWMmFNTjV2Xy1BU3pHOVhfSC12WDZ2NEpySWR3YyJdfSwianRpIjoidXJuOnV1aWQ6YmRmMjE2ZGItOTJmOC00MTYwLThiYTYtNmRlNDliODRhNGQ5IiwiZXhwIjoxNzc1Njc4MDE5LCJpYXQiOjE3NDQxNDIwMTksIm5iZiI6MTc0NDE0MjAxOX0.OZ3EywnJ_KDTPH5-S0OC3jN67pIdrJAwTugHh53jrI471TAbCCJ48-YVgX5l8ZLWLeKQMdFyvNpNQLqsbu9ECA~WyIzX0hndTl4UDh2T25qaW03UE4yc3FRPT0iLCJuYW1lIiwiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSJd
    """.trimIndent()
    val ossIssuedW3CSdJwtValues = mapOf(
        "issuer" to "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
        "subject" to "did:key:z6MkkvXSTYa1ftiSa9ZYviaf3TEPHVyVP1VhB7Msju9Er7LG"
    )

    val ossIssuedW3CSdJwt2 = """
        eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnL25zL2NyZWRlbnRpYWxzL3YyIiwiaHR0cHM6Ly9wdXJsLmltc2dsb2JhbC5vcmcvc3BlYy9vYi92M3AwL2NvbnRleHQuanNvbiJdLCJpZCI6InVybjp1dWlkOjI1MjkyOWRjLThmMTUtNGMyOS05MjQ5LTUxM2QxMGNjNjc4YiIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJPcGVuQmFkZ2VDcmVkZW50aWFsIl0sImlzc3VlciI6eyJ0eXBlIjpbIlByb2ZpbGUiXSwibmFtZSI6IkpvYnMgZm9yIHRoZSBGdXR1cmUgKEpGRikiLCJ1cmwiOiJodHRwczovL3d3dy5qZmYub3JnLyIsImltYWdlIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0xLTIwMjIvaW1hZ2VzL0pGRl9Mb2dvTG9ja3VwLnBuZyIsImlkIjoiZGlkOmtleTp6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AifSwiY3JlZGVudGlhbFN1YmplY3QiOnsidHlwZSI6WyJBY2hpZXZlbWVudFN1YmplY3QiXSwiYWNoaWV2ZW1lbnQiOnsiaWQiOiJ1cm46dXVpZDphYzI1NGJkNS04ZmFkLTRiYjEtOWQyOS1lZmQ5Mzg1MzY5MjYiLCJ0eXBlIjpbIkFjaGlldmVtZW50Il0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiaW1hZ2UiOnsiaWQiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTMtMjAyMy9pbWFnZXMvSkZGLVZDLUVEVS1QTFVHRkVTVDMtYmFkZ2UtaW1hZ2UucG5nIiwidHlwZSI6IkltYWdlIn0sIl9zZCI6WyJVTlprdVp6WTZrdW96RHFiQjlTZ08yZFE1aElKMDJ1R3ZfVTQyMDlGRF9jIiwiRUFqdUdjQjMwbnpPSVhVOHN0RE5KS0ltbjhXVjZCNFhsRkdyNlVDOTVtcyJdfSwiaWQiOiJkaWQ6a2V5Ono2TWtrdlhTVFlhMWZ0aVNhOVpZdmlhZjNURVBIVnlWUDFWaEI3TXNqdTlFcjdMRyJ9LCJpc3N1YW5jZURhdGUiOiIyMDI1LTA0LTA4VDIzOjI5OjUxLjQ1NjQwMDYwMVoiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDQtMDhUMjM6Mjk6NTEuNDU2NDIxNjAxWiIsIl9zZCI6WyItYnBYUUhXS0lseGtiaF95Y2VVRzMwYktwS3lyZjE0OUdoY0hGei1EUXpFIl19LCJqdGkiOiJ1cm46dXVpZDoyNTI5MjlkYy04ZjE1LTRjMjktOTI0OS01MTNkMTBjYzY3OGIiLCJleHAiOjE3NzU2OTA5OTEsImlhdCI6MTc0NDE1NDk5MSwibmJmIjoxNzQ0MTU0OTkxfQ.mI0HjLiauyZ0nN_KZMyWLuJqGuWXLyrNw9EWg_4mvLt1jJ_4Y8MFb7L1Zw04ColQ2_7pNRh45xPgacVNKOnSDg~WyJ0QlFGazR2WlF4d05zNmN1SzFqRUtRPT0iLCJuYW1lIiwiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSJd~WyJqRDE3ZFRCOWFwSWRpblQ4b0hpVmNRPT0iLCJkZXNjcmlwdGlvbiIsIlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iXQ~WyJya01mMkthSkdDWkJLdERDRkpSeTNBPT0iLCJjcml0ZXJpYSIseyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9XQ
    """.trimIndent()
    val ossIssuedW3CSdJwt2Values = mapOf(
        "issuer" to "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
        "subject" to "did:key:z6MkkvXSTYa1ftiSa9ZYviaf3TEPHVyVP1VhB7Msju9Er7LG"
    )

    // language=JSON
    val compliantOpenBadgeCredential = Json.decodeFromString<AbstractW3C>(
        """
    {
      "type": "vc-w3c_2",
      "disclosables": {},
      "credentialData": {
        "@context": [
          "https://www.w3.org/ns/credentials/v2",
          "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
        ],
        "id": "urn:uuid:b16045cb-a2bb-472d-bc6b-5d7cd1fdc436",
        "type": [
          "VerifiableCredential",
          "OpenBadgeCredential"
        ],
        "name": "JFF x vc-edu PlugFest 3 Interoperability",
        "issuer": {
          "type": "Profile",
          "name": "Jobs for the Future (JFF)",
          "url": "https://www.jff.org/",
          "image": {
            "id": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png",
            "type": "Image"
          },
          "id": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
        },
        "credentialSubject": {
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
          },
          "id": "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17"
        },
        "validFrom": "2026-01-13T21:38:31.810337493Z",
        "validUntil": "2027-01-13T21:38:31.810366593Z"
      },
      "issuer": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
      "subject": "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17",
      "signature": {
        "type": "signature-jwt",
        "signature": "sHYRH4U0Fi80OP9jjSu4ZF9EtdFZ8YddI9Mhyqu-2IdAI3T8T12c7RDcl6cdIlAh5OeqJ6yicBV6PuKH2HgVDQ",
        "jwtHeader": {
          "kid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp#z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
          "typ": "JWT",
          "alg": "EdDSA"
        }
      },
      "signed": "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ekRuYWVZYjdEYWtRV21Za3JMa21zVkVSQWF6RjVZYTFHNW54YlNuUWNMSlo4Q3IxNyIsInZjIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy9ucy9jcmVkZW50aWFscy92MiIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDpiMTYwNDVjYi1hMmJiLTQ3MmQtYmM2Yi01ZDdjZDFmZGM0MzYiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJuYW1lIjoiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSIsImlzc3VlciI6eyJ0eXBlIjoiUHJvZmlsZSIsIm5hbWUiOiJKb2JzIGZvciB0aGUgRnV0dXJlIChKRkYpIiwidXJsIjoiaHR0cHM6Ly93d3cuamZmLm9yZy8iLCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMS0yMDIyL2ltYWdlcy9KRkZfTG9nb0xvY2t1cC5wbmciLCJ0eXBlIjoiSW1hZ2UifSwiaWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCJ9LCJjcmVkZW50aWFsU3ViamVjdCI6eyJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX0sImlkIjoiZGlkOmtleTp6RG5hZVliN0Rha1FXbVlrckxrbXNWRVJBYXpGNVlhMUc1bnhiU25RY0xKWjhDcjE3In0sInZhbGlkRnJvbSI6IjIwMjYtMDEtMTNUMjE6Mzg6MzEuODEwMzM3NDkzWiIsInZhbGlkVW50aWwiOiIyMDI3LTAxLTEzVDIxOjM4OjMxLjgxMDM2NjU5M1oifSwianRpIjoidXJuOnV1aWQ6YjE2MDQ1Y2ItYTJiYi00NzJkLWJjNmItNWQ3Y2QxZmRjNDM2IiwiZXhwIjoxNzk5ODc2MzExfQ.sHYRH4U0Fi80OP9jjSu4ZF9EtdFZ8YddI9Mhyqu-2IdAI3T8T12c7RDcl6cdIlAh5OeqJ6yicBV6PuKH2HgVDQ",
      "format": "jwt_vc_json"
    }
    """.trimIndent()
    )

    // language=JSON
    val noncompliantOpenBadgeCredential = Json.decodeFromString<AbstractW3C>(
        """
    {
      "type": "vc-w3c_1_1",
      "disclosables": {},
      "credentialData": {
        "@context": [
          "https://www.w3.org/2018/credentials/v1",
          "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
        ],
        "id": "urn:uuid:98a8e420-d85e-4a1f-9a2b-d99a89c42bb4",
        "type": [
          "VerifiableCredential",
          "OpenBadgeCredential"
        ],
        "name": "JFF x vc-edu PlugFest 3 Interoperability",
        "issuer": {
          "type": [
            "Profile"
          ],
          "name": "Jobs for the Future (JFF)",
          "url": "https://www.jff.org/",
          "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png",
          "id": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
        },
        "credentialSubject": {
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
          },
          "id": "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17"
        },
        "issuanceDate": "2025-10-20T06:22:37.444427698Z",
        "expirationDate": "2026-10-20T06:22:37.444457898Z"
      },
      "issuer": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
      "subject": "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17",
      "signature": {
        "type": "signature-jwt",
        "signature": "GETFmX9JOGDmTe8k3t1i4gVA3PzQGi_WNb6zXEIxavoZSYsxJcyiJ8pZ_jEjvIUFfFPBvJLVJqb4Mcgwc09fAQ",
        "jwtHeader": {
          "kid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp#z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
          "typ": "JWT",
          "alg": "EdDSA"
        }
      },
      "signed": "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ekRuYWVZYjdEYWtRV21Za3JMa21zVkVSQWF6RjVZYTFHNW54YlNuUWNMSlo4Q3IxNyIsInZjIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIiwiaHR0cHM6Ly9wdXJsLmltc2dsb2JhbC5vcmcvc3BlYy9vYi92M3AwL2NvbnRleHQuanNvbiJdLCJpZCI6InVybjp1dWlkOjk4YThlNDIwLWQ4NWUtNGExZi05YTJiLWQ5OWE4OWM0MmJiNCIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJPcGVuQmFkZ2VDcmVkZW50aWFsIl0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiaXNzdWVyIjp7InR5cGUiOlsiUHJvZmlsZSJdLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIiwiaWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCJ9LCJjcmVkZW50aWFsU3ViamVjdCI6eyJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX0sImlkIjoiZGlkOmtleTp6RG5hZVliN0Rha1FXbVlrckxrbXNWRVJBYXpGNVlhMUc1bnhiU25RY0xKWjhDcjE3In0sImlzc3VhbmNlRGF0ZSI6IjIwMjUtMTAtMjBUMDY6MjI6MzcuNDQ0NDI3Njk4WiIsImV4cGlyYXRpb25EYXRlIjoiMjAyNi0xMC0yMFQwNjoyMjozNy40NDQ0NTc4OThaIn0sImp0aSI6InVybjp1dWlkOjk4YThlNDIwLWQ4NWUtNGExZi05YTJiLWQ5OWE4OWM0MmJiNCIsImV4cCI6MTc5MjQ3NzM1NywiaWF0IjoxNzYwOTQxMzU3LCJuYmYiOjE3NjA5NDEzNTd9.GETFmX9JOGDmTe8k3t1i4gVA3PzQGi_WNb6zXEIxavoZSYsxJcyiJ8pZ_jEjvIUFfFPBvJLVJqb4Mcgwc09fAQ",
      "format": "jwt_vc_json"
    }
    """.trimIndent()
    )
}
