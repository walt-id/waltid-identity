package id.walt.issuer.issuance2

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.issuer.issuance.NewIssuanceRequest
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlin.time.Duration.Companion.minutes

object IssuanceOfferManager {

    val offerPersistence = ConfiguredPersistence<NewIssuanceRequest>("offer", 5.minutes,
        encoding = { Json.encodeToString(it) },
        decoding = { Json.decodeFromString(it) }
    )
    val sessionTypePersistence = ConfiguredPersistence<String>("session_type", 5.minutes,
        encoding = { it },
        decoding = { it }
    )
    val sessionTypeDocumentPersistence = ConfiguredPersistence<String>("session_type_document", 5.minutes,
        encoding = { it },
        decoding = { it }
    )


    // val issuerConfig = ConfigManager.getConfig<OIDCIssuerServiceConfig>()
    // val baseUrl = issuerConfig.baseUrl
    val baseUrl = "localhost:1234"

    fun getAllActiveSessionTypes(): Set<List<String>> {
        val activeSessionTypes = mutableSetOf<List<String>>()
        val keys = sessionTypePersistence.listAllKeys()
        //val keys = offerPersistence.pool.keys("session_type:*")

        for (typeId in keys) {
            if (sessionTypePersistence.listSize(typeId) > 0) {
                sessionTypeDocumentPersistence[typeId]?.split(";")?.let { type ->
                    activeSessionTypes.add(type)
                }
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
        // val transaction = offerPersistence.pool.transaction(true)
        offerPersistence[sessionId] = issuanceRequest

        // Also store active types
        issuanceRequest.credential.forEach {
            val expiry = offerPersistence.defaultExpiration.inWholeSeconds
            val type = it.credentialData.getType()
            val typeId = type.last()

            sessionTypePersistence.listAdd(typeId, sessionId)
//            sessionTypePersistence.pool.sadd("session_type:$typeId", sessionId)
//            sessionTypePersistence.pool.expire("session_type:$typeId", expiry)
            sessionTypeDocumentPersistence[typeId] = type.joinToString(";")
//            offerPersistence.pool.setex("session_type_document:$typeId", expiry, type.joinToString(";"))
        }

        return offer
    }
}

fun main() {
    println(IssuanceOfferManager.makeOfferFor(NewExamples.newIssuanceRequestExample))
    println(IssuanceOfferManager.getAllActiveSessionTypes())
}
