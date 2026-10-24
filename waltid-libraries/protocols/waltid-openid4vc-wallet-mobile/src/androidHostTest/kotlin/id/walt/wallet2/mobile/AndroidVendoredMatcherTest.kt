package id.walt.wallet2.mobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the vendored matchers, read through the same asset API the registry uses, because their absence
 * or replacement is otherwise only observable when Credential Manager opens the platform picker on a device. A
 * refresh is a deliberate compatibility change rather than a file swap: see `ANNEX-C-MATCHER.md`,
 * `OPENID4VP-MATCHER.md`, and `OPENID4VCI-MATCHER.md`.
 *
 * The asset paths are spelled out here rather than shared with the registry, so that this asserts the
 * packaging contract instead of restating whatever path production happens to declare.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidVendoredMatcherTest {
    private val assets = RuntimeEnvironment.getApplication().assets

    @Test
    fun matcherAssetsMatchPinnedBinaries() {
        val expectedHashes = mapOf(
            "id/walt/wallet2/mobile/identitycredentialmatcher.wasm" to
                "420385a46bf554b34224c960051a0cd6c4ecff12ca2c3bdb8948a555afd6e0f8",
            "id/walt/wallet2/mobile/issuance.wasm" to
                "3a745c778f1881365cced1f4215b5e7e80f673928052f9d3883eb3d596d42c95",
            "id/walt/wallet2/mobile/openid4vpmatcher.wasm" to
                "5f1738caf65854d8999701dd54a7caafbd8c2fccc355be340e553fe16f5cfd79",
        )
        for ((path, expectedHash) in expectedHashes) {
            val matcher = assets.open(path).use { it.readBytes() }
            assertEquals(
                expectedHash,
                MessageDigest.getInstance("SHA-256").digest(matcher).joinToString("") { "%02x".format(it) },
                "$path changed; review the matcher provenance before repinning",
            )
        }
    }

    @Test
    fun matcherNoticesRetainProvenanceAndAttribution() {
        val requiredNotices = mapOf(
            "id/walt/wallet2/mobile/NOTICE-identitycredentialmatcher.txt" to listOf(
                "7c0988bee3384d13a0732e0c33336ae0faf3b863",
                "Apache License",
                "MIT License",
            ),
            "id/walt/wallet2/mobile/NOTICE-issuance.txt" to listOf(
                "android/identity-samples",
                "issuance.wasm",
                "d5a8adc1b84061a4e3a9581cdaf867df89fb1f19",
                "src/bin/issuance.rs",
                "Apache License",
                "LLVM Exception",
                "MIT License",
            ),
            "id/walt/wallet2/mobile/NOTICE-openid4vpmatcher.txt" to listOf(
                "5d966fc4913cac93f3b3b11e11bdd44d3e0b5c9e",
                "Apache License",
                "LLVM Exception",
                "MIT License",
            ),
        )
        for ((path, requiredText) in requiredNotices) {
            val notice = assets.open(path).use { it.readBytes() }.decodeToString()
            for (text in requiredText) {
                assertTrue(notice.contains(text), "$path lost required notice text: $text")
            }
        }
    }
}
