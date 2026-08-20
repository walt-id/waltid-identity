package id.walt.wallet2.mobile

import androidx.credentials.registry.digitalcredentials.mdoc.MdocEntry
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtEntry
import id.walt.cose.coseCompliantCbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun capabilityMatrixReportsUnsignedOpenId4VpAndOpenId4VciAsSupportableWhenRegistered() {
        val capabilities = registry.capabilities

        assertTrue(capabilities.platformAvailable)
        assertFalse(capabilities.registrationAvailable)
        val unsigned = capabilities.capabilities.single {
            it.protocol == MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED
        }
        // Unsupported here only because registration has not run; the combination itself is implemented.
        assertFalse(unsigned.supported)
        assertTrue(unsigned.unsupportedReason?.contains("registration") == true)
        val openId4Vci = capabilities.capabilities.single {
            it.protocol == MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1
        }
        assertFalse(openId4Vci.supported)
        assertTrue(openId4Vci.unsupportedReason?.contains("creation registration") == true)
        // Both DC API response modes: dc_api and dc_api.jwt.
        assertEquals(
            listOf(
                MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED,
                MobileWalletDigitalCredentialResponseProtection.JWE,
            ),
            unsigned.responseProtection,
        )
        val signed = capabilities.capabilities.single {
            it.protocol == MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED
        }
        assertFalse(signed.supported)
        assertTrue(signed.unsupportedReason?.contains("unsigned") == true)
        val multisigned = capabilities.capabilities.single {
            it.protocol == MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED
        }
        assertFalse(multisigned.supported)
        assertTrue(multisigned.unsupportedReason?.contains("JWS JSON Serialization") == true)
        // A capability that is not implemented must not be advertised as available on any protocol,
        // whatever this wallet does implement elsewhere.
        assertTrue(
            capabilities.capabilities.filter { it.supported }.none { capability ->
                MobileWalletDigitalCredentialRequestProtection.SIGNED in capability.requestProtection ||
                    MobileWalletDigitalCredentialRequestProtection.MULTISIGNED in capability.requestProtection
            },
            "a signed request protection must never be advertised as supported",
        )
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

    @Test
    fun openId4VciCreationOptionsMatchesGoogleIssuanceContract() {
        val icon = byteArrayOf(1, 2, 3, 4)
        val bytes = registry.encodeOpenId4VciCreationOptions(
            entryId = "openid4vci",
            applicationName = "walt.id Wallet",
            subtitle = "Save a credential to this wallet",
            explainer = "Save a credential to this wallet.",
            icon = icon,
        )
        val jsonOffset = java.nio.ByteBuffer.wrap(bytes, 0, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .int
        assertEquals(4 + icon.size, jsonOffset)
        assertEquals(icon.toList(), bytes.slice(4 until jsonOffset))
        val json = Json.parseToJsonElement(bytes.copyOfRange(jsonOffset, bytes.size).decodeToString()).jsonObject
        assertEquals("openid4vci", json["entry_id"]?.jsonPrimitive?.content)
        val entry = json["entries"]!!.jsonArray.single().jsonObject
        assertEquals("Save a credential to this wallet", entry["subtitle"]?.jsonPrimitive?.content)
        assertEquals(
            "Save a credential to this wallet.",
            entry["explainer"]?.jsonObject?.get("default")?.jsonPrimitive?.content,
        )
        assertEquals(Json.parseToJsonElement("{\"Pass\":{}}"), json["filter"])
        assertEquals(
            listOf("openid4vci-v1"),
            json["preferred_protocols"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("walt.id Wallet", json["package_info"]!!.jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals(4, json["package_info"]!!.jsonObject["icon"]!!.jsonArray[0].jsonPrimitive.content.toInt())
        assertEquals(
            4 + icon.size,
            json["package_info"]!!.jsonObject["icon"]!!.jsonArray[1].jsonPrimitive.content.toInt(),
        )
        assertFalse(json.containsKey("display"))
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
