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
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

private enum class ReviewDetailPage { Information, Technical }

internal class WalletReviewNavigation(
    val openOffered: (String) -> Unit,
    val openStored: (String) -> Unit,
)

internal val LocalWalletReviewNavigation = staticCompositionLocalOf<WalletReviewNavigation?> { null }

/** Issuance review and receipt details share one host. Only route identifiers are retained. */
@Composable
internal fun WalletReviewNavigationHost(
    requestKey: String,
    offer: WalletDemoOfferPreview? = null,
    savedCredentials: List<WalletDemoCredential> = emptyList(),
    enabled: Boolean = true,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var route by rememberSaveable(requestKey) { mutableStateOf<String?>(null) }
    var pageName by rememberSaveable(requestKey) { mutableStateOf(ReviewDetailPage.Information.name) }
    val page = ReviewDetailPage.valueOf(pageName)
    val offered = offer?.offeredCredentials?.find { route == "offer:${it.configurationId}" }
    val storedCredential = savedCredentials.find { route == "stored:${it.id}" }
    val stored = remember(storedCredential) { storedCredential?.toCredentialDetails() }
    val latestStored by rememberUpdatedState(stored)
    val latestOffer by rememberUpdatedState(offer)
    val latestOffered by rememberUpdatedState(offered)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestClose by rememberUpdatedState(onClose)
    val latestContent by rememberUpdatedState(content)
    LaunchedEffect(route, offered, stored) {
        if (route != null && offered == null && stored == null) { route = null; pageName = ReviewDetailPage.Information.name }
    }
    val navigation = remember(requestKey) {
        WalletReviewNavigation(
            openOffered = { route = "offer:$it"; pageName = ReviewDetailPage.Information.name },
            openStored = { route = "stored:$it"; pageName = ReviewDetailPage.Information.name },
        )
    }
    val back = {
        if (page == ReviewDetailPage.Information) route = null
        else pageName = ReviewDetailPage.Information.name
    }
    SystemBackHandler(enabled = route != null, onBack = back)
    val detailPages = ReviewDetailPage.entries.take(page.ordinal + 1)
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
                val offer = latestOffer
                val stored = latestStored
                val enabled = latestEnabled
                savedPages.SaveableStateProvider("$requestKey:$key") {
                    if (key == "review") Box(Modifier.fillMaxWidth().walletNavigationBackground()) { latestContent() }
                    else {
                        val displayedPage = ReviewDetailPage.valueOf(key.substringAfterLast(':'))
                        Column(Modifier.fillMaxSize().walletNavigationBackground().testTag("issuance-credential-details")) {
                            WalletScreenHeader(stringResource(if (displayedPage == ReviewDetailPage.Technical)
                                Res.string.credential_technical_details else Res.string.issuance_information), leading = {
                                IconButton(onClick = back, modifier = Modifier.testTag("wallet-detail-back")) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }
                            }) {
                                latestClose?.let { close ->
                                    IconButton(onClick = close, enabled = enabled) { Icon(Icons.Filled.Close, "Close request") }
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when {
                                    stored != null -> if (displayedPage == ReviewDetailPage.Technical) CredentialTechnicalInformation(stored)
                                    else {
                                        CredentialSummaryRow(stored.toCardDisplayData().toCardArt())
                                        CredentialDetailsBody(stored, onTechnicalDetails = { pageName = ReviewDetailPage.Technical.name })
                                    }
                                    offered != null -> OfferedCredentialDetails(offered,
                                        offer!!.issuer.display?.name?.trim()?.takeIf(String::isNotEmpty) ?: offer.issuer.credentialIssuer,
                                        offer.issuer.credentialIssuer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
