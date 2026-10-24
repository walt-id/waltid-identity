package id.walt.mdoc.proximity.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidNfcServiceMetadataTest {
    @Test
    fun acceptsOneLockedOtherCategoryGroupWithExactlyTheSupportedApplications() {
        assertNull(validateAndroidMdocNfcServiceMetadata(validMetadata()))
    }

    @Test
    fun rejectsInvalidServiceMetadata() {
        val cases = mapOf(
            "available while locked" to validMetadata().copy(requiresDeviceUnlock = false),
            "applications split across groups" to validMetadata().copy(
                aidGroups = listOf(
                    validGroup().copy(aids = setOf("D2760000850101")),
                    validGroup().copy(aids = setOf("A0000002480400", "A0000002480401")),
                ),
            ),
            "payment category" to validMetadata().copy(
                aidGroups = listOf(validGroup().copy(category = "payment")),
            ),
            "missing user description" to validMetadata().copy(
                aidGroups = listOf(validGroup().copy(hasDescription = false)),
            ),
            "unowned application identifier" to validMetadata().copy(
                aidGroups = listOf(validGroup().copy(aids = supportedAids + "F00102030405")),
            ),
        )
        for ((name, metadata) in cases) {
            assertEquals("nfc_service_invalid", validateAndroidMdocNfcServiceMetadata(metadata)?.code, name)
        }
    }

    @Test
    fun reportsMissingApplicationIdentifierPrecisely() {
        val failure = validateAndroidMdocNfcServiceMetadata(
            validMetadata().copy(
                aidGroups = listOf(
                    validGroup().copy(aids = supportedAids - "A0000002480401"),
                ),
            ),
        )

        assertEquals("nfc_aids_missing", failure?.code)
    }

    private fun validMetadata() = AndroidNfcServiceMetadata(
        requiresDeviceUnlock = true,
        aidGroups = listOf(validGroup()),
    )

    private fun validGroup() = AndroidNfcAidGroupMetadata(
        category = "other",
        hasDescription = true,
        aids = supportedAids,
    )

    private val supportedAids = setOf("D2760000850101", "A0000002480400", "A0000002480401")
}
