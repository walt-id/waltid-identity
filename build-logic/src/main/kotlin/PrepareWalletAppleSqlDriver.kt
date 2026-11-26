import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Builds the Apple implementation from SQLDelight's Apache-2.0 sources until its listener lock is closed upstream. */
@CacheableTask
abstract class PrepareWalletAppleSqlDriver : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceArchive: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val archive = sourceArchive.get().asFile
        val digest = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
        check(digest.joinToString("") { "%02x".format(it) } ==
            "0a243bc7ed0fbe004ed02eaaf0d9a6017a162ef8377646474b5ca0bd85fe3ed5") {
            "Review SQLDelight's native listener lifetime before changing its source version"
        }
        val output = outputDirectory.get().asFile.apply { mkdirs() }
        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                if (!entry.name.endsWith(".kt")) continue
                val name = entry.name.substringAfterLast('/')
                // The persistence module targets Apple only. Use the Darwin implementation of PoolLock.
                if (name == "PoolLock.kt" && entry.name.startsWith("nativeMain/")) continue
                val source = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    .replace("app.cash.sqldelight.driver.native", "id.walt.wallet2.persistence.sqldelight")
                val patched = when (name) {
                    "NativeSqlDatabase.kt" -> source
                        .replace("    readerPool.close()", "    readerPool.close()\n    lock.close()")
                        .replace("sealed class ConnectionWrapper", "internal sealed class ConnectionWrapper")
                        .replace("class NativeSqliteDriver(", "internal class NativeSqliteDriver(")
                        .replace("\nfun ", "\ninternal fun ")
                    "PoolLock.kt" -> source.replace("actual ", "")
                        .replace("constructor(reentrant: Boolean)", "constructor(reentrant: Boolean = false)")
                        .replace("internal expect val pthread_mutex_recursive: Int", "")
                    "PthreadMutexRecursive.kt" -> source.replace("actual val", "internal val")
                    else -> source
                }
                output.resolve(name).writeText("// Adapted from SQLDelight 2.4.1 (Apache-2.0) for wallet-owned Apple driver teardown.\n" + patched)
            }
        }
    }
}
