import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.*
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.sdjwt.SDMap
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.test.*

class LocalVcApiTest {
  
  suspend fun init() {
    DidService.init()
    
    println("Resolvers -> ${DidService.resolverMethods.keys}")
    println("Registrars -> ${DidService.registrarMethods.keys}")
    
    assertContains(DidService.resolverMethods.keys, "key")
    assertContains(DidService.resolverMethods.keys, "jwk")
    assertContains(DidService.resolverMethods.keys, "web")
    assertContains(DidService.resolverMethods.keys, "cheqd")
    
    val key = LocalKey.generate(KeyType.Ed25519)
   
    try {
      val ebsi = DidService.registerByKey("ebsi", key).did
    } catch (nie: NotImplementedError) {
      println(">>>>>>>>> did:ebsi not implemented")
    }
    try {
      val sovdid = DidService.registerByKey("sov", key).did
    } catch (nie: NotImplementedError) {
      println(">>>>>>>>> did:sov not implemented")
    }
  }
  
  @OptIn(ExperimentalJsExport::class)
  @Test
  fun testVcApi() = runTest {
    init()
    // test create and sign credential using 4 different key types
    testCreateSignCredential(KeyType.Ed25519)
    testCreateSignCredential(KeyType.secp256r1)
    testCreateSignCredential(KeyType.secp256k1)
    testCreateSignCredential(KeyType.RSA)
  }
  
  suspend private fun testCreateSignCredential(keyType: KeyType) {
    val key = LocalKey.generate(keyType)
    println("Create and Sign VC using KeyType ${keyType}...\n")
    
    // test create and sign VC with did:jwk
    testJwk(key)
    
    // test create and sign VC with did:web
    testWeb(key)
    
    // test create and sign VC with did:key
    testKey(key)
    
    // test create and sign VC with did:cheqd
    testCheqd(key)
    
    println()
  }
  
  private fun testIssuerDid(did: JsonElement?, key: String) {
    val issuerDid = did.toString()
    println("Created W3C Verifiable Credential: Issuer did = $issuerDid")
    val contains = issuerDid.contains(key)
    assertTrue(contains)
  }
  
  suspend private fun testJwk(key: LocalKey) {
    val jwkDid = DidService.registerByKey("jwk", key).did
    println("\n>>>>>>>>> jwk DID = ${jwkDid}")
    var vc = createVC(jwkDid)
    assertNotNull(vc)
    testIssuerDid(vc["issuer"], "did:jwk")
    
    // Sign VC with JWK (JWS)
    var jws = vc.signJws(
      issuerKey = key,
      issuerDid = jwkDid,
      subjectDid = jwkDid
    )
    assertNotNull(jws)
    println("did:jwk (JWT) SIGNATURE = $jws")
    
    // Sign VC with JWK (SD-JWT)
    var sdJwt = vc.signSdJwt(
      issuerKey = key,
      issuerDid = jwkDid,
      subjectDid = jwkDid,
      SDMap(emptyMap()) // empty selective disclosure map, we'll test this elsewhere
    )
    assertNotNull(sdJwt)
    println("did:jwk (SD-JWT) SIGNATURE = $sdJwt")
  }
  
  suspend private fun testWeb(key: LocalKey) {
    val TEST_WALLET_KEY =
      "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"
    
    val pubKey = LocalKey.importJWK(TEST_WALLET_KEY).getOrThrow().getPublicKey()
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
  
  suspend private fun testKey(key: LocalKey) {
    val keydid = DidService.registerByKey("key", key).did
    println("\n>>>>>>>>> key DID = ${keydid}")
    val vc = createVC(keydid)
    assertNotNull(vc)
    testIssuerDid(vc["issuer"], "did:key")
    
    // Sign VC with KEY
    val jws = vc.signJws(
      issuerKey = key,
      issuerDid = keydid,
      subjectDid = keydid
    )
    assertNotNull(jws)
    println("did:key (JWT) SIGNATURE = $jws")
    
    // Sign VC with KEY (SD-JWT)
    val sdJwt = vc.signSdJwt(
      issuerKey = key,
      issuerDid = keydid,
      subjectDid = keydid,
      SDMap(emptyMap()) // empty selective disclosure map, we'll test this elsewhere
    )
    assertNotNull(sdJwt)
    println("did:key (SD-JWT) SIGNATURE = $sdJwt")
  }
  
  suspend fun testCheqd(key: LocalKey) {
    if (key.keyType != KeyType.Ed25519) {
      return
    }
    val cheqdid = DidService.registerByKey("cheqd", key).did
    println("\n>>>>>>>>> cheqd DID = ${cheqdid}")
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

}


