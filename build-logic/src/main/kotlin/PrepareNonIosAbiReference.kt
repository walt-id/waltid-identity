import kotlinx.validation.ExperimentalBCVApi
import kotlinx.validation.api.klib.KlibDump
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/** Keeps the canonical baseline intact when the build omits its optional iOS targets. */
@CacheableTask
abstract class PrepareNonIosAbiReference : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun prepare() {
        fileSystem.sync {
            from(referenceDirectory)
            into(outputDirectory)
        }
        outputDirectory.get().asFile.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".klib.api") }
            .forEach { it.writeText(withoutIosTargets(it.readText())) }
    }
}

@OptIn(ExperimentalBCVApi::class)
internal fun withoutIosTargets(reference: String): String {
    val dump = KlibDump.from(reference)
    // Do not hide removal of any other target: that must still fail ABI validation.
    dump.remove(dump.targets.filter { it.targetName in setOf("iosArm64", "iosSimulatorArm64") })
    return buildString { dump.saveTo(this) }
}
