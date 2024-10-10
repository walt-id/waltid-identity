package id.walt.commons.testing.utils

object ServiceTestUtils {

    fun loadResource(relativePath: String): String =
        ServiceTestUtils::class.java.classLoader.getResource(relativePath)?.readText() ?: error("Could not load test resource: $relativePath")
}
