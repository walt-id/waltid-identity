package id.walt.openid4vci.mdoc

import kotlinx.serialization.Serializable

@Serializable
data class MsoData(
    val validFrom: String? = null,
    val validUntil: String? = null,
    val expectedUpdate: String? = null,
) {
    fun merge(override: MsoData?): MsoData =
        if (override == null) this
        else MsoData(
            validFrom = override.validFrom ?: validFrom,
            validUntil = override.validUntil ?: validUntil,
            expectedUpdate = override.expectedUpdate ?: expectedUpdate,
        )

    fun isEmpty(): Boolean = validFrom == null && validUntil == null && expectedUpdate == null
}
