package id.waltid.openid4vp.wallet.presentation

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.w3c.PresentationBuilder
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.createKeyBindingJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive

object W3CPresenter {

    private val log = KotlinLogging.logger { }

    suspend fun presentW3C(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String
    ): JsonPrimitive {
        val selectedClaimsMap = matchResult.selectedDisclosures

        if (digitalCredential is SelectivelyDisclosableVerifiableCredential && digitalCredential.disclosables != null && digitalCredential.disclosables?.isNotEmpty() == true) {
            // This W3C JWT VC uses SD-JWT mechanism internally
            val disclosuresToPresent =
                selectedClaimsMap?.values?.mapNotNull {
                    when (it) {
                        is SdJwtSelectiveDisclosure -> it
                        is DcqlDisclosure -> digitalCredential.disclosures?.find { sdJwtDisclosure -> sdJwtDisclosure.name == it.name }
                        else -> null
                    }
                } ?: emptyList()
            log.debug { "Handling W3C JWT VC (${digitalCredential} with claims $disclosuresToPresent) with SD mechanism" }

            val disclosed = digitalCredential.disclose(digitalCredential, disclosuresToPresent)

            // Construct the Key Binding JWT for SD-JWT mechanism
            val kbJwtString = createKeyBindingJwt(
                disclosed = disclosed,
                nonce = authorizationRequest.nonce!!,
                audience = authorizationRequest.clientId,
                selectedDisclosures = disclosuresToPresent, // Pass the actual disclosures for sd_hash
                holderKey = holderKey
            )
            // Use the disclose method, appending the KB-JWT
            val sdPresentationString =
                disclosed + "~" + kbJwtString

            return JsonPrimitive(sdPresentationString)
        } else {
            // Standard W3C VP JWT wrapping this non-SD W3C VC JWT
            log.debug { "Handling standard W3C JWT VC (${digitalCredential})" }
            val w3cPresentationJwt = PresentationBuilder().apply {
                this.did = holderDid
                this.nonce = authorizationRequest.nonce!!
                this.audience = authorizationRequest.clientId
                addCredential(
                    JsonPrimitive(
                        digitalCredential.signed ?: error("Signed W3C VC JWT missing for $digitalCredential")
                    )
                )
                //addVerifiableCredentialJwt()
            }.buildAndSign(holderKey)

            return JsonPrimitive(w3cPresentationJwt)
        }
    }

}
