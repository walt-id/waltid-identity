package id.walt.wallet

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.did.helpers.WaltidServices
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object CoreWalletOpenId4VCI {


    /*
       The Wallet should check if it should create a proof.

       The scenario where proof_types_supported is empty or missing but the issuer still sends a c_nonce needs to be handled explicitly. Since proof is optional in OID4VCI, we need to carefully distinguish between:
          - Issuer explicitly supporting certain proof types (proof_types_supported is present).
          - Issuer not specifying any proof type (proof_types_supported is empty).
          - Issuer sending a c_nonce despite not specifying proof support (likely for replay attack protection).

        Possible cases:
          A. If proof_types_supported = "true", wallet must send proof.
          B. If proof_types_supported = "false" AND c_nonce is not provided, wallet skips proof.
          C. If proof_types_supported = "false" BUT c_nonce is provided, wallet should still generate proof to include the c_nonce (for replay protection).

        Steps:
        1. The wallet should check if there is the proof_types_supported in credential_configurations_supported(in Issuer Metadata), if its there it checks for the proof_type (i.e. jwt, ctw or ldp_vp) - currently the issuer implementation is does not contain this
            {
                 "cryptographic_binding_methods_supported": ["did:example", "jwk", "cose_key],
                 "proof_types_supported": [
                   {
                     "proof_type": "jwt",
                     "proof_signing_alg_values_supported": ["ES256", "EdDSA", "ES256K"]
                   },
                   {
                     "proof_type": "cwt",
                     "proof_signing_alg_values_supported": ["ES256"]
                   }
                 ]
               }


           It selects a proof type that both the issuer and wallet support (e.g., jwt or cwt).
           If proof_types_supported is defined and not empty → Select a supported proof type (jwt, cwt, etc.).
           If proof_types_supported is empty or missing:
               If c_nonce is provided, the wallet must still generate proof, but it must guess the proof type based on its own capabilities and common standards (e.g., jwt).
               If c_nonce is not provided, wallet skips proof since it's not required.

        2. When the proofType is defined we need to check the `proof_signing_alg_values_supported` (i.e. ES256) and choose a supported algorithm
           If a proof type was selected, choose a compatible algorithm from proof_signing_alg_values_supported.
           If no supported algorithm is found, we should stop with an error (but maybe for compatibility??).


        3. Then, the wallet should check the cryptographic_binding_methods_supported to understand with which key material will sign the proof (i.e. keys in JWK format `jwk`, keys expressed as a COSE Key `cose_key` or a specific did method, (e.g did:key), currently the issuer implementation is wrong
           If cryptographic_binding_methods_supported is specified, select a compatible method (did, jwk, etc.).
           If missing, default to a commonly supported method (e.g., did, jwk).
           If no compatible method is found, stop with an error (but maybe for compatibility??).

               sealed class ProofHandlingResult {
                   object SkipProof : ProofHandlingResult()  // No proof required, skip this step
                   object ProceedWithJWT : ProofHandlingResult() // Continue with JWT-based proof flow
                   data class TodoHandleOtherProofs(val proofTypes: List<ProofType>) : ProofHandlingResult() // Handle non-JWT proofs
                   object ProceedWithChosenProof : ProofHandlingResult() // Proof is not required, but c_nonce exists → choose proof or Continue with JWT-based proof flow
               }
       */

    suspend fun resolveCredentialOffer(credentialOfferUrl: String): CredentialOffer =
        OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(credentialOfferUrl)

    suspend fun getProviderMetadataForOffer(credentialOffer: CredentialOffer): OpenIDProviderMetadata =
        OpenID4VCI.resolveCIProviderMetadata(credentialOffer)

    // Step 3:
    suspend fun requestToken(credentialOffer: CredentialOffer, providerMetadata: OpenIDProviderMetadata): TokenResponse {
        // The Wallet API checks if the offer has a preauthorized or authorization code (check GrantTypes.isAvailableIn() or so)
        // Its Pre-Authorized

        check(credentialOffer.grants.contains(GrantType.pre_authorized_code.value)) // TODO

        // ----------

        // The Wallet API constructs the Token the Request as follows (considering make this with type safety with sealed classed (e.g. TokenRequest() -> AuthorizationCode(), PreAuthorizedCode()) :
        val tokenRequest = TokenRequest.PreAuthorizedCode(
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode!!,
            clientId = null,  // The Wallet API should check for token_endpoint_auth_method in Issuer Metadata to see what client authentication types are supported.
        )

        val tokenResponse = OpenID4VCI.sendTokenRequest(
            providerMetadata = providerMetadata,
            tokenRequest = tokenRequest
        )
        // W: sendTokenRequest (code or preauthCode ) -> TokenRepsonseObject

        // Wallet Validates the response ( check if there is successful and contains the required access_token)
        OpenID4VCI.validateTokenResponse(tokenResponse)
        return tokenResponse
    }

    suspend fun getOfferedCredentials(credentialOffer: CredentialOffer, providerMetadata: OpenIDProviderMetadata): List<OfferedCredential> =
        OpenID4VCI.resolveOfferedCredentials(
            credentialOffer = credentialOffer,
            providerMetadata = providerMetadata
        )


    /**
     * @param credentialIssuerUrl issuerMetadata.credentialIssuer
     * @param nonce tokenResponse.cNonce
     * @param proofKeyId `"$holderDid#$holderKeyId"` (`holderKeyId = holderKey.getKeyId()`)
     */
    suspend fun signProofOfPossession(
        holderKey: Key,
        credentialIssuerUrl: String,
        clientId: String?,
        nonce: String?,
        proofKeyId: String?
    ): ProofOfPossession {
        return ProofOfPossession.JWTProofBuilder(
            issuerUrl = credentialIssuerUrl,
            clientId = clientId,
            nonce = nonce,
            keyId = proofKeyId
        ).build(
            key = holderKey
        )
    }


    suspend fun receiveCredential(
        offeredCredential: OfferedCredential,
        proofOfPossession: ProofOfPossession,
        tokenResponse: TokenResponse,
        providerMetadata: OpenIDProviderMetadata
    ): CredentialResponse {
        // 4. The Wallet API constructs the credential request
        // The wallet constructs the proof based on the selected proof type, signing algorithm, and binding method.
        // If c_nonce is provided, it is included in the proof in a specific parameter based on `proof_type`.
        // The proof is included in the credential request and sent to the issuer.
        val accessToken = tokenResponse.accessToken!!

        val credentialRequest = CredentialRequest.forOfferedCredential(
            offeredCredential = offeredCredential,
            proof = proofOfPossession
        )

        return OpenID4VCI.sendCredentialRequest(
            providerMetadata = providerMetadata,
            accessToken = accessToken,
            credentialRequest = credentialRequest
        )
    }

    /**
     * @return issued credential
     */
    fun checkReceivedCredential(credentialResponse: CredentialResponse): JsonElement {
        check(credentialResponse.isSuccess) { "Credential response was unsuccessful" }

        checkNotNull(credentialResponse.credential) { "Credential in credential response is null" }

        return credentialResponse.credential!!
    }


    suspend fun fullIssuanceReceivalFlow(
        resolvedOffer: CredentialOffer,
        key: Key,
        did: String,
        clientId: String
    ): List<CredentialResponse> {
        val providerMetadata = getProviderMetadataForOffer(resolvedOffer)
        val offeredCredentials =
            getOfferedCredentials(resolvedOffer, providerMetadata)
        val token = requestToken(resolvedOffer, providerMetadata)
        val proofOfPossession =
            signProofOfPossession(
                key,
                providerMetadata.credentialIssuer!!,
                clientId,
                token.cNonce,
                "$did#${key.getKeyId()}"
            )

        return offeredCredentials.map { offeredCredential ->
            receiveCredential(
                offeredCredential,
                proofOfPossession,
                token,
                providerMetadata
            ).also { credentialResponse ->
                checkReceivedCredential(credentialResponse)
            }
        }
    }

    @Serializable
    data class ResolvedOfferWithProviderMetadata(
        val offerUrl: String? = null,
        var resolvedOffer: CredentialOffer? = null,
        val providerMetadata: OpenIDProviderMetadata? = null
    ) {
        init {
            check((offerUrl != null) xor (resolvedOffer != null)) { "Either `offerUrl` OR `resolvedOffer` has to be set." }
        }

        suspend fun getEffectiveOffer() = resolvedOffer ?: resolveCredentialOffer(offerUrl!!).also { resolvedOffer = it }
        suspend fun getEffectiveProviderMetadata() = providerMetadata ?: getProviderMetadataForOffer(getEffectiveOffer())
    }
}

