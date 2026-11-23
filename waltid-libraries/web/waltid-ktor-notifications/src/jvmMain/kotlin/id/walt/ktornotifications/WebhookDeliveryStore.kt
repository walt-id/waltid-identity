package id.walt.ktornotifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.toList

private val storeLog = io.github.oshai.kotlinlogging.KotlinLogging.logger { }

/** Durable outbox storage. Implementations must claim due deliveries atomically across all workers. */
interface WebhookDeliveryStore {
    suspend fun enqueue(delivery: WebhookDelivery)
    /** Revoke older generations, including producers that still hold a pre-erasure snapshot. */
    suspend fun eraseSession(sessionOwner: String, allowFutureCallbacks: Boolean = false): Long
    /** Keep erasure from succeeding between admission of a scoped claim and completion of its send. */
    suspend fun dispatch(claim: WebhookDeliveryClaim, send: suspend () -> Unit)
    suspend fun pending(deliveryId: String): WebhookDelivery?
    suspend fun claimDue(
        nowEpochMilliseconds: Long,
        leaseUntilEpochMilliseconds: Long,
        limit: Int = 100,
    ): List<WebhookDeliveryClaim>
    suspend fun reschedule(claim: WebhookDeliveryClaim, delivery: WebhookDelivery): Boolean
    suspend fun markDelivered(claim: WebhookDeliveryClaim): Boolean
    suspend fun moveToDeadLetter(claim: WebhookDeliveryClaim, delivery: WebhookDelivery): Boolean
    suspend fun deadLetters(): List<WebhookDeadLetter>
    suspend fun redrive(deliveryId: String, nowEpochMilliseconds: Long): Boolean
}

/** Opaque durable marker keys avoid persisting tenant paths or session IDs after erasure. */
fun webhookSessionKey(sessionOwner: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sessionOwner.encodeToByteArray()))

/** No callback, including a redacted purge event, can use this generation. */
const val DELETED_WEBHOOK_SESSION: Long = -1

/**
 * A single-process durable webhook outbox.
 *
 * Each delivery is kept in its own JSON file and updates use atomic replacement. Each service process must use
 * its own directory so separate workers cannot claim the same delivery.
 */
