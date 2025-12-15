package id.walt.verifier2.annexc

import java.time.Instant

data class AnnexCSession(
    val transactionId: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val origin: String,
    val encryptionInfoB64: String,
    val recipientPrivateKey: ByteArray,
    val nonce: ByteArray,
    val policies: List<String> = emptyList()
)

interface AnnexCSessionStore {
    fun put(session: AnnexCSession)
    fun get(transactionId: String): AnnexCSession?
    fun delete(transactionId: String)
}
