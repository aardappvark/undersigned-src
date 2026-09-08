package com.undersigned.app.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** A package a notification asked us to open; consumed once by the nav host. */
object NavIntents { val pendingPackage = MutableStateFlow<String?>(null) }