internal class FileWebhookDeliveryStore(
    private val root: Path,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) : WebhookDeliveryStore {
    private val mutex = Mutex()
    private val dispatches = mutableMapOf<String, MutableSet<CompletableDeferred<Unit>>>()
    private val generations = root.resolve("session-erasure")
    private val pendingDirectory = root.resolve("pending")
    private val deadLetterDirectory = root.resolve("dead-letter")
    private val corruptDirectory = root.resolve("corrupt")

    init {
        Files.createDirectories(generations)
        setOwnerOnlyPermissions(generations, "rwx------")
        Files.createDirectories(pendingDirectory)
        Files.createDirectories(deadLetterDirectory)
        Files.createDirectories(corruptDirectory)
        setOwnerOnlyPermissions(root, "rwx------")
        setOwnerOnlyPermissions(pendingDirectory, "rwx------")
        setOwnerOnlyPermissions(deadLetterDirectory, "rwx------")
        setOwnerOnlyPermissions(corruptDirectory, "rwx------")
    }

    override suspend fun enqueue(delivery: WebhookDelivery) = mutex.withLock {
        delivery.sessionOwner?.let { owner ->
            if (!accepts(owner, delivery.sessionGeneration)) return@withLock
        }
        val path = pendingPath(delivery.deliveryId)
        require(!Files.exists(path) && !Files.exists(deadLetterPath(delivery.deliveryId))) {
            "Webhook delivery ${delivery.deliveryId} already exists"
        }
        writeAtomically(path, delivery)
    }

    private fun generation(owner: String): Long = try {
        Files.readString(generations.resolve(webhookSessionKey(owner))).toLong()
    } catch (_: NoSuchFileException) {
        0L
    }

    private fun accepts(owner: String, expected: Long) = expected >= 0 && generation(owner) == expected

    override suspend fun eraseSession(sessionOwner: String, allowFutureCallbacks: Boolean): Long {
        val (next, inFlight) = mutex.withLock {
            val previous = generation(sessionOwner)
            val next = if (allowFutureCallbacks && previous >= 0) Math.incrementExact(previous) else DELETED_WEBHOOK_SESSION
            writeAtomically(generations.resolve(webhookSessionKey(sessionOwner)), next.toString())
            // Remove pending first so a shadow left by an interrupted redrive cannot become visible.
            for (directory in listOf(pendingDirectory, deadLetterDirectory)) {
                for (path in listDeliveryFiles(directory)) {
                    if (readOrQuarantine(path)?.sessionOwner == sessionOwner) Files.deleteIfExists(path)
                }
            }
            next to dispatches[sessionOwner].orEmpty().toList()
        }
        inFlight.forEach { it.await() }
        return next
    }

    override suspend fun dispatch(claim: WebhookDeliveryClaim, send: suspend () -> Unit) {
        val owner = claim.delivery.sessionOwner ?: return send()
        val finished = CompletableDeferred<Unit>()
        mutex.withLock {
            if (!accepts(owner, claim.delivery.sessionGeneration) ||
                readPending(pendingPath(claim.delivery.deliveryId))?.leaseId != claim.leaseId) return
            dispatches.getOrPut(owner) { mutableSetOf() }.add(finished)
        }
        withContext(NonCancellable) {
            try {
                send()
            } finally {
                mutex.withLock {
                    val active = dispatches.getValue(owner)
                    active.remove(finished)
                    if (active.isEmpty()) dispatches.remove(owner)
                    finished.complete(Unit)
                }
            }
        }
    }

    override suspend fun pending(deliveryId: String): WebhookDelivery? = mutex.withLock {
        readPending(pendingPath(deliveryId))
    }

    override suspend fun claimDue(
        nowEpochMilliseconds: Long,
        leaseUntilEpochMilliseconds: Long,
        limit: Int,
    ): List<WebhookDeliveryClaim> = mutex.withLock {
        val due = listDeliveryFiles(pendingDirectory)
            .mapNotNull(::readPending)
            .filter {
                it.nextAttemptAtEpochMilliseconds <= nowEpochMilliseconds &&
                    (it.leaseUntilEpochMilliseconds == null || it.leaseUntilEpochMilliseconds <= nowEpochMilliseconds)
            }
            .sortedWith(compareBy(WebhookDelivery::nextAttemptAtEpochMilliseconds, WebhookDelivery::enqueueOrder))
            .take(limit)
        due.map { delivery ->
            val leaseId = UUID.randomUUID().toString()
            val claimed = delivery.copy(
                leaseId = leaseId,
                leaseUntilEpochMilliseconds = leaseUntilEpochMilliseconds,
            )
            writeAtomically(pendingPath(delivery.deliveryId), claimed)
            WebhookDeliveryClaim(claimed, leaseId)
        }
    }

    override suspend fun reschedule(claim: WebhookDeliveryClaim, delivery: WebhookDelivery): Boolean = mutex.withLock {
        require(delivery.deliveryId == claim.delivery.deliveryId)
        val current = readPending(pendingPath(claim.delivery.deliveryId))
        if (current?.leaseId != claim.leaseId) return@withLock false
        writeAtomically(
            pendingPath(delivery.deliveryId),
            delivery.copy(leaseId = null, leaseUntilEpochMilliseconds = null),
        )
        true
    }

    override suspend fun markDelivered(claim: WebhookDeliveryClaim): Boolean = mutex.withLock {
        val path = pendingPath(claim.delivery.deliveryId)
        val current = readPending(path)
        if (current?.leaseId != claim.leaseId) return@withLock false
        Files.deleteIfExists(path)
    }

    override suspend fun moveToDeadLetter(
        claim: WebhookDeliveryClaim,
        delivery: WebhookDelivery,
    ): Boolean = mutex.withLock {
        require(delivery.deliveryId == claim.delivery.deliveryId)
        val pendingPath = pendingPath(claim.delivery.deliveryId)
        val current = readPending(pendingPath)
        if (current?.leaseId != claim.leaseId) return@withLock false
        writeAtomically(
            deadLetterPath(delivery.deliveryId),
            delivery.copy(leaseId = null, leaseUntilEpochMilliseconds = null),
        )
        Files.deleteIfExists(pendingPath)
    }

    override suspend fun deadLetters(): List<WebhookDeadLetter> = mutex.withLock {
        listDeliveryFiles(deadLetterDirectory)
            .mapNotNull(::readOrQuarantine)
            .sortedBy(WebhookDelivery::failedAtEpochMilliseconds)
            .map(WebhookDelivery::deadLetter)
    }

    override suspend fun redrive(deliveryId: String, nowEpochMilliseconds: Long): Boolean = mutex.withLock {
        val deadLetterPath = deadLetterPath(deliveryId)
        val delivery = readOrQuarantine(deadLetterPath) ?: return@withLock false
        val redriven = delivery.copy(
            attemptCount = 0,
            nextAttemptAtEpochMilliseconds = nowEpochMilliseconds,
            failedAtEpochMilliseconds = null,
            lastError = null,
            leaseId = null,
            leaseUntilEpochMilliseconds = null,
        )
        writeAtomically(pendingPath(deliveryId), redriven)
        Files.delete(deadLetterPath)
        true
    }

    private fun pendingPath(deliveryId: String) = pendingDirectory.resolve(recordName(deliveryId))
    private fun deadLetterPath(deliveryId: String) = deadLetterDirectory.resolve(recordName(deliveryId))

    private fun recordName(deliveryId: String): String {
        require(deliveryId.isNotBlank() && '/' !in deliveryId && '\\' !in deliveryId) { "Invalid delivery ID" }
        return "$deliveryId.json"
    }

    // Publishing a dead letter commits failure. Removing it commits redrive. A crash between
    // the two file operations in either transition leaves the dead letter authoritative.
    private fun readPending(path: Path): WebhookDelivery? {
        if (Files.exists(deadLetterDirectory.resolve(path.fileName))) {
            Files.deleteIfExists(path)
            return null
        }
        return readOrQuarantine(path)
    }

    private fun listDeliveryFiles(directory: Path): List<Path> =
        Files.list(directory).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "json" }.toList()
        }

    private fun readOrQuarantine(path: Path): WebhookDelivery? {
        return try {
            json.decodeFromString<WebhookDelivery>(Files.readString(path)).also { delivery ->
                require(recordName(delivery.deliveryId) == path.name)
                if (path.parent == deadLetterDirectory) {
                    require(delivery.failedAtEpochMilliseconds != null && delivery.lastError != null)
                }
            }
        } catch (_: NoSuchFileException) {
            null
        } catch (_: CharacterCodingException) {
            quarantine(path)
            null
        } catch (_: IllegalArgumentException) {
            quarantine(path)
            null
        }
    }

    private fun quarantine(path: Path) {
        if (path.parent == deadLetterDirectory) Files.deleteIfExists(pendingDirectory.resolve(path.fileName))
        val quarantined = corruptDirectory.resolve("${path.name}.${UUID.randomUUID()}.corrupt")
        Files.move(path, quarantined)
        // Decoder errors can include excerpts of the credential-bearing record.
        storeLog.error { "Quarantined invalid webhook delivery record (source=$path, quarantined=$quarantined)" }
    }

    private fun writeAtomically(path: Path, delivery: WebhookDelivery) = writeAtomically(path, json.encodeToString(delivery))

    private fun writeAtomically(path: Path, content: String) {
        val temporary = Files.createTempFile(path.parent, ".${path.name}.", ".tmp")
        try {
            setOwnerOnlyPermissions(temporary, "rw-------")
            Files.writeString(temporary, content)
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun setOwnerOnlyPermissions(path: Path, mode: String) {
        Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
            ?.setPermissions(PosixFilePermissions.fromString(mode))
    }
}
