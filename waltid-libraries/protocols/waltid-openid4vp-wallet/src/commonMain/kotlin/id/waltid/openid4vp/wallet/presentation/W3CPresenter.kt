package id.waltid.openid4vp.wallet.presentation

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.transactiondata.filterTransactionDataForCredentialId
import id.walt.w3c.PresentationBuilder
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.createKeyBindingJwt
import id.waltid.openid4vp.wallet.WalletCrypto2KeyAdapter
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import id.waltid.openid4vp.wallet.supportedPresentationAlgorithms
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive

object W3CPresenter {

    private val log = KotlinLogging.logger { }

    @Deprecated("Use the Crypto2Key overload")
    suspend fun presentW3C(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String,
    ): JsonPrimitive = presentW3CWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        holderKey,
        holderDid,
        null,
    )

    suspend fun presentW3C(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Crypto2Key,
        holderDid: String,
    ): JsonPrimitive = presentW3CWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        null,
        holderDid,
        holderKey,
    )

    @Deprecated("Use the Crypto2Key overload")
    suspend fun presentW3C(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String,
        holderCrypto2Key: Crypto2Key?,
    ): JsonPrimitive = presentW3CWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        holderKey,
        holderDid,
        holderCrypto2Key,
    )

    private suspend fun presentW3CWithKey(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key?,
        holderDid: String,
        holderCrypto2Key: Crypto2Key?,
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
            val acceptedAlgorithms = authorizationRequest.supportedPresentationAlgorithms(
                WalletPresentationFormatRegistry.SupportedFormat.JWT_VC_JSON,
                "alg_values",
            )
            val transactionData = filterTransactionDataForCredentialId(
                transactionData = authorizationRequest.transactionData,
                credentialId = matchResult.originalQuery.id,
            )
            val kbJwtString = holderCrypto2Key?.let {
                createKeyBindingJwt(
                    disclosed,
                    authorizationRequest.nonce!!,
                    authorizationRequest.clientId,
                    disclosuresToPresent,
                    it,
                    transactionData,
                    acceptedAlgorithms,
                )
            } ?: createKeyBindingJwt(
                disclosed,
                authorizationRequest.nonce!!,
                authorizationRequest.clientId,
                disclosuresToPresent,
                requireNotNull(holderKey),
                transactionData,
                acceptedAlgorithms,
            )
            // Use the disclose method, appending the KB-JWT
            val sdPresentationString = "$disclosed${if (disclosed.endsWith("~")) "" else "~"}$kbJwtString"

            return JsonPrimitive(sdPresentationString)
        } else {
            // Standard W3C VP JWT wrapping this non-SD W3C VC JWT
            log.debug { "Handling standard W3C JWT VC (${digitalCredential})" }
            val presentationBuilder = PresentationBuilder().apply {
                this.did = holderDid
                this.nonce = authorizationRequest.nonce!!
                this.audience = authorizationRequest.clientId
                addCredential(
                    JsonPrimitive(
                        digitalCredential.signed ?: error("Signed W3C VC JWT missing for $digitalCredential")
                    )
                )
                //addVerifiableCredentialJwt()
            }
            val crypto2Key = holderCrypto2Key ?: holderKey?.let { WalletCrypto2KeyAdapter.signingKey(it) }
            val acceptedAlgorithms = authorizationRequest.supportedPresentationAlgorithms(
                WalletPresentationFormatRegistry.SupportedFormat.JWT_VC_JSON,
                "alg_values",
            )
            val crypto2Algorithm = crypto2Key?.selectJwsAlgorithm(acceptedAlgorithms)
            val signingAlgorithm = crypto2Algorithm?.identifier ?: requireNotNull(holderKey).keyType.jwsAlg.also { legacyAlgorithm ->
                acceptedAlgorithms?.let {
                    require(legacyAlgorithm in it) { "Verifier does not support W3C VP algorithm $legacyAlgorithm" }
                }
            }
            val w3cPresentationJwt = crypto2Key?.let {
                presentationBuilder.buildAndSign(it, requireNotNull(crypto2Algorithm))
            } ?: presentationBuilder.buildAndSign(requireNotNull(holderKey))

            return JsonPrimitive(w3cPresentationJwt)
        }
    }

}
