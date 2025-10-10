package utils

actual fun loadResource(path: String) = object {}.javaClass.classLoader.getResource(path)?.readBytes()
    ?: error("Resource '$path' not found on the JVM test classpath")

