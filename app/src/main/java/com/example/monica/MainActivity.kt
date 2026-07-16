package com.example.monica

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.screens.ChatListScreen
import com.example.monica.ui.screens.ChatScreen
import com.example.monica.ui.screens.LoginScreen
import com.example.monica.ui.screens.NotificationsScreen
import com.example.monica.ui.screens.PrivateChatScreen
import com.example.monica.ui.screens.ProfileScreen
import com.example.monica.ui.screens.SettingsScreen
import com.example.monica.ui.theme.MonicaTheme

class MainActivity : ComponentActivity() {
    private val vm: MonicaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val darkTheme by vm.darkTheme.collectAsStateWithLifecycle()
            MonicaTheme(darkTheme = darkTheme) {
                val snackbar = remember { SnackbarHostState() }
                val error by vm.error.collectAsStateWithLifecycle()

                LaunchedEffect(error) {
                    val msg = error ?: return@LaunchedEffect
                    snackbar.showSnackbar(msg)
                    vm.clearError()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbar) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { padding ->
                    MonicaNav(
                        vm = vm,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("chat_id")?.takeIf { it.isNotBlank() }
        if (!chatId.isNullOrBlank()) {
            vm.openChatFromNotification(chatId)
            // чтобы повторный onCreate с тем же intent не открывал чат снова
            intent.removeExtra(EXTRA_CHAT_ID)
            intent.removeExtra("chat_id")
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "monica.extra.CHAT_ID"
    }
}

@Composable
private fun MonicaNav(
    vm: MonicaViewModel,
    modifier: Modifier = Modifier,
) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val privateNav by vm.privateNav.collectAsStateWithLifecycle()
    val pendingChatNav by vm.pendingChatNav.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    NotificationPermissionEffect()

    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        } else if (navController.currentDestination?.route == "login" ||
            navController.currentDestination == null
        ) {
            navController.navigate("chats") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(pendingChatNav, loggedIn) {
        val chatId = pendingChatNav ?: return@LaunchedEffect
        if (!loggedIn) return@LaunchedEffect
        val route = "chat/$chatId"
        val current = navController.currentDestination?.route
        if (current != route) {
            navController.navigate(route) {
                // список чатов остаётся под чатом
                launchSingleTop = true
                restoreState = true
            }
        }
        vm.consumePendingChatNav()
    }

    LaunchedEffect(privateNav) {
        val target = privateNav ?: return@LaunchedEffect
        val route = "private/${target.sessionId}/${target.chatId}"
        val current = navController.currentDestination?.route
        if (current != route) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        vm.consumePrivateNav()
    }

    NavHost(
        navController = navController,
        startDestination = if (loggedIn) "chats" else "login",
        modifier = modifier,
    ) {
        composable("login") {
            LoginScreen(
                vm = vm,
                onLoggedIn = {
                    navController.navigate("chats") {
                        popUpTo("login") { inclusive = true }
                    }
                },
            )
        }
        composable("chats") {
            ChatListScreen(
                vm = vm,
                onOpenChat = { chatId ->
                    navController.navigate("chat/$chatId")
                },
                onOpenNotifications = {
                    navController.navigate("notifications")
                },
                onOpenProfile = {
                    navController.navigate("profile")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                },
            )
        }
        composable("profile") {
            ProfileScreen(
                nickname = vm.session.nickname,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("notifications") {
            NotificationsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { entry ->
            val chatId = entry.arguments?.getString("chatId").orEmpty()
            ChatScreen(
                chatId = chatId,
                vm = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "private/{sessionId}/{chatId}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("chatId") { type = NavType.StringType },
            ),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val chatId = entry.arguments?.getString("chatId").orEmpty()
            val partner = vm.chatById(chatId)?.partner?.nickname
            PrivateChatScreen(
                sessionId = sessionId,
                partnerNickname = partner,
                vm = vm,
                onClose = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun NotificationPermissionEffect() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
