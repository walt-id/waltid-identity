package id.walt.issuer

import id.walt.crypto.keys.KeyCategory
import io.github.smiley4.ktorswaggerui.dsl.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable


enum class EventType {
    ISSUER,
    VERIFIER,
    WALLET,
    DID,
    KEY
}

enum class EventResult {
    SUCCESS,
    FAILURE
}

@Serializable
data class Metrics(
    val numberOfOfferedRequests: Long,
    val numberOfClaimedCredentials: Long,
    val numberOfIssuanceRequests: Long,
    val numberOfVerificationRequests: Long,
    val numberOfVerificationFailues: Long,
    val numberOfManagedKeys: Long,
    val numberOfCreatedKeys: Long,
    val numberOfImportedKeys: Long,
    val numberOfExportedKeys: Long,
    val numberOfCreatedDids: Long,
    val numberOfUpdatedDids: Long,
    val numberOfDeletedDids: Long,
    val numberOfResolvedDids: Long,
    val numberOfWalletAccounts: Long,
    val numberOfWalletCredentials: Long,
)

@Serializable
data class Audit(
    val type: EventType,
    val user: String, // tenant001, tenant002_user1234,
    val action: String, // e.g. issue_jwt_vc, claim_jwt_vc, wallet_create_did, wallet_request_vc
    val result: EventResult = EventResult.SUCCESS,
    val timestamp: String, // ISO8601
    val issuerLog: IssuerLog? = null,
    val verifierLog: VerifierLog? = null,
    val walletLog: WalletLog? = null,
    val rawData: String // PII??
)

@Serializable
data class IssuerLog(
    val issuerId: String,
    val subjectId: String,
    val issuerKeyId: String,
    val issuerKeyType: String,
    val subjectKeyType: String,
    val credentialType: String,
    val credentialFormat: String,
    val credentialProofType: String,
    val ecosystem: String,
    val exchangeProtocol: String,
)

@Serializable
data class VerifierLog(
    val issuerId: String,
    val subjectId: String,
    val policies: List<String>,
    val issuerKeyType: String,
    val subjectKeyType: String,
    val credentialType: String,
    val credentialFormat: String,
    val credentialProofType: String,
    val ecosystem: String,
    val exchangeProtocol: String,
)

@Serializable
data class WalletLog(
    val did: String,
    val method: String
)

fun Application.auditApi() {
    routing {
        get("/audit") {

            val resp = Audit(
                type = EventType.ISSUER,
                action = "issue_jwt_vc",
                user = "tenant001",
                result = EventResult.FAILURE,
                timestamp = "2021-08-02T08:03:13Z",
                rawData = "...",
                issuerLog = IssuerLog(
                    issuerId = "did:web:issuer.walt.id",
                    subjectId = "did:web:wallet.walt.id",
                    issuerKeyId = "did:web:issuer.walt.id#key-1",
                    issuerKeyType = KeyCategory.EdDSA.name,
                    subjectKeyType = KeyCategory.EdDSA.name,
                    credentialType = "VerifiableCredential,OpenBadge",
                    credentialFormat = "JWT",
                    credentialProofType = "EdDSA",
                    ecosystem = "EBSI",
                    exchangeProtocol = "OID4VC"
                ),
                verifierLog = VerifierLog(
                    issuerId = "did:web:verifier.walt.id",
                    subjectId = "did:web:wallet.walt.id",
                    policies = listOf("signature", "schema"),
                    issuerKeyType = KeyCategory.EdDSA.name,
                    subjectKeyType = KeyCategory.EdDSA.name,
                    credentialType = "VerifiableCredential,OpenBadge",
                    credentialFormat = "JWT",
                    credentialProofType = "EdDSA",
                    ecosystem = "EBSI",
                    exchangeProtocol = "OID4VC"
                ),
                walletLog = WalletLog(
                    did = "did:web:wallet.walt.id",
                    method = "web"
                )
            )

            context.respond(HttpStatusCode.OK, resp)
        }
    }
}