suspend fun main() {
    WaltidServices.minimalInit()
    val key =
        KeyManager.resolveSerializedKey("""{"type":"jwk","jwk":{"kty":"OKP","d":"lPR4XjW-9_rI4hLjvdjmjoGC6ozblm9juDv4OHYdm5M","crv":"Ed25519","kid":"sryFIxLJ7aIqTsXo0QCnNUR9TG6jmHOQa9CFhxg5OIA","x":"LRHvL7I9utgSl47JksY0-uY21TlIxp_queROJJzknNM"}}""")
    val did = "did:key:z6MkhVCFddJrLQo1nQMycciBQ2pRs8Z2opMdCPn9WRMQg2UA"
    val clientId = "x"

    val offer =
        "openid-credential-offer://issuer.portal.test.waltid.cloud/draft13/?credential_offer_uri=https%3A%2F%2Fissuer.portal.test.waltid.cloud%2Fdraft13%2FcredentialOffer%3Fid%3D8a707c24-98d8-4517-a079-03e9ff75ccaa"

    println("Resolving offer...")
    val resolvedOffer = CoreWalletOpenId4VCI.resolveCredentialOffer(offer)

    println("Retrieving provider metadata...")
    val providerMetadata = CoreWalletOpenId4VCI.getProviderMetadataForOffer(resolvedOffer)

    println("Getting credentials on offer...")
    val offeredCredentials = CoreWalletOpenId4VCI.getOfferedCredentials(resolvedOffer, providerMetadata)
    println("Offered credentials (${offeredCredentials.size}): $offeredCredentials")

    println("Getting token...")
    val token = CoreWalletOpenId4VCI.requestToken(resolvedOffer, providerMetadata)

    println("Building ProofOfPossession...")
    val proofOfPossession = CoreWalletOpenId4VCI.signProofOfPossession(
        key,
        providerMetadata.credentialIssuer!!,
        clientId,
        token.cNonce,
        "$did#${key.getKeyId()}"
    )

    println("Requesting credentials...")
    offeredCredentials.forEachIndexed { idx, offeredCredential ->
        println("--- Requesting credential (${idx + 1}/${offeredCredentials.size}): ${offeredCredential.credentialDefinition?.type?.last()}")
        val credentialResponse = CoreWalletOpenId4VCI.receiveCredential(offeredCredential, proofOfPossession, token, providerMetadata)
        println("Response: $credentialResponse")

        CoreWalletOpenId4VCI.checkReceivedCredential(credentialResponse)
        println("Valid!")
    }

    println("All done.")
}
