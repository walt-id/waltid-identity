import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.sdjwt.SDMap
import kotlinx.serialization.json.JsonElement
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


val keysToTest = listOf(KeyType.Ed25519, KeyType.secp256r1, KeyType.secp256k1, KeyType.RSA)

private suspend fun init(didMethodsToTest: List<String>) {
    DidService.minimalInit()

    println("Resolvers -> ${DidService.resolverMethods.keys}")
    println("Registrars -> ${DidService.registrarMethods.keys}")

    didMethodsToTest.forEach {
        assertContains(DidService.resolverMethods.keys, it)
    }
}

suspend fun testDidMethodsAndKeys(methods: List<String>) {
    init(methods)

    // test create and sign credential using 4 different key types
    keysToTest.forEach {
        testCreateSignCredential(methods, it)
    }
}

private suspend fun testCreateSignCredential(didMethodsToTest: List<String>, keyType: KeyType) {
    val key = JWKKey.generate(keyType)
    println("Create and Sign VC using KeyType ${keyType}...\n")

    didMethodsToTest.forEach {
        if (it == "web") {
            // test create and sign VC with did:web
            testWeb(key)
        } else {
            testDidMethod(it, key)
        }
    }
}

private suspend fun testDidMethod(didMethod: String, key: JWKKey) {
    if (didMethod == "cheqd" && key.keyType != KeyType.Ed25519) {
        return
    }
    println("REGISTER $didMethod, KEY $key ")
    val did = DidService.registerByKey(didMethod, key).did
    val vc = createVC(did)
    assertNotNull(vc)
    testIssuerDid(vc["issuer"], "did:$didMethod")

    // Sign VC with did method (JWS)
    val jws = vc.signJws(
        issuerKey = key,
        issuerDid = did,
        subjectDid = did
    )
    assertNotNull(jws)
    println("did:$didMethod (JWT) SIGNATURE = $jws")

    // Sign VC with did method (SD-JWT)
    val sdJwt = vc.signSdJwt(
        issuerKey = key,
        issuerDid = did,
        subjectDid = did,
        SDMap(emptyMap()) // empty selective disclosure map, we'll test this elsewhere
    )
    assertNotNull(sdJwt)
    println("did:$didMethod (SD-JWT) SIGNATURE = $sdJwt")
}

private fun testIssuerDid(did: JsonElement?, key: String) {
    val issuerDid = did.toString()
    println("Created W3C Verifiable Credential: Issuer did = $issuerDid")
    val contains = issuerDid.contains(key)
    assertTrue(contains)

}

private suspend fun testWeb(key: JWKKey) {
    val TEST_WALLET_KEY =
        "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"

    val pubKey = JWKKey.importJWK(TEST_WALLET_KEY).getOrThrow().getPublicKey()
    val didWebResult = DidService.registerByKey(
        "web",
        pubKey,
        DidWebCreateOptions("entra.walt.id", "holder", KeyType.Ed25519)
    )
    println("\n>>>>>>>>> web DID = ${didWebResult.did}")

    val vc = createVC(didWebResult.did)
    assertNotNull(vc)
    testIssuerDid(vc["issuer"], "did:web")
    // Sign VC with WEB
    val jws = vc.signJws(
        issuerKey = key,
        issuerDid = didWebResult.did,
        subjectDid = didWebResult.did
    )

    assertNotNull(jws)
    println("did:web (JWT) SIGNATURE = $jws")

    // Sign VC with WEB (SD-JWT)
    val sdJwt = vc.signSdJwt(
        issuerKey = key,
        issuerDid = didWebResult.did,
        subjectDid = didWebResult.did,
        SDMap(emptyMap()) // empty selective disclosure map, we'll test this elsewhere
    )
    assertNotNull(sdJwt)
    println("did:web (SD-JWT) SIGNATURE = $sdJwt")
}


suspend fun testCheqd(key: JWKKey) {
    if (key.keyType != KeyType.Ed25519) {
        return
    }
    val cheqdid = DidService.registerByKey("cheqd", key).did
    println("\n>>>>>>>>> cheqd DID = $cheqdid")
    val vc = createVC(cheqdid)
    assertNotNull(vc)
    testIssuerDid(vc["issuer"], "did:cheqd")

    val jws = vc.signJws(
        issuerKey = key,
        issuerDid = cheqdid,
        subjectDid = cheqdid
    )
    assertNotNull(jws)
    println("did:cheqd (JWT) SIGNATURE = $jws")

    // Sign VC with CHEQD (SD-JWT)
    val sdJwt = vc.signSdJwt(
        issuerKey = key,
        issuerDid = cheqdid,
        subjectDid = cheqdid,
        SDMap(emptyMap()) // empty selective disclosure map, we'll test this elsewhere
    )
    assertNotNull(sdJwt)
    println("did:cheqd (SD-JWT) SIGNATURE = $sdJwt")
}

private fun createVC(did: String): W3CVC {
    // Syntax-sugar to create VC

    // TODO move the test credential into a helper file to share with other tests
    return W3CVC.build(
        context = listOf("https://example.org"),
        type = listOf("VerifiableCredential", "VerifiableId"),

        "id" to "urn:uuid:4177e048-9a4a-474e-9dc6-aed4e61a6439",
        "issuer" to did,
        "issuanceDate" to "2023-08-02T08:03:13Z",
        "issued" to "2023-08-02T08:03:13Z",
        "validFrom" to "2023-08-02T08:03:13Z",
        "credentialSchema" to mapOf(
            "id" to "https://raw.githubusercontent.com/walt-id/waltid-ssikit-vclib/master/src/test/resources/schemas/VerifiableId.json",
            "type" to "FullJsonSchemaValidator2021"
        ),
        "credentialSubject" to mapOf(
            "id" to did,
            "currentAddress" to listOf("1 Boulevard de la Liberté, 59800 Lille"),
            "dateOfBirth" to "1993-04-08",
            "familyName" to "DOE",
            "firstName" to "Jane",
            "gender" to "FEMALE",
            "nameAndFamilyNameAtBirth" to "Jane DOE",
            "personalIdentifier" to "0904008084H",
            "placeOfBirth" to "LILLE, FRANCE"
        ),
        "evidence" to listOf(
            mapOf(
                "documentPresence" to listOf("Physical"),
                "evidenceDocument" to listOf("Passport"),
                "subjectPresence" to "Physical",
                "type" to listOf("DocumentVerification"),
                "verifier" to "did:ebsi:2A9BZ9SUe6BatacSpvs1V5CdjHvLpQ7bEsi2Jb6LdHKnQxaN"
            )
        )
    )
}



