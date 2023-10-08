package id.walt.ssikit.schemes

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.keys.TSEKey
import id.walt.core.crypto.utils.JwsUtils.decodeJws
import id.walt.ssikit.did.DidService
import id.walt.ssikit.did.registrar.dids.DidJwkCreateOptions
import id.walt.ssikit.did.registrar.local.jwk.DidJwkRegistrar
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class JwsSignatureScheme : SignatureScheme {

    /**
     * args:
     * - kid: Key ID
     * - subjectDid: Holder DID
     * - issuerDid: Issuer DID
     */
    suspend fun sign(data: JsonObject, key: Key, args: Map<String, String>): String {
        val header = mapOf(
            "kid" to args["kid"].toString(),
        ).also { println("Header: $it") }

        val payload = Json.encodeToString(
            mapOf(
                "sub" to JsonPrimitive(args["subjectDid"]),
                "iss" to JsonPrimitive(args["issuerDid"]),
                "vc" to data,
            )
        ).also { println("Payload: $it") }.encodeToByteArray()

        return key.signJws(payload, header)
    }

    suspend fun verify(data: String): Result<JsonObject> = runCatching {
        val jws = data.decodeJws()

        val payload = jws.payload

        val issuerDid = payload["iss"]!!.jsonPrimitive.content
//        val subjectDid = payload["sub"]!!.jsonPrimitive.content
//        println("Issuer: $issuerDid")
//        println("Subject: $subjectDid")

        DidService.resolveToKey(issuerDid).getOrThrow()
            .verifyJws(data).getOrThrow()
    }
}

suspend fun main() {
    println("> INIT SERVICES")
    DidService.init()

    println("> INIT VARIABLES")
    val key = TSEKey.generate(KeyType.Ed25519)
    val didResult = DidJwkRegistrar().registerByKey(key.getPublicKey(), DidJwkCreateOptions())
    val did = didResult.did

    val vc = Json.parseToJsonElement(
        """{
    "type": [
      "VerifiableCredential",
      "VerifiableAttestation",
      "VerifiableId"
    ],
    "@context": [
      "https://www.w3.org/2018/credentials/v1"
    ],
    "id": "urn:uuid:4177e048-9a4a-474e-9dc6-aed4e61a6439",
    "issuer": "$did",
    "issuanceDate": "2023-08-02T08:03:13Z",
    "issued": "2023-08-02T08:03:13Z",
    "validFrom": "2023-08-02T08:03:13Z",
    "credentialSchema": {
      "id": "https://raw.githubusercontent.com/walt-id/waltid-ssikit-vclib/master/src/test/resources/schemas/VerifiableId.json",
      "type": "FullJsonSchemaValidator2021"
    },
    "credentialSubject": {
      "id": "$did",
      "currentAddress": [
        "1 Boulevard de la Liberté, 59800 Lille"
      ],
      "dateOfBirth": "1993-04-08",
      "familyName": "DOE",
      "firstName": "Jane",
      "gender": "FEMALE",
      "nameAndFamilyNameAtBirth": "Jane DOE",
      "personalIdentifier": "0904008084H",
      "placeOfBirth": "LILLE, FRANCE"
    },
    "evidence": [
      {
        "documentPresence": [
          "Physical"
        ],
        "evidenceDocument": [
          "Passport"
        ],
        "subjectPresence": "Physical",
        "type": [
          "DocumentVerification"
        ],
        "verifier": "did:ebsi:2A9BZ9SUe6BatacSpvs1V5CdjHvLpQ7bEsi2Jb6LdHKnQxaN"
      }
    ]
  }"""
    ).jsonObject


    //val did = "did:key:z6Mkqh3Wegafn8DcWNqw1xAneCzaW76kDPMCjrZC8d9zcHo9"

    println()
    println("> ISSUE CREDENTIAL")
    val jws = JwsSignatureScheme().sign(vc, key, mapOf("kid" to did, "issuerDid" to did, "subjectDid" to did))
    println("JWS: $jws")

    println()
    println("> VERIFY CREDENTIAL")
    println(JwsSignatureScheme().verify(jws))
}
