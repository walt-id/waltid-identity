package id.walt.cli.commands

import com.github.ajalt.clikt.testing.test
import id.walt.cli.util.getResourcePath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains

class WaltIdVPVerifyCmdTest {

    private val command = VPVerifyCmd()

    private val holderDID = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"

    private val openBadgeVpPath = getResourcePath(this, "vp/openbadge_vc_type.jwt")

    private val verifiableEducationalIdVpPath = getResourcePath(this, "vp/verifiable_educational_id_vc_type.jwt")

    @Test
    fun `should verify openbadge vp with vc type presdef and default verification policies`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/openbadge_presdef_vc_type.json")
            val presSubPath = getResourcePath(this, "presexch/openbadge_pressub_vc_type.json")
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-pd",
                    presDefPath,
                    "-ps",
                    presSubPath,
                    "-vp",
                    openBadgeVpPath,
                )
            )
            assertContains(result.output, "Overall: Success!")
        }

    @Test
    fun `should verify openbadge vp with vc type presdef and min and max credential policies set to 1`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/openbadge_presdef_vc_type.json")
            val presSubPath = getResourcePath(this, "presexch/openbadge_pressub_vc_type.json")
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-pd",
                    presDefPath,
                    "-ps",
                    presSubPath,
                    "-vp",
                    openBadgeVpPath,
                    "-vpp",
                    "maximum-credentials",
                    "-vppa",
                    "max=1",
                    "-vpp",
                    "minimum-credentials",
                    "-vppa",
                    "min=1",
                )
            )
            assertContains(result.output, "Overall: Success!")
        }

    @Test
    fun `should verify openbadge_verifiable_educational_id vp with respective presdef and default verification policies`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/verifiable_educational_id_presdef_vc_type.json")
            val presSubPath = getResourcePath(this, "presexch/verifiable_educational_id_pressub_vc_type.json")
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-pd",
                    presDefPath,
                    "-ps",
                    presSubPath,
                    "-vp",
                    verifiableEducationalIdVpPath,
                )
            )
            assertContains(result.output, "Overall: Success!")
        }

    @Test
    fun `should verify openbadge_verifiable_educational_id vp with min and max credential policies set to 1 and 2 respectively`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/verifiable_educational_id_presdef_vc_type.json")
            val presSubPath = getResourcePath(this, "presexch/verifiable_educational_id_pressub_vc_type.json")
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-pd",
                    presDefPath,
                    "-ps",
                    presSubPath,
                    "-vp",
                    openBadgeVpPath,
                    "-vpp",
                    "maximum-credentials",
                    "-vppa",
                    "max=2",
                    "-vpp",
                    "minimum-credentials",
                    "-vppa",
                    "min=1",
                )
            )
            assertContains(result.output, "Overall: Success!")
        }

    @Test
    fun `should not verify openbadge_verifiable_educational_id vp with holder-binding `() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/verifiable_educational_id_presdef_vc_type.json")
            val presSubPath = getResourcePath(this, "presexch/verifiable_educational_id_pressub_vc_type.json")
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-pd",
                    presDefPath,
                    "-ps",
                    presSubPath,
                    "-vp",
                    openBadgeVpPath,
                    "-vpp",
                    "maximum-credentials",
                    "-vppa",
                    "max=2",
                    "-vpp",
                    "minimum-credentials",
                    "-vppa",
                    "min=1",
                    "-vpp",
                    "holder-binding",
                )
            )
            assertContains(result.output, "Overall: Fail!")
        }

    @Test
    fun `should not verify openbadge_verifiable_educational_id vp with min and max credential policies set to 3 and 4 respectively`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/verifiable_educational_id_presdef_vc_type.json")
            val presSubPath = getResourcePath(this, "presexch/verifiable_educational_id_pressub_vc_type.json")
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-pd",
                    presDefPath,
                    "-ps",
                    presSubPath,
                    "-vp",
                    openBadgeVpPath,
                    "-vpp",
                    "maximum-credentials",
                    "-vppa",
                    "max=4",
                    "-vpp",
                    "minimum-credentials",
                    "-vppa",
                    "min=3",
                )
            )
            assertContains(result.output, "Overall: Fail!")
        }
}