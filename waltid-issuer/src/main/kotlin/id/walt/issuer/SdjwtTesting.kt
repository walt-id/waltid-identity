package id.walt.issuer

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import id.walt.sdjwt.*
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object SdjwtTesting {

    private val sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"


    fun iss2(): SDJwt {
        val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, MACSigner(sharedSecret), MACVerifier(sharedSecret))

        val sdPayload = SDPayload.createSDPayload(
            fullPayload = JsonObject(
                mapOf(
                    "publicabc" to JsonPrimitive("202020"),

                    "priv1" to JsonPrimitive("abc"),
                    "priv2" to JsonPrimitive("xyz"),
                    "priv3" to JsonObject(
                        mapOf(
                            "a" to JsonPrimitive(12),
                            "b" to JsonPrimitive(false)
                        )
                    )
                )
            ),
            undisclosedPayload = JsonObject(
                mapOf(
                    "priv1" to JsonNull,
                    "priv2" to JsonNull,
                    "priv3" to JsonObject(
                        mapOf(
                            "a" to JsonNull,
                            "b" to JsonNull
                        )
                    )
                )
            ),
            decoyMode = DecoyMode.NONE,
            decoys = 10
        )

        return SDJwt.sign(sdPayload, cryptoProvider)
    }

    fun iss(): SDJwt {

        // Create SimpleJWTCryptoProvider with MACSigner and MACVerifier
        val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, MACSigner(sharedSecret), MACVerifier(sharedSecret))

        // Create original JWT claims set, using nimbusds claims set builder
        val originalClaimsSet = JWTClaimsSet.Builder()
            .subject("123")
            .audience("456")
            .claim("abc123", "hello world")
            .build()

        // Create undisclosed claims set, by removing e.g. subject property from original claims set
        val undisclosedClaimsSet = JWTClaimsSet.Builder(originalClaimsSet)
            .subject(null)
            .claim("abc123", null)
            .build()

        // Create SD payload by comparing original claims set with undisclosed claims set
        val sdPayload = SDPayload.createSDPayload(originalClaimsSet, undisclosedClaimsSet)

        // Create and sign SD-JWT using the generated SD payload and the previously configured crypto provider
        return SDJwt.sign(sdPayload, cryptoProvider)
    }

}


fun main() {
    //println(SdjwtTesting.iss2())
    println(SDMapBuilder().apply {
        addField("field-key", true)
        addField("field-parent", false, children = SDMapBuilder().addField("child", true).build())
    }.build().toJSON())
}
