package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.CredentialDisplayNormalizer
import id.walt.walletdemo.compose.logic.CredentialSummary
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.ui.components.ClaimGroupSection
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import kotlin.test.Test
import kotlin.test.assertIs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClaimValueRowAndroidTest {
    @Test
    fun offscreenImageWaitsUntilItsRowIsVisible() = runComposeUiTest {
        val item = deferredInvalidImage()
        setContent {
            WalletDemoTheme {
                Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(1200.dp))
                    ClaimValueRow(item)
                }
            }
        }
        waitForIdle()
        onNodeWithText(unavailable).assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.claim("visual_proof")).performScrollTo()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(unavailable).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun collapsedImageWaitsUntilItsGroupIsExpanded() = runComposeUiTest {
        val item = deferredInvalidImage()
        setContent {
            WalletDemoTheme {
                ClaimGroupSection(ClaimGroup("images", "Images", listOf(item), initiallyExpanded = false))
            }
        }
        waitForIdle()
        onNodeWithText(unavailable).assertDoesNotExist()
        onNodeWithText("Images").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(unavailable).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun deferredInvalidImage(): ClaimItem {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(
            id = "image", format = "vc+sd-jwt", issuer = null, label = "Image",
            credentialDataJson = """{"visual_proof":"data:image/png;base64,iVBORw0KGgo="}""",
        ))
        return details.groups.single().items.single().also { assertIs<DisplayValue.DeferredImage>(it.value) }
    }

    private val unavailable = "Image unavailable or unsupported"

    @Test
    fun largeListRendersABoundedPreviewAndCanRevealTheRest() = runComposeUiTest {
        setContent {
            WalletDemoTheme {
                Column(Modifier.height(600.dp).verticalScroll(rememberScrollState())) {
                ClaimValueRow(
                    item = ClaimItem(
                        path = ClaimItemPath.topLevel("unknown_binary"),
                        label = "Unknown binary",
                        value = DisplayValue.ListValue(
                            values = List(30) { index -> DisplayValue.NumberValue("item $index") },
                        ),
                    ),
                )
                }
            }
        }

        onNodeWithText("1.").assertExists()
        onNodeWithText("item 24").assertExists()
        onNodeWithText("item 25").assertDoesNotExist()
        onNodeWithText("Showing first 25 of 30 items").assertExists()
        onNodeWithText("Show 5 more").performScrollTo().performClick()
        onNodeWithText("item 29").assertExists()
        onNodeWithText("30.").assertExists()
        onNodeWithText("Show 5 more").assertDoesNotExist()
    }
    @Test
    fun equallyNamedSmallObjectsExpandIndependentlyBySourcePath() = runComposeUiTest {
        fun record(id: String) = ClaimItem(path = ClaimItemPath.topLevel(id), label = "Record",
            value = DisplayValue.ObjectValue((0..1).map { index ->
                ClaimItem(path = ClaimItemPath.topLevel(id).child("field$index"), label = "Field $index", value = DisplayValue.Text("$id value $index"))
            }))
        setContent {
            WalletDemoTheme {
                Column(Modifier.height(600.dp).verticalScroll(rememberScrollState())) {
                    ClaimValueRow(record("first"))
                    ClaimValueRow(record("second"))
                }
            }
        }
        onNodeWithText("first value 0").assertDoesNotExist()
        onNodeWithText("second value 0").assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.claim("first")).performClick()
        onNodeWithText("first value 0").assertExists()
        onNodeWithText("second value 0").assertDoesNotExist()
    }

    @Test
    fun largeObjectsRenderABoundedPreviewAfterExpansion() = runAndroidComposeUiTest<ComponentActivity> {
        val root = ClaimItemPath.topLevel("record")
        val item = ClaimItem(path = root, label = "Record", value = DisplayValue.ObjectValue((0..29).map { index ->
            ClaimItem(path = root.child("field$index"), label = "Field $index", value = DisplayValue.Text("value $index"))
        }))
        val restoration = StateRestorationTester(this)
        restoration.setContent { WalletDemoTheme {
            Column(Modifier.height(600.dp).verticalScroll(rememberScrollState())) { ClaimValueRow(item) }
        } }
        onNodeWithText("value 0").assertDoesNotExist()
        onNodeWithText("Record").performClick()
        onNodeWithText("value 24").assertExists()
        onNodeWithText("value 25").assertDoesNotExist()
        onNodeWithText("Show 5 more").performScrollTo().performClick()
        onNodeWithText("value 29").assertExists()
        restoration.emulateSaveAndRestore()
        onNodeWithText("value 29").assertExists()
    }

    @Test
    fun nestedAndEmptyObjectsStartCollapsedAndRetainTheirOwnState() = runComposeUiTest {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(id = "nested", format = "dc+sd-jwt", issuer = null,
            label = "Identity", credentialDataJson = """{"address":{"street":"Rietveld","location":{"city":"Utrecht"},"extra":{}}}"""))
        setContent { WalletDemoTheme { ClaimValueRow(details.groups.flatMap { it.items }.single()) } }
        onNodeWithText("Rietveld").assertDoesNotExist()
        onNodeWithText("Address").performClick()
        onNodeWithText("Rietveld").assertExists()
        onNodeWithText("Utrecht").assertDoesNotExist()
        onNodeWithText("Location").performClick()
        onNodeWithText("Utrecht").assertExists()
        onNodeWithText("No fields").assertDoesNotExist()
        onNodeWithText("Extra").performClick()
        onNodeWithText("No fields").assertExists()
        onNodeWithText("Address").performClick()
        onNodeWithText("Utrecht").assertDoesNotExist()
    }

    @Test
    fun scalarArrayIssuerLabelsRenderBesideTheirOriginalValues() = runComposeUiTest {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(id = "array", format = "dc+sd-jwt", issuer = null,
            label = "Identity", credentialDataJson = """{"nationalities":["EX","YY"]}""",
            metadataJson = """{"credentialClaims":[{"path":["nationalities",0],"display":[{"name":"Primary nationality"}]}]}"""))
        setContent { WalletDemoTheme { ClaimValueRow(details.groups.flatMap { it.items }.single()) } }
        onNodeWithText("Primary nationality").assertExists()
        onNodeWithText("EX").assertExists()
        onNodeWithText("YY").assertExists()
        onNodeWithTag(WalletUiTestTags.claim("nationalities[0]")).assertExists()
    }

    @Test
    fun nestedDisclosureDigestsStayInAnExpandableTechnicalSection() = runComposeUiTest {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(id = "partial", format = "dc+sd-jwt", issuer = null,
            label = "Identity", credentialDataJson = """{"address":{"country":"EX","_sd":["decoy","unavailable"]}}"""))
        setContent { WalletDemoTheme { ClaimValueRow(details.groups.flatMap { it.items }.single()) } }
        onNodeWithText("EX").assertDoesNotExist()
        onNodeWithText("Address").performClick()
        onNodeWithText("EX").assertExists()
        onNodeWithText("2 disclosure digests").assertDoesNotExist()
        onNodeWithText("Technical details").performClick()
        onNodeWithText("2 disclosure digests").assertExists()
        onNodeWithText("EX").assertExists()
    }

    @Test
    fun arrayObjectDigestsUseTheSameTechnicalBoundary() = runComposeUiTest {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(id = "partial-array", format = "dc+sd-jwt", issuer = null,
            label = "Identity", credentialDataJson = """{"profiles":[{"country":"EX","_sd":["decoy"]}]}"""))
        setContent { WalletDemoTheme { ClaimValueRow(details.groups.flatMap { it.items }.single()) } }
        onNodeWithText("EX").assertDoesNotExist()
        onNodeWithText("Profiles").performClick()
        onNodeWithText("EX").assertExists()
        onNodeWithText("1 disclosure digest").assertDoesNotExist()
        onNodeWithText("Technical details").performClick()
        onNodeWithText("1 disclosure digest").assertExists()
    }

    @Test
    fun objectArraysUseSeparateCardsAndKeepNestedObjectsCollapsed() = runComposeUiTest {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(id = "members", format = "dc+sd-jwt", issuer = null,
            label = "Identity", credentialDataJson = """{"members":[{"name":"Alex","contact":{"city":"North city"}},{"name":"Sam","contact":{"city":"South city"}},{}]}""",
            metadataJson = """{"credentialClaims":[{"path":["members",0],"display":[{"name":"Primary member"}]}]}"""))
        setContent { WalletDemoTheme { ClaimValueRow(details.groups.flatMap { it.items }.single()) } }
        onNodeWithText("Alex").assertDoesNotExist()
        onNodeWithText("Members").performClick()
        onNodeWithText("Entry 1").assertDoesNotExist()
        onNodeWithText("Primary member").assertExists()
        onNodeWithText("Alex").assertExists()
        onNodeWithText("Sam").assertExists()
        onNodeWithText("No fields").assertExists()
        onNodeWithText("North city").assertDoesNotExist()
        onNodeWithText("South city").assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.claim("members[0].contact")).performClick()
        onNodeWithText("North city").assertExists()
        onNodeWithText("South city").assertDoesNotExist()
    }

    @Test
    fun nestedArraysUseCardsWithoutGeneratedHeadingsAndPreserveMixedEntries() = runComposeUiTest {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(id = "matrix", format = "dc+sd-jwt", issuer = null,
            label = "Identity", credentialDataJson = """{"matrix":[["first",0],[],[{"value":"inside"},null,["deep"]]]}""",
            metadataJson = """{"credentialClaims":[{"path":["matrix",0],"display":[{"name":"Issuer row label"}]}]}"""))
        setContent { WalletDemoTheme { ClaimValueRow(details.groups.flatMap { it.items }.single()) } }
        onNodeWithText("first").assertDoesNotExist()
        onNodeWithText("Matrix").performClick()
        onNodeWithText("Entry 1").assertDoesNotExist()
        onNodeWithText("Row 1").assertDoesNotExist()
        onNodeWithText("Issuer row label").assertExists()
        onNodeWithText("first").assertExists()
        onNodeWithText("0").assertExists()
        onNodeWithText("No items").assertExists()
        onNodeWithText("inside").assertExists()
        onNodeWithText("No value supplied").assertExists()
        onNodeWithText("deep").assertExists()
        onNodeWithTag(WalletUiTestTags.claim("matrix[2][0]")).assertExists()
        onNode(hasText("2") and hasAnyAncestor(hasTestTag(WalletUiTestTags.claim("matrix[2][1]")))).assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.claim("matrix[2][2]")).assertExists()
    }

    @Test
    fun mixedArrayUsesUnnumberedCardsEvenBeforeTheContainerIsRevealed() = runComposeUiTest {
        val root = ClaimItemPath.topLevel("mixed")
        val values = List(29) { DisplayValue.Text("value $it") } + DisplayValue.ObjectValue(listOf(
            ClaimItem(path = root.indexedChild(29).child("value"), label = "Value", value = DisplayValue.Text("tail object"))
        ))
        setContent { WalletDemoTheme {
            Column(Modifier.height(600.dp).verticalScroll(rememberScrollState())) {
                ClaimValueRow(ClaimItem(path = root, label = "Mixed", value = DisplayValue.ListValue(values, mapOf(0 to "First value label"))))
            }
        } }
        onNodeWithText("First value label").assertExists()
        onNodeWithTag(WalletUiTestTags.claim("mixed[0]")).assertExists()
        onNode(hasText("1") and hasAnyAncestor(hasTestTag(WalletUiTestTags.claim("mixed[0]")))).assertDoesNotExist()
        onNodeWithText("tail object").assertDoesNotExist()
        onNodeWithText("Show 5 more").performScrollTo().performClick()
        onNodeWithText("tail object").assertExists()
        onNodeWithTag(WalletUiTestTags.claim("mixed[29]")).assertExists()
        onNode(hasText("30") and hasAnyAncestor(hasTestTag(WalletUiTestTags.claim("mixed[29]")))).assertDoesNotExist()
        onNode(hasText("1") and hasAnyAncestor(hasTestTag(WalletUiTestTags.claim("mixed[0]")))).assertDoesNotExist()
    }

}
