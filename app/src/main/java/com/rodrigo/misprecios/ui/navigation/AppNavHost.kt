package com.rodrigo.misprecios.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rodrigo.misprecios.ui.AppViewModel
import com.rodrigo.misprecios.ui.screens.AddProductScreen
import com.rodrigo.misprecios.ui.screens.HomeScreen
import com.rodrigo.misprecios.ui.screens.ProductDetailScreen
import com.rodrigo.misprecios.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val ADD_PRODUCT = "add_product"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{productId}"
    fun detail(productId: Long) = "detail/$productId"
}

@Composable
fun AppNavHost(viewModel: AppViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onAddProduct = { navController.navigate(Routes.ADD_PRODUCT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProduct = { id -> navController.navigate(Routes.detail(id)) }
            )
        }
        composable(Routes.ADD_PRODUCT) {
            AddProductScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("productId") { type = NavType.LongType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            ProductDetailScreen(
                viewModel = viewModel,
                productId = productId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
