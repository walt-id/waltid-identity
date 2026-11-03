package id.walt.openid4vp.conformance.testplans

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@Serializable
enum class IssuerIssuanceMode {
    IMMEDIATE, DEFERRED;

    companion object {
        fun fromEnvironment(): IssuerIssuanceMode = when (System.getenv("OPENID4VCI_CONFORMANCE_ISSUANCE_MODE") ?: "immediate") {
            "immediate" -> IMMEDIATE
            "deferred" -> DEFERRED
            else -> error("OPENID4VCI_CONFORMANCE_ISSUANCE_MODE must be immediate or deferred")
        }
    }
}

/** Only hashes and event names are written by the proxy, never tokens or protocol bodies. */
@Serializable
data class DeferredProxyEvent(val offerKey: String, val event: String)

fun deferredOfferKey(offerId: String): String =
    MessageDigest.getInstance("SHA-256").digest(offerId.toByteArray()).joinToString("") { "%02x".format(it) }

@Serializable
data class IssuerDeferredEvidence(
    val offers: Int,
    val initial202: Int,
    val approved204: Int,
    val collected200: Int,
    val completeOffers: Int,
    val failures: Int,
    val proofVerified: Boolean = false,
) {
    fun requireComplete() {
        require(offers > 0 && completeOffers == offers && failures == 0) {
            "Deferred evidence is incomplete: every offer requires an initial 202, approval 204, and deferred collection 200 without proxy failures"
        }
    }
}

fun readIssuerDeferredEvidence(path: Path, offerIds: List<String>): IssuerDeferredEvidence {
    val keys = offerIds.map(::deferredOfferKey).toSet()
    val events = Files.readAllLines(path).filter(String::isNotBlank).map { Json.decodeFromString<DeferredProxyEvent>(it) }
        .filter { it.offerKey in keys }
    fun count(event: String) = events.count { it.event == event }
    val complete = keys.count { key ->
        val sequence = events.filter { it.offerKey == key }.map { it.event }
        val initial = sequence.indexOf("initial202")
        val approved = sequence.indexOf("approved204")
        val collected = sequence.indexOf("collected200")
        initial >= 0 && approved > initial && collected > approved && "failed" !in sequence
    }
    return IssuerDeferredEvidence(keys.size, count("initial202"), count("approved204"), count("collected200"), complete, count("failed"))
}
