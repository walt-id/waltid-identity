import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

object WaltidBuildConstants {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 30
    const val META_INF_EXCLUDES = "/META-INF/{AL2.0,LGPL2.1}"

    val POWER_ASSERT_FUNCTIONS = listOf(
        "kotlin.assert",
        "kotlin.test.assertEquals",
        "kotlin.test.assertNull",
        "kotlin.test.assertTrue",
        "kotlin.test.assertFalse",
        "kotlin.test.assertContentEquals",
        "kotlin.require",
        "kotlin.check"
    )
}

val Project.identityCatalog: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("identityLibs")

val Project.javaLibraryVersion: Int
    get() = identityCatalog.findVersion("java-library").get().requiredVersion.toInt()

val Project.javaServiceVersion: Int
    get() = identityCatalog.findVersion("java-service").get().requiredVersion.toInt()
