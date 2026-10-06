package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags

internal class WalletReviewNavigation(
    val openOffered: (String) -> Unit,
    val openSharing: (String) -> Unit,
    val openStored: (String) -> Unit,
)

internal val LocalWalletReviewNavigation = staticCompositionLocalOf<WalletReviewNavigation?> { null }

/** A review and its details share one host. Only route identifiers are retained, never stale consent. */
@Composable
internal fun WalletReviewNavigationHost(
    requestKey: String,
    offer: WalletDemoOfferPreview? = null,
    savedCredentials: List<WalletDemoCredential> = emptyList(),
    sharingOptions: List<WalletDemoPresentationCredentialOption> = emptyList(),
    selectedCredentials: Set<WalletDemoPresentationCredentialSelection> = emptySet(),
    selectedDisclosures: Set<WalletDemoPresentationDisclosureSelection> = emptySet(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var route by rememberSaveable(requestKey) { mutableStateOf<String?>(null) }
    var pageName by rememberSaveable(requestKey) { mutableStateOf(CredentialInformationPage.Requested.name) }
    val page = CredentialInformationPage.valueOf(pageName)
    val offered = offer?.offeredCredentials?.find { route == "offer:${it.configurationId}" }
    val sharing = sharingOptions.find { route == "sharing:${it.selection.id}" }
    val storedCredential = savedCredentials.find { route == "stored:${it.id}" }
    val stored = remember(storedCredential) { storedCredential?.toCredentialDetails() }
    val latestStored by rememberUpdatedState(stored)
    val latestOffer by rememberUpdatedState(offer)
    val latestOffered by rememberUpdatedState(offered)
    val latestSharing by rememberUpdatedState(sharing)
    val latestCredentials by rememberUpdatedState(selectedCredentials)
    val latestDisclosures by rememberUpdatedState(selectedDisclosures)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestReadOnly by rememberUpdatedState(readOnly)
    val latestToggle by rememberUpdatedState(onToggleDisclosure)
    val latestClose by rememberUpdatedState(onClose)
    val latestContent by rememberUpdatedState(content)
    LaunchedEffect(route, offered, sharing, stored) {
        if (route != null && offered == null && sharing == null && stored == null) { route = null; pageName = CredentialInformationPage.Requested.name }
    }
    val navigation = remember(requestKey) {
        WalletReviewNavigation(
            openOffered = { route = "offer:$it"; pageName = CredentialInformationPage.Requested.name },
            openSharing = { route = "sharing:$it"; pageName = CredentialInformationPage.Requested.name },
            openStored = { route = "stored:$it"; pageName = CredentialInformationPage.Requested.name },
        )
    }
    val back = {
        if (page == CredentialInformationPage.Requested) route = null
        else pageName = if (stored != null) CredentialInformationPage.Requested.name
            else CredentialInformationPage.entries[page.ordinal - 1].name
    }
    SystemBackHandler(enabled = route != null, onBack = back)
    val detailPages = if (stored != null) listOf(CredentialInformationPage.Requested) +
        if (page == CredentialInformationPage.Technical) listOf(page) else emptyList()
        else CredentialInformationPage.entries.take(page.ordinal + 1)
    val pages = listOf("review") + if (route == null) emptyList() else detailPages.map { "$route:${it.name}" }
    val savedPages = rememberSaveableStateHolder()
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    CompositionLocalProvider(LocalWalletReviewNavigation provides navigation) {
        NavDisplay(pages, modifier = modifier.fillMaxWidth(), onBack = back, entryDecorators = emptyList(),
            transitionSpec = { walletNavigationMotion(true, reduceMotion, rtl) },
            popTransitionSpec = { walletNavigationMotion(false, reduceMotion, rtl) },
            predictivePopTransitionSpec = { _ -> walletNavigationMotion(false, reduceMotion, rtl) },
        ) { key ->
            NavEntry(key) {
                val offered = latestOffered
                val sharing = latestSharing
                val offer = latestOffer
                val stored = latestStored
                val selectedCredentials = latestCredentials
                val selectedDisclosures = latestDisclosures
                val enabled = latestEnabled
                savedPages.SaveableStateProvider("$requestKey:$key") {
                    if (key == "review") latestContent()
                    else {
                        val displayedPage = CredentialInformationPage.valueOf(key.substringAfterLast(':'))
                        Column(Modifier.fillMaxSize().testTag(if (offered != null || stored != null) "issuance-credential-details" else WalletUiTestTags.PresentationClaimsDialog)) {
                            WalletScreenHeader(credentialInformationTitle(displayedPage), leading = {
                                IconButton(onClick = back, modifier = Modifier.testTag("wallet-detail-back")) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }
                            }) {
                                latestClose?.let { close ->
                                    IconButton(onClick = close, enabled = enabled) { Icon(Icons.Filled.Close, "Close request") }
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when {
                                    stored != null -> if (displayedPage == CredentialInformationPage.Technical) CredentialTechnicalInformation(stored)
                                    else {
                                        CredentialSummaryRow(stored.toCardDisplayData().toCardArt())
                                        CredentialDetailsBody(stored, onTechnicalDetails = { pageName = CredentialInformationPage.Technical.name })
                                    }
                                    offered != null -> OfferedCredentialDetails(offered,
                                        offer!!.issuer.display?.name?.trim()?.takeIf(String::isNotEmpty) ?: offer.issuer.credentialIssuer,
                                        offer.issuer.credentialIssuer)
                                    sharing != null -> SharingCredentialInformation(sharing, sharing.toCredentialDetails(),
                                        sharing.selection in selectedCredentials, selectedDisclosures, enabled, latestReadOnly,
                                        latestToggle, displayedPage, onPageChange = { pageName = it.name })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
