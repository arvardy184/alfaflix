package com.application.alfaflix.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.application.alfaflix.R
import com.application.alfaflix.presentation.about.AboutScreen
import com.application.alfaflix.presentation.detail.MovieDetailScreen
import com.application.alfaflix.presentation.favorites.FavoritesScreen
import com.application.alfaflix.presentation.home.HomeScreen
import com.application.alfaflix.presentation.search.SearchScreen
import kotlin.reflect.KClass

/**
 * The app's navigation graph.
 *
 * The bottom bar is hidden on movie detail, which is a full-screen destination rather than
 * a tab — keeping it visible there would suggest detail belongs to a section it does not.
 */
@Composable
fun AlfaflixNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hasRoute(destination.route) == true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                AlfaflixBottomBar(
                    isSelected = { destination ->
                        currentDestination?.hasRoute(destination.route) == true
                    },
                    onSelect = { destination ->
                        navController.navigate(destination.routeInstance) {
                            // Return to a tab without stacking duplicates, and preserve each
                            // tab's scroll position and back stack across switches.
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onMovieClick = navController::navigateToMovieDetail,
                    onAboutClick = { navController.navigate(AboutRoute) { launchSingleTop = true } },
                )
            }

            composable<SearchRoute> {
                SearchScreen(onMovieClick = navController::navigateToMovieDetail)
            }

            composable<FavoritesRoute> {
                FavoritesScreen(onMovieClick = navController::navigateToMovieDetail)
            }

            composable<AboutRoute> {
                AboutScreen(onNavigateBack = navController::navigateUp)
            }

            composable<MovieDetailRoute>(
                enterTransition = {
                    slideInVertically(tween(TRANSITION_MILLIS)) { it / 8 } +
                        fadeIn(tween(TRANSITION_MILLIS))
                },
                exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
                popExitTransition = {
                    slideOutVertically(tween(TRANSITION_MILLIS)) { it / 8 } +
                        fadeOut(tween(TRANSITION_MILLIS))
                },
            ) {
                // The ViewModel reads the TMDB movie ID from its SavedStateHandle, so the screen
                // reconstructs itself from the route alone.
                MovieDetailScreen(onNavigateBack = navController::navigateUp)
            }
        }
    }
}

/**
 * Navigates to a movie, guarding against the double-tap that would otherwise push two
 * copies of the same destination.
 */
private fun NavHostController.navigateToMovieDetail(movieId: Int) {
    navigate(MovieDetailRoute(movieId)) { launchSingleTop = true }
}

@Composable
private fun AlfaflixBottomBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = isSelected(destination)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        // The visible label already names the tab; labelling the icon too
                        // would make a screen reader announce it twice.
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

/** The three bottom-bar destinations. */
private enum class TopLevelDestination(
    val route: KClass<*>,
    val routeInstance: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(
        route = HomeRoute::class,
        routeInstance = HomeRoute,
        labelRes = R.string.nav_home,
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home,
    ),
    SEARCH(
        route = SearchRoute::class,
        routeInstance = SearchRoute,
        labelRes = R.string.nav_search,
        icon = Icons.Outlined.Search,
        selectedIcon = Icons.Filled.Search,
    ),
    FAVORITES(
        route = FavoritesRoute::class,
        routeInstance = FavoritesRoute,
        labelRes = R.string.nav_favorites,
        icon = Icons.Outlined.FavoriteBorder,
        selectedIcon = Icons.Filled.Favorite,
    ),
}

private const val TRANSITION_MILLIS = 280
