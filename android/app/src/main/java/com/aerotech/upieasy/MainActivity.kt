package com.aerotech.upieasy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.feature.auth.GoogleSignInScreen
import com.aerotech.upieasy.feature.setup.AppSetupScreen
import com.aerotech.upieasy.feature.dashboard.DashboardScreen
import com.aerotech.upieasy.feature.qr.QrGeneratorScreen
import com.aerotech.upieasy.feature.qr.QrScannerScreen
import com.aerotech.upieasy.feature.settings.SettingsScreen
import com.aerotech.upieasy.feature.staff.StaffScreen
import com.aerotech.upieasy.feature.transactions.TransactionsScreen
import com.aerotech.upieasy.feature.upi.UpiScreen
import com.aerotech.upieasy.ui.theme.BackgroundLight
import com.aerotech.upieasy.ui.theme.BrandAccent
import com.aerotech.upieasy.ui.theme.SurfaceLight
import com.aerotech.upieasy.ui.theme.UPIEasyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    data object Transactions : Screen("transactions", "Ledger", Icons.AutoMirrored.Filled.ReceiptLong)
    data object Upi : Screen("upi", "UPI", Icons.Default.AccountBalanceWallet)
    data object Staff : Screen("staff", "Staff", Icons.Default.Group)
    data object Settings : Screen("settings", "More", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionManager = SessionManager(applicationContext)
        val database by lazy { AppDatabase.getInstance(applicationContext) }

        setContent {
            UPIEasyTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                var isInitialized by remember { mutableStateOf(false) }
                var resolvedStartDestination by remember { mutableStateOf("auth") }

                // Resolve initial destination once before building NavHost to avoid recomposition graph crash
                LaunchedEffect(Unit) {
                    try {
                        val token = sessionManager.getAccessToken()
                        val isComplete = sessionManager.isSetupCompleteFlow.first()
                        resolvedStartDestination = when {
                            token.isNullOrBlank() -> "auth"
                            !isComplete -> "setup"
                            else -> "main"
                        }
                    } catch (e: Exception) {
                        resolvedStartDestination = "auth"
                    } finally {
                        isInitialized = true
                    }
                }

                if (!isInitialized) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundLight),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandAccent)
                    }
                } else {
                    val token by sessionManager.accessTokenFlow.collectAsState(initial = null)
                    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)

                    // If user is logged in but hasn't selected an organization, fetch and set first org
                    LaunchedEffect(token, currentOrgId) {
                        if (!token.isNullOrBlank() && currentOrgId.isNullOrBlank()) {
                            try {
                                val api = NetworkClient.getApiService(sessionManager)
                                val res = api.getOrganizations()
                                if (res.isSuccessful && res.body()?.success == true) {
                                    val orgs = res.body()!!.organizations
                                    if (orgs.isNotEmpty()) {
                                        sessionManager.setOrganization(orgs[0].id, orgs[0].name, orgs[0].role)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    val activePaymentAlert by com.aerotech.upieasy.core.util.PaymentAlertManager.activePaymentAlert.collectAsState()

                    LaunchedEffect(Unit) {
                        UPIEasyApp.triggerImmediateSync(applicationContext)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = resolvedStartDestination
                    ) {
                        composable("auth") {
                            GoogleSignInScreen(
                                sessionManager = sessionManager,
                                onNavigateToSetup = {
                                    navController.navigate("setup") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                },
                                onNavigateToMain = {
                                    navController.navigate("main") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("setup") {
                            AppSetupScreen(
                                sessionManager = sessionManager,
                                onSetupComplete = {
                                    navController.navigate("main") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("main") {
                            MainAppContent(
                                sessionManager = sessionManager,
                                database = database,
                                onNavigateToScan = { navController.navigate("qr_scan") },
                                onNavigateToQr = { navController.navigate("qr_gen") },
                                onLogout = {
                                    scope.launch {
                                        sessionManager.clearSession()
                                        navController.navigate("auth") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("qr_scan") {
                            QrScannerScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("qr_gen") {
                            QrGeneratorScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    activePaymentAlert?.let { alert ->
                        com.aerotech.upieasy.ui.components.PaymentPopupDialog(
                            alert = alert,
                            onDismiss = { com.aerotech.upieasy.core.util.PaymentAlertManager.dismissAlert() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppContent(
    sessionManager: SessionManager,
    database: AppDatabase,
    onNavigateToScan: () -> Unit,
    onNavigateToQr: () -> Unit,
    onLogout: () -> Unit
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        Screen.Dashboard,
        Screen.Transactions,
        Screen.Upi,
        Screen.Staff,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceLight,
                tonalElevation = 8.dp
            ) {
                items.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                bottomNavController.navigate(screen.route) {
                                    popUpTo(bottomNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandAccent,
                            selectedTextColor = BrandAccent,
                            indicatorColor = BrandAccent.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = bottomNavController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    sessionManager = sessionManager,
                    onNavigateToScan = onNavigateToScan,
                    onNavigateToQr = onNavigateToQr,
                    onNavigateToTransactions = { bottomNavController.navigate(Screen.Transactions.route) },
                    onNavigateToUpi = { bottomNavController.navigate(Screen.Upi.route) }
                )
            }

            composable(Screen.Transactions.route) {
                TransactionsScreen(sessionManager = sessionManager, database = database)
            }

            composable(Screen.Upi.route) {
                UpiScreen(
                    sessionManager = sessionManager,
                    onNavigateToQrForVpa = { _, _ -> onNavigateToQr() }
                )
            }

            composable(Screen.Staff.route) {
                StaffScreen(sessionManager = sessionManager)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(sessionManager = sessionManager, database = database, onLogout = onLogout)
            }
        }
    }
}
