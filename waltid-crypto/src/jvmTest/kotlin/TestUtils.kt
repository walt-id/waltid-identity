import java.io.File

object TestUtils {
    fun loadJwkLocal(filename: String): String = loadResource("jwk/$filename")
    fun loadPemLocal(filename: String): String = loadResource("pem/$filename")
    fun loadSerializedLocal(filename: String): String = loadResource("serialized/local/$filename")
    fun loadSerializedTse(filename: String): String = loadResource("serialized/tse/$filename")
    fun loadResource(relativePath: String): String =
        this::class.java.classLoader.getResource(relativePath)!!.path.let { File(it).readText() }
    fun loadResourceBytes(relativePath: String): ByteArray =
        this::class.java.classLoader.getResource(relativePath)!!.path.let { File(it).readBytes() }
}