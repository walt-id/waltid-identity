package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class CardTitleResolverTest {
    @Test
    fun metadataTakesPriorityAndInvalidPayloadKeepsTheFallback() {
        assertEquals("My identity", resolveCardTitle("vc+sd-jwt", "invalid", " My identity ", "PID"))
        assertEquals("PID", resolveCardTitle("vc+sd-jwt", "[]", " ", " PID "))
        assertEquals("vc+sd-jwt", resolveCardTitle("vc+sd-jwt", null, null, " "))
    }

    @Test
    fun browserAndMobileResolveSupportedFormatsConsistently() {
        listOf(
            Triple("vc+sd-jwt", """{"vct":"https://example.org/mobile-driving-licence"}""", "Mobile Driving Licence"),
            Triple("mso_mdoc", """{"docType":"org.iso.18013.5.1.mDL"}""", "Mobile Driving Licence"),
            Triple("mso_mdoc", """{"doctype":"eu.europa.ec.eudi.pid.1"}""", "PID"),
            Triple("jwt_vc_json", """{"vc":{"type":["VerifiableCredential","EmployeeBadge"]}}""", "Employee Badge"),
            Triple("jwt_vc_json", """{"type":["VerifiableCredential"]}""", "Fallback"),
        ).forEach { (format, payload, expected) ->
            assertEquals(expected, resolveCardTitle(format, payload, null, "Fallback"), format)
        }
    }
}
