package id.walt.webwallet.db.models

import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.commons.temp.UuidSerializer
import id.walt.oid4vc.data.CredentialOffer
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

object WalletOid4vciAuthReqSessions : Table("auth_req_sessions") {
    val wallet = reference("wallet", Wallets)

    val id = varchar("id", 256)
    val accountId = uuid("account_id")
    val authorizationRequest = text("authorization_request")
    val issuerMetadata = text("issuer_metadata")
    val credentialOffer = text("credential_offer")
    val successRedirectUri = text("success_redirect_uri")
    val createdOn = timestamp("created_on")

    override val primaryKey = PrimaryKey(wallet, id)
}

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class WalletOid4vciAuthReqSession @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String,
    @Serializable(with = UuidSerializer::class)
    val accountId: Uuid,
    val authorizationRequest: JsonObject,
    val issuerMetadata: OpenIDProviderMetadata,
    val credentialOffer: CredentialOffer,
    val successRedirectUri: String,
    val createdOn: Instant
) {
    @OptIn(ExperimentalUuidApi::class)
    constructor(row: ResultRow) : this(
        accountId = row[WalletOid4vciAuthReqSessions.accountId].toKotlinUuid(),
        id = row[WalletOid4vciAuthReqSessions.id],
        authorizationRequest = Json.parseToJsonElement(row[WalletOid4vciAuthReqSessions.authorizationRequest]).jsonObject,
        issuerMetadata = OpenIDProviderMetadata.fromJSON(Json.parseToJsonElement(row[WalletOid4vciAuthReqSessions.issuerMetadata]).jsonObject),
        credentialOffer = CredentialOffer.fromJSON(Json.parseToJsonElement(row[WalletOid4vciAuthReqSessions.credentialOffer]).jsonObject),
        successRedirectUri = row[WalletOid4vciAuthReqSessions.successRedirectUri],
        createdOn = row[WalletOid4vciAuthReqSessions.createdOn].toKotlinInstant(),
    )
}

@OptIn(ExperimentalUuidApi::class)
fun insertAuthReqSession(wallet: Uuid, session: WalletOid4vciAuthReqSession) {
    transaction {
        WalletOid4vciAuthReqSessions.insert {
            it[id] = session.id
            it[accountId] = session.accountId.toJavaUuid()
            it[WalletOid4vciAuthReqSessions.wallet] = wallet.toJavaUuid()
            it[authorizationRequest] = session.authorizationRequest.toString()
            it[issuerMetadata] = Json.encodeToString(OpenIDProviderMetadata.serializer(), session.issuerMetadata)
            it[credentialOffer] = Json.encodeToString(CredentialOffer.serializer(), session.credentialOffer)
            it[successRedirectUri] = session.successRedirectUri
            it[createdOn] = Clock.System.now().toJavaInstant()
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
fun getAuthReqSessions(wallet: Uuid, id: String): WalletOid4vciAuthReqSession? = transaction {
    WalletOid4vciAuthReqSessions.selectAll()
        .where { (WalletOid4vciAuthReqSessions.wallet eq wallet.toJavaUuid()) and (WalletOid4vciAuthReqSessions.id eq id) }
        .singleOrNull()?.let { WalletOid4vciAuthReqSession(it) }
}

fun getAuthReqSessionsById(id: String): WalletOid4vciAuthReqSession? = transaction {
    WalletOid4vciAuthReqSessions.selectAll().where { (WalletOid4vciAuthReqSessions.id eq id) }
        .singleOrNull()?.let { WalletOid4vciAuthReqSession(it) }
}
