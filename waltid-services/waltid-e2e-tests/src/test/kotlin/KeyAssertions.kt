import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertNotNull

fun assertKeyComponents(document: JsonElement, keyId: String, type: KeyType, isPrivate: Boolean = false) {
    assertNotNull(document.tryGetData("kid")?.jsonPrimitive?.content) { "Missing _kid_ component!" }
    assert(document.tryGetData("kid")?.jsonPrimitive?.content == keyId) { "Wrong _kid_ value!" }
    assertNotNull(document.tryGetData("kty")?.jsonPrimitive?.content) { "Missing _kty_ component!" }
    when (type) {
        KeyType.Ed25519 -> assertEd25519KeyComponents(document, isPrivate)
        KeyType.secp256k1 -> assertSecp256k1KeyComponents(document, isPrivate)
        KeyType.secp256r1 -> assertSecp256r1KeyComponents(document, isPrivate)
        KeyType.RSA -> assertRSAKeyComponents(document, isPrivate)
    }
}

fun assertEd25519KeyComponents(document: JsonElement, isPrivate: Boolean) {
    assert(document.tryGetData("kty")?.jsonPrimitive?.content == "OKP") { "Wrong _kty_ value!" }
    assertNotNull(document.tryGetData("crv")?.jsonPrimitive?.content) { "Missing _crv_ component!" }
    assert(document.tryGetData("crv")?.jsonPrimitive?.content == "Ed25519") { "Wrong _crv_ value!" }
    assertNotNull(document.tryGetData("x")?.jsonPrimitive?.content) { "Missing _x_ component!" }
    if (isPrivate) assertNotNull(document.tryGetData("d")?.jsonPrimitive?.content) { "Missing _d_ component!" }
}

fun assertSecp256k1KeyComponents(document: JsonElement, isPrivate: Boolean) {
    assert(document.tryGetData("kty")?.jsonPrimitive?.content == "EC") { "Wrong _kty_ value!" }
    assertNotNull(document.tryGetData("crv")?.jsonPrimitive?.content) { "Missing _crv_ component!" }
    assert(document.tryGetData("crv")?.jsonPrimitive?.content == "secp256k1") { "Wrong _crv_ value!" }
    assertNotNull(document.tryGetData("x")?.jsonPrimitive?.content) { "Missing _x_ component!" }
    if (isPrivate) assertNotNull(document.tryGetData("d")?.jsonPrimitive?.content) { "Missing _d_ component!" }
}

fun assertSecp256r1KeyComponents(document: JsonElement, isPrivate: Boolean) {
    assert(document.tryGetData("kty")?.jsonPrimitive?.content == "EC") { "Wrong _kty_ value!" }
    assertNotNull(document.tryGetData("crv")?.jsonPrimitive?.content) { "Missing _crv_ component!" }
    assert(document.tryGetData("crv")?.jsonPrimitive?.content == "P-256") { "Wrong _crv_ value!" }
    assertNotNull(document.tryGetData("x")?.jsonPrimitive?.content) { "Missing _x_ component!" }
    assertNotNull(document.tryGetData("y")?.jsonPrimitive?.content) { "Missing _x_ component!" }
    if (isPrivate) assertNotNull(document.tryGetData("d")?.jsonPrimitive?.content) { "Missing _d_ component!" }
}

fun assertRSAKeyComponents(document: JsonElement, isPrivate: Boolean) {
    assert(document.tryGetData("kty")?.jsonPrimitive?.content == "RSA") { "Wrong _kty_ value!" }
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