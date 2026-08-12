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
 * or replacement is otherwise only observable when a verifier opens the platform picker on a device. A
 * refresh is a deliberate compatibility change rather than a file swap: see `ANNEX-C-MATCHER.md` and
 * `OPENID4VP-MATCHER.md`.
 *
 * The asset paths are spelled out here rather than shared with the registry, so that this asserts the
 * packaging contract instead of restating whatever path production happens to declare.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidVendoredMatcherTest {
    private val assets = RuntimeEnvironment.getApplication().assets

    @Test
    fun matcherAssetIsThePinnedMultipazBinary() {
        val matcher = assets.open("id/walt/wallet2/mobile/identitycredentialmatcher.wasm")
            .use { it.readBytes() }

        assertEquals(
            "420385a46bf554b34224c960051a0cd6c4ecff12ca2c3bdb8948a555afd6e0f8",
            MessageDigest.getInstance("SHA-256").digest(matcher).joinToString("") { "%02x".format(it) },
            "vendored matcher content changed; see ANNEX-C-MATCHER.md before repinning",
        )
    }

    @Test
    fun thirdPartyNoticeShipsBesideTheMatcher() {
        val notice = assets.open("id/walt/wallet2/mobile/NOTICE-identitycredentialmatcher.txt")
            .use { it.readBytes() }.decodeToString()

        assertTrue(notice.contains("7c0988bee3384d13a0732e0c33336ae0faf3b863"), "notice lost its provenance")
        assertTrue(notice.contains("Apache License"), "notice lost the Apache-2.0 text")
        assertTrue(notice.contains("MIT License"), "notice lost the cJSON attribution")
    }

    @Test
    fun openId4VpMatcherAssetIsThePinnedGoogleBinary() {
        val matcher = assets.open("id/walt/wallet2/mobile/openid4vpmatcher.wasm")
            .use { it.readBytes() }

        assertEquals(
            "5f1738caf65854d8999701dd54a7caafbd8c2fccc355be340e553fe16f5cfd79",
            MessageDigest.getInstance("SHA-256").digest(matcher).joinToString("") { "%02x".format(it) },
            "vendored matcher content changed; see OPENID4VP-MATCHER.md before repinning",
        )
    }

    @Test
    fun thirdPartyNoticeShipsBesideTheOpenId4VpMatcher() {
        val notice = assets.open("id/walt/wallet2/mobile/NOTICE-openid4vpmatcher.txt")
            .use { it.readBytes() }.decodeToString()

        assertTrue(
            notice.contains("5d966fc4913cac93f3b3b11e11bdd44d3e0b5c9e"),
            "notice lost its provenance",
        )
        assertTrue(notice.contains("Apache License"), "notice lost the Apache-2.0 text")
        assertTrue(notice.contains("LLVM Exception"), "notice lost the compiler-builtins exception")
        assertTrue(notice.contains("MIT License"), "notice lost the memchr attribution")
    }
}
