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
    fun rejectsServiceAvailableWhileDeviceIsLocked() {
        val failure = validateAndroidMdocNfcServiceMetadata(
            validMetadata().copy(requiresDeviceUnlock = false),
        )

        assertEquals("nfc_service_invalid", failure?.code)
    }

    @Test
    fun rejectsApplicationsSplitAcrossGroups() {
        val failure = validateAndroidMdocNfcServiceMetadata(
            validMetadata().copy(
                aidGroups = listOf(
                    validGroup().copy(aids = setOf("D2760000850101")),
                    validGroup().copy(aids = setOf("A0000002480400", "A0000002480401")),
                ),
            ),
        )

        assertEquals("nfc_service_invalid", failure?.code)
    }

    @Test
    fun rejectsPaymentCategory() {
        val failure = validateAndroidMdocNfcServiceMetadata(
            validMetadata().copy(aidGroups = listOf(validGroup().copy(category = "payment"))),
        )

        assertEquals("nfc_service_invalid", failure?.code)
    }

    @Test
    fun rejectsGroupWithoutUserDescription() {
        val failure = validateAndroidMdocNfcServiceMetadata(
            validMetadata().copy(aidGroups = listOf(validGroup().copy(hasDescription = false))),
        )

        assertEquals("nfc_service_invalid", failure?.code)
    }

    @Test
    fun reportsMissingApplicationIdentifierPrecisely() {
        val failure = validateAndroidMdocNfcServiceMetadata(
            validMetadata().copy(
                aidGroups = listOf(
                    validGroup().copy(aids = ANDROID_MDOC_REQUIRED_AIDS - "A0000002480401"),
                ),
            ),
        )

        assertEquals("nfc_aids_missing", failure?.code)
    }

    @Test
    fun rejectsUnownedApplicationIdentifierInDedicatedGroup() {
        val failure = validateAndroidMdocNfcServiceMetadata(
            validMetadata().copy(
                aidGroups = listOf(
                    validGroup().copy(aids = ANDROID_MDOC_REQUIRED_AIDS + "F00102030405"),
                ),
            ),
        )

        assertEquals("nfc_service_invalid", failure?.code)
    }

    private fun validMetadata() = AndroidNfcServiceMetadata(
        requiresDeviceUnlock = true,
        aidGroups = listOf(validGroup()),
    )

    private fun validGroup() = AndroidNfcAidGroupMetadata(
        category = "other",
        hasDescription = true,
        aids = ANDROID_MDOC_REQUIRED_AIDS,
    )
}
