package id.walt.issuer.issuance2

import id.walt.commons.persistence.RedisPersistence
import id.walt.issuer.issuance.NewIssuanceRequest
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlin.time.Duration.Companion.minutes

object IssuanceOfferManager {

    val offerPersistence = RedisPersistence<NewIssuanceRequest>("offer", 5.minutes,
        encoding = { Json.encodeToString(it) },
        decoding = { Json.decodeFromString(it) }
    )


    // val issuerConfig = ConfigManager.getConfig<OIDCIssuerServiceConfig>()
    // val baseUrl = issuerConfig.baseUrl
    val baseUrl = "localhost:1234"

    fun getAllActiveSessionTypes(): Set<List<String>> {
        val activeSessionTypes = mutableSetOf<List<String>>()
        val keys = offerPersistence.pool.keys("session_type:*")

        for (key in keys) {
            if (offerPersistence.pool.scard(key) > 0) {
                val typeId = key.removePrefix("session_type:")
                val type = offerPersistence.pool.get("session_type_document:$typeId").split(";")

                activeSessionTypes.add(type)
            }
        }

        println("Active types: $activeSessionTypes")
        return activeSessionTypes
    }

    fun makeOfferFor(issuanceRequest: NewIssuanceRequest): CredentialOffer {
        issuanceRequest.issuance

        issuanceRequest.issuer


        var offerBuilder = CredentialOffer.Builder(baseUrl)

        issuanceRequest.credential.map { it.credentialData.getType().last() }.forEach {
            offerBuilder = offerBuilder.addOfferedCredential(it)
        }

        val sessionId = UUID.generateUUID().toString()


        when (issuanceRequest.issuance.flow) {
            GrantType.authorization_code -> offerBuilder.addAuthorizationCodeGrant(
                issuerState = sessionId
            )

            GrantType.pre_authorized_code -> {
                val pin = issuanceRequest.issuance.pin

                offerBuilder.addPreAuthorizedCodeGrant(
                    preAuthCode = sessionId,
                    txCode = pin?.toTxCode(),
                )
            }

            else -> error("Cannot handle issuance flow: ${issuanceRequest.issuance.flow}")
        }

        val offer = offerBuilder.build()
        val transaction = offerPersistence.pool.transaction(true)
//        transaction.comm
        offerPersistence.set(sessionId, issuanceRequest)

        // Also store active types
        issuanceRequest.credential.forEach {
            val expiry = offerPersistence.defaultExpiration.inWholeSeconds
            val type = it.credentialData.getType()
            val typeId = type.last()

            offerPersistence.pool.sadd("session_type:$typeId", sessionId)
            offerPersistence.pool.expire("session_type:$typeId", expiry)
            offerPersistence.pool.setex("session_type_document:$typeId", expiry, type.joinToString(";"))
        }

        return offer
    }
}

fun main() {
    println(IssuanceOfferManager.makeOfferFor(NewExamples.newIssuanceRequestExample))
    println(IssuanceOfferManager.getAllActiveSessionTypes())
}
