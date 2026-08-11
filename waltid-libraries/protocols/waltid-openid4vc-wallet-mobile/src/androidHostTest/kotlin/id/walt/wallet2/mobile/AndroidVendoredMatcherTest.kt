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
 * Pins the vendored Annex C matcher, read through the same asset API the registry uses, because its
 * absence or replacement is otherwise only observable when a verifier opens the platform picker on a
 * device. A refresh is a deliberate compatibility change rather than a file swap: see
 * `ANNEX-C-MATCHER.md`.
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
    fun openId4VciMatcherAssetIsThePinnedProvisionBinary() {
        val matcher = assets.open("id/walt/wallet2/mobile/provision_hardcoded.wasm")
            .use { it.readBytes() }

        assertEquals(
            "d6b4846072839bb43b98dfa5da5ae9ec83f2c30ce875c1ebd19c5ad2b5344ac1",
            MessageDigest.getInstance("SHA-256").digest(matcher).joinToString("") { "%02x".format(it) },
            "vendored OpenID4VCI matcher content changed; see OPENID4VCI-MATCHER.md before repinning",
        )
    }

    @Test
    fun openId4VciMatcherNoticeShipsBesideTheMatcher() {
        val notice = assets.open("id/walt/wallet2/mobile/NOTICE-provision_hardcoded.txt")
            .use { it.readBytes() }.decodeToString()

        assertTrue(notice.contains("digitalcredentialsdev/CMWallet"), "notice lost its provenance")
        assertTrue(notice.contains("provision_hardcoded.wasm"), "notice lost the asset name")
        assertTrue(notice.contains("6b350ff8cfc9ed49b301603c25eb56fcd2a904b1"), "notice lost the pinned commit")
        assertTrue(notice.contains("matcher/issuance/provision.c"), "notice lost the C provision source path")
        assertTrue(notice.contains("Do not attribute this binary to the Rust matcher"), "notice lost the Rust-mismatch clarification")
        assertTrue(notice.contains("release"), "notice lost the redistribution release-gate note")
    }
}
