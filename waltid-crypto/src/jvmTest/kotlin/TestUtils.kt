import java.io.File

object TestUtils {
    fun loadJwk(filename: String): String = loadResource("jwk/$filename")
    fun loadPem(filename: String): String = loadResource("pem/$filename")
    fun loadResource(relativePath: String): String =
        this::class.java.classLoader.getResource(relativePath)!!.path.let { File(it).readText() }
    fun loadResourceBytes(relativePath: String): ByteArray =
        this::class.java.classLoader.getResource(relativePath)!!.path.let { File(it).readBytes() }
}