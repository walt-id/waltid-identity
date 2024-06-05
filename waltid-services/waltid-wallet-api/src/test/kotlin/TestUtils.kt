import java.io.File
import java.net.URLDecoder

object TestUtils {
    fun loadResource(relativePath: String): String =
        URLDecoder.decode(this::class.java.classLoader.getResource(relativePath)!!.path, "UTF-8")
            .let { File(it).readText() }
}