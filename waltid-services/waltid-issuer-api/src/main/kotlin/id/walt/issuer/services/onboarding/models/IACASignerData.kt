package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject


@Serializable
data class IACASignerData(
    val iacaKey: JsonObject,
    val certificateData: IACACertificateData,
)

