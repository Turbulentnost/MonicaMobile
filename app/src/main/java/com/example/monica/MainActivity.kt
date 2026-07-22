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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.monica.data.SessionStore
import com.example.monica.data.ws.PresenceHub
import com.example.monica.push.MonicaDaemonService
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.CallHost
import com.example.monica.ui.screens.ChatDetailsScreen
import com.example.monica.ui.screens.ChatListScreen
import com.example.monica.ui.screens.ChatScreen
import com.example.monica.ui.screens.LoginScreen
import com.example.monica.ui.screens.NotificationsScreen
import com.example.monica.ui.screens.PrivateChatScreen
import com.example.monica.ui.screens.ProfileScreen
import com.example.monica.ui.screens.RegistrationScreen
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

    override fun onStart() {
        super.onStart()
        // UI открыт — гарантируем демон + presence (не рвём уже живой сокет).
        if (SessionStore(this).isLoggedIn) {
            MonicaDaemonService.start(this)
            PresenceHub.ensureConnected(this)
        }
    }

    override fun onStop() {
        // Не отключаем presence: пользователь должен оставаться «в сети».
        if (SessionStore(this).isLoggedIn) {
            MonicaDaemonService.start(this)
            PresenceHub.ensureConnected(this)
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val callId = intent.getStringExtra(EXTRA_CALL_ID)?.takeIf { it.isNotBlank() }
        if (callId != null) {
            vm.handleIncomingCallIntent(
                callId = callId,
                chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
                    .ifBlank { intent.getStringExtra("chat_id").orEmpty() },
                mediaMode = intent.getStringExtra(EXTRA_CALL_MEDIA_MODE).orEmpty()
                    .ifBlank { "audio" },
                callerId = intent.getStringExtra(EXTRA_CALL_CALLER_ID).orEmpty(),
                callerNickname = intent.getStringExtra(EXTRA_CALL_CALLER_NICKNAME).orEmpty(),
                action = intent.getStringExtra(EXTRA_CALL_ACTION) ?: intent.action,
            )
            intent.removeExtra(EXTRA_CALL_ID)
            intent.removeExtra(EXTRA_CALL_ACTION)
            intent.removeExtra(EXTRA_CALL_MEDIA_MODE)
            intent.removeExtra(EXTRA_CALL_CALLER_ID)
            intent.removeExtra(EXTRA_CALL_CALLER_NICKNAME)
            intent.removeExtra(EXTRA_CHAT_ID)
            intent.removeExtra("chat_id")
            return
        }
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
        const val EXTRA_CALL_ID = "monica.extra.CALL_ID"
        const val EXTRA_CALL_ACTION = "monica.extra.CALL_ACTION"
        const val EXTRA_CALL_MEDIA_MODE = "monica.extra.CALL_MEDIA_MODE"
        const val EXTRA_CALL_CALLER_ID = "monica.extra.CALL_CALLER_ID"
        const val EXTRA_CALL_CALLER_NICKNAME = "monica.extra.CALL_CALLER_NICKNAME"
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

    val callState by vm.callState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (loggedIn) "chats" else "login",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("login") {
                LoginScreen(
                    vm = vm,
                    onLoggedIn = {
                        navController.navigate("chats") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onRegister = {
                        navController.navigate("register")
                    },
                )
            }
            composable("register") {
                RegistrationScreen(
                    vm = vm,
                    onRegistered = {
                        navController.navigate("chats") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onBackToLogin = {
                        navController.popBackStack()
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
                    vm = vm,
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
                    onBack = {
                        vm.leaveChat()
                        navController.popBackStack()
                    },
                    onOpenDetails = {
                        navController.navigate("chat/$chatId/details")
                    },
                )
            }
            composable(
                route = "chat/{chatId}/details",
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { entry ->
                val chatId = entry.arguments?.getString("chatId").orEmpty()
                ChatDetailsScreen(
                    chatId = chatId,
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onJumpToMessage = { navController.popBackStack() },
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

        if (loggedIn) {
            val context = LocalContext.current
            var pendingCallAction by remember { mutableStateOf<String?>(null) }

            val callPermissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                val action = pendingCallAction
                pendingCallAction = null
                val micGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED ||
                    grants[Manifest.permission.RECORD_AUDIO] == true
                val camGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED ||
                    grants[Manifest.permission.CAMERA] == true
                when (action) {
                    // Входящий показываем всегда; для ответа достаточно микрофона.
                    // Камера опциональна — видеозвонок можно принять без своей камеры.
                    "accept" -> if (micGranted) vm.acceptCall()
                    "toggle_camera" -> if (camGranted) vm.toggleCallCamera()
                    "upgrade_video" -> if (camGranted) vm.upgradeCallToVideo()
                }
            }

            fun hasMic() = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

            fun hasCam() = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

            CallHost(
                state = callState,
                callController = vm.callController,
                onAccept = {
                    when {
                        hasMic() -> vm.acceptCall()
                        else -> {
                            pendingCallAction = "accept"
                            // Камеру тоже запросим, если видео — но отказ по камере не блокирует accept.
                            if (callState.isVideo) {
                                callPermissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.CAMERA,
                                    ),
                                )
                            } else {
                                callPermissionsLauncher.launch(
                                    arrayOf(Manifest.permission.RECORD_AUDIO),
                                )
                            }
                        }
                    }
                },
                onReject = { vm.rejectCall() },
                onCancel = { vm.cancelCall() },
                onHangup = { vm.hangupCall() },
                onToggleMute = { vm.toggleCallMute() },
                onCycleAudioRoute = { vm.cycleCallAudioRoute() },
                onToggleCamera = {
                    if (callState.cameraEnabled || hasCam()) {
                        vm.toggleCallCamera()
                    } else {
                        pendingCallAction = "toggle_camera"
                        callPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    }
                },
                onUpgradeToVideo = {
                    if (hasCam()) {
                        vm.upgradeCallToVideo()
                    } else {
                        pendingCallAction = "upgrade_video"
                        callPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    }
                },
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
