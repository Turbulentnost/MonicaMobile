package com.example.monica.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.monica.data.ChatSummary
import com.example.monica.data.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardPickerSheet(
    chats: List<ChatSummary>,
    searchResults: List<UserProfile>,
    query: String,
    busy: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectChat: (String) -> Unit,
    onSelectUser: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Переслать в…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                placeholder = { Text("Поиск пользователя") },
                singleLine = true,
                enabled = !busy,
            )
            val people = if (query.trim().length >= 2) searchResults else emptyList()
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                if (people.isNotEmpty()) {
                    item {
                        Text(
                            "Пользователи",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(people, key = { "user-${it.id}" }) { user ->
                        ForwardTargetRow(
                            user = user,
                            enabled = !busy,
                            onClick = { onSelectUser(user.id) },
                        )
                    }
                } else if (query.trim().length < 2) {
                    items(chats, key = { "chat-${it.id}" }) { chat ->
                        val partner = chat.partner ?: return@items
                        ForwardTargetRow(
                            user = partner,
                            enabled = !busy,
                            onClick = { onSelectChat(chat.id) },
                        )
                    }
                } else {
                    item {
                        Text(
                            "Ничего не найдено",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardTargetRow(
    user: UserProfile,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatar(user, size = 42.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, fontWeight = FontWeight.SemiBold)
            if (user.nickname.isNotBlank()) {
                Text(
                    "@${user.nickname}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}
