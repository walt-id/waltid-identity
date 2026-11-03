package id.walt.etsi.cbades

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LocalPlugtestPkiTest {
    @Test
    fun persistsOwnerOnlyKeysAndReloadsTheSameIdentity() {
        val dir = Files.createTempDirectory("cbades-pki-test").toFile()
        try {
            val first = LocalPlugtestPki(dir, "TEST")
            dir.listFiles()!!.filter { it.extension == "key" }.forEach {
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(it.toPath()))
            }
            val reloaded = LocalPlugtestPki(dir, "TEST")
            assertContentEquals(first.root.cert.encoded, reloaded.root.cert.encoded)
            assertContentEquals(first.signer.keyPair.private.encoded, reloaded.signer.keyPair.private.encoded)
        } finally {
            dir.walkBottomUp().forEach { Files.delete(it.toPath()) }
        }
    }
}
