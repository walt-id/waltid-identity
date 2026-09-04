package id.walt.walletdemo.compose.logic

import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalEncodingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredentialDisplayImageFallbackAndroidTest {
    @Test
    fun rendersDecodableDataImageClaimsForSdJwtAndW3cCredentialsUsingDetectedMimeType() {
        val jpeg = fixture("synthetic-verification-document.jpg")
        val png = fixture("synthetic-signature.png")

        listOf("vc+sd-jwt", "dc+sd-jwt", "jwt_vc", "jwt_vc_json", "jwt_vc_json-ld", "ldp_vc").forEach { format ->
            val details = details(
                format = format,
                credentialDataJson =
                    """{
                        "verification_artifact":"${dataUrl("image/jpeg", jpeg)}",
                        "resident_address":{"visual_proof":"${dataUrl("image/jpeg", png)}"}
                    }""".trimIndent(),
            )

            val claims = details.groups.flatMap { it.items }
            val claim = claims.first { it.path.id == "verification_artifact" }
            assertEquals("Verification artifact", claim.label)
            val image = assertIs<DisplayValue.Image>(claim.value)
            assertEquals("image/jpeg", image.mimeType)
            assertTrue(image.bytes.contentEquals(jpeg))

            val nestedImage = assertIs<DisplayValue.Image>(
                claims.first { it.path.id == "resident_address.visual_proof" }.value
            )
            assertEquals("image/png", nestedImage.mimeType)
            assertTrue(nestedImage.bytes.contentEquals(png))
        }
    }

    @Test
    fun rendersDecodableDataImageRequestedDisclosure() {
        val pngDataUrl = dataUrl("image/png", fixture("synthetic-signature.png"))
        val option = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "credential-1",
            label = "PID",
            issuer = "https://issuer.example",
            format = "dc+sd-jwt",
            credentialDataJson = "{}",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Verification artifact",
                    path = """["${'$'}","verification_artifact"]""",
                    valueJson = """"$pngDataUrl"""",
                    selectivelyDisclosable = true,
                )
            ),
        )

        val image = assertIs<DisplayValue.Image>(
            option.toCredentialDetails().groups.first().items.single().value
        )
        assertEquals("image/png", image.mimeType)
    }

    @Test
    fun truncatedImageSignaturesFallBackToOrdinaryClaimValues() {
        val details = details(
            format = "vc+sd-jwt",
            credentialDataJson =
                """{
                    "truncated_png":"${dataUrl("image/png", pngSignature)}",
                    "truncated_jpeg":"${dataUrl("image/jpeg", jpegSignature)}"
                }""".trimIndent(),
        )

        details.groups.flatMap { it.items }.forEach { claim ->
            assertFalse(claim.value is DisplayValue.Image)
        }
    }

    @Test
    fun oversizedDataImageFallsBackWithoutDecoding() {
        val png = fixture("synthetic-signature.png")
        val oversizedPng = png + ByteArray(oversizedImageByteCount - png.size)
        val details = details(
            format = "vc+sd-jwt",
            credentialDataJson = """{"visual_proof":"${dataUrl("image/png", oversizedPng)}"}""",
        )

        val value = details.groups.flatMap { it.items }.single().value
        assertFalse(value is DisplayValue.Image)
    }

    private fun details(format: String, credentialDataJson: String): CredentialDetails =
        CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = format,
                issuer = null,
                label = format,
                credentialDataJson = credentialDataJson,
            )
        )

    private fun fixture(name: String): ByteArray {
        val directory = checkNotNull(System.getProperty("walletDemoImageFixturesDir")) {
            "walletDemoImageFixturesDir is not configured"
        }
        return File(directory, name).readBytes()
    }

    private fun dataUrl(mimeType: String, bytes: ByteArray): String =
        "data:$mimeType;base64,${Base64.Default.encode(bytes)}"

    private companion object {
        const val oversizedImageByteCount = 2_000_001
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val jpegSignature = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46,
        )
    }
}
