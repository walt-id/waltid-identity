import io.ktor.util.*
import java.io.File
import java.net.URLDecoder

object TestUtils {
    fun loadJwkLocal(filename: String): String = loadResource("jwk/$filename")
    fun loadPemLocal(filename: String): String = loadResource("pem/$filename")
    fun loadSerializedLocal(filename: String): String = loadResource("serialized/local/$filename")
    fun loadSerializedTse(filename: String): String = loadResource("serialized/tse/$filename")
    fun loadResource(relativePath: String): String =
        URLDecoder.decode(this::class.java.classLoader.getResource(relativePath)!!.path, "UTF-8")
            .let { File(it).readText() }

    fun loadResourceBytes(relativePath: String): ByteArray =
        URLDecoder.decode(this::class.java.classLoader.getResource(relativePath)!!.path, "UTF-8")
            .let { File(it).readBytes() }

    fun loadResourceBase64(relativePath: String): ByteArray = loadResource(relativePath).decodeBase64Bytes()
}
