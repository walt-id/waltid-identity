package id.walt.ktornotifications

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.toList

private val storeLog = io.github.oshai.kotlinlogging.KotlinLogging.logger { }

/** Durable outbox storage. Implementations must claim due deliveries atomically across all workers. */
interface WebhookDeliveryStore {
    suspend fun enqueue(delivery: WebhookDelivery)
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
    private val pendingDirectory = root.resolve("pending")
    private val deadLetterDirectory = root.resolve("dead-letter")
    private val corruptDirectory = root.resolve("corrupt")

    init {
        Files.createDirectories(pendingDirectory)
        Files.createDirectories(deadLetterDirectory)
        Files.createDirectories(corruptDirectory)
        setOwnerOnlyDirectoryPermissions(root)
        setOwnerOnlyDirectoryPermissions(pendingDirectory)
        setOwnerOnlyDirectoryPermissions(deadLetterDirectory)
        setOwnerOnlyDirectoryPermissions(corruptDirectory)
    }

    override suspend fun enqueue(delivery: WebhookDelivery) = mutex.withLock {
        val path = pendingPath(delivery.deliveryId)
        require(!Files.exists(path) && !Files.exists(deadLetterPath(delivery.deliveryId))) {
            "Webhook delivery ${delivery.deliveryId} already exists"
        }
        writeAtomically(path, delivery)
    }

    override suspend fun pending(deliveryId: String): WebhookDelivery? = mutex.withLock {
        readOrQuarantine(pendingPath(deliveryId))
    }

    override suspend fun claimDue(
        nowEpochMilliseconds: Long,
        leaseUntilEpochMilliseconds: Long,
        limit: Int,
    ): List<WebhookDeliveryClaim> = mutex.withLock {
        val due = listDeliveryFiles(pendingDirectory)
            .mapNotNull(::readOrQuarantine)
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
        val current = readOrQuarantine(pendingPath(claim.delivery.deliveryId))
        if (current?.leaseId != claim.leaseId) return@withLock false
        writeAtomically(
            pendingPath(delivery.deliveryId),
            delivery.copy(leaseId = null, leaseUntilEpochMilliseconds = null),
        )
        true
    }

    override suspend fun markDelivered(claim: WebhookDeliveryClaim): Boolean = mutex.withLock {
        val path = pendingPath(claim.delivery.deliveryId)
        val current = readOrQuarantine(path)
        if (current?.leaseId != claim.leaseId) return@withLock false
        Files.deleteIfExists(path)
    }

    override suspend fun moveToDeadLetter(
        claim: WebhookDeliveryClaim,
        delivery: WebhookDelivery,
    ): Boolean = mutex.withLock {
        val pendingPath = pendingPath(claim.delivery.deliveryId)
        val current = readOrQuarantine(pendingPath)
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

    private fun pendingPath(deliveryId: String) = pendingDirectory.resolve("$deliveryId.json")
    private fun deadLetterPath(deliveryId: String) = deadLetterDirectory.resolve("$deliveryId.json")

    private fun listDeliveryFiles(directory: Path): List<Path> =
        Files.list(directory).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "json" }.toList()
        }

    private fun read(path: Path): WebhookDelivery? =
        if (Files.exists(path)) json.decodeFromString(Files.readString(path)) else null

    private fun readOrQuarantine(path: Path): WebhookDelivery? =
        runCatching { read(path) }.getOrElse { cause ->
            val quarantined = corruptDirectory.resolve("${path.name}.${UUID.randomUUID()}.corrupt")
            val quarantineFailure = runCatching {
                try {
                    Files.move(path, quarantined, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(path, quarantined)
                }
                setOwnerOnlyFilePermissions(quarantined)
            }.exceptionOrNull()

            if (quarantineFailure == null) {
                storeLog.error(cause) {
                    "Quarantined unreadable webhook delivery record (source=$path, quarantined=$quarantined)"
                }
            } else {
                storeLog.error(cause) {
                    "Could not read or quarantine webhook delivery record (path=$path, " +
                        "quarantineError=${quarantineFailure.message})"
                }
            }
            null
        }

    private fun writeAtomically(path: Path, delivery: WebhookDelivery) {
        Files.createDirectories(path.parent)
        val temporary = path.parent.resolve(".${path.name}.${UUID.randomUUID()}.tmp")
        Files.writeString(temporary, json.encodeToString(delivery))
        setOwnerOnlyFilePermissions(temporary)
        try {
            Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun setOwnerOnlyDirectoryPermissions(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        }
    }

    private fun setOwnerOnlyFilePermissions(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            )
        }
    }
}
