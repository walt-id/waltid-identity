package id.walt.credentials.display

import kotlin.test.Test
import kotlin.test.assertEquals

class CredentialTitlesTest {

    @Test
    fun prefersDisplayName() {
        assertEquals(
            "Photo ID",
            CredentialTitles.fromPayload(
                format = "mso_mdoc",
                credentialDataJson = """{"docType":"org.iso.18013.5.1.mDL"}""",
                displayName = "Photo ID",
                fallback = "fallback",
            ),
        )
    }

    @Test
    fun w3cUsesFirstNonGenericType() {
        assertEquals(
            "University Degree Credential",
            CredentialTitles.fromPayload(
                format = "jwt_vc_json",
                credentialDataJson = """{"type":["VerifiableCredential","UniversityDegreeCredential"]}""",
            ),
        )
    }

    @Test
    fun sdJwtHumanizesVct() {
        assertEquals(
            "This Case",
            CredentialTitles.fromPayload(
                format = "vc+sd-jwt",
                credentialDataJson = """{"vct":"this_case"}""",
            ),
        )
        assertEquals(
            "Pid 1",
            CredentialTitles.fromPayload(
                format = "dc+sd-jwt",
                credentialDataJson = """{"vct":"urn:eudi:pid:1"}""",
            ),
        )
    }

    @Test
    fun mdocUsesFriendlyNames() {
        assertEquals(
            "Mobile Driving Licence",
            CredentialTitles.fromPayload(
                format = "mso_mdoc",
                credentialDataJson = """{"docType":"org.iso.18013.5.1.mDL"}""",
            ),
        )
        assertEquals(
            "PID",
            CredentialTitles.fromPayload(
                format = "mso_mdoc",
                credentialDataJson = """{"doctype":"eu.europa.ec.eudi.pid.1"}""",
            ),
        )
        assertEquals(
            "Photo ID",
            CredentialTitles.fromPayload(
                format = "mso_mdoc",
                credentialDataJson = """{"docType":"org.iso.23220.photoid.1"}""",
            ),
        )
    }

    @Test
    fun fallsBackWhenPayloadMissing() {
        assertEquals(
            "stored-label",
            CredentialTitles.fromPayload(
                format = "jwt_vc_json",
                credentialDataJson = null,
                fallback = "stored-label",
            ),
        )
    }
}
