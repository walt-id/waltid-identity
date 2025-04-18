package id.walt.credentials.formats

import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import kotlinx.serialization.Serializable

@Serializable
sealed class AbstractW3C(
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {
    init {
        selfCheck()
    }
}

