@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import io.klogging.Klogging
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@TestMethodOrder(OrderAnnotation::class)
class WalletSettingsIntegrationTest : AbstractIntegrationTest(), Klogging {

    @Order(0)
    @Test
    fun shouldGetDefaultSettings() = runTest {
        val settings = defaultWalletApi.getSettings()
        assertNotNull(settings)
        logger.info("Default settings: ${settings.settings}")
    }

    @Order(1)
    @Test
    fun shouldUpdateSettings() = runTest {
        val newSettings = buildJsonObject {
            put("theme", "dark")
            put("language", "en")
            put("notifications", buildJsonObject {
                put("email", true)
                put("push", false)
            })
        }
        defaultWalletApi.updateSettings(newSettings)
        logger.info("Settings updated successfully")
    }

    @Order(2)
    @Test
    fun shouldRetrieveUpdatedSettings() = runTest {
        val settings = defaultWalletApi.getSettings()
        assertNotNull(settings)
        assertEquals("dark", settings.settings["theme"]?.jsonPrimitive?.content)
        assertEquals("en", settings.settings["language"]?.jsonPrimitive?.content)
        val notifications = settings.settings["notifications"]?.jsonObject
        assertNotNull(notifications)
        assertEquals(true, notifications["email"]?.jsonPrimitive?.boolean)
        assertEquals(false, notifications["push"]?.jsonPrimitive?.boolean)
    }

    @Order(3)
    @Test
    fun shouldUpdatePartialSettings() = runTest {
        val partialSettings = buildJsonObject {
            put("theme", "light")
        }
        defaultWalletApi.updateSettings(partialSettings)
        val settings = defaultWalletApi.getSettings()
        assertEquals("light", settings.settings["theme"]?.jsonPrimitive?.content)
    }

    @Order(4)
    @Test
    fun shouldHandleComplexSettings() = runTest {
        val complexSettings = buildJsonObject {
            put("preferences", buildJsonObject {
                put("autoAccept", false)
                put("defaultDid", "did:key:example")
                putJsonArray("trustedIssuers") {
                    add("issuer1")
                    add("issuer2")
                }
            })
            put("display", buildJsonObject {
                put("credentialView", "list")
                put("showExpired", true)
            })
        }
        defaultWalletApi.updateSettings(complexSettings)
        val settings = defaultWalletApi.getSettings()
        assertNotNull(settings.settings["preferences"])
        assertNotNull(settings.settings["display"])
        val trustedIssuers = settings.settings["preferences"]?.jsonObject?.get("trustedIssuers")?.jsonArray
        assertNotNull(trustedIssuers)
        assertEquals(2, trustedIssuers.size)
    }

    @Order(5)
    @Test
    fun shouldResetToEmptySettings() = runTest {
        val emptySettings = buildJsonObject { }
        defaultWalletApi.updateSettings(emptySettings)
        val settings = defaultWalletApi.getSettings()
        assertTrue(settings.settings.isEmpty(), "Settings should be empty after reset")
    }
}
