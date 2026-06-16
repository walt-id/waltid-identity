package id.waltid.openid4vp.wallet

import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletPresentationFormatRegistryJvmTest {

    @Test
    fun `runtime capabilities expose format support only when matching runtime algorithms are present`() {
        val capabilities = WalletPresentationFormatRegistry.capabilitiesFromKeyTypes(setOf(KeyType.Ed25519))

        val hasJwsAlgorithms = capabilities.supportedJwsAlgorithms.isNotEmpty()
        val hasMdocAlgorithms = capabilities.supportedMdocCoseAlgorithms.isNotEmpty()

        assertEquals(
            hasJwsAlgorithms,
            WalletPresentationFormatRegistry.SupportedFormat.JWT_VC_JSON in capabilities.supportedFormats,
        )
        assertEquals(
            hasJwsAlgorithms,
            WalletPresentationFormatRegistry.SupportedFormat.DC_SD_JWT in capabilities.supportedFormats,
        )
        assertEquals(
            hasMdocAlgorithms,
            WalletPresentationFormatRegistry.SupportedFormat.MSO_MDOC in capabilities.supportedFormats,
        )
    }

    @Test
    fun `empty runtime key set produces empty vp_formats_supported metadata`() {
        val capabilities = WalletPresentationFormatRegistry.capabilitiesFromKeyTypes(emptySet())
        val vpFormatsSupported = WalletPresentationFormatRegistry.buildVpFormatsSupported(capabilities)

        assertTrue(vpFormatsSupported.isEmpty())
    }

    @Test
    fun `runtime metadata uses runtime algorithm values`() {
        val capabilities = WalletPresentationFormatRegistry.capabilitiesFromKeyTypes(setOf(KeyType.secp256r1))
        val vpFormatsSupported = WalletPresentationFormatRegistry.buildVpFormatsSupported(capabilities)

        vpFormatsSupported[WalletPresentationFormatRegistry.SupportedFormat.JWT_VC_JSON.primaryId]
            ?.jsonObject
            ?.get("alg_values")
            ?.jsonArray
            ?.let { algValues -> assertTrue(algValues.any { it.toString().contains("ES256") }) }
    }
}
