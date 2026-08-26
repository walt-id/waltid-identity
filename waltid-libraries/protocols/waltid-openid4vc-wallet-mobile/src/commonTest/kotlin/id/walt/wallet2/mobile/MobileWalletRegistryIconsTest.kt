package id.walt.wallet2.mobile

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobileWalletRegistryIconsTest {

    @Test
    fun prefersCredentialBackgroundImageOverLogoAndPortrait() = runTest {
        val fetched = mutableListOf<String>()
        val background = solidColorPng(0x12107C)
        val icon = MobileWalletRegistryIcons.resolveIconPng(
            metadata = displayMetadata(
                credentialBackgroundUri = "https://issuer.example/pid-bg.png",
                credentialLogoUri = "https://issuer.example/pid.png",
                issuerLogoUri = "https://issuer.example/issuer.png",
            ),
            credentialData = buildJsonObject {
                put("portrait", JsonPrimitive("not-an-image"))
            },
            displayName = "PID",
            fetchHttps = { uri ->
                fetched += uri
                if (uri.endsWith("pid-bg.png")) background else ByteArray(0)
            },
        )

        assertEquals(listOf("https://issuer.example/pid-bg.png"), fetched)
        assertContentEquals(background, icon)
    }

    @Test
    fun fallsBackToCredentialLogoThenIssuerLogo() = runTest {
        val fetched = mutableListOf<String>()
        val logo = solidColorPng(0x00AA00)
        val icon = MobileWalletRegistryIcons.resolveIconPng(
            metadata = displayMetadata(
                credentialBackgroundUri = "https://issuer.example/missing-bg.png",
                credentialLogoUri = "https://issuer.example/pid.png",
                issuerLogoUri = "https://issuer.example/issuer.png",
            ),
            credentialData = buildJsonObject {},
            displayName = "PID",
            fetchHttps = { uri ->
                fetched += uri
                if (uri.endsWith("pid.png")) logo else null
            },
        )

        assertEquals(
            listOf(
                "https://issuer.example/missing-bg.png",
                "https://issuer.example/pid.png",
            ),
            fetched,
        )
        assertContentEquals(logo, icon)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun usesPortraitClaimWhenDisplayArtIsMissing() = runTest {
        val portrait = solidColorPng(0xFF0000)
        val icon = MobileWalletRegistryIcons.resolveIconPng(
            metadata = null,
            credentialData = buildJsonObject {
                put("vct", "https://credentials.example/pid")
                put("portrait", JsonPrimitive(Base64.encode(portrait)))
            },
            displayName = "PID",
            fetchHttps = { error("display URIs should not be fetched") },
        )

        assertContentEquals(portrait, icon)
    }

    @Test
    fun usesMdocPortraitNamespace() = runTest {
        val portrait = solidColorPng(0x0000FF)
        val icon = MobileWalletRegistryIcons.resolveIconPng(
            metadata = null,
            credentialData = buildJsonObject {
                put(
                    "org.iso.18013.5.1",
                    buildJsonObject {
                        put(
                            "portrait",
                            buildJsonArray {
                                portrait.forEach { byte -> add(JsonPrimitive(byte.toInt() and 0xFF)) }
                            },
                        )
                    },
                )
            },
            displayName = "Driving licence",
            fetchHttps = { error("display URIs should not be fetched") },
        )

        assertContentEquals(portrait, icon)
    }

    @Test
    fun constructsColorSwatchWhenNoArtExists() = runTest {
        val icon = MobileWalletRegistryIcons.resolveIconPng(
            metadata = displayMetadata(backgroundColor = "#12107c"),
            credentialData = buildJsonObject { put("given_name", "Ada") },
            displayName = "Personal ID",
            fetchHttps = { error("no remote art should be fetched") },
        )

        assertTrue(icon.size >= 8)
        assertEquals(0x89.toByte(), icon[0])
        assertEquals(0x50.toByte(), icon[1])
        assertEquals(0x4E.toByte(), icon[2])
        assertEquals(0x47.toByte(), icon[3])
        assertContentEquals(solidColorPng(0x12107C), icon)
    }

    @Test
    fun parseCssRgbAcceptsCssColorLevel3Functions() {
        assertEquals(0x12107C, parseCssRgb("#12107c"))
        assertEquals(0xFF0080, parseCssRgb("rgb(255, 0, 128)"))
        assertEquals(0x00FF00, parseCssRgb("hsl(120, 100%, 50%)"))
        assertEquals(null, parseCssRgb("#11223344"))
    }

    private fun displayMetadata(
        credentialBackgroundUri: String? = null,
        credentialLogoUri: String? = null,
        issuerLogoUri: String? = null,
        backgroundColor: String? = null,
    ) = buildJsonObject {
        put(
            "issuerDisplay",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("name", "Example Issuer")
                        issuerLogoUri?.let { uri ->
                            put("logo", buildJsonObject { put("uri", uri) })
                        }
                    },
                )
            },
        )
        put(
            "credentialDisplay",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("name", "Personal ID")
                        backgroundColor?.let { put("background_color", it) }
                        credentialLogoUri?.let { uri ->
                            put("logo", buildJsonObject { put("uri", uri) })
                        }
                        credentialBackgroundUri?.let { uri ->
                            put("background_image", buildJsonObject { put("uri", uri) })
                        }
                    },
                )
            },
        )
    }
}
