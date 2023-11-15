package id.walt.issuer.utils

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.reflect.full.memberProperties

val httpClient = HttpClient() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.HEADERS
    }
}

fun resolveContent(fileUrlContent: String): String {
    val file = File(fileUrlContent)
    if (file.exists()) {
        return file.readText()
    }
    if (fileUrlContent.startsWith("class:")) {
        val enclosingClass = object {}.javaClass.enclosingClass
        val path = fileUrlContent.substring(6)
        var url = enclosingClass.getResource(path)
        if (url == null && !path.startsWith('/'))
            url = enclosingClass.getResource("/$path")
        return url?.readText() ?: fileUrlContent
    }
    if (Regex("^https?:\\/\\/.*$").matches(fileUrlContent)) {
        return runBlocking { httpClient.get(fileUrlContent).bodyAsText() }
    }
    return fileUrlContent
}

/**
 * Converts a class properties into map.
 *
 * ___Note___: Applicable only for linear properties, nested properties will be ignored.
 */
inline fun <reified T : Any> T.asMap() : Map<String, Any?> {
    val props = T::class.memberProperties.associateBy { it.name }
    return props.keys.associateWith { props[it]?.get(this) }
}

fun String.toBitSet(initialSize: Int) = let {
    val bitSet = BitSet(initialSize)
    for (i in this.indices) {
        if (this[i] == '1') bitSet.set(i)
    }
    bitSet
}

fun CharArray.toBitSet(initialSize: Int) = String(this).toBitSet(initialSize)

fun compressGzip(data: ByteArray): ByteArray {
    val result = ByteArrayOutputStream()
    GZIPOutputStream(result).use {
        it.write(data)
    }
    return result.toByteArray()
}

fun uncompressGzip(data: ByteArray, idx: ULong? = null) =
    GZIPInputStream(data.inputStream()).bufferedReader().use {
        idx?.let { index ->
            var int = it.read()
            var count = 0U
            var char = int.toChar()
            while (int != -1 && count++ <= index) {
                char = int.toChar()
                int = it.read()
            }
            char
        }?.let {
            val array = CharArray(1)
            array[0] = it
            array
        } ?: it.readText().toCharArray()
    }

fun buildRawBitString(bitSet: BitSet): ByteArray{
    var lastIndex = 0
    var currIndex = bitSet.nextSetBit(lastIndex);
    val builder = StringBuilder()
    while (currIndex > -1) {
        val delta = 1 % (lastIndex + 1)
        builder.append("0".repeat(currIndex - lastIndex - delta)).append("1")
        lastIndex = currIndex
        currIndex = bitSet.nextSetBit(lastIndex + 1)//TODO: handle overflow
    }
    builder.append("0".repeat(bitSet.size() - lastIndex - 1))
    return builder.toString().toByteArray()
}

fun createEncodedBitString(bitSet: BitSet = BitSet(16 * 1024 * 8)): ByteArray =
    Base64.getEncoder().encode(compressGzip(buildRawBitString(bitSet)))

fun decodeBitSet(bitString: String): BitSet = uncompressGzip(Base64.getDecoder().decode(bitString)).toBitSet(16 * 1024 * 8)

fun decBase64(base64: String): ByteArray = Base64.getDecoder().decode(base64)

@OptIn(ExperimentalSerializationApi::class)
fun createJsonBuilder(serializerModule: SerializersModule? = null) = Json {
    serializerModule?.run { serializersModule = this }
    ignoreUnknownKeys = true
    prettyPrint = true
    isLenient = true
    explicitNulls = false
}