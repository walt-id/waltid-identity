package id.walt.crypto2.signum

import id.walt.crypto2.keys.PlatformKeyConfiguration
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SignumPolicyCompatibilityTest {
    @Test fun existingPlatformDiscriminatorsRemainReadableAndStable() {
        for ((name, settings, fields) in listOf(
            Triple("Default", PlatformKeyConfiguration.Default, ""),
            Triple("AndroidKeystore", PlatformKeyConfiguration.AndroidKeystore(maxUsageCount = 3), ",\"maxUsageCount\":3"),
            Triple("IosKeychain", PlatformKeyConfiguration.IosKeychain(accessGroup = "fixture"), ",\"accessGroup\":\"fixture\""),
        )) {
            val original = "{\"platform\":{\"type\":\"id.walt.crypto2.signum.SignumPlatformPolicy.$name\"$fields}}"
            val policy = SignumKeyPolicy(platform = settings)
            assertEquals(policy, Json.decodeFromString<SignumKeyPolicy>(original))
            assertEquals(if (settings == PlatformKeyConfiguration.Default) "{}" else original, Json.encodeToString(policy))
            val complete = Json { encodeDefaults = true }.encodeToString(policy)
            assertEquals("id.walt.crypto2.signum.SignumPlatformPolicy.$name",
                Json.parseToJsonElement(complete).jsonObject.getValue("platform").jsonObject.getValue("type").jsonPrimitive.content)
        }
    }
}
