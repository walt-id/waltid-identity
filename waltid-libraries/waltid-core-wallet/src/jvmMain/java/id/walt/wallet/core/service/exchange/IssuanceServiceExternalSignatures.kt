package id.walt.wallet.core.service.exchange

import id.walt.crypto.keys.Key
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.EntraIssuanceRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.wallet.core.manifest.extractor.EntraManifestExtractor
import id.walt.wallet.core.service.oidc4vc.TestCredentialWallet
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

object IssuanceServiceExternalSignatures : IssuanceServiceBase() {

    override val logger = KotlinLogging.logger { }

    suspend fun prepareExternallySignedOfferRequest(
        offerURL: String,
        credentialWallet: TestCredentialWallet,
        publicKey: Key,
        did: String,
        didAuthKeyId: String,
    ): PrepareExternalClaimResult {
        logger.debug { "// -------- WALLET: PREPARE STEP FOR OID4VCI WITH EXTERNAL SIGNATURES ----------" }
        logger.debug { "// parse credential URI" }
        val reqParams = parseOfferParams(offerURL)

        // entra or openid4vc credential offer
        val isEntra = EntraIssuanceRequest.isEntraIssuanceRequestUri(offerURL)
        return if (isEntra) {
            //TODO: Not yet implemented
            throw UnsupportedOperationException("MS Entra credential issuance requests with externally provided signatures are not supported yet")
        } else {
            processPrepareCredentialOffer(
                credentialWallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(reqParams)),
                credentialWallet,
                did,
                didAuthKeyId,
                publicKey,
            )
        }
    }

    private suspend fun processPrepareCredentialOffer(
        credentialOffer: CredentialOffer,
        credentialWallet: TestCredentialWallet,
        did: String,
        didAuthKeyId: String,
        publicKey: Key,
    ): PrepareExternalClaimResult {
        val providerMetadata = getCredentialIssuerOpenIDMetadata(
            credentialOffer.credentialIssuer,
            credentialWallet,
        )
        logger.debug { "providerMetadata: $providerMetadata" }

        logger.debug { "// resolve offered credentials" }
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        logger.debug { "offeredCredentials: $offeredCredentials" }
        require(offeredCredentials.isNotEmpty()) { "Resolved an empty list of offered credentials" }

        logger.debug { "// fetch access token using pre-authorized code (skipping authorization step)" }
        val tokenReq = TokenRequest.PreAuthorizedCode(
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode!!,
            clientId = did
        )

        val tokenResp = issueTokenRequest(
            providerMetadata.tokenEndpoint!!,
            tokenReq,
        )
        logger.debug { ">>> Token response is: $tokenResp" }
        validateTokenResponse(tokenResp)

        val offeredCredentialsProofRequests = offeredCredentials.map { offeredCredential ->
            OfferedCredentialProofOfPossessionParameters(
                offeredCredential,
                getOfferedCredentialProofOfPossessionParameters(
                    credentialOffer,
                    offeredCredential,
                    didAuthKeyId,
                    publicKey,
                    tokenResp.cNonce,
                ),
            )
        }
        return PrepareExternalClaimResult(
            resolvedCredentialOffer = credentialOffer,
            offeredCredentialsProofRequests = offeredCredentialsProofRequests,
            accessToken = tokenResp.accessToken,
        )
    }

    private suspend fun getOfferedCredentialProofOfPossessionParameters(
        credentialOffer: CredentialOffer,
        offeredCredential: OfferedCredential,
        didAuthKeyId: String,
        publicKey: Key,
        nonce: String?,
    ): ProofOfPossessionParameters {
        return ProofOfPossessionParameterFactory.new(
            didAuthKeyId,
            publicKey,
            isKeyProofRequiredForOfferedCredential(offeredCredential),
            offeredCredential,
            credentialOffer,
            nonce,
        )
    }

    suspend fun submitExternallySignedOfferRequest(
        offerURL: String,
        credentialIssuerURL: String,
        credentialWallet: TestCredentialWallet,
        offeredCredentialProofsOfPossession: List<OfferedCredentialProofOfPossession>,
        accessToken: String?,
    ): List<CredentialDataResult> {
        val isEntra = EntraIssuanceRequest.isEntraIssuanceRequestUri(offerURL)
        val processedCredentialOffers = if (isEntra) {
            //TODO: Not yet implemented
            throw UnsupportedOperationException("MS Entra credential issuance requests with externally provided signatures are not supported yet")
        } else {
            submitExternallySignedOID4VCICredentialRequests(
                credentialIssuerURL,
                credentialWallet,
                offeredCredentialProofsOfPossession,
                accessToken,
            )
        }
        logger.debug { "// parse and verify credential(s)" }
        check(processedCredentialOffers.any { it.credentialResponse.credential != null }) { "No credential was returned from credentialEndpoint: $processedCredentialOffers" }

        val manifest = if (isEntra) EntraManifestExtractor().extract(offerURL) else null
        return processedCredentialOffers.map {
            getCredentialData(it, manifest)
        }
    }

    private suspend fun submitExternallySignedOID4VCICredentialRequests(
        credentialIssuerURL: String,
        credentialWallet: TestCredentialWallet,
        offeredCredentialProofsOfPossession: List<OfferedCredentialProofOfPossession>,
        accessToken: String?,
    ): List<ProcessedCredentialOffer> {
        logger.debug { "// get issuer metadata" }
        val providerMetadata = getCredentialIssuerOpenIDMetadata(
            credentialIssuerURL,
            credentialWallet,
        )
        logger.debug { "providerMetadata: $providerMetadata" }
        logger.debug { "Using issuer URL: $credentialIssuerURL" }
        val credReqs = offeredCredentialProofsOfPossession.map { offeredCredentialProofOfPossession ->
            val offeredCredential = offeredCredentialProofOfPossession.offeredCredential
            logger.info { "Offered credential format: ${offeredCredential.format.name}" }
            logger.info {
                "Offered credential cryptographic binding methods: ${
                    offeredCredential.cryptographicBindingMethodsSupported?.joinToString(
                        ", "
                    ) ?: ""
                }"
            }
            CredentialRequest.forOfferedCredential(
                offeredCredential = offeredCredential,
                proof = offeredCredentialProofOfPossession.toProofOfPossession(),
            )
        }
        logger.debug { "credReqs: $credReqs" }

        require(credReqs.isNotEmpty()) { "No credentials offered" }
        return CredentialOfferProcessor.process(credReqs, providerMetadata, accessToken!!)
    }

    @Serializable
    data class PrepareExternalClaimResult(
        val resolvedCredentialOffer: CredentialOffer,
        val offeredCredentialsProofRequests: List<OfferedCredentialProofOfPossessionParameters>,
        val accessToken: String?,
    )

    @Serializable
    data class OfferedCredentialProofOfPossessionParameters(
        val offeredCredential: OfferedCredential,
        val proofOfPossessionParameters: ProofOfPossessionParameters,
    )

    @Serializable
    data class OfferedCredentialProofOfPossession(
        val offeredCredential: OfferedCredential,
        val proofType: ProofType,
        val signedProofOfPossession: String,
    ) {
        fun toProofOfPossession() = when (proofType) {
            ProofType.cwt -> {
                ProofOfPossession.CWTProofBuilder("").build(signedProofOfPossession)
            }

            ProofType.ldp_vp -> TODO("ldp_vp proof not yet implemented")
            else -> {
                ProofOfPossession.JWTProofBuilder("").build(signedProofOfPossession)
            }
        }
    }
}
