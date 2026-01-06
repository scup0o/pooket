package com.project.pooket.core.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentPasteSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentPasteSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.ui.graphics.vector.ImageVector
import com.project.pooket.R

sealed class AppRoute(
    val route: String,
    val title: String,
    val iconSelected: ImageVector? = null,
    val iconUnSelected: ImageVector? = null,
    val allowDrawerSwipe: Boolean = false
) {
    data object Library : AppRoute(
        "library",
        "Library",
        Icons.Rounded.AutoStories,
        Icons.Rounded.Book,
        true
    )

    data object Collection : AppRoute(
        "collection",
        "Collection",
        Icons.Rounded.BurstMode,
        Icons.Rounded.Collections,
        true
    )

    data object Search :
        AppRoute("search", "Search", Icons.Rounded.ContentPasteSearch,Icons.Rounded.Search,  true)

    data object Setting :
        AppRoute("setting", "Setting", Icons.Rounded.SettingsSuggest, Icons.Rounded.Settings, true)

    data object Reader : AppRoute("reader/{bookUri}/{bookTitle}", "Reader", null) {
        fun createRoute(uri: String, title: String): String =
            "reader/${Uri.encode(uri)}/${Uri.encode(title)}"
    }

    companion object {
        val drawerRoutes by lazy {
            listOf(Library, Collection, Search, Setting)
        }

        fun getByRoute(route: String?): AppRoute? {
            return (drawerRoutes + Reader).find { it.route == route }
        }
    }
}