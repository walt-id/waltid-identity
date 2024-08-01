package id.walt.issuer.issuance2

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.issuer.issuance.IssuanceConfiguration
import id.walt.issuer.issuance.IssuerConfiguration
import id.walt.issuer.issuance.NewIssuanceRequest
import id.walt.issuer.issuance.NewSingleCredentialIssuanceRequest
import id.walt.oid4vc.data.GrantType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object NewExamples {

    private val issuerKey = Json.parseToJsonElement(
        """{
    "type": "jwk",
    "jwk": {
      "kty": "OKP",
      "d": "mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI",
      "crv": "Ed25519",
      "kid": "Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8",
      "x": "T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM"
    }
  }"""
    ).jsonObject


    private val cred1 = W3CVC.fromJson(
        """
        {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://www.w3.org/2018/credentials/examples/v1"
    ],
    "type": [
      "VerifiableCredential",
      "UniversityDegreeCredential"
    ],
    "credentialSubject": {
      "degree": {
        "type": "BachelorDegree",
        "name": "Bachelor of Science and Arts"
      }
    }
  }""".trimIndent()
    )


    private val cred2 = W3CVC.fromJson(
        """
        {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://www.w3.org/2018/credentials/examples/v1"
    ],
    "type": [
      "VerifiableCredential",
      "UniversityDegreeCredential"
    ],
    "credentialSubject": {
      "degree": {
        "type": "MasterDegree",
        "name": "Master of Science and Arts"
      }
    }
  }""".trimIndent()
    )


    val mapping = Json.parseToJsonElement(
        """
        {
    "id": "<uuid>",
    "issuer": {
      "id": "<issuerDid>"
    },
    "credentialSubject": {
      "id": "<subjectDid>"
    },
    "issuanceDate": "<timestamp>",
    "expirationDate": "<timestamp-in:365d>"
  }
    """.trimIndent()
    ).jsonObject

    val newIssuanceRequestExample = NewIssuanceRequest(
        issuer = IssuerConfiguration(issuerKey, "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"),
        issuance = IssuanceConfiguration(
            flow = GrantType.pre_authorized_code,
            callbackUrl = "https://example.org/credential-callback?abc=xyz"
        ),
        credential = listOf(
            NewSingleCredentialIssuanceRequest(
                credentialData = cred1,
                mapping = mapping
            ),
            NewSingleCredentialIssuanceRequest(
                credentialData = cred2,
                mapping = mapping
            )
        )
    )

}
