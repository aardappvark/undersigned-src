package com.undersigned.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.ui.components.UndersignedTabBar
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.undersigned.app.data.TermsAcceptanceService
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.ui.screens.*
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

object Routes {
    const val BROWSE = "browse"
    const val MY_REVIEWS = "my-reviews"
    const val DETAIL = "detail/{pkg}"
    const val REVIEW = "review/{pkg}?fu={fu}"
    const val CHANGES = "changes"
    const val SETTINGS = "settings"
    const val INBOX = "inbox"
    const val PROFILE = "profile/{wallet}"
    const val HOWTO = "howto"
    const val PRO = "pro"
    const val DESK = "desk"
    const val STATS = "stats"
    const val PAYMENTS = "payments"
    const val WATCH = "watch"
    const val LEADERBOARD = "leaderboard"
    const val TO_REVIEW = "to-review"
    const val INTRO = "intro"
    /** One app's payment history, from the Payments tab (owner, 10 Oct 2026): pushed, no tab bar, back returns. */
    const val PAY_HISTORY = "pay-history/{pkg}"
    /** The Me hub: my reviews, inbox, publisher desk, settings (owner, 10 Oct 2026: a tab bar at the bottom). */
    const val ME = "me"
    /** The five tabs along the bottom, in order. */
    val TABS = listOf(BROWSE, CHANGES, PAYMENTS, PRO, ME)
    fun profile(w: String) = "profile/$w"
    fun detail(pkg: String) = "detail/$pkg"
    fun payHistory(pkg: String) = "pay-history/$pkg"
    fun review(pkg: String) = "review/$pkg"
    /** The composer as a follow-up to the signer's own review [original] (after the publisher marked it fixed). */
    fun followUp(pkg: String, original: String) = "review/$pkg?fu=$original"
}

