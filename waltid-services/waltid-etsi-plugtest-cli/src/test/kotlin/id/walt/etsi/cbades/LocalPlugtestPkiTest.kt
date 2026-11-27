package id.walt.etsi.cbades

import id.walt.ades.x509.X509Crl
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalPlugtestPkiTest {
    @Test
    fun persistsOwnerOnlyKeysAndReloadsTheSameIdentity() {
        val dir = Files.createTempDirectory("cbades-pki-test").toFile()
        try {
            val first = LocalPlugtestPki(dir, "TEST")
            val serials = listOf(first.root, first.ca, first.signer, first.signer2, first.counter1, first.counter2, first.tsa, first.aa)
                .map { it.cert.serialNumber }
            assertTrue(serials.all { it.signum() > 0 })
            assertEquals(serials.size, serials.distinct().size)
            dir.listFiles()!!.filter { it.extension == "key" }.forEach {
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(it.toPath()))
            }
            val now = Clock.System.now()
            for (file in File(dir, "trust").listFiles()!!.filter { it.extension == "crl" }) {
                val crl = X509Crl(file.readBytes())
                assertTrue(crl.thisUpdate >= now - 2.minutes, "New CRLs must not start at the freshness limit")
                assertTrue(crl.thisUpdate <= now)
            }
            val reloaded = LocalPlugtestPki(dir, "TEST")
            assertContentEquals(first.root.cert.encoded, reloaded.root.cert.encoded)
            assertContentEquals(first.signer.keyPair.private.encoded, reloaded.signer.keyPair.private.encoded)
        } finally {
            dir.walkBottomUp().forEach { Files.delete(it.toPath()) }
        }
    }
}
