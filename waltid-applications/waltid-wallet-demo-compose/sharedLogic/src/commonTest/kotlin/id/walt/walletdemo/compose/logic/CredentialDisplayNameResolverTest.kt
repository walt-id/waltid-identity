package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class CredentialDisplayNameResolverTest {
    @Test
    fun derivesKnownDocumentNameFromCredentialData() {
        assertEquals(
            "Mobile driving licence",
            CredentialDisplayNameResolver.resolve(
                label = null,
                format = "mso_mdoc",
                credentialDataJson = """{"docType":"org.iso.18013.5.1.mDL"}""",
            ),
        )
    }

    @Test
    fun preservesConfiguredProtocolLabel() {
        assertEquals(
            "mso_mdoc",
            CredentialDisplayNameResolver.resolve(label = "mso_mdoc", format = "mso_mdoc"),
        )
    }

    @Test
    fun replacesIdentifierLabelWithKnownCredentialName() {
        assertEquals(
            "Personal ID",
            CredentialDisplayNameResolver.resolve(
                label = null,
                format = "dc+sd-jwt",
                credentialType = "urn:eu.europa.ec.eudi:pid:1",
            ),
        )
    }

    @Test
    fun preservesIssuerProvidedDisplayName() {
        assertEquals(
            "Driving licence",
            CredentialDisplayNameResolver.resolve(label = "Driving licence", format = "mso_mdoc"),
        )
    }
}
