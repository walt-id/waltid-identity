package id.walt.webwallet.web.controllers

import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.json.toJsonElement
import id.walt.mdoc.dataelement.json.toUIJson
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.*
import java.util.Base64

fun Application.utility() {
    webWalletRoute {
        route("util", { tags = listOf("Utilities") }) {
            post("parseMDoc", {
                summary = "Parse MDOC document to JSON element"
                request {
                    body<String> {
                        required = true
                        example("Sample mdoc") {
                            value =
                                "a267646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c6973737565725369676e6564a26a6e616d65537061636573a1716f72672e69736f2e31383031332e352e3183d8185852a4686469676573744944006672616e646f6d50fce6b21d930b5b99fad34980ab06c8ee71656c656d656e744964656e7469666965726b66616d696c795f6e616d656c656c656d656e7456616c756563446f65d8185852a4686469676573744944016672616e646f6d5058daba0e58ae65726d9ba1aaa62256ee71656c656d656e744964656e7469666965726a676976656e5f6e616d656c656c656d656e7456616c7565644a6f686ed8185858a4686469676573744944026672616e646f6d5063fd5066277bce71963369771b78c1f671656c656d656e744964656e7469666965726a62697274685f646174656c656c656d656e7456616c75656a313938302d30312d30316a697373756572417574688443a10126a1182159014b308201473081eea003020102020839edc87a9a78f92a300a06082a8648ce3d04030230173115301306035504030c0c4d444f4320524f4f54204341301e170d3234303530323133313333305a170d3235303530323133313333305a301b3119301706035504030c104d444f432054657374204973737565723059301306072a8648ce3d020106082a8648ce3d030107034200041b4448341885fa84140f77790c69de810b977a7236f490da306a0cbe2a0a441379ddde146b36a44b6ba7bbc067b04b71bad4b692a4616013d893d440ae253781a320301e300c0603551d130101ff04023000300e0603551d0f0101ff040403020780300a06082a8648ce3d04030203480030450221008e70041000ddec2a230b2586ecc59f8acd156f5d933d9363bc5e2263bb0ab69802201885a8b537327a69b022620f07c5c45d6293b86eed927a3f04e82cc51cadf8635901c3d8185901bea66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a1716f72672e69736f2e31383031332e352e31a3005820ac6801aa40d9871db115c9ba804bbccbddf7f29a6773d626cb6604d468e8714e015820066fc7c19bce2aeaf2d655351da21dbb12561db212e21e8c3e969fa469fd1c7c025820dbf831a97d5b504ca70c212224109e243f01f82cb4cde7c704a7166fd671ed326d6465766963654b6579496e666fa1696465766963654b6579a4010220012158200f08fd91a6b62e757e090514cd54d506ea4fb4354e10cdaa24c7748f59fb5e10225820ffa4113b5aef1a4dbd3fb4b9da126bc1ffc09b9cc679b4673dd321f021f2fc2167646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa3667369676e6564c0781e323032342d30372d32355431333a30353a33312e3438333237373433355a6976616c696446726f6dc0781e323032342d30372d32355431333a30353a33312e3438333237373738305a6a76616c6964556e74696cc0781e323032352d30372d32355431333a30353a33312e3438333237373836335a5840d57ee4f1a38cf49860b2f9b7c8f2469faa68720a8b731eae1d727e681bf0299fe86c0c120407cc8f0a7b951a6db6eac4c1905f07436fc556be1a65c13e432490"
                        }
                    }
                }
                response {
                    HttpStatusCode.OK to { description = "MDoc successfully parsed to JSON" }
                }
            }) {
                val mdocHex = call.receive<String>()
                val mdoc = id.walt.mdoc.doc.MDoc.fromCBORHex(mdocHex)
                val uiJson = buildJsonObject {
                    put("docType", JsonPrimitive(mdoc.docType.value))
                    mdoc.issuerSigned.toUIJson().let { issuerNamespaces ->
                        put("issuerSigned", buildJsonObject {
                            put("nameSpaces", enhanceForUI(issuerNamespaces))
                        })
                    }
                    mdoc.deviceSigned?.let {
                        put("deviceSigned", it.toMapElement().toJsonElement())
                    }
                }
                call.respond(uiJson)
            }
        }
    }
}

private fun enhanceForUI(issuerNamespaces: JsonObject): JsonObject = buildJsonObject {
    issuerNamespaces.forEach { (namespace, claims) ->
        put(namespace, claims.jsonObject.mapValues { (_, value) ->
            enhanceClaimValueForUI(value)
        }.let { JsonObject(it) })
    }
}

private fun enhanceClaimValueForUI(value: JsonElement): JsonElement = when {
    // Already a base64 string (check common base64 image prefixes)
    value is JsonPrimitive && value.isString && value.content.let { 
        it.startsWith("iVBOR") || it.startsWith("/9j/") || it.startsWith("R0lG") || it.startsWith("UklG")
    } -> {
        // Convert raw base64 to data URL
        val base64 = value.content
        val mimeType = when {
            base64.startsWith("iVBOR") -> "image/png"
            base64.startsWith("/9j/") -> "image/jpeg"
            base64.startsWith("R0lG") -> "image/gif"
            base64.startsWith("UklG") -> "image/webp"
            else -> "image/jpeg"
        }
        JsonPrimitive("data:$mimeType;base64,$base64")
    }
    // ByteString array → base64 data URL for images (portrait, signature_usual_mark)
    value is JsonArray && value.all { it is JsonPrimitive && it.intOrNull != null } -> {
        val bytes = value.map { it.jsonPrimitive.int.toByte() }.toByteArray()
        if (bytes.size > 100) {
            // Likely binary data (image) → data URL
            val base64 = Base64.getEncoder().encodeToString(bytes)
            val mimeType = when {
                bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
                bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                else -> "image/jpeg"
            }
            JsonPrimitive("data:$mimeType;base64,$base64")
        } else {
            // Short byte arrays stay as-is
            value
        }
    }
    // Arrays of objects (driving_privileges) → keep as array
    value is JsonArray -> value
    // Nested objects → recursively enhance
    value is JsonObject -> buildJsonObject {
        value.forEach { (key, nestedValue) ->
            put(key, enhanceClaimValueForUI(nestedValue))
        }
    }
    else -> value
}
