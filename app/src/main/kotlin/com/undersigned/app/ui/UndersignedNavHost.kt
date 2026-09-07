package com.undersigned.app.ui

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
    const val DETAIL = "detail/{pkg}"
    const val REVIEW = "review/{pkg}"
    const val CHANGES = "changes"
    const val SETTINGS = "settings"
    fun detail(pkg: String) = "detail/$pkg"
    fun review(pkg: String) = "review/$pkg"
}

@Composable
fun UndersignedNavHost(activityResultSender: ActivityResultSender?) {
    val context = LocalContext.current
    val termsService = remember { TermsAcceptanceService(context) }
    var consentGiven by remember { mutableStateOf(termsService.hasValidConsent()) }
    if (!consentGiven) { TermsAcceptanceScreen(activityResultSender = activityResultSender, onAccepted = { consentGiven = true }); return }
    val onboardingSeen by UserPreferences.onboardingSeen.collectAsState()
    if (!onboardingSeen) { OnboardingScreen(onDone = { UserPreferences.setOnboardingSeen(true) }); return }

    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    NavHost(navController = nav, startDestination = Routes.BROWSE) {
        composable(Routes.BROWSE) { BrowseScreen(vm, onOpen = { nav.navigate(Routes.detail(it)) }, onChanges = { nav.navigate(Routes.CHANGES) }, onSettings = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.DETAIL) { b -> val pkg = b.arguments?.getString("pkg") ?: return@composable; AppDetailScreen(vm, pkg, activityResultSender, onBack = { nav.popBackStack() }, onReview = { nav.navigate(Routes.review(pkg)) }) }
        composable(Routes.REVIEW) { b -> val pkg = b.arguments?.getString("pkg") ?: return@composable; ReviewScreen(vm, pkg, activityResultSender, onBack = { nav.popBackStack() }, onPublished = { nav.popBackStack() }) }
        composable(Routes.CHANGES) { ChangesScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) }) }
        composable(Routes.SETTINGS) { SettingsScreen(vm, activityResultSender, onBack = { nav.popBackStack() }) }
    }
}
