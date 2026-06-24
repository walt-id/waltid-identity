import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import java.util.Properties

val Project.identityCatalog: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("identityLibs")

val Project.javaLibraryVersion: Int
    get() = identityCatalog.findVersion("java-library").get().requiredVersion.toInt()

val Project.javaServiceVersion: Int
    get() = identityCatalog.findVersion("java-service").get().requiredVersion.toInt()

fun Project.properties(path: String) = rootProject.file(path)
    .takeIf { it.isFile }
    ?.inputStream()
    ?.use { Properties().apply { load(it) } }
    ?: Properties()

fun Project.getBooleanProperty(name: String): Boolean =
    (gradle.startParameter.projectProperties[name]
        ?: properties("local.properties").getProperty(name)
        ?: providers.gradleProperty(name).orNull)
        .toBoolean()

val Project.enableAndroidBuild: Boolean
    get() = getBooleanProperty("enableAndroidBuild")

val Project.enableIosBuild: Boolean
    get() = getBooleanProperty("enableIosBuild")
