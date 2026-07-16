package com.example.monica.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MonicaDrawerContent(
    nickname: String?,
    darkTheme: Boolean,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onNotifications: () -> Unit,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    "Monica",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (!nickname.isNullOrBlank()) "@$nickname" else "Меню",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                label = { Text("Профиль") },
                selected = false,
                onClick = onProfile,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                label = { Text("Настройки") },
                selected = false,
                onClick = onSettings,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                label = { Text("Уведомления") },
                selected = false,
                onClick = onNotifications,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                label = { Text("Выйти") },
                selected = false,
                onClick = onLogout,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(onClick = onToggleTheme)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = "Сменить тему",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        if (darkTheme) "Светлая тема" else "Тёмная тема",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Сохраняется на устройстве",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
