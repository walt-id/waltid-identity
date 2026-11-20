package id.waltid.openid4vp.wallet.presentation

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.sdjwt.SDJwt
import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.dcql.DcqlMatcher
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.transactiondata.filterTransactionDataForCredentialId
import id.walt.verifier.openid.transactiondata.decodeList
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.createKeyBindingJwt
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import id.waltid.openid4vp.wallet.supportedPresentationAlgorithms
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonPrimitive

object SdJwtVcPresenter {

    private val log = KotlinLogging.logger { }

    @Deprecated("Use the Crypto2Key overload")
    suspend fun presentSdJwtVc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String?,
        holderBindingAudience: String? = null,
    ): JsonPrimitive = presentSdJwtVcWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        holderKey,
        holderDid,
        null,
        holderBindingAudience,
    )

    suspend fun presentSdJwtVc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Crypto2Key,
        holderDid: String?,
        holderBindingAudience: String? = null,
    ): JsonPrimitive = presentSdJwtVcWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        null,
        holderDid,
        holderKey,
        holderBindingAudience,
    )

    @Deprecated("Use the Crypto2Key overload")
    suspend fun presentSdJwtVc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String?,
        holderCrypto2Key: Crypto2Key?,
        holderBindingAudience: String? = null,
    ): JsonPrimitive = presentSdJwtVcWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        holderKey,
        holderDid,
        holderCrypto2Key,
        holderBindingAudience,
    )

    internal suspend fun presentSdJwtVcAuthorized(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key?,
        holderDid: String?,
        holderCrypto2Key: Crypto2Key?,
        holderBindingAudience: String?,
        scaAuthorizer: ScaPresentationAuthorizer?,
    ): JsonPrimitive = presentSdJwtVcWithKey(
        digitalCredential, matchResult, authorizationRequest, holderKey, holderDid,
        holderCrypto2Key, holderBindingAudience, scaAuthorizer,
    )

    /**
     * Reconstructs the claims carried by this effective selection without creating a key-binding JWT,
     * accessing a holder key or sending a response. [matchResult] must already reflect the selected
     * credential and optional claims. Withheld descendants and unmatched decoy digests stay absent.
     *
     * Array positions in this result are received positions; controls must retain their original
     * matcher paths rather than treating a compacted array index as a stored-credential location.
     */
    fun previewClaims(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
    ): JsonObject = preview(digitalCredential, matchResult).claims

    /** Received values and the original identities needed by selection controls and issuer metadata. */
    fun preview(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
    ): PresentationClaimsProjection {
        val prepared = prepareDisclosure(digitalCredential, matchResult)
        val parsed = SDJwt.parse(prepared.serialized)
        val claims = parsed.fullPayload // Canonical reconstruction is authoritative, never the stored full data.
        val selective = digitalCredential as SelectivelyDisclosableVerifiableCredential
        val available = selective.disclosures.orEmpty()
        val claimPaths = matchResult.disclosuresByPath.keys + matchResult.selectedDisclosures.orEmpty().keys
        val byClaimPath = claimPaths.associateWith { path ->
            selective.disclosuresToPresent(matchResult.copy(
                disclosuresByPath = mapOf(path to matchResult.disclosuresByPath[path].orEmpty()),
                selectedDisclosures = matchResult.selectedDisclosures?.filterKeys { it == path },
            )).mapTo(mutableSetOf()) { it.encoded }
        }
        val locations = sdJwtClaimLocations(parsed.sdPayload.undisclosedPayload, available, prepared.disclosures, byClaimPath)
        val receivedPaths = mutableSetOf<List<JsonElement>>()
        fun collect(value: JsonElement, path: List<JsonElement>) {
            receivedPaths += path
            when (value) {
                is JsonObject -> value.forEach { (key, child) -> collect(child, path + JsonPrimitive(key)) }
                is JsonArray -> value.forEachIndexed { index, child -> collect(child, path + JsonPrimitive(index)) }
                else -> Unit
            }
        }
        collect(claims, emptyList())
        check(locations.size == receivedPaths.size && locations.mapTo(mutableSetOf()) { it.receivedPath } == receivedPaths) {
            "Cannot reliably map presented claims to their original credential paths"
        }
        return PresentationClaimsProjection(claims, false, locations)
    }

    private class PreparedDisclosure(
        val serialized: String,
        val disclosures: List<SdJwtSelectiveDisclosure>,
    )

    private fun prepareDisclosure(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
    ): PreparedDisclosure {
        val selective = digitalCredential as? SelectivelyDisclosableVerifiableCredential
            ?: error("Expected a selectively disclosable credential")
        val disclosures = selective.disclosuresToPresent(matchResult)
        return PreparedDisclosure(selective.disclose(digitalCredential, disclosures), disclosures)
    }

    private suspend fun presentSdJwtVcWithKey(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key?,
        holderDid: String?,
        holderCrypto2Key: Crypto2Key?,
        holderBindingAudience: String? = null,
        scaAuthorizer: ScaPresentationAuthorizer? = null,
    ): JsonPrimitive {
        val prepared = prepareDisclosure(digitalCredential, matchResult)
        val disclosuresToPresent = prepared.disclosures
        val disclosed = prepared.serialized

        // Construct the Key Binding JWT
        val acceptedAlgorithms = authorizationRequest.supportedPresentationAlgorithms(
            WalletPresentationFormatRegistry.SupportedFormat.DC_SD_JWT,
            "kb-jwt_alg_values",
        )
        val transactionData = filterTransactionDataForCredentialId(
            transactionData = authorizationRequest.transactionData,
            credentialId = matchResult.originalQuery.id,
        )
        val scaContext = if (decodeList(transactionData.orEmpty()).any { it.transactionData.type == TS12_PAYMENT_TYPE }) {
            ScaKeyBindingContext(
                credentialId = matchResult.credential.id,
                responseMode = requireNotNull(authorizationRequest.responseMode) { "TS12 requires response_mode" },
                authorizer = requireNotNull(scaAuthorizer) { "TS12 requires authentication evidence for this presentation" },
            )
        } else null
        // DC API binds the holder to the platform-asserted origin, not to client_id.
        val audience = holderBindingAudience ?: authorizationRequest.clientId
        val kbJwtString = holderCrypto2Key?.let {
            createKeyBindingJwt(
                disclosed,
                authorizationRequest.nonce!!,
                audience,
                disclosuresToPresent,
                it,
                transactionData,
                acceptedAlgorithms,
                scaContext = scaContext,
            )
        } ?: createKeyBindingJwt(
            disclosed,
            authorizationRequest.nonce!!,
            audience,
            disclosuresToPresent,
            requireNotNull(holderKey),
            transactionData,
            acceptedAlgorithms,
            scaContext = scaContext,
        )

        // Use the disclose method from the interface, then append the KB-JWT
        val finalPresentationString = "$disclosed${if (!disclosed.endsWith("~")) "~" else ""}$kbJwtString"
        log.trace { "Final presentation string for dc+sd-jwt is: $finalPresentationString" }

        return JsonPrimitive(finalPresentationString)
    }

}
