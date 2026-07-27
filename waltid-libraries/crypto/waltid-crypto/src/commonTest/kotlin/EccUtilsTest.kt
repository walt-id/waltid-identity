import id.walt.crypto.keys.EccUtils
import kotlin.test.Test
import kotlin.test.assertContentEquals

class EccUtilsTest {
    @Test
    fun `converts a valid 64 byte DER signature instead of treating it as P1363`() {
        val r = ByteArray(30) { (it + 1).toByte() }
        val s = ByteArray(28) { (it + 31).toByte() }
        val der = byteArrayOf(0x30, 0x3e, 0x02, 0x1e) + r + byteArrayOf(0x02, 0x1c) + s

        val converted = EccUtils.convertDERtoIEEEP1363(der)

        assertContentEquals(ByteArray(2) + r + ByteArray(4) + s, converted)
    }

    @Test
    fun `keeps a raw P1363 signature unchanged`() {
        val raw = ByteArray(64) { (it + 1).toByte() }

        assertContentEquals(raw, EccUtils.convertDERtoIEEEP1363(raw))
    }
}
