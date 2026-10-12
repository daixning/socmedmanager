@file:OptIn(ExperimentalMaterial3Api::class)

package app.socmed.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import app.socmed.manager.data.Prefs
import app.socmed.manager.ui.AnalyticsScreen
import app.socmed.manager.ui.AppEnv
import app.socmed.manager.ui.ContentScreen
import app.socmed.manager.ui.CsvImportScreen
import app.socmed.manager.ui.EventScreen
import app.socmed.manager.ui.EventsScreen
import app.socmed.manager.ui.HomeScreen
import app.socmed.manager.ui.LocalData
import app.socmed.manager.ui.LocalDark
import app.socmed.manager.ui.LocalEnv
import app.socmed.manager.ui.MetricsScreen
import app.socmed.manager.ui.PostScreen
import app.socmed.manager.ui.SettingsScreen
import app.socmed.manager.ui.SocMedTheme
import app.socmed.manager.ui.rememberNav

class MainActivity : ComponentActivity() {
    private var pendingRoute by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent?.getStringExtra("route")
        val app = application as SocMedApp
        setContent { Root(app) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute = intent.getStringExtra("route")
    }

    @Composable
    private fun Root(app: SocMedApp) {
        val data by app.repo.data.collectAsState()
        val nav = rememberNav()
        val snackbar = remember { SnackbarHostState() }
        val env = remember { AppEnv(app, nav, snackbar) }
        val mode = data.pref(Prefs.DARK, "system")
        val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
        var tab by rememberSaveable { mutableIntStateOf(0) }

        LaunchedEffect(pendingRoute) { pendingRoute?.let { nav.push(it); pendingRoute = null } }

        SocMedTheme(data.pref(Prefs.PALETTE, "lavender"), mode) {
            CompositionLocalProvider(LocalEnv provides env, LocalData provides data, LocalDark provides dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val top = nav.top
                    BackHandler(enabled = top != null) { nav.pop() }
                    if (top != null) {
                        // Detail screens sit above the main tabs; Back returns to wherever you were.
                        Box(Modifier.fillMaxSize()) {
                            val parts = top.split("/", limit = 2)
                            when (parts[0]) {
                                "post" -> PostScreen(parts[1])
                                "event" -> EventScreen(parts[1])
                                "metrics" -> MetricsScreen(parts[1])
                                "csvimport" -> CsvImportScreen()
                                else -> LaunchedEffect(top) { nav.pop() }
                            }
                        }
                    } else MainTabs(tab, { tab = it }, env)
                }
            }
        }
    }

    @Composable
    private fun MainTabs(tab: Int, onTab: (Int) -> Unit, env: AppEnv) {
        val items = listOf("Home" to Icons.Filled.Home, "Content" to Icons.Filled.Dashboard, "Events" to Icons.Filled.CalendarMonth, "Analytics" to Icons.Filled.BarChart, "Settings" to Icons.Filled.Settings)
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(env.snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    items.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = tab == i, onClick = { onTab(i) }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(goTo = onTab)
                    1 -> ContentScreen()
                    2 -> EventsScreen()
                    3 -> AnalyticsScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}
