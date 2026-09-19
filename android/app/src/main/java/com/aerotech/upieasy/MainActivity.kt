package com.aerotech.upieasy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
import com.aerotech.upieasy.ui.theme.*
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
            val dynamicColor by sessionManager.dynamicColorFlow.collectAsState(initial = false)
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            UPIEasyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
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
                                        val o = orgs[0]
                                        sessionManager.setOrganization(
                                            orgId = o.id,
                                            orgName = o.name,
                                            role = o.role,
                                            legalName = o.legalBusinessName,
                                            category = o.category,
                                            panNumber = o.panNumber,
                                            gstin = o.gstin
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    val activePaymentAlert by com.aerotech.upieasy.core.util.PaymentAlertManager.activePaymentAlert.collectAsState()

                    // Ask for notification permission right after the user is authenticated
                    var askNotificationPermission by remember { mutableStateOf(false) }
                    com.aerotech.upieasy.core.util.NotificationPermissionEffect(
                        trigger = askNotificationPermission,
                        onGranted = { askNotificationPermission = false },
                        onDismissed = { askNotificationPermission = false }
                    )

                    LaunchedEffect(token) {
                        // Trigger notification permission request once when user is logged in
                        if (!token.isNullOrBlank()) {
                            askNotificationPermission = true
                        }
                    }

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
                                onNavigateToQr = { vpa, name ->
                                    if (!vpa.isNullOrBlank()) {
                                        navController.navigate("qr_gen?vpa=$vpa&name=${name ?: ""}")
                                    } else {
                                        navController.navigate("qr_gen")
                                    }
                                },
                                onNavigateToLegal = { navController.navigate("legal") },
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

                        composable(
                            route = "qr_gen?vpa={vpa}&name={name}",
                            arguments = listOf(
                                navArgument("vpa") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { backStackEntry ->
                            val vpaArg = backStackEntry.arguments?.getString("vpa")
                            val nameArg = backStackEntry.arguments?.getString("name")
                            QrGeneratorScreen(
                                sessionManager = sessionManager,
                                database = database,
                                initialVpa = vpaArg,
                                initialPayeeName = nameArg,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("legal") {
                            com.aerotech.upieasy.feature.legal.LegalScreen(
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
    onNavigateToQr: (vpa: String?, name: String?) -> Unit,
    onNavigateToLegal: () -> Unit,
    onLogout: () -> Unit
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            FloatingGlassBottomBar(
                currentRoute = currentRoute,
                onNavigateToScan = onNavigateToScan,
                onItemClick = { screen ->
                    if (screen.route == Screen.Dashboard.route) {
                        bottomNavController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    } else {
                        bottomNavController.navigate(screen.route) {
                            popUpTo(Screen.Dashboard.route) {
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
                    onNavigateToQr = { onNavigateToQr(null, null) },
                    onNavigateToTransactions = { bottomNavController.navigate(Screen.Transactions.route) },
                    onNavigateToUpi = { bottomNavController.navigate(Screen.Upi.route) },
                    onNavigateToSettings = { bottomNavController.navigate(Screen.Settings.route) },
                    onNavigateToStaff = { bottomNavController.navigate(Screen.Staff.route) }
                )
            }

            composable(Screen.Transactions.route) {
                TransactionsScreen(sessionManager = sessionManager, database = database)
            }

            composable(Screen.Upi.route) {
                UpiScreen(
                    sessionManager = sessionManager,
                    onNavigateToQrForVpa = { vpa, name -> onNavigateToQr(vpa, name) }
                )
            }

            composable(Screen.Staff.route) {
                StaffScreen(sessionManager = sessionManager)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    sessionManager = sessionManager,
                    database = database,
                    onNavigateToLegal = onNavigateToLegal,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
fun FloatingGlassBottomBar(
    currentRoute: String,
    onNavigateToScan: () -> Unit,
    onItemClick: (Screen) -> Unit
) {
    val leftItems = listOf(Screen.Dashboard, Screen.Transactions)
    val rightItems = listOf(Screen.Upi, Screen.Staff)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient soft glow pod beneath the floating bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(SoftGlowIndigo.copy(alpha = 0.35f))
        )

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, GlassBorderLight),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left 2 items (Home, Ledger)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leftItems.forEach { screen ->
                        BottomNavItem(
                            screen = screen,
                            isSelected = currentRoute == screen.route,
                            onClick = { onItemClick(screen) }
                        )
                    }
                }

                // Reserved space in bar for the centered floating QR FAB
                Spacer(modifier = Modifier.width(62.dp))

                // Right 2 items (UPI, Staff)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rightItems.forEach { screen ->
                        BottomNavItem(
                            screen = screen,
                            isSelected = currentRoute == screen.route,
                            onClick = { onItemClick(screen) }
                        )
                    }
                }
            }
        }

        // Center Elevated QR Action Button Glow Ring
        Box(
            modifier = Modifier
                .offset(y = (-16).dp)
                .size(68.dp)
                .clip(CircleShape)
                .background(SoftGlowIndigo.copy(alpha = 0.45f))
        )

        // Center Elevated QR Action Button (PayOu floating glass style)
        Surface(
            modifier = Modifier
                .offset(y = (-16).dp)
                .size(58.dp)
                .clickable { onNavigateToScan() },
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 12.dp,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandGradientStart, BrandGradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        animationSpec = tween(200),
        label = "iconColor"
    )
    val pillBgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(200),
        label = "pillBgColor"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pillBgColor)
            .then(
                if (isSelected) Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)), RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = screen.title,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = screen.title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = iconColor,
            maxLines = 1
        )
    }
}

