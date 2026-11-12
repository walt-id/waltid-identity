package id.walt.issuer2.profile

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EudiPidSdJwtPictureTest {
    @Test
    fun pictureIsAJpegDataUrlInTheShippedExamples() {
        val issuerConfig = issuer2ConfigDir()
        val repoRoot = issuerConfig.parent.parent.parent
        val pictures = listOf(
            pictureFromProfile(issuerConfig.resolve("issuer2-profiles.conf")),
            pictureFromProfile(repoRoot.resolve("docker-compose/issuer-api2/config/issuer2-profiles.conf")),
            pictureFromMarkdown(
                repoRoot.resolve("waltid-applications/waltid-credentials/src/content/2.sd-jwt-credentials/eu-europa-ec-eudi-pid-1.md"),
            ),
        )
        assertEquals(pictures.first(), pictures[1])
        assertEquals(pictures.first(), pictures[2])
        assertJpegDataUrl(pictures.first())
    }

    private fun assertJpegDataUrl(value: String) {
        val prefix = "data:image/jpeg;base64,"
        assertTrue(value.startsWith(prefix), "picture must be a JPEG data URL")
        val bytes = Base64.getDecoder().decode(value.removePrefix(prefix))
        assertTrue(
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte(),
            "picture payload must start with a JPEG marker",
        )
        val image = ImageIO.read(bytes.inputStream())
        assertNotNull(image, "picture payload must decode as a JPEG")
        assertTrue(image.width > 0 && image.height > 0)
    }

    private fun pictureFromProfile(path: Path): String {
        val text = Files.readString(path)
        val start = text.indexOf("eudiPidSdJwt = {")
        val end = text.indexOf("identityCredentialSdJwt = {", start)
        assertTrue(start >= 0 && end > start, "eudiPidSdJwt profile missing in $path")
        return PICTURE.find(text.substring(start, end))?.groupValues?.get(1)
            ?: error("picture claim missing in $path")
    }

    private fun pictureFromMarkdown(path: Path): String =
        PICTURE.find(Files.readString(path))?.groupValues?.get(1)
            ?: error("picture claim missing in $path")

    private fun issuer2ConfigDir(): Path =
        listOf(
            Path.of("config"),
            Path.of("waltid-services/waltid-issuer-api2/config"),
            Path.of("waltid-identity/waltid-services/waltid-issuer-api2/config"),
        )
            .map { it.toAbsolutePath().normalize() }
            .firstOrNull { Files.isRegularFile(it.resolve("issuer2-profiles.conf")) }
            ?: error("Could not locate waltid-issuer-api2 config directory")

    private companion object {
        val PICTURE = Regex(""""picture"\s*[:=]\s*"([^"]+)"""")
    }
}
