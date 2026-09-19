package com.aerotech.upieasy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
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

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionManager = SessionManager(applicationContext)
        val database by lazy { AppDatabase.getInstance(applicationContext) }

        setContent {
            val themeMode by sessionManager.themeModeFlow.collectAsState(initial = "SYSTEM")
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            UPIEasyTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                var isInitialized by remember { mutableStateOf(false) }
                var resolvedStartDestination by remember { mutableStateOf("auth") }

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
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandAccent)
                    }
                } else {
                    val token by sessionManager.accessTokenFlow.collectAsState(initial = null)
                    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)

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
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

    val items = listOf(
        Screen.Dashboard,
        Screen.Transactions,
        Screen.Upi,
        Screen.Staff,
        Screen.Settings
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            FloatingGlassBottomBar(
                items = items,
                currentRoute = currentRoute,
                onNavigateToScan = onNavigateToScan,
                onItemClick = { screen ->
                    if (currentRoute != screen.route) {
                        bottomNavController.navigate(screen.route) {
                            popUpTo(bottomNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
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

@Composable
fun FloatingGlassBottomBar(
    items: List<Screen>,
    currentRoute: String,
    onNavigateToScan: () -> Unit,
    onItemClick: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val isHomeScreen = screen == Screen.Dashboard

                    // If we're on the dashboard and looking at the home tab, change to Scan QR button style
                    val showScanButton = isHomeScreen && isSelected

                    val icon = if (showScanButton) Icons.Default.QrCodeScanner else screen.icon
                    val title = if (showScanButton) "Scan QR" else screen.title

                    val iconColor by animateColorAsState(
                        targetValue = when {
                            showScanButton -> Color.White
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        animationSpec = tween(250),
                        label = "iconColor"
                    )

                    val pillBgColor by animateColorAsState(
                        targetValue = when {
                            showScanButton -> BrandAccent
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(250),
                        label = "pillBgColor"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(pillBgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (showScanButton) {
                                    onNavigateToScan()
                                } else {
                                    onItemClick(screen)
                                }
                            }
                            .padding(
                                horizontal = if (isSelected || showScanButton) 14.dp else 10.dp,
                                vertical = 8.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                            AnimatedVisibility(visible = isSelected || showScanButton) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected || showScanButton) FontWeight.Bold else FontWeight.Normal,
                                    color = if (showScanButton) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

