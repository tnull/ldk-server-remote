package org.lightningdevkit.ldkserver.remote.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.lightningdevkit.ldkserver.remote.ui.navigation.Routes
import org.lightningdevkit.ldkserver.remote.ui.servers.ServerListScreen
import org.lightningdevkit.ldkserver.remote.ui.setup.AddOrEditServerScreen
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme

/**
 * Top-level entry point used by MainActivity. Owns the outer navigation graph:
 * ServerList ⇄ AddServer ⇄ Main (per-server tabbed scaffold).
 */
@Composable
fun AppRoot(appState: AppState = viewModel(factory = AppState.Factory)) {
    LdkServerRemoteTheme {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = Routes.SERVER_LIST,
            modifier = Modifier,
        ) {
            composable(Routes.SERVER_LIST) {
                ServerListScreen(
                    appState = appState,
                    onServerSelected = { entry ->
                        navController.navigate(Routes.main(entry.id))
                    },
                    onAddServerClicked = {
                        navController.navigate(Routes.addServer())
                    },
                    onEditServerClicked = { entry ->
                        navController.navigate(Routes.addServer(entry.id))
                    },
                )
            }

            composable(
                route = Routes.ADD_SERVER_PATTERN,
                arguments =
                    listOf(
                        navArgument("editId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
            ) { backStackEntry ->
                val editId = backStackEntry.arguments?.getString("editId")
                AddOrEditServerScreen(
                    appState = appState,
                    editId = editId,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.MAIN_PATTERN,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: return@composable
                MainScaffold(
                    appState = appState,
                    serverId = serverId,
                    onBackToServerList = { navController.popBackStack() },
                )
            }
        }
    }
}
