package id.walt.commons.featureflag

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FeatureManagerDeprecatedFlagsTest {

    @BeforeTest
    fun setUp() = FeatureManager.preclear()

    @AfterTest
    fun tearDown() = FeatureManager.preclear()

    @Test
    fun `deprecated flags are ignored instead of throwing`() {
        FeatureManager.registerDeprecatedFeatures(listOf("issuer2"))

        assertNull(FeatureManager.configuredFeature("issuer2", "enable"))
        assertNull(FeatureManager.configuredFeature("issuer2", "disable"))
    }

    @Test
    fun `unknown flags that are not deprecated still throw`() {
        val exception = assertFailsWith<IllegalStateException> {
            FeatureManager.configuredFeature("not-a-real-flag", "enable")
        }

        assertEquals(
            "Could not enable feature \"not-a-real-flag\" as it's not loaded/registered by any catalog. Registered features are: []",
            exception.message
        )
    }

    @Test
    fun `registered features are resolved even when also marked deprecated`() {
        val feature = OptionalFeature(name = "issuer2", description = "test", default = true)
        FeatureManager.registerFeature(feature)
        FeatureManager.registerDeprecatedFeatures(listOf("issuer2"))

        assertEquals(feature, FeatureManager.configuredFeature("issuer2", "enable"))
    }
}
