package com.manger.app.presentation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
internal fun RoleNavigationBar(isAdmin: Boolean, page: Int, accountsOpen: Boolean,
                               busy: Boolean, onDestination: (Int) -> Unit) {
    NavigationBar {
        val tabs = if (isAdmin)
            listOf(0 to ("الرئيسية" to "◉"), 1 to ("أسماء القوة" to "▦"),
                2 to ("الأرشيف" to "▤"), 3 to ("الإدارة" to "♧"), 4 to ("الإعدادات" to "⚙"))
        else listOf(0 to ("الرئيسية" to "◉"), 1 to ("أسماء القوة" to "▦"),
            2 to ("الأرشيف" to "▤"), 5 to ("التقارير" to "▤"), 4 to ("الإعدادات" to "⚙"))
        tabs.forEach { (target, item) ->
            NavigationBarItem(
                selected = (page == target || (isAdmin && page == 5 && target == 3)) && !accountsOpen,
                onClick = { onDestination(target) }, enabled = !busy,
                icon = { Text(item.second, fontSize = 21.sp) },
                label = { Text(item.first, maxLines = 1, fontSize = 10.sp) }
            )
        }
    }
}
