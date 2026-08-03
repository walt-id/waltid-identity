package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path

/** Creates the suite configuration for one [WalletVariant]. */
class Oid4vciWalletVariantPlan(
    val variantContext: WalletVariant,
    private val adapterPublicUrl: String,
    private val clientId: String,
    private val clientAttestationIssuer: String,
    private val clientAttesterJwks: JsonObject,
    private val clientAttestationTrustAnchorPem: String,
    private val keyAttestationTrustAnchorPem: String,
    private val clientCertificatePem: String,
) : VciWalletTestPlan {
    override val description: String = variantContext.description
    override val planName: String = variantContext.conformanceTestPlanName
    override val variant: Map<String, String> = variantContext.toJsonObject()
        .mapValues { (_, value) -> value.toString().trim('"') }

    override val configuration: JsonObject = buildJsonObject {
        put("alias", "vci_wallet_${variantContext.id}")
        put("description", variantContext.description)
        putJsonObject("server") {
            putJsonObject("jwks") {
                put("keys", serverJwks["keys"]!!)
            }
        }
        putJsonObject("client") {
            put("client_id", clientId)
            put("redirect_uri", "$adapterPublicUrl/callback")
            put("certificate", clientCertificatePem)
            putJsonObject("jwks") {
                put("keys", clientJwks["keys"]!!)
            }
        }
