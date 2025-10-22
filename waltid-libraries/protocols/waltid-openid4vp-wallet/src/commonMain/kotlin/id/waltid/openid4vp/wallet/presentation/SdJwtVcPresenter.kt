package id.waltid.openid4vp.wallet.presentation

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.createKeyBindingJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive

object SdJwtVcPresenter {

    private val log = KotlinLogging.logger {  }

    suspend fun presentSdJwtVc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String
    ): JsonPrimitive {
        val selectedClaimsMap = matchResult.selectedDisclosures

        val sdJwtCredential = digitalCredential as? SelectivelyDisclosableVerifiableCredential
            ?: error("Mismatch: Expected SelectivelyDisclosableVerifiableCredential for DC_SD_JWT format for $digitalCredential")

        log.trace { "Selected claims: ${selectedClaimsMap?.values?.map { it.toString() + " (${it::class.simpleName})" }}" }
        val disclosuresToPresent =
            selectedClaimsMap?.values?.mapNotNull {
                when (it) {
                    is SdJwtSelectiveDisclosure -> it
                    is DcqlDisclosure -> {
                        sdJwtCredential.disclosures?.find { sdJwtDisclosure -> sdJwtDisclosure.name == it.name }
                    }

                    else -> null
                }
            } ?: emptyList()

        log.debug { "Handling IETF SD-JWT VC (${digitalCredential} with disclosures $disclosuresToPresent)" }

        val disclosed = sdJwtCredential.disclose(digitalCredential, disclosuresToPresent)

        // Construct the Key Binding JWT
        val kbJwtString = createKeyBindingJwt(
            disclosed = disclosed,
            nonce = authorizationRequest.nonce!!,
            audience = authorizationRequest.clientId,
            selectedDisclosures = disclosuresToPresent,
            holderKey = holderKey
        )

        // Use the disclose method from the interface, then append the KB-JWT
        val finalPresentationString = "$disclosed~$kbJwtString"
        log.trace { "Final presentation string for dc+sd-jwt is: $finalPresentationString" }

        return JsonPrimitive(finalPresentationString)
    }

}
