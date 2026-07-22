package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class WalletDemoMetadataModelsTest {

    @Test
    fun groupsMdocOfferClaimsByUserFacingSemantics() {
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
            listOf(
                WalletDemoCredentialClaimDisplayGroup(
                    title = "Credential claims",
                    claims = listOf(WalletDemoCredentialClaimDisplay("Given name", "Always included")),
                ),
                WalletDemoCredentialClaimDisplayGroup(
                    title = "Age attestations",
                    claims = listOf(
                        WalletDemoCredentialClaimDisplay("18 or older", "Always included"),
                        WalletDemoCredentialClaimDisplay("65 or older", "May be included"),
                    ),
                ),
                WalletDemoCredentialClaimDisplayGroup(
                    title = "Travel document data",
                    claims = listOf(
                        WalletDemoCredentialClaimDisplay("Specification version", "Always included"),
                        WalletDemoCredentialClaimDisplay("Document security object (SOD)", "Always included"),
                        WalletDemoCredentialClaimDisplay("DG1: Machine-readable zone", "May be included"),
                        WalletDemoCredentialClaimDisplay("DG2: Facial image", "May be included"),
                        WalletDemoCredentialClaimDisplay("Document content information", "May be included"),
                    ),
                ),
            ),
            credential.claimDisplayGroups(),
        )
    }
}
