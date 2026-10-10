package com.undersigned.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.undersigned.app.R
import com.undersigned.app.ui.Routes
import com.undersigned.app.ui.theme.RbColors

private data class TabItem(val route: String, val label: Int, val selected: ImageVector, val unselected: ImageVector)

private val TAB_ITEMS = listOf(
    TabItem(Routes.BROWSE, R.string.nav_browse, Icons.Filled.Apps, Icons.Outlined.Apps),
    TabItem(Routes.CHANGES, R.string.nav_changes, Icons.Filled.Update, Icons.Outlined.Update),
    TabItem(Routes.PAYMENTS, R.string.nav_payments, Icons.Filled.Insights, Icons.Outlined.Insights),
    TabItem(Routes.PRO, R.string.pro_title, Icons.Filled.WorkspacePremium, Icons.Outlined.WorkspacePremium),
    TabItem(Routes.ME, R.string.nav_me, Icons.Filled.Person, Icons.Outlined.Person),
)

/**
 * The tab bar along the bottom (owner, 10 Oct 2026: "upgrade to Pro should have its own tab; a nav bar with tabs at the
 * bottom"). Shown on the five top-level screens only; filled icon when selected, outlined otherwise, labels always shown.
 * Me carries the inbox count; What changed a dot when there are changes not yet seen.
 */
@Composable
fun UndersignedTabBar(current: String?, inboxCount: Int, newChanges: Boolean, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = RbColors.navBackground, contentColor = RbColors.textSecondary, modifier = Modifier.tourTarget("nav_bar")) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = RbColors.textPrimary, selectedTextColor = RbColors.textPrimary, indicatorColor = RbColors.purple.copy(alpha = 0.32f),
            unselectedIconColor = RbColors.textTertiary, unselectedTextColor = RbColors.textTertiary,
        )
        TAB_ITEMS.forEach { t ->
            val selected = current == t.route
            val label = stringResource(t.label)
            val a11y = when {
                t.route == Routes.ME && inboxCount > 0 -> label + ", " + pluralStringResource(R.plurals.inbox_a11y, inboxCount, inboxCount)
                t.route == Routes.CHANGES && newChanges -> stringResource(R.string.changes_new_a11y)
                else -> label
            }
            NavigationBarItem(
                selected = selected, onClick = { onSelect(t.route) }, colors = colors, alwaysShowLabel = true,
                modifier = Modifier.tourTarget("nav_" + t.route).semantics { contentDescription = a11y },
                icon = {
                    BadgedBox(badge = {
                        when {
                            t.route == Routes.ME && inboxCount > 0 -> Badge(containerColor = RbColors.purple) { Text(if (inboxCount > 99) "99+" else "$inboxCount") }
                            t.route == Routes.CHANGES && newChanges -> Badge(containerColor = RbColors.warning)
                        }
                    }) { Icon(if (selected) t.selected else t.unselected, contentDescription = null) }
                },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
