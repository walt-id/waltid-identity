package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
data class IACASignerData(
    val iacaKey: JsonElement,
    val certificateData: IACACertificateData,
)

