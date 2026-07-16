package com.example.monica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.data.AppNotification
import com.example.monica.data.ChatSummary
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.MonicaAppBar
import com.example.monica.ui.components.NeonInviteBorder
import com.example.monica.ui.components.UserAvatar
import com.example.monica.ui.util.TimeFormat
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    vm: MonicaViewModel,
    onOpenChat: (String) -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val darkTheme by vm.darkTheme.collectAsStateWithLifecycle()
    val onlineIds by vm.onlineIds.collectAsStateWithLifecycle()
    val inviteBanner by vm.inviteBanner.collectAsStateWithLifecycle()
    val incomingInvites by vm.incomingInvitesByChat.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val unread = notifications.count { !it.isRead }

    LaunchedEffect(Unit) {
        vm.refreshChats()
        vm.refreshNotifications()
    }

    LaunchedEffect(query) {
        delay(250)
        vm.searchUsers(query)
    }

    Scaffold(
        topBar = {
            MonicaAppBar(
                centerTitle = true,
                title = {
                    Text(
                        "Monica",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.size(40.dp),
                    ) {
                        BadgedBox(badge = {
                            if (unread > 0) {
                                Badge {
                                    Text("${minOf(unread, 9)}${if (unread > 9) "+" else ""}")
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Уведомления")
                        }
                    }
                    IconButton(
                        onClick = { vm.toggleTheme() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Тема",
                        )
                    }
                    IconButton(
                        onClick = { vm.logout() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Выйти")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
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

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Поиск пользователей…") },
            )
            Spacer(Modifier.height(8.dp))

            if (searchResults.isNotEmpty()) {
                Text("Результаты", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                searchResults.forEach { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.startChatWith(user.id) { chatId ->
                                    query = ""
                                    onOpenChat(chatId)
                                }
                            }
                            .padding(vertical = 8.dp),
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
                            Text("@${user.nickname}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${user.firstName} ${user.lastName}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    val hasInvite = incomingInvites.containsKey(chat.id)
                    ChatRow(
                        chat = chat,
                        isOnline = onlineIds.contains(chat.partner?.id),
                        hasPrivateInvite = hasInvite,
                        onClick = { onOpenChat(chat.id) },
                        onAcceptInvite = { vm.acceptInviteForChat(chat.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    isOnline: Boolean,
    hasPrivateInvite: Boolean,
    onClick: () -> Unit,
    onAcceptInvite: () -> Unit,
) {
    val preview = when {
        hasPrivateInvite -> "Приглашение в приватный чат"
        chat.lastMessage == null -> "Нет сообщений"
        chat.lastMessage.messageType == "photo" -> "Фото"
        chat.lastMessage.messageType == "file" -> {
            val name = chat.lastMessage.fileName.orEmpty()
            when {
                name.endsWith(".py") -> "Python: $name"
                name.endsWith(".js") -> "JS: $name"
                else -> "Файл: ${name.ifBlank { "вложение" }}"
            }
        }
        else -> chat.lastMessage.content.ifBlank { "Сообщение" }
    }
    val time = TimeFormat.chatListTime(chat.lastMessage?.sentAt ?: chat.updatedAt)

    NeonInviteBorder(
        enabled = hasPrivateInvite,
        modifier = Modifier.fillMaxWidth(),
        corner = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(chat.partner, size = 42.dp, showOnline = true, isOnline = isOnline)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@${chat.partner?.nickname ?: "—"}",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (time.isNotBlank()) {
                        Text(
                            time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasPrivateInvite) {
                        Color(0xFFFF5252)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasPrivateInvite) {
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onAcceptInvite,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "Принять приватный чат")
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = notification.title,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
            fontWeight = FontWeight.SemiBold,
        )
        if (notification.body.isNotBlank()) {
            Text(
                text = notification.body,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
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
