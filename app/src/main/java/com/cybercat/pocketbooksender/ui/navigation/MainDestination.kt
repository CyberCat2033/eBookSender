package com.cybercat.pocketbooksender.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.ui.graphics.vector.ImageVector

sealed class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Send : MainDestination("send", "Send", Icons.Outlined.Send)
    data object Catalog : MainDestination("catalog", "Catalog", Icons.Outlined.LibraryBooks)
    data object Opds : MainDestination("opds", "Web", Icons.Outlined.TravelExplore)
    data object Settings : MainDestination("settings", "Settings", Icons.Outlined.Settings)
}

val MainDestinations = listOf(
    MainDestination.Send,
    MainDestination.Catalog,
    MainDestination.Opds,
    MainDestination.Settings,
)
