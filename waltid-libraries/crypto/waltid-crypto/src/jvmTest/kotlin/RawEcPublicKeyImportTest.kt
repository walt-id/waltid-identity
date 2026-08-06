import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Raw public keys of `did:key:zDna...` (P-256) DIDs are compressed curve points. A compressed point
 * starts with `0x02` or `0x03`, which BouncyCastle also reads as an ASN.1 INTEGER or BIT STRING tag,
 * so for roughly one in six points the SPKI parsing attempt fails with `IllegalStateException`
 * instead of `IllegalArgumentException`. Importing a plain curve point must never depend on that.
 */
class RawEcPublicKeyImportTest {

    /**
     * Valid compressed P-256 point whose bytes form a complete DER BIT STRING
     * (`0x03` tag, length `0x1f`), so `ASN1Sequence.getInstance` reports a wrong ASN.1 object type.
     */
    private val compressedPointParsingAsAsn1Object =
        "031f00000000000000000000000000000000000000000000000000000000000002".hexToByteArray()

    private val expectedJwkX = "HwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAI"

    @Test
    fun `imports a compressed P-256 point whose bytes parse as an ASN1 object`() = runTest {
        val key = JWKKey.importRawPublicKey(KeyType.secp256r1, compressedPointParsingAsAsn1Object, null)

        assertEquals(expectedJwkX, key.exportJWKObject().getValue("x").jsonPrimitive.content)
    }
}
