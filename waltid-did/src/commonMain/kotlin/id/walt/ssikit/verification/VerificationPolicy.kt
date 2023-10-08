package id.walt.ssikit.verification

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
abstract class VerificationPolicy(
    open val name: String,
    @Transient open val description: String? = null
) {


}
