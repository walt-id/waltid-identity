package id.walt.wallet2.mobile

import androidx.credentials.registry.digitalcredentials.mdoc.MdocEntry
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtEntry
import id.walt.cose.coseCompliantCbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDigitalCredentialRegistryTest {
    private val registry = AndroidDigitalCredentialRegistry(RuntimeEnvironment.getApplication())

    @Test
    fun capabilityMatrixIsExplicitAboutMultisignedSupport() {
        val capabilities = registry.capabilities

        assertTrue(capabilities.platformAvailable)
        assertFalse(capabilities.registrationAvailable)
        val unsigned = capabilities.capabilities.single {
            it.protocol == MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED
        }
        assertFalse(unsigned.supported)
        assertTrue(unsigned.unsupportedReason?.contains("registration") == true)
        val multisigned = capabilities.capabilities.single {
            it.protocol == MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED
        }
        assertFalse(multisigned.supported)
        assertTrue(multisigned.unsupportedReason?.contains("JWS JSON Serialization") == true)
    }

    @Test
    fun mapsMdocMetadataWithoutRawCredentialsOrKeys() {
        val entry = with(registry) {
            MobileWalletCredentialRegistryRecord(
                registryEntryId = "opaque-id",
                credentialId = "wallet-private-id",
                format = MobileWalletDigitalCredentialFormat.MDOC,
                type = "org.iso.18013.5.1.mDL",
                fields = listOf(
                    MobileWalletCredentialRegistryField(
                        path = listOf("org.iso.18013.5.1", "given_name"),
                        valueJson = "\"Ada\"",
                        selectivelyDisclosable = true,
                    )
                ),
                displayName = "Driving licence",
            ).toAndroidEntry()
        } as MdocEntry

        assertEquals("org.iso.18013.5.1.mDL", entry.docType)
        assertEquals("given_name", entry.fields.single().identifier)
        assertEquals("Ada", entry.fields.single().fieldValue)
        assertEquals("opaque-id", entry.id)
    }

    @Test
    fun mapsSdJwtClaimPathAndSelectiveDisclosure() {
        val entry = with(registry) {
            MobileWalletCredentialRegistryRecord(
                registryEntryId = "opaque-id",
                credentialId = "wallet-private-id",
                format = MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                type = "https://credentials.example/pid",
                fields = listOf(
                    MobileWalletCredentialRegistryField(
                        path = listOf("address", "locality"),
                        valueJson = "\"Vienna\"",
                        selectivelyDisclosable = true,
                    )
                ),
                displayName = "PID",
            ).toAndroidEntry()
        } as SdJwtEntry

        assertEquals(listOf("address", "locality"), entry.claims.single().path)
        assertEquals("Vienna", entry.claims.single().value)
        assertTrue(entry.claims.single().isSelectivelyDisclosable)
        assertEquals("opaque-id", entry.id)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun annexCMatcherDatabaseUsesOpaqueIdsAndMdocNamespaces() {
        val bytes = registry.encodeAnnexCCredentialDatabase(
            listOf(
                MobileWalletCredentialRegistryRecord(
                    registryEntryId = "opaque-id",
                    credentialId = "wallet-private-id",
                    format = MobileWalletDigitalCredentialFormat.MDOC,
                    type = "org.iso.18013.5.1.mDL",
                    fields = listOf(
                        MobileWalletCredentialRegistryField(
                            path = listOf("org.iso.18013.5.1", "given_name"),
                            valueJson = "\"Ada\"",
                            selectivelyDisclosable = true,
                        )
                    ),
                    displayName = "Driving licence",
                )
            )
        )
        val database = coseCompliantCbor.decodeFromByteArray<AndroidAnnexCCredentialDatabase>(bytes)
        val credential = database.credentials.single()

        assertEquals(listOf("org-iso-mdoc"), database.protocols)
        assertEquals("opaque-id", credential.mdoc.documentId)
        assertEquals("org.iso.18013.5.1.mDL", credential.mdoc.docType)
        assertEquals(
            listOf("given_name", "Ada", "Ada"),
            credential.mdoc.namespaces.getValue("org.iso.18013.5.1").getValue("given_name"),
        )
    }
}
