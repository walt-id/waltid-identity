import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.webwallet.service.keys.SingleKeyResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertNotNull

fun assertKeyComponents(document: JsonElement, keyId: String, type: KeyType, isPrivate: Boolean = false) {
    assertNotNull(document.tryGetData("kid")?.jsonPrimitive?.content) { "Missing _kid_ component!" }
    assertTrue(document.tryGetData("kid")?.jsonPrimitive?.content == keyId) { "Wrong _kid_ value!" }
    assertNotNull(document.tryGetData("kty")?.jsonPrimitive?.content) { "Missing _kty_ component!" }
    when (type) {
        KeyType.Ed25519 -> assertEd25519KeyComponents(document, isPrivate)
        KeyType.secp256k1 -> assertSecp256k1KeyComponents(document, isPrivate)
        KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> assertSecpKeyComponents(document, isPrivate)
        KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 -> assertRSAKeyComponents(document, isPrivate)
    }
}

fun assertEd25519KeyComponents(document: JsonElement, isPrivate: Boolean) {
    assertTrue(document.tryGetData("kty")?.jsonPrimitive?.content == "OKP") { "Wrong _kty_ value!" }
    assertNotNull(document.tryGetData("crv")?.jsonPrimitive?.content) { "Missing _crv_ component!" }
    assertTrue(document.tryGetData("crv")?.jsonPrimitive?.content == "Ed25519") { "Wrong _crv_ value!" }
    assertNotNull(document.tryGetData("x")?.jsonPrimitive?.content) { "Missing _x_ component!" }
    if (isPrivate) assertNotNull(document.tryGetData("d")?.jsonPrimitive?.content) { "Missing _d_ component!" }
}

fun assertSecp256k1KeyComponents(document: JsonElement, isPrivate: Boolean) {
    assertTrue(document.tryGetData("kty")?.jsonPrimitive?.content == "EC") { "Wrong _kty_ value!" }
    assertNotNull(document.tryGetData("crv")?.jsonPrimitive?.content) { "Missing _crv_ component!" }
    assertTrue(document.tryGetData("crv")?.jsonPrimitive?.content == "secp256k1") { "Wrong _crv_ value!" }
    assertNotNull(document.tryGetData("x")?.jsonPrimitive?.content) { "Missing _x_ component!" }
    if (isPrivate) assertNotNull(document.tryGetData("d")?.jsonPrimitive?.content) { "Missing _d_ component!" }
}

fun assertSecpKeyComponents(document: JsonElement, isPrivate: Boolean) {
    assertTrue(document.tryGetData("kty")?.jsonPrimitive?.content == "EC") { "Wrong _kty_ value!" }
    assertNotNull(document.tryGetData("crv")?.jsonPrimitive?.content) { "Missing _crv_ component!" }
    assertTrue(document.tryGetData("crv")?.jsonPrimitive?.content == "P-256") { "Wrong _crv_ value!" }
    assertNotNull(document.tryGetData("x")?.jsonPrimitive?.content) { "Missing _x_ component!" }
    assertNotNull(document.tryGetData("y")?.jsonPrimitive?.content) { "Missing _x_ component!" }
    if (isPrivate) assertNotNull(document.tryGetData("d")?.jsonPrimitive?.content) { "Missing _d_ component!" }
}

fun assertRSAKeyComponents(document: JsonElement, isPrivate: Boolean) {
    assertTrue(document.tryGetData("kty")?.jsonPrimitive?.content == "RSA") { "Wrong _kty_ value!" }
    assertNotNull(document.tryGetData("e")?.jsonPrimitive?.content) { "Missing _e_ component!" }
    assertNotNull(document.tryGetData("n")?.jsonPrimitive?.content) { "Missing _n_ component!" }
    if (isPrivate) {
        assertNotNull(document.tryGetData("d")?.jsonPrimitive?.content) { "Missing _d_ component!" }
        assertNotNull(document.tryGetData("p")?.jsonPrimitive?.content) { "Missing _p_ component!" }
        assertNotNull(document.tryGetData("q")?.jsonPrimitive?.content) { "Missing _q_ component!" }
        assertNotNull(document.tryGetData("qi")?.jsonPrimitive?.content) { "Missing _qi_ component!" }
        assertNotNull(document.tryGetData("dp")?.jsonPrimitive?.content) { "Missing _dp_ component!" }
        assertNotNull(document.tryGetData("dq")?.jsonPrimitive?.content) { "Missing _dq_ component!" }
    }
}

fun assertDefaultKey(listing: List<SingleKeyResponse>, default: KeyGenerationRequest) {
    assertTrue(listing.isNotEmpty()) { "No default key was created!" }
    assertTrue(KeyType.valueOf(listing[0].algorithm) == default.keyType) { "Default key type not ${default.keyType}" }
}

fun assertNoDefaultKey(listing: List<SingleKeyResponse>) {
    assertTrue(listing.isEmpty()) { "Expected no default key!" }
}
