package id.walt.verifier.openid.models.authorization

import id.walt.verifier.openid.models.credentials.AttestationFormat
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifierInfoItemTest {
    @Test
    fun decodesRegistrationCertificateVerifierInfo() {
        val request = Json.decodeFromString<AuthorizationRequest>(
            """
            {
              "verifier_info": [
                {
                  "format": "registration_cert",
                  "data": "signed-registration-certificate"
                }
              ]
            }
            """.trimIndent(),
        )

        val verifierInfo = requireNotNull(request.verifierInfo).single()
        assertEquals(AttestationFormat.REGISTRATION_CERT, verifierInfo.format)
        assertEquals("signed-registration-certificate", verifierInfo.data)
    }
}