@Composable
fun UndersignedNavHost(activityResultSender: ActivityResultSender?) {
    val context = LocalContext.current
    val termsService = remember { TermsAcceptanceService(context) }
    var consentGiven by remember { mutableStateOf(termsService.hasValidConsent()) }
    val onboardingSeen by UserPreferences.onboardingSeen.collectAsState()
    // One opening set on a first run: the five slides with the sign-in gate as the last page. Consent is the
    // only thing that cannot be skipped. Replay (Settings) shows the slides alone; the how-to lives in Settings.
    if (!consentGiven) {
        OnboardingScreen(onDone = {}, gate = { TermsAcceptanceScreen(activityResultSender = activityResultSender, onAccepted = { UserPreferences.setOnboardingSeen(true); UserPreferences.setHowToSeen(true); consentGiven = true }) })
        return
    }
    if (!onboardingSeen) { OnboardingScreen(onDone = { UserPreferences.setOnboardingSeen(true) }); return }

    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    val pending by NavIntents.pendingPackage.collectAsState()
    LaunchedEffect(pending) { pending?.let { NavIntents.pendingPackage.value = null; nav.navigate(Routes.detail(it)) { launchSingleTop = true } } }
    // One tag dialog for every import path (app page, My reviews, Settings).
    com.undersigned.app.ui.components.ImportTagsDialog(vm)
    val pendingReview by NavIntents.pendingReview.collectAsState()
    LaunchedEffect(pendingReview) { pendingReview?.let { NavIntents.pendingReview.value = null; nav.navigate(Routes.review(it)) { launchSingleTop = true } } }
    val pendingScreen by NavIntents.pendingScreen.collectAsState()
    LaunchedEffect(pendingScreen) { pendingScreen?.takeIf { it in setOf(Routes.MY_REVIEWS, Routes.WATCH, Routes.LEADERBOARD, Routes.TO_REVIEW) }?.let { NavIntents.pendingScreen.value = null; nav.navigate(it) { launchSingleTop = true } } }
    val pendingReply by NavIntents.pendingReply.collectAsState()
    val entries by vm.entries.collectAsState()
    LaunchedEffect(pendingReply, entries) { pendingReply?.let { (target, _) -> vm.packageOf(target)?.let { pkg -> nav.navigate(Routes.detail(pkg)) { launchSingleTop = true } } } }
    val pendingAction by NavIntents.pendingAction.collectAsState()
    LaunchedEffect(pendingAction, entries) { pendingAction?.let { a -> vm.packageOf(a.target)?.let { pkg -> nav.navigate(Routes.detail(pkg)) { launchSingleTop = true } } } }
    // The first-use tour, once after the opening slides; replayed from Settings.
    LaunchedEffect(Unit) { if (!UserPreferences.tourSeen) com.undersigned.app.ui.components.Tour.start() }
    // A tab is a top-level screen opened from the bar, so it sits straight on Browse. The same screen opened from inside
    // another one (Pro from an app page's lock, Payments from Settings) is pushed instead: no bar, a back arrow, and back
    // returns to where it was opened.
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val below = remember(current) { nav.previousBackStackEntry?.destination?.route }
    val showBar = route in Routes.TABS && (route == Routes.BROWSE || below == Routes.BROWSE)
    fun tab(r: String) {
        // Compose docs pattern: each tab keeps its own stack; back from any tab lands on Browse.
        nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    }
    /** Whether the entry is a tab opened from the bar (no back arrow) rather than pushed from another screen. */
    fun isTabRoot(): Boolean = nav.previousBackStackEntry?.destination?.route.let { it == null || it == Routes.BROWSE }
    LaunchedEffect(pendingScreen) { pendingScreen?.takeIf { it in setOf(Routes.CHANGES, Routes.PAYMENTS) }?.let { NavIntents.pendingScreen.value = null; tab(it) } }
    // The week's New list and a shared text that names no app are both shown by Browse, so go there first.
    val pendingSearch by NavIntents.pendingSearch.collectAsState()
    LaunchedEffect(pendingScreen, pendingSearch) { if ((pendingScreen == "new" || pendingSearch != null) && route != Routes.BROWSE) runCatching { nav.popBackStack(Routes.BROWSE, inclusive = false) } }
    val entriesForBadge by vm.entries.collectAsState()
    val inboxCount = remember(entriesForBadge) { vm.unanswered().size + vm.repliesToMe().size }
    val changes by vm.changes.collectAsState()
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = RbColors.background,
        // The screens pad themselves for the status and navigation bars; the Scaffold only adds the tab bar's height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (showBar) UndersignedTabBar(route, inboxCount, changes.any { !it.firstSeen }) { if (it != route) tab(it) } },
    ) { inner ->
    NavHost(navController = nav, startDestination = Routes.BROWSE, modifier = Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
        composable(Routes.BROWSE) { BrowseScreen(vm, onOpen = { nav.navigate(Routes.detail(it)) }, onProfile = { nav.navigate(Routes.profile(it)) }, onStats = { nav.navigate(Routes.STATS) }, onMyReviews = { nav.navigate(Routes.MY_REVIEWS) }) }
        composable(Routes.ME) { MeScreen(vm, onMyReviews = { nav.navigate(Routes.MY_REVIEWS) }, onInbox = { nav.navigate(Routes.INBOX) }, onToReview = { nav.navigate(Routes.TO_REVIEW) }, onDesk = { nav.navigate(Routes.DESK) }, onWatch = { nav.navigate(Routes.WATCH) }, onLeaderboard = { nav.navigate(Routes.LEADERBOARD) }, onStats = { nav.navigate(Routes.STATS) }, onSettings = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.MY_REVIEWS) { MyReviewsScreen(vm, activityResultSender, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }, onReview = { nav.navigate(Routes.review(it)) }, onPro = { nav.navigate(Routes.PRO) }, onToReview = { nav.navigate(Routes.TO_REVIEW) }, onSignIn = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.TO_REVIEW) { ToReviewScreen(vm, activityResultSender, onBack = { nav.popBackStack() }, onReview = { nav.navigate(Routes.review(it)) }) }
        composable(Routes.STATS) { LedgerStatsScreen(vm, onBack = { nav.popBackStack() }) }
        composable(Routes.DETAIL) { b -> val pkg = b.arguments?.getString("pkg") ?: return@composable; AppDetailScreen(vm, pkg, activityResultSender, onBack = { nav.popBackStack() }, onReview = { nav.navigate(Routes.review(pkg)) }, onFollowUp = { nav.navigate(Routes.followUp(pkg, it)) }, onProfile = { nav.navigate(Routes.profile(it)) }) }
        composable(Routes.REVIEW, arguments = listOf(androidx.navigation.navArgument("fu") { nullable = true; defaultValue = null })) { b -> val pkg = b.arguments?.getString("pkg") ?: return@composable; ReviewScreen(vm, pkg, activityResultSender, followUpOf = b.arguments?.getString("fu"), onBack = { nav.popBackStack() }, onPublished = { nav.popBackStack() }, onPro = { nav.navigate(Routes.PRO) }) }
        composable(Routes.CHANGES) { e -> val root = remember(e) { isTabRoot() }; ChangesScreen(vm, onBack = if (root) null else ({ nav.popBackStack() }), onOpen = { nav.navigate(Routes.detail(it)) }, onReview = { nav.navigate(Routes.review(it)) }, onPro = { nav.navigate(Routes.PRO) }) }
        composable(Routes.SETTINGS) { SettingsScreen(vm, activityResultSender, onBack = { nav.popBackStack() }, onHowTo = { nav.navigate(Routes.HOWTO) }, onIntro = { nav.navigate(Routes.INTRO) }, onTour = { com.undersigned.app.ui.components.Tour.start() }, onPro = { nav.navigate(Routes.PRO) }, onDesk = { nav.navigate(Routes.DESK) }, onStats = { nav.navigate(Routes.STATS) }, onPayments = { nav.navigate(Routes.PAYMENTS) }, onWatch = { nav.navigate(Routes.WATCH) }, onLeaderboard = { nav.navigate(Routes.LEADERBOARD) }) }
        composable(Routes.LEADERBOARD) { LeaderboardScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }) }
        composable(Routes.WATCH) { StoreWatchScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }) }
        composable(Routes.PAYMENTS) { e -> val root = remember(e) { isTabRoot() }; PaymentsScreen(vm, activityResultSender, onBack = if (root) null else ({ nav.popBackStack() }), onOpen = { nav.navigate(Routes.detail(it)) }, onHistory = { nav.navigate(Routes.payHistory(it)) }) }
        composable(Routes.PAY_HISTORY) { b -> val pkg = b.arguments?.getString("pkg") ?: return@composable; PaymentHistoryScreen(vm, pkg, onBack = { nav.popBackStack() }, onOpenApp = { nav.navigate(Routes.detail(pkg)) }) }
        composable(Routes.PRO) { e -> val root = remember(e) { isTabRoot() }; ProScreen(vm, activityResultSender, onBack = if (root) null else ({ nav.popBackStack() })) }
        composable(Routes.DESK) { PublisherDeskScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }, onPro = { nav.navigate(Routes.PRO) }, onPayments = { nav.navigate(Routes.PAYMENTS) }) }
        composable(Routes.HOWTO) { HowToScreen(onDone = { nav.popBackStack() }) }
        // Replay is a screen on the stack, so finishing it returns to Settings (it used to swap out the whole app and land on Browse).
        composable(Routes.INTRO) { OnboardingScreen(onDone = { nav.popBackStack() }) }
        composable(Routes.INBOX) { InboxScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }, onDesk = { nav.navigate(Routes.DESK) }) }
        composable(Routes.PROFILE) { b -> val w = b.arguments?.getString("wallet") ?: return@composable; ProfileScreen(vm, w, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }) }
    }
    }
    com.undersigned.app.ui.components.TourOverlay(nav) { vm.tourSamplePackage() }
    }
}
