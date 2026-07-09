package id.walt.certificate.x509


object ResourceUtil {

    fun loadClassPathResourceAsString(filePath: String): String {
        val inputStream = this.javaClass.classLoader.getResourceAsStream(filePath)
            ?: error("Resource not found: $filePath")
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}