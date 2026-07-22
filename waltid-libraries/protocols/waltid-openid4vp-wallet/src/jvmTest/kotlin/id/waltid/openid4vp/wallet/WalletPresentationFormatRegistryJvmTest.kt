@file:Suppress("DEPRECATION")

package id.waltid.openid4vp.wallet

import id.walt.cose.Cose
import id.walt.crypto.keys.KeyType
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Signer
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
        assertEquals(listOf("Ed25519"), capabilities.supportedJwsAlgorithms)
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

    @Test
    fun `crypto2 capabilities reflect compatible signing keys`() {
        val capabilities = WalletPresentationFormatRegistry.capabilitiesFromKeys(
            listOf(
                crypto2Key(
                    KeySpec.Ec(EcCurve.P256),
                    setOf(SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)),
                ),
                crypto2Key(
                    KeySpec.Edwards(EdwardsCurve.ED25519),
                    setOf(SignatureAlgorithm.EdDsa),
                ),
            )
        )

        assertEquals(setOf("ES256", "Ed25519"), capabilities.supportedJwsAlgorithms.toSet())
        assertEquals(listOf(Cose.Algorithm.ESP256), capabilities.supportedMdocCoseAlgorithms)
        assertEquals(WalletPresentationFormatRegistry.SupportedFormat.entries.toSet(), capabilities.supportedFormats)
    }

    @Test
    fun `crypto2 capabilities reject non-signing and algorithm-incompatible keys`() {
        val capabilities = WalletPresentationFormatRegistry.capabilitiesFromKeys(
            listOf(
                crypto2Key(
                    KeySpec.Ec(EcCurve.P256),
                    setOf(SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)),
                    signing = false,
                ),
                crypto2Key(
                    KeySpec.Ec(EcCurve.P384),
                    setOf(SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)),
                ),
            )
        )

        assertTrue(capabilities.supportedFormats.isEmpty())
        assertTrue(capabilities.supportedJwsAlgorithms.isEmpty())
        assertTrue(capabilities.supportedMdocCoseAlgorithms.isEmpty())
    }

    private fun crypto2Key(
        spec: KeySpec,
        algorithms: Set<SignatureAlgorithm>,
        signing: Boolean = true,
    ): Crypto2Key = object : Crypto2Key {
        override val id = KeyId("test-key-$spec-$signing")
        override val spec = spec
        override val usages = if (signing) setOf(KeyUsage.SIGN) else setOf(KeyUsage.VERIFY)
        override val capabilities = KeyCapabilities(
            signer = if (signing) Signer { _, _ -> byteArrayOf() } else null,
            signatureAlgorithms = algorithms,
        )
    }
}
