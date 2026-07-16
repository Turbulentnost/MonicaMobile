package com.example.monica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.R
import com.example.monica.data.AppNotification
import com.example.monica.data.isPendingPrivateInvite
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.AppIcon
import com.example.monica.ui.components.MonicaAppBar
import com.example.monica.ui.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    vm: MonicaViewModel,
    onBack: () -> Unit,
) {
    val notifications by vm.notifications.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.refreshNotifications()
    }

    Scaffold(
        topBar = {
            MonicaAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        "Уведомления",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = { vm.markAllRead() }) { Text("Прочитать") }
                    TextButton(onClick = { vm.clearNotifications() }) { Text("Очистить") }
                },
            )
        },
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Нет уведомлений", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notifications, key = { it.id }) { n ->
                    NotificationRow(
                        notification = n,
                        onAccept = { vm.acceptInvite(n) },
                        onDecline = { vm.declineInvite(n) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val pendingInvite = notification.isPendingPrivateInvite()
    val time = TimeFormat.chatListTime(notification.createdAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                notification.title,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
            )
            if (notification.body.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (time.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (pendingInvite) {
            IconButton(
                onClick = onAccept,
                modifier = Modifier.size(40.dp),
            ) {
                AppIcon(
                    resId = R.drawable.ic_check_green,
                    contentDescription = "Принять",
                    size = 32.dp,
                    tint = null,
                )
            }
            Spacer(Modifier.size(8.dp))
            FilledIconButton(
                onClick = onDecline,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFC62828),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Отклонить")
            }
        }
    }
}
