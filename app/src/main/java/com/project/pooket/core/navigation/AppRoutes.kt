package com.project.pooket.core.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import com.project.pooket.R

sealed class AppRoute(
    val route: String,
    val title: String,
    @DrawableRes val iconSelected: Int? = null,
    @DrawableRes val iconUnSelected: Int? = null,
    val allowDrawerSwipe: Boolean = false
) {
    data object Library : AppRoute(
        "library",
        "Library",
        R.drawable.library_selected,
        R.drawable.library_unselected,
        true
    )

    data object Collection : AppRoute(
        "collection",
        "Collection",
        R.drawable.collection_selected,
        R.drawable.collection_unselected,
        true
    )

    data object Search :
        AppRoute("search", "Search", R.drawable.search_selected, R.drawable.search_unselected, true)

    data object Setting :
        AppRoute("setting", "Setting", R.drawable.settings, R.drawable.settings, true)

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