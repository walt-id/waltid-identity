package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A point of the OpenID4VP 1.0 wallet conformance matrix.
 *
 * Replaces the previous five wallet plan classes, which all pointed at
 * `oid4vp-1final-wallet-haip-test-plan` and between them expressed only two distinct configurations,
 * with several class names contradicting the variant they actually requested.
 *
 * Now driven from [WalletVariantMatrix], which also reaches
 * `oid4vp-1final-wallet-test-plan` - the non-HAIP plan that was previously never requested at all.
 */
class Oid4vpWalletVariantPlan(
    val walletVariant: WalletVariant,
    override val walletApiUrl: String,
    conformanceHost: String,
    conformancePort: Int,
) : WalletTestPlan {

    val name: String = walletVariant.id

    override val description = walletVariant.description

    override val planName = walletVariant.planName

    override val variant = walletVariant.testPlanCreationVariant()

    override val axisValues = walletVariant.axisValues()

    // Taken from the typed variant rather than the interface's map-derived defaults: the HAIP plan
    // fixes prefix, request method and profile itself, so they are absent from the map and those
    // defaults would report the wrong thing for every HAIP point.
    override val credentialFormat = walletVariant.credentialFormat
    override val clientIdScheme = walletVariant.clientIdPrefix
    override val isHAIP = walletVariant.isHaip
    override val requiresEncryptedResponse = walletVariant.encryptedResponse
    override val requiresSignedRequest = walletVariant.signedRequest

    /**
     * `client.client_id`, which `AbstractVP1FinalWalletTest` declares a required configuration field
     * for `x509_san_dns` (and for `decentralized_identifier` / `pre_registered`, neither driven yet).
     *
     * The value is the SAN DNS entry of the leaf in [TestKeyMaterial.SUITE_VERIFIER_SIGNING_KEY]'s
     * `x5c`, which is the cert the suite signs request objects with. `x509_hash` derives its
     * identifier from the certificate itself and needs no declaration, which is why it passed while
     * `x509_san_dns` failed every module without this.
     */
    /** The DID prefix needs the signing `kid` to be a DID URL; see [TestKeyMaterial.VERIFIER_SIGNING_JWKS_FOR_DID]. */
    private val verifierJwks =
        if (walletVariant.clientIdPrefix == "decentralized_identifier") {
            TestKeyMaterial.VERIFIER_SIGNING_JWKS_FOR_DID
        } else {
            TestKeyMaterial.VERIFIER_SIGNING_JWKS
        }

    private val clientIdConfigEntry = when (walletVariant.clientIdPrefix) {
        "x509_san_dns" -> """"client_id": "$VERIFIER_SAN_DNS","""
        // AbstractVP1FinalWalletTest declares client.client_id required for this prefix too. The value
        // is the did:jwk of the key the suite signs with, which the wallet resolves offline.
        "decentralized_identifier" -> """"client_id": "${TestKeyMaterial.SUITE_VERIFIER_DID_JWK}","""
        else -> ""
    }

    /**
     * Suite configuration.
     *
     * - `client.jwks`: the suite acts as the verifier here, so it needs a signing key with an `x5c`
     *   chain to sign request objects and to derive the `x509_san_dns` / `x509_hash` client
     *   identifier. Harmless for `redirect_uri`, which authenticates through the URI itself.
     * - `client.dcql`: must match the credential provisioned into the wallet, see
     *   [WalletCredentialFixture].
     * - `credential.trust_anchor_pem` / `status_list_trust_anchor_pem`: required by the HAIP plan so
     *   the suite can chain the presented credential to a known anchor. Supplied for every variant
     *   because the non-HAIP plan accepts them too and the anchor is the same.
     * - The `authorization_encrypted_response_*` pair only matters for a `.jwt` response mode; the
     *   suite ignores it otherwise.
     */
    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "$name",
            "description": "$description",
            "server": {
                "authorization_endpoint": "$walletApiUrl"
            },
            "credential": {
                "trust_anchor_pem": ${TestKeyMaterial.CREDENTIAL_ISSUER_CA_PEM_JSON},
                "status_list_trust_anchor_pem": ${TestKeyMaterial.CREDENTIAL_ISSUER_CA_PEM_JSON}
            },
            "client": {
                $clientIdConfigEntry
                "jwks": $verifierJwks,
                "dcql": ${WalletCredentialFixture.dcqlFor(walletVariant.credentialFormat)},
                "authorization_encrypted_response_alg": "ECDH-ES",
                "authorization_encrypted_response_enc": "A256GCM"
            },
            "publish": "everything"
        }
        """.trimIndent()
    )

    companion object {
        /** SAN DNS entry of the leaf in [TestKeyMaterial.SUITE_VERIFIER_SIGNING_KEY]'s `x5c`. */
        private const val VERIFIER_SAN_DNS = "verifier.example.com"

        /** Every matrix point the wallet can be driven through; both formats are provisioned. */
        fun supportedByWallet2(
            walletApiUrl: String,
            conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST,
            conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT,
        ): List<Oid4vpWalletVariantPlan> = WalletVariantMatrix.all().map { variant ->
            Oid4vpWalletVariantPlan(variant, walletApiUrl, conformanceHost, conformancePort)
        }
    }
}
