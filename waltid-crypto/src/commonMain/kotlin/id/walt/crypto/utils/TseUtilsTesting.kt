package id.walt.crypto.utils

/*import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi*/

/*
@OptIn(ExperimentalEncodingApi::class)
fun main() {
    val tseKeyExportJson = """
    {
  "keys": {
    "1": "BGPcgdbwZsNT6jsWnFFT9CXR3OQAc0J9XTrSu0j48uym+IQRL7xSPK95RcJNf8H1Gl+qmLvDcd/hMra7Q40fPA=="
  },
  "type": "ed25519",
  "name": "k1"
}
    """

    val exportKeyPairBase64 = Json.decodeFromString<JsonObject>(tseKeyExportJson)["keys"]!!.jsonObject["1"]!!.jsonPrimitive.content
    val decodedKeyPair = Base64.decode(exportKeyPairBase64)
    check(decodedKeyPair.size == 64)

    val privateKey = decodedKeyPair.take(32).toByteArray()
    val publicKey = decodedKeyPair.drop(32).toByteArray()

    val privateKeyBase64Url = Base64.UrlSafe.encode(privateKey).trimEnd('=')
    val publicKeyBase64Url = Base64.UrlSafe.encode(publicKey).trimEnd('=')

    val jwk =
        """{"kty":"OKP","d":"$privateKeyBase64Url","use":"sig","crv":"Ed25519","kid":"k1","x":"$publicKeyBase64Url","alg":"EdDSA"}""".trimIndent()

    println(jwk)
}
*/
