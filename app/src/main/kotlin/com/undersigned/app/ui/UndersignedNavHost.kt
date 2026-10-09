package com.undersigned.app.ui

import androidx.compose.foundation.layout.fillMaxSize
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
    fun profile(w: String) = "profile/$w"
    fun detail(pkg: String) = "detail/$pkg"
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
    LaunchedEffect(pendingScreen) { pendingScreen?.takeIf { it in setOf(Routes.MY_REVIEWS, Routes.CHANGES, Routes.PAYMENTS, Routes.WATCH, Routes.LEADERBOARD, Routes.TO_REVIEW) }?.let { NavIntents.pendingScreen.value = null; nav.navigate(it) { launchSingleTop = true } } }
    val pendingReply by NavIntents.pendingReply.collectAsState()
    val entries by vm.entries.collectAsState()
    LaunchedEffect(pendingReply, entries) { pendingReply?.let { (target, _) -> vm.packageOf(target)?.let { pkg -> nav.navigate(Routes.detail(pkg)) { launchSingleTop = true } } } }
    val pendingAction by NavIntents.pendingAction.collectAsState()
    LaunchedEffect(pendingAction, entries) { pendingAction?.let { a -> vm.packageOf(a.target)?.let { pkg -> nav.navigate(Routes.detail(pkg)) { launchSingleTop = true } } } }
    // The first-use tour, once after the opening slides; replayed from Settings.
    LaunchedEffect(Unit) { if (!UserPreferences.tourSeen) com.undersigned.app.ui.components.Tour.start() }
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
    NavHost(navController = nav, startDestination = Routes.BROWSE) {
        composable(Routes.BROWSE) { BrowseScreen(vm, onOpen = { nav.navigate(Routes.detail(it)) }, onChanges = { nav.navigate(Routes.CHANGES) }, onSettings = { nav.navigate(Routes.SETTINGS) }, onInbox = { nav.navigate(Routes.INBOX) }, onProfile = { nav.navigate(Routes.profile(it)) }, onStats = { nav.navigate(Routes.STATS) }, onMyReviews = { nav.navigate(Routes.MY_REVIEWS) }) }
        composable(Routes.MY_REVIEWS) { MyReviewsScreen(vm, activityResultSender, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }, onReview = { nav.navigate(Routes.review(it)) }, onPro = { nav.navigate(Routes.PRO) }, onToReview = { nav.navigate(Routes.TO_REVIEW) }, onSignIn = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.TO_REVIEW) { ToReviewScreen(vm, activityResultSender, onBack = { nav.popBackStack() }, onReview = { nav.navigate(Routes.review(it)) }) }
        composable(Routes.STATS) { LedgerStatsScreen(vm, onBack = { nav.popBackStack() }) }
        composable(Routes.DETAIL) { b -> val pkg = b.arguments?.getString("pkg") ?: return@composable; AppDetailScreen(vm, pkg, activityResultSender, onBack = { nav.popBackStack() }, onReview = { nav.navigate(Routes.review(pkg)) }, onFollowUp = { nav.navigate(Routes.followUp(pkg, it)) }, onProfile = { nav.navigate(Routes.profile(it)) }, onPro = { nav.navigate(Routes.PRO) }) }
        composable(Routes.REVIEW, arguments = listOf(androidx.navigation.navArgument("fu") { nullable = true; defaultValue = null })) { b -> val pkg = b.arguments?.getString("pkg") ?: return@composable; ReviewScreen(vm, pkg, activityResultSender, followUpOf = b.arguments?.getString("fu"), onBack = { nav.popBackStack() }, onPublished = { nav.popBackStack() }, onPro = { nav.navigate(Routes.PRO) }) }
        composable(Routes.CHANGES) { ChangesScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }, onReview = { nav.navigate(Routes.review(it)) }, onPro = { nav.navigate(Routes.PRO) }) }
        composable(Routes.SETTINGS) { SettingsScreen(vm, activityResultSender, onBack = { nav.popBackStack() }, onHowTo = { nav.navigate(Routes.HOWTO) }, onIntro = { nav.navigate(Routes.INTRO) }, onTour = { com.undersigned.app.ui.components.Tour.start() }, onPro = { nav.navigate(Routes.PRO) }, onDesk = { nav.navigate(Routes.DESK) }, onStats = { nav.navigate(Routes.STATS) }, onPayments = { nav.navigate(Routes.PAYMENTS) }, onWatch = { nav.navigate(Routes.WATCH) }, onLeaderboard = { nav.navigate(Routes.LEADERBOARD) }) }
        composable(Routes.LEADERBOARD) { LeaderboardScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }) }
        composable(Routes.WATCH) { StoreWatchScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }) }
        composable(Routes.PAYMENTS) { PaymentsScreen(vm, activityResultSender, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }) }
        composable(Routes.PRO) { ProScreen(vm, activityResultSender, onBack = { nav.popBackStack() }) }
        composable(Routes.DESK) { PublisherDeskScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }, onPro = { nav.navigate(Routes.PRO) }, onPayments = { nav.navigate(Routes.PAYMENTS) }) }
        composable(Routes.HOWTO) { HowToScreen(onDone = { nav.popBackStack() }) }
        // Replay is a screen on the stack, so finishing it returns to Settings (it used to swap out the whole app and land on Browse).
        composable(Routes.INTRO) { OnboardingScreen(onDone = { nav.popBackStack() }) }
        composable(Routes.INBOX) { InboxScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }, onDesk = { nav.navigate(Routes.DESK) }) }
        composable(Routes.PROFILE) { b -> val w = b.arguments?.getString("wallet") ?: return@composable; ProfileScreen(vm, w, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }) }
    }
    com.undersigned.app.ui.components.TourOverlay(nav) { vm.tourSamplePackage() }
    }
}
