package app.prajakeeyam.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.prajakeeyam.AppContainer
import app.prajakeeyam.ui.detail.ProblemDetailScreen
import app.prajakeeyam.ui.feed.FeedScreen
import app.prajakeeyam.ui.home.HomeScreen
import app.prajakeeyam.ui.newproblem.NewProblemScreen
import app.prajakeeyam.ui.profile.ProfileScreen

object Routes {
    const val LANGUAGE = "language"
    const val HOME = "home"
    const val FEED = "feed"
    const val PROBLEM = "problem/{id}"
    const val NEW = "new"
    const val PROFILE = "profile"
    fun problem(id: Int) = "problem/$id"
}

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val activity = LocalActivity.current
    val start = when {
        container.prefs.language == null -> Routes.LANGUAGE
        container.prefs.place == null -> Routes.HOME
        else -> Routes.FEED
    }
    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LANGUAGE) {
            val context = LocalContext.current
            LanguageScreen(onPicked = { lang ->
                container.prefs.language = lang
                // Navigate first: NavHost restores its back stack across recreate(), so recreating from
                // here would bring the language screen straight back.
                nav.navigate(Routes.HOME) { popUpTo(Routes.LANGUAGE) { inclusive = true } }
                val currentLang = context.resources.configuration.locales[0]?.language
                if (currentLang != lang) activity?.recreate()
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                canGoBack = container.prefs.place != null,
                onBack = { nav.popBackStack() },
                onPlaceChosen = { place ->
                    container.prefs.place = place
                    nav.navigate(Routes.FEED) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.FEED) {
            FeedScreen(
                onOpenProblem = { nav.navigate(Routes.problem(it)) },
                onNewProblem = { nav.navigate(Routes.NEW) },
                onChangePlace = { nav.navigate(Routes.HOME) },
                onProfile = { nav.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.PROBLEM, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            ProblemDetailScreen(problemId = entry.arguments?.getInt("id") ?: 0, onBack = { nav.popBackStack() })
        }
        composable(Routes.NEW) {
            NewProblemScreen(onBack = { nav.popBackStack() }, onPosted = { nav.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { nav.popBackStack() }, onChangePlace = { nav.navigate(Routes.HOME) })
        }
    }
}
