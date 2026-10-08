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

internal interface WebhookDeliveryStore {
    suspend fun enqueue(delivery: WebhookDelivery)
    suspend fun pending(deliveryId: String): WebhookDelivery?
    suspend fun due(nowEpochMilliseconds: Long, limit: Int = 100): List<WebhookDelivery>
    suspend fun update(delivery: WebhookDelivery)
    suspend fun markDelivered(deliveryId: String)
    suspend fun moveToDeadLetter(delivery: WebhookDelivery)
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

    init {
        Files.createDirectories(pendingDirectory)
        Files.createDirectories(deadLetterDirectory)
        setOwnerOnlyDirectoryPermissions(root)
        setOwnerOnlyDirectoryPermissions(pendingDirectory)
        setOwnerOnlyDirectoryPermissions(deadLetterDirectory)
    }

    override suspend fun enqueue(delivery: WebhookDelivery) = mutex.withLock {
        val path = pendingPath(delivery.deliveryId)
        require(!Files.exists(path) && !Files.exists(deadLetterPath(delivery.deliveryId))) {
            "Webhook delivery ${delivery.deliveryId} already exists"
        }
        writeAtomically(path, delivery)
    }

    override suspend fun pending(deliveryId: String): WebhookDelivery? = mutex.withLock {
        read(pendingPath(deliveryId))
    }

    override suspend fun due(nowEpochMilliseconds: Long, limit: Int): List<WebhookDelivery> = mutex.withLock {
        listDeliveryFiles(pendingDirectory)
            .mapNotNull(::read)
            .filter { it.nextAttemptAtEpochMilliseconds <= nowEpochMilliseconds }
            .sortedWith(compareBy(WebhookDelivery::nextAttemptAtEpochMilliseconds, WebhookDelivery::enqueueOrder))
            .take(limit)
    }

    override suspend fun update(delivery: WebhookDelivery) = mutex.withLock {
        val path = pendingPath(delivery.deliveryId)
        require(Files.exists(path)) { "Unknown pending webhook delivery ${delivery.deliveryId}" }
        writeAtomically(path, delivery)
    }

    override suspend fun markDelivered(deliveryId: String) {
        mutex.withLock {
            Files.deleteIfExists(pendingPath(deliveryId))
        }
    }

    override suspend fun moveToDeadLetter(delivery: WebhookDelivery) {
        mutex.withLock {
            writeAtomically(deadLetterPath(delivery.deliveryId), delivery)
            Files.deleteIfExists(pendingPath(delivery.deliveryId))
        }
    }

    override suspend fun deadLetters(): List<WebhookDeadLetter> = mutex.withLock {
        listDeliveryFiles(deadLetterDirectory)
            .mapNotNull(::read)
            .sortedBy(WebhookDelivery::failedAtEpochMilliseconds)
            .map(WebhookDelivery::deadLetter)
    }

    override suspend fun redrive(deliveryId: String, nowEpochMilliseconds: Long): Boolean = mutex.withLock {
        val deadLetterPath = deadLetterPath(deliveryId)
        val delivery = read(deadLetterPath) ?: return@withLock false
        val redriven = delivery.copy(
            attemptCount = 0,
            nextAttemptAtEpochMilliseconds = nowEpochMilliseconds,
            failedAtEpochMilliseconds = null,
            lastError = null,
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
