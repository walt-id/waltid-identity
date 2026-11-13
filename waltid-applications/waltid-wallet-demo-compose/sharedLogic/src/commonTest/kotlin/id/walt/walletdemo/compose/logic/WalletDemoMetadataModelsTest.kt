package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class WalletDemoMetadataModelsTest {

    @Test
    fun preservesIssuerClaimOrderWhileUsingReadableFallbackLabels() {
        val credential = WalletDemoOfferedCredentialMetadata(
            configurationId = "org.iso.23220.photoid.1",
            format = "mso_mdoc",
            vct = null,
            doctype = "org.iso.23220.photoid.1",
            display = null,
            claims = listOf(
                WalletDemoCredentialClaimMetadata(
                    path = listOf("org.iso.23220.1", "given_name"),
                    mandatory = true,
                    displayName = "Given name",
                ),
                WalletDemoCredentialClaimMetadata(
                    path = listOf("org.iso.23220.1", "age_over_65"),
                    mandatory = false,
                    displayName = null,
                ),
                WalletDemoCredentialClaimMetadata(
                    path = listOf("org.iso.23220.dtc.1", "dtc_dg2"),
                    mandatory = null,
                    displayName = null,
                ),
                WalletDemoCredentialClaimMetadata(
                    path = listOf("org.iso.23220.1", "age_over_18"),
                    mandatory = true,
                    displayName = null,
                ),
                WalletDemoCredentialClaimMetadata(
                    path = listOf("org.iso.23220.dtc.1", "dtc_sod"),
                    mandatory = true,
                    displayName = null,
                ),
                WalletDemoCredentialClaimMetadata(
                    path = listOf("org.iso.23220.dtc.1", "dtc_dg1"),
                    mandatory = null,
                    displayName = null,
                ),
                WalletDemoCredentialClaimMetadata(
                    path = listOf("org.iso.23220.dtc.1", "dg_content_info"),
                    mandatory = false,
                    displayName = null,
                ),
                WalletDemoCredentialClaimMetadata(
                    path = listOf("org.iso.23220.dtc.1", "dtc_version"),
                    mandatory = true,
                    displayName = null,
                ),
            ),
        )

        assertEquals(
            listOf(WalletDemoCredentialClaimDisplayGroup(title = "Credential claims", claims = listOf(
                WalletDemoCredentialClaimDisplay("Given name", "Always included"),
                WalletDemoCredentialClaimDisplay("65 or older", "May be included"),
                WalletDemoCredentialClaimDisplay("DG2: Facial image", "May be included"),
                WalletDemoCredentialClaimDisplay("18 or older", "Always included"),
                WalletDemoCredentialClaimDisplay("Document security object (SOD)", "Always included"),
                WalletDemoCredentialClaimDisplay("DG1: Machine-readable zone", "May be included"),
                WalletDemoCredentialClaimDisplay("Document content information", "May be included"),
                WalletDemoCredentialClaimDisplay("Specification version", "Always included"),
            ))), credential.claimDisplayGroups(),
        )
    }

    @Test
    fun readableFallbackOmitsPathSyntaxWhileKeepingTypedPaths() {
        val metadata = StoredCredentialMetadataParser.claims("""{"credentialClaims":[
          {"path":["nationalities",null]},
          {"path":["other_nationalities",0]},
          {"path":["namespace","element",-7]},
          {"path":["namespace","element",18446744073709551615]}
        ]}""")
        assertEquals(listOf("Nationalities", "Other nationalities", "Element", "Element"), metadata.map { it.displayLabel("dc+sd-jwt") })
        assertEquals(listOf("null", "0", "-7", "18446744073709551615"), metadata.map { it.path.last().toString() })
    }

    @Test
    fun blankClaimKeysHaveReadableFallbackLabels() {
        for (path in listOf("""[""]""", """[" "]""")) {
            val claim = StoredCredentialMetadataParser.claims("""{"credentialClaims":[{"path":$path}]}""").single()
            assertEquals("Claim", claim.displayLabel("dc+sd-jwt"))
        }
    }
}
