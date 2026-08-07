package com.example.monica.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.media.ActiveMediaSessionRepository
import com.example.monica.ui.components.MonicaAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = remember(context) { ActiveMediaSessionRepository.get(context) }
    val musicDisplay by repo.musicDisplayEnabled.collectAsStateWithLifecycle()
    val listenerEnabled by repo.listenerEnabled.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner, repo) {
        repo.refreshListenerAccess()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repo.refreshListenerAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppDetails() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openNotificationAccess() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun onMusicDisplayChange(enabled: Boolean) {
        val hasAccess = repo.setMusicDisplayEnabled(enabled)
        if (enabled && !hasAccess) {
            // Сначала ограниченные настройки приложения, затем доступ к уведомлениям.
            openAppDetails()
        }
    }

    Scaffold(
        topBar = {
            MonicaAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        "Настройки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "Медиа",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMusicDisplayChange(!musicDisplay) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Отображать музыку",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            !musicDisplay ->
                                "Панель текущего трека скрыта. Включите, чтобы показывать музыку из других приложений."
                            musicDisplay && !listenerEnabled ->
                                "Нужен доступ к уведомлениям. Если система блокирует — в карточке Monica нажмите ⋮ → «Разрешить ограниченные настройки», затем включите Monica в доступе к уведомлениям."
                            else ->
                                "Показывать текущий трек над списком чатов."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = musicDisplay,
                    onCheckedChange = { onMusicDisplayChange(it) },
                )
            }

            if (musicDisplay && !listenerEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = ::openAppDetails) {
                        Text("О приложении")
                    }
                    TextButton(onClick = ::openNotificationAccess) {
                        Text("Доступ к уведомлениям")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}
