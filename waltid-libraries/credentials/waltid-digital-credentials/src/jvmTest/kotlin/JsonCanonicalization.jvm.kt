import org.json.JSONObject
import java.util.*

actual object JsonCanonicalization {
    actual fun getCanonicalBytes(json: String): ByteArray {
        return getCanonicalString(json).toByteArray(Charsets.UTF_8)
    }

    actual fun getCanonicalString(json: String): String {
        val jsonObject = JSONObject(json)
        val sortedMap = TreeMap<String, Any?>()

        for (key in jsonObject.keys()) {
            sortedMap[key] = jsonObject.get(key)
        }

        return JSONObject(sortedMap as Map<*, *>).toString()
    }
}
