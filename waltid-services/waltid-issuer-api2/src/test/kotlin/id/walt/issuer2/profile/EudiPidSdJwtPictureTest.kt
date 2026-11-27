package id.walt.issuer2.profile

import com.typesafe.config.ConfigFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private fun pictureFromProfile(path: Path): String =
        ConfigFactory.parseFile(path.toFile()).getString("profiles.eudiPidSdJwt.credentialData.picture")

    private fun pictureFromMarkdown(path: Path): String {
        val credentialData = Files.readAllLines(path)
            .dropWhile { it != "## Credential Data" }
            .dropWhile { it != "```json" }
            .drop(1)
            .takeWhile { it != "```" }
            .joinToString("\n")
        return Json.parseToJsonElement(credentialData).jsonObject.getValue("picture").jsonPrimitive.content
    }

    private fun issuer2ConfigDir(): Path =
        listOf(
            Path.of("config"),
            Path.of("waltid-services/waltid-issuer-api2/config"),
            Path.of("waltid-identity/waltid-services/waltid-issuer-api2/config"),
        )
            .map { it.toAbsolutePath().normalize() }
            .firstOrNull { Files.isRegularFile(it.resolve("issuer2-profiles.conf")) }
            ?: error("Could not locate waltid-issuer-api2 config directory")
}
