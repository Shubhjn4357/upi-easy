package com.aerotech.upieasy

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.aerotech.upieasy.core.util.HapticHelper
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.draw.scale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aerotech.upieasy.core.util.BiometricPromptHelper
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

                var initialToken by remember { mutableStateOf<String?>(null) }
                var isInitialized by remember { mutableStateOf(false) }
                var resolvedStartDestination by remember { mutableStateOf("auth") }
                var isAppLocked by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    try {
                        val token = sessionManager.getAccessToken()
                        initialToken = token
                        val isComplete = sessionManager.isSetupCompleteFlow.first()
                        val currentOrg = sessionManager.currentOrgIdFlow.first()
                        val bioEnabled = sessionManager.biometricLockFlow.first()

                        if (!token.isNullOrBlank() && bioEnabled) {
                            isAppLocked = true
                        }

                        resolvedStartDestination = when {
                            token.isNullOrBlank() -> "auth"
                            !isComplete && currentOrg.isNullOrBlank() -> "setup"
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
                    val token by sessionManager.accessTokenFlow.collectAsState(initial = initialToken)
                    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
                    val biometricLockEnabled by sessionManager.biometricLockFlow.collectAsState(initial = false)

                    // Reactive Auth Guard: forcefully throw to login page only if user was logged in and token is cleared
                    var hadValidSession by remember { mutableStateOf(!initialToken.isNullOrBlank()) }
                    LaunchedEffect(token) {
                        if (!token.isNullOrBlank()) {
                            hadValidSession = true
                        } else if (hadValidSession) {
                            hadValidSession = false
                            val currentRoute = navController.currentBackStackEntry?.destination?.route
                            if (currentRoute != null && currentRoute != "auth") {
                                navController.navigate("auth") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    }

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner, biometricLockEnabled, token) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP) {
                                if (biometricLockEnabled && !token.isNullOrBlank()) {
                                    isAppLocked = true
                                }
                            } else if (event == Lifecycle.Event.ON_RESUME) {
                                if (isAppLocked && biometricLockEnabled && !token.isNullOrBlank()) {
                                    if (BiometricPromptHelper.isBiometricAvailable(this@MainActivity)) {
                                        BiometricPromptHelper.showBiometricPrompt(
                                            activity = this@MainActivity,
                                            title = "Unlock UPIEasy",
                                            subtitle = "Biometric identity required to access account",
                                            onSuccess = {
                                                isAppLocked = false
                                                HapticHelper.performHaptic(this@MainActivity, HapticHelper.FeedbackType.SUCCESS)
                                            },
                                            onError = { _ -> }
                                        )
                                    }
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    LaunchedEffect(isAppLocked) {
                        if (isAppLocked) {
                            if (BiometricPromptHelper.isBiometricAvailable(this@MainActivity)) {
                                BiometricPromptHelper.showBiometricPrompt(
                                    activity = this@MainActivity,
                                    title = "Unlock UPIEasy",
                                    subtitle = "Biometric identity required to access account",
                                    onSuccess = {
                                        isAppLocked = false
                                        HapticHelper.performHaptic(this@MainActivity, HapticHelper.FeedbackType.SUCCESS)
                                    },
                                    onError = { _ -> }
                                )
                            } else {
                                isAppLocked = false
                            }
                        }
                    }

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
                        startDestination = resolvedStartDestination,
                        enterTransition = { fadeIn(animationSpec = tween(220)) },
                        exitTransition = { fadeOut(animationSpec = tween(180)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                        popExitTransition = { fadeOut(animationSpec = tween(180)) }
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
                                onNavigateToPaymentDetection = { navController.navigate("payment_detection") },
                                onLogout = {
                                    scope.launch {
                                        sessionManager.clearSession()
                                        com.aerotech.upieasy.core.util.PaymentAlertManager.clearAlertHistory()
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                database.clearAllTables()
                                            } catch (_: Exception) {}
                                        }
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

                        composable("payment_detection") {
                            com.aerotech.upieasy.feature.settings.PaymentDetectionSettingsScreen(
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

                    if (isAppLocked) {
                        BiometricLockBottomDrawer(
                            onUnlockRequest = {
                                if (BiometricPromptHelper.isBiometricAvailable(this@MainActivity)) {
                                    BiometricPromptHelper.showBiometricPrompt(
                                        activity = this@MainActivity,
                                        title = "Unlock UPIEasy",
                                        subtitle = "Biometric identity required to access account",
                                        onSuccess = {
                                            isAppLocked = false
                                            HapticHelper.performHaptic(this@MainActivity, HapticHelper.FeedbackType.SUCCESS)
                                        },
                                        onError = { err ->
                                            android.widget.Toast.makeText(this@MainActivity, err, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    isAppLocked = false
                                }
                            }
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
    onNavigateToPaymentDetection: () -> Unit = {},
    onLogout: () -> Unit
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val userRole by sessionManager.userRoleFlow.collectAsState(initial = null)
    var showHubSheet by remember { mutableStateOf(false) }
    var isBottomBarShrunk by remember { mutableStateOf(false) }

    // Reset shrink state when navigating between tabs/screens
    LaunchedEffect(currentRoute) {
        isBottomBarShrunk = false
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -14f) {
                    // Scrolling down (finger moves up) -> shrink bottom tab
                    if (!isBottomBarShrunk) {
                        isBottomBarShrunk = true
                    }
                } else if (delta > 14f) {
                    // Scrolling up towards top (finger moves down) -> restore to normal size
                    if (isBottomBarShrunk) {
                        isBottomBarShrunk = false
                    }
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        NavHost(
            navController = bottomNavController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    sessionManager = sessionManager,
                    onNavigateToScan = onNavigateToScan,
                    onNavigateToQr = { onNavigateToQr(null, null) },
                    onNavigateToTransactions = { bottomNavController.navigate(Screen.Transactions.route) },
                    onNavigateToUpi = { bottomNavController.navigate(Screen.Upi.route) },
                    onNavigateToSettings = { bottomNavController.navigate(Screen.Settings.route) },
                    onNavigateToStaff = { bottomNavController.navigate(Screen.Staff.route) },
                    onNavigateToLegal = onNavigateToLegal,
                    onLogout = onLogout
                )
            }

            composable(Screen.Transactions.route) {
                TransactionsScreen(sessionManager = sessionManager, database = database)
            }

            composable(Screen.Upi.route) {
                UpiScreen(
                    sessionManager = sessionManager,
                    onNavigateToQrForVpa = { vpa, name -> onNavigateToQr(vpa, name) },
                    onNavigateToPaymentDetection = onNavigateToPaymentDetection
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
                    onNavigateToPaymentDetection = onNavigateToPaymentDetection,
                    onLogout = onLogout
                )
            }
        }

        FloatingGlassBottomBar(
            currentRoute = currentRoute,
            isShrunk = isBottomBarShrunk,
            onExpand = { isBottomBarShrunk = false },
            onOpenHub = { showHubSheet = true },
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
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        BentoGridRoutesBottomDrawer(
            visible = showHubSheet,
            userRole = userRole,
            onDismiss = { showHubSheet = false },
            onNavigateToScan = {
                showHubSheet = false
                onNavigateToScan()
            },
            onNavigateToQr = {
                showHubSheet = false
                onNavigateToQr(null, null)
            },
            onNavigateToTransactions = {
                showHubSheet = false
                bottomNavController.navigate(Screen.Transactions.route) {
                    popUpTo(Screen.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onNavigateToUpi = {
                showHubSheet = false
                bottomNavController.navigate(Screen.Upi.route) {
                    popUpTo(Screen.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onNavigateToStaff = {
                showHubSheet = false
                bottomNavController.navigate(Screen.Staff.route) {
                    popUpTo(Screen.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onNavigateToSettings = {
                showHubSheet = false
                bottomNavController.navigate(Screen.Settings.route) {
                    popUpTo(Screen.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onNavigateToLegal = {
                showHubSheet = false
                onNavigateToLegal()
            }
        )
    }
}

/**
 * 3-button icon-based sleek floating rounded pill glassmorphism bottom bar.
 * No text labels. Left: Home, Center: + Hub Button, Right: Settings.
 * Shrinks dynamically to a sleek compact circle on scroll down,
 * and expands back to normal size on scroll up or tap.
 */
@Composable
fun FloatingGlassBottomBar(
    currentRoute: String,
    isShrunk: Boolean,
    onExpand: () -> Unit,
    onOpenHub: () -> Unit,
    onItemClick: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val barWidth by animateDpAsState(
        targetValue = if (isShrunk) 58.dp else 228.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "barWidth"
    )

    val glowWidth by animateDpAsState(
        targetValue = if (isShrunk) 54.dp else 224.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glowWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient soft glow behind the floating pill
        Box(
            modifier = Modifier
                .width(glowWidth)
                .height(40.dp)
                .clip(CircleShape)
                .background(SoftGlowIndigo.copy(alpha = if (isShrunk) 0.38f else 0.32f))
        )

        // Floating rounded glassmorphism pill container
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = if (isShrunk) 8.dp else 6.dp,
            shadowElevation = if (isShrunk) 18.dp else 16.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
            modifier = Modifier
                .width(barWidth)
                .height(58.dp)
                .then(
                    if (isShrunk) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onExpand()
                        }
                    } else Modifier
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 1. Home Button (Animated visibility when expanded)
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isShrunk,
                    enter = fadeIn(tween(160)) + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.End),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                ) {
                    val isHomeSelected = currentRoute == Screen.Dashboard.route
                    val homeIconColor by animateColorAsState(
                        targetValue = if (isHomeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        animationSpec = tween(200),
                        label = "homeIconColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isHomeSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                onItemClick(Screen.Dashboard)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = homeIconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 2. Center '+' Quick Action Hub Button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BrandGradientStart, BrandGradientEnd)
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isShrunk) {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                onExpand()
                            } else {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                                onOpenHub()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (isShrunk) "Expand Tab" else "Quick Actions & Routes",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 3. Settings Button (Animated visibility when expanded)
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isShrunk,
                    enter = fadeIn(tween(160)) + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.Start),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                ) {
                    val isSettingsSelected = currentRoute == Screen.Settings.route
                    val settingsIconColor by animateColorAsState(
                        targetValue = if (isSettingsSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        animationSpec = tween(200),
                        label = "settingsIconColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isSettingsSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                onItemClick(Screen.Settings)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = settingsIconColor,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bento Grid Routes Bottom Drawer displaying all destinations with glassmorphic cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BentoGridRoutesBottomDrawer(
    visible: Boolean,
    userRole: String? = null,
    onDismiss: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToQr: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToUpi: () -> Unit,
    onNavigateToStaff: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLegal: () -> Unit
) {
    if (visible) {
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            windowInsets = WindowInsets.navigationBars
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Quick Actions & Routes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Access all tools and modules in one tap",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Row 1: Primary Actions (Scan & Receive)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoCard(
                        modifier = Modifier.weight(1f),
                        title = "Scan QR",
                        subtitle = "Pay any merchant",
                        icon = Icons.Default.QrCodeScanner,
                        iconBg = PastelEmeraldBg,
                        iconTint = SuccessGreen,
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            onNavigateToScan()
                        }
                    )

                    BentoCard(
                        modifier = Modifier.weight(1f),
                        title = "My QR Code",
                        subtitle = "Receive payments",
                        icon = Icons.Default.QrCode,
                        iconBg = PastelIndigoBg,
                        iconTint = BrandPrimary,
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            onNavigateToQr()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 2: Transactions & Accounts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoCard(
                        modifier = Modifier.weight(1f),
                        title = "Ledger History",
                        subtitle = "Inflows & filters",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        iconBg = PastelAmberBg,
                        iconTint = AmberAlert,
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onNavigateToTransactions()
                        }
                    )

                    BentoCard(
                        modifier = Modifier.weight(1f),
                        title = "UPI Accounts",
                        subtitle = "VPAs & Bank handles",
                        icon = Icons.Default.AccountBalanceWallet,
                        iconBg = PastelBlueBg,
                        iconTint = PastelBlueIcon,
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onNavigateToUpi()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 3: Staff & Settings (Role-Gated: Cashiers cannot access Staff)
                val isCashier = userRole?.uppercase() == "CASHIER"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isCashier) {
                        BentoCard(
                            modifier = Modifier.weight(1f),
                            title = "Team & Staff",
                            subtitle = "Cashiers & roles",
                            icon = Icons.Default.Group,
                            iconBg = PastelPurpleBg,
                            iconTint = PastelPurpleIcon,
                            onClick = {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                onNavigateToStaff()
                            }
                        )
                    }

                    BentoCard(
                        modifier = Modifier.weight(1f),
                        title = "Settings",
                        subtitle = "Audio, alerts & theme",
                        icon = Icons.Default.Settings,
                        iconBg = PastelCyan,
                        iconTint = PastelCyanIcon,
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onNavigateToSettings()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 4: Legal & Compliance Slim Bento Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onNavigateToLegal()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Legal & NPCI Disclosures",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "RBI compliance, grievance redressal & privacy policy",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Biometric lock bottom drawer displayed when app requires biometric verification.
 */
@Composable
fun BiometricLockBottomDrawer(
    onUnlockRequest: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tapping scrim prompts biometric unlock with haptic feedback
                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.ERROR)
                onUnlockRequest()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Catch clicks inside drawer */ }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // Drawer handle pill
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Pulsing biometric icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Lock",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "UPIEasy Secured",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Biometric lock is active. Authenticate with your fingerprint or device credential to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                        onUnlockRequest()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Tap to Unlock", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

