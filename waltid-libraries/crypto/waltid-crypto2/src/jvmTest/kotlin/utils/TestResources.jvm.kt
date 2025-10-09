package utils

import java.io.InputStream

actual fun loadResource(path: String): ByteArray {
    val normalized = path.removePrefix("/")
    val stream: InputStream =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(normalized)
            ?: object {}.javaClass.classLoader?.getResourceAsStream(normalized)
            ?: error("Resource '$path' not found on the JVM test classpath")

    return stream.use(InputStream::readBytes)

}