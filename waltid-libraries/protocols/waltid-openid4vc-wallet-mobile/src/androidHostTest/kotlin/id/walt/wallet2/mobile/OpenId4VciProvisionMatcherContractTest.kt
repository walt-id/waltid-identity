package id.walt.wallet2.mobile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behavioral contract for the vendored `provision_hardcoded.wasm` matcher.
 *
 * Credential Manager executes the WASM with host imports that unit tests cannot supply. This suite
 * therefore (1) asserts the shipped binary still encodes the OpenID4VCI protocol strings the C
 * provision matcher compares against, and (2) mirrors `matcher/issuance/provision.c` matching
 * against the same creation-options database layout production registers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenId4VciProvisionMatcherContractTest {
    private val registry = AndroidDigitalCredentialRegistry(RuntimeEnvironment.getApplication())
    private val assets = RuntimeEnvironment.getApplication().assets

    @Test
    fun shippedWasmEncodesOpenId4VciProtocolLiterals() {
        val wasm = assets.open("id/walt/wallet2/mobile/provision_hardcoded.wasm").use { it.readBytes() }
        val latin1 = wasm.toString(Charsets.ISO_8859_1)
        assertTrue(latin1.contains("openid4vci-v1"), "WASM lost the openid4vci-v1 protocol literal")
        assertTrue(latin1.contains("openid4vci1.0"), "WASM lost the historical openid4vci1.0 literal")
    }

    @Test
    fun provisionMatcherContractMatchesOpenId4VciV1AgainstCreationOptions() {
        val icon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val creationOptions = registry.encodeOpenId4VciCreationOptions(
            title = "Demo Wallet",
            subtitle = "Save a credential",
            iconPng = icon,
        )
        val request = buildJsonObject {
            putJsonArray("requests") {
                add(
                    buildJsonObject {
                        put("protocol", "openid4vci-v1")
                        putJsonObject("data") {
                            put("credential_issuer", "https://issuer.example")
                            putJsonArray("credential_configuration_ids") { add(JsonPrimitive("pid")) }
                        }
                    },
                )
            }
        }

        val match = mirrorProvisionMatcher(creationOptions, request)
        assertNotNull(match)
        assertEquals("Demo Wallet", match.title)
        assertEquals("Save a credential", match.subtitle)
        assertTrue(match.icon.contentEquals(icon))
    }

    @Test
    fun provisionMatcherContractRejectsUnsupportedProtocols() {
        val creationOptions = registry.encodeOpenId4VciCreationOptions(
            title = "Demo Wallet",
            subtitle = null,
            iconPng = byteArrayOf(1, 2, 3),
        )
        val request = buildJsonObject {
            putJsonArray("requests") {
                add(
                    buildJsonObject {
                        put("protocol", "openid4vp-v1-unsigned")
                        putJsonObject("data") { put("nonce", "n") }
                    },
                )
            }
        }

        assertEquals(null, mirrorProvisionMatcher(creationOptions, request))
    }

    /**
     * Mirrors the decision loop in CMWallet `matcher/issuance/provision.c` for the creation-options
     * database produced by [AndroidDigitalCredentialRegistry.encodeOpenId4VciCreationOptions].
     */
    private fun mirrorProvisionMatcher(
        creationOptions: ByteArray,
        dcRequest: JsonObject,
    ): MatchedCreationOption? {
        val jsonOffset = ByteBuffer.wrap(creationOptions, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val creds = Json.parseToJsonElement(
            creationOptions.copyOfRange(jsonOffset, creationOptions.size).decodeToString(),
        ).jsonObject
        val display = creds["display"]?.jsonObject ?: return null
        val title = display["title"]?.jsonPrimitive?.content ?: return null
        val subtitle = display["subtitle"]?.jsonPrimitive?.content
        val iconMeta = display["icon"]?.jsonObject
        val icon = if (iconMeta != null) {
            val start = iconMeta["start"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val length = iconMeta["length"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            creationOptions.copyOfRange(start, start + length)
        } else {
            byteArrayOf()
        }

        val requests = dcRequest["requests"] as? JsonArray ?: return null
        for (element in requests) {
            val request = element.jsonObject
            val protocol = request["protocol"]?.jsonPrimitive?.content ?: continue
            if (protocol != "openid4vci-v1" && protocol != "openid4vci1.0") continue
            val offer = request["data"]?.jsonObject ?: continue
            val issuer = offer["credential_issuer"]?.jsonPrimitive?.content ?: continue
            val capabilities = creds["capabilities"]?.jsonObject
            if (capabilities != null && !capabilities.containsKey(issuer)) continue
            return MatchedCreationOption(title = title, subtitle = subtitle, icon = icon)
        }
        return null
    }

    private data class MatchedCreationOption(
        val title: String,
        val subtitle: String?,
        val icon: ByteArray,
    )
}
