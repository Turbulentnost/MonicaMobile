package com.example.monica.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.R
import com.example.monica.data.AppNotification
import com.example.monica.data.ChatSummary
import com.example.monica.data.isVideoMime
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.AppIcon
import com.example.monica.ui.components.MainMenuIcon
import com.example.monica.ui.components.MonicaDrawerContent
import com.example.monica.ui.components.NeonInviteBorder
import com.example.monica.ui.components.NowPlayingStripHost
import com.example.monica.ui.components.UserAvatar
import com.example.monica.ui.util.TimeFormat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ChatGlassFill = Color(0x28FFFFFF)
private val ChatGlassBorder = Color(0x55E8D5B0)
private val ChatGlassDivider = Color(0x66E8D5B0)
private val ChatListGlassShape = RoundedCornerShape(18.dp)
private val ChatGlassStyle = HazeStyle(
    backgroundColor = Color(0xFF121018),
    tint = HazeTint(Color.White.copy(alpha = 0.22f)),
    blurRadius = 26.dp,
    noiseFactor = 0.08f,
    fallbackTint = HazeTint(Color.Black.copy(alpha = 0.48f)),
)
private val ChatHeaderGlassStyle = HazeStyle(
    backgroundColor = Color(0xFF121018),
    tint = HazeTint(Color.Black.copy(alpha = 0.28f)),
    blurRadius = 22.dp,
    noiseFactor = 0.06f,
    fallbackTint = HazeTint(Color.Black.copy(alpha = 0.55f)),
)
private val LocalChatListHaze = staticCompositionLocalOf<HazeState> {
    error("Chat list HazeState missing")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    vm: MonicaViewModel,
    onOpenChat: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val darkTheme by vm.darkTheme.collectAsStateWithLifecycle()
    val onlineIds by vm.onlineIds.collectAsStateWithLifecycle()
    val inviteBanner by vm.inviteBanner.collectAsStateWithLifecycle()
    val incomingInvites by vm.incomingInvitesByChat.collectAsStateWithLifecycle()
    val callState by vm.callState.collectAsStateWithLifecycle()
    val appUpdate by vm.appUpdate.collectAsStateWithLifecycle()
    val updateDownloadProgress by vm.updateDownloadProgress.collectAsStateWithLifecycle()
    val ringingChatId = callState.ringingChatId

    var query by remember { mutableStateOf("") }
    val unread = notifications.count { !it.isRead }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var menuPlay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshChats()
        vm.refreshNotifications()
    }

    LaunchedEffect(query) {
        delay(250)
        vm.searchUsers(query)
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) {
            menuPlay = false
        }
    }

    fun openDrawer() {
        menuPlay = true
        scope.launch { drawerState.open() }
    }

    fun closeDrawerAnd(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            menuPlay = false
            action()
        }
    }

    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.chat_list_bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentScale = ContentScale.Crop,
        )

        CompositionLocalProvider(LocalChatListHaze provides hazeState) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    MonicaDrawerContent(
                        nickname = vm.session.nickname,
                        darkTheme = darkTheme,
                        updateVersionName = appUpdate?.versionName,
                        updateDownloading = updateDownloadProgress != null,
                        onProfile = { closeDrawerAnd(onOpenProfile) },
                        onSettings = { closeDrawerAnd(onOpenSettings) },
                        onNotifications = { closeDrawerAnd(onOpenNotifications) },
                        onUpdate = appUpdate?.let {
                            {
                                closeDrawerAnd { vm.startUpdateDownload() }
                            }
                        },
                        onToggleTheme = { vm.toggleTheme() },
                        onLogout = {
                            scope.launch {
                                drawerState.close()
                                menuPlay = false
                                vm.logout()
                            }
                        },
                    )
                },
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ChatListHeader(
                            vm = vm,
                            query = query,
                            onQueryChange = { query = it },
                            unread = unread,
                            menuPlay = menuPlay,
                            onMenuClick = { openDrawer() },
                            onNotifications = onOpenNotifications,
                            onCreateGroup = onCreateGroup,
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        inviteBanner?.let { banner ->
                            InviteBanner(
                                notification = banner,
                                onAccept = { vm.acceptInvite(banner) },
                                onDecline = { vm.declineInvite(banner) },
                                onDismiss = { vm.dismissInviteBanner() },
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        if (searchResults.isNotEmpty()) {
                            GlassPanel(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "Результаты",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                    searchResults.forEachIndexed { index, user ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = ChatGlassDivider,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    vm.startChatWith(user.id) { chatId ->
                                                        query = ""
                                                        onOpenChat(chatId)
                                                    }
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            UserAvatar(
                                                user = user,
                                                size = 40.dp,
                                                showOnline = true,
                                                isOnline = onlineIds.contains(user.id),
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    user.displayName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                )
                                                if (user.nickname.isNotBlank()) {
                                                    Text(
                                                        "@${user.nickname}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White.copy(alpha = 0.72f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        ) {
                            itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                        thickness = 0.5.dp,
                                        color = ChatGlassDivider,
                                    )
                                }
                                val hasInvite = incomingInvites.containsKey(chat.id)
                                ChatRow(
                                    chat = chat,
                                    isOnline = !chat.isGroup && onlineIds.contains(chat.partner?.id),
                                    hasPrivateInvite = !chat.isGroup && hasInvite,
                                    isRinging = ringingChatId == chat.id,
                                    isVideoRinging = ringingChatId == chat.id && callState.isVideo,
                                    isUnread = chat.isUnreadFor(vm.session.userId) &&
                                        ringingChatId != chat.id,
                                    onClick = { onOpenChat(chat.id) },
                                    onAcceptInvite = { vm.acceptInviteForChat(chat.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hazeState = LocalChatListHaze.current
    Box(
        modifier = modifier
            .clip(ChatListGlassShape)
            .hazeEffect(state = hazeState, style = ChatGlassStyle)
            .border(1.dp, ChatGlassBorder, ChatListGlassShape),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListHeader(
    vm: MonicaViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    unread: Int,
    menuPlay: Boolean,
    onMenuClick: () -> Unit,
    onNotifications: () -> Unit,
    onCreateGroup: () -> Unit,
) {
    var createMenuOpen by remember { mutableStateOf(false) }
    val hazeState = LocalChatListHaze.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = ChatHeaderGlassStyle),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(44.dp),
                ) {
                    MainMenuIcon(
                        play = menuPlay,
                        size = 26.dp,
                        color = Color.White,
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Поиск…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White.copy(alpha = 0.85f),
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedContainerColor = ChatGlassFill,
                        unfocusedContainerColor = ChatGlassFill,
                        disabledContainerColor = ChatGlassFill,
                        focusedBorderColor = ChatGlassBorder,
                        unfocusedBorderColor = ChatGlassBorder.copy(alpha = 0.55f),
                    ),
                )

                Box {
                    IconButton(
                        onClick = { createMenuOpen = true },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "Создать",
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = createMenuOpen,
                        onDismissRequest = { createMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Создать группу") },
                            leadingIcon = {
                                Icon(Icons.Outlined.GroupAdd, contentDescription = null)
                            },
                            onClick = {
                                createMenuOpen = false
                                onCreateGroup()
                            },
                        )
                    }
                }

                IconButton(
                    onClick = onNotifications,
                    modifier = Modifier.size(44.dp),
                ) {
                    BadgedBox(
                        badge = {
                            if (unread > 0) {
                                Badge {
                                    Text("${minOf(unread, 9)}${if (unread > 9) "+" else ""}")
                                }
                            }
                        },
                    ) {
                        AppIcon(
                            resId = R.drawable.ic_bell,
                            contentDescription = "Уведомления",
                            size = 32.dp,
                            tint = Color.White,
                        )
                    }
                }
            }
            NowPlayingStripHost(vm = vm)
            HorizontalDivider(
                thickness = 0.5.dp,
                color = ChatGlassDivider,
            )
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    isOnline: Boolean,
    hasPrivateInvite: Boolean,
    isRinging: Boolean,
    isVideoRinging: Boolean,
    isUnread: Boolean,
    onClick: () -> Unit,
    onAcceptInvite: () -> Unit,
) {
    val preview = when {
        isRinging -> if (isVideoRinging) "Входящий видеозвонок…" else "Входящий аудиозвонок…"
        hasPrivateInvite -> "Приглашение в приватный чат"
        chat.lastMessage == null -> "Нет сообщений"
        chat.lastMessage.messageType == "photo" -> "Фото"
        chat.lastMessage.messageType == "voice" -> "Голосовое сообщение"
        chat.lastMessage.messageType == "call" -> {
            chat.lastMessage.content.ifBlank { "Звонок" }
        }
        chat.lastMessage.messageType == "file" -> {
            val name = chat.lastMessage.fileName.orEmpty()
            when {
                isVideoMime(chat.lastMessage.mimeType, name) -> "Видео: ${name.ifBlank { "клип" }}"
                name.endsWith(".py") -> "Python: $name"
                name.endsWith(".js") -> "JS: $name"
                else -> "Файл: ${name.ifBlank { "вложение" }}"
            }
        }
        else -> chat.lastMessage.content.ifBlank { "Сообщение" }
    }
    val time = TimeFormat.chatListTime(chat.lastMessage?.sentAt ?: chat.updatedAt)
    val subtitle = if (chat.isGroup && chat.membersCount > 0) {
        "${chat.membersCount} уч. · $preview"
    } else {
        preview
    }

    NeonInviteBorder(
        enabled = hasPrivateInvite,
        modifier = Modifier.fillMaxWidth(),
        corner = 18.dp,
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    chat.avatarUser(),
                    size = 42.dp,
                    showOnline = !chat.isGroup,
                    isOnline = isOnline,
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = chat.displayTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (time.isNotBlank()) {
                            Text(
                                time,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isRinging -> Color(0xFF81C784)
                            hasPrivateInvite -> Color(0xFFFF8A80)
                            else -> Color.White.copy(alpha = 0.72f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isUnread) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF7EB6FF), CircleShape),
                    )
                }
                if (hasPrivateInvite) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onAcceptInvite,
                        modifier = Modifier.size(40.dp),
                    ) {
                        AppIcon(
                            resId = R.drawable.ic_check_green,
                            contentDescription = "Принять приватный чат",
                            size = 32.dp,
                            tint = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteBanner(
    notification: AppNotification,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = notification.title,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        if (notification.body.isNotBlank()) {
            Text(
                text = notification.body,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onAccept) { Text("Принять") }
            TextButton(onClick = onDecline) { Text("Отклонить") }
            TextButton(onClick = onDismiss) { Text("Скрыть") }
        }
    }
}
