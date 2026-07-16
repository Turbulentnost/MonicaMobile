package com.example.monica.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.ui.MonicaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    sessionId: String,
    partnerNickname: String?,
    vm: MonicaViewModel,
    onClose: () -> Unit,
) {
    val myText by vm.myPrivateText.collectAsStateWithLifecycle()
    val peerText by vm.peerPrivateText.collectAsStateWithLifecycle()
    val connected by vm.privateConnected.collectAsStateWithLifecycle()
    val currentId by vm.privateSessionId.collectAsStateWithLifecycle()

    DisposableEffect(sessionId) {
        if (currentId != sessionId) {
            // chatId уже в стеке навигации / ViewModel — только дотягиваем WS
            vm.openPrivate(sessionId, null)
        }
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        vm.closePrivate()
                        onClose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column {
                        Text("Приватный чат", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (connected) "в реальном времени · @${partnerNickname ?: "—"}"
                            else "подключение… · @${partnerNickname ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        vm.closePrivate()
                        onClose()
                    }) {
                        Text("Закрыть")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("Вы пишете", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = myText,
                onValueChange = { vm.updateMyPrivateText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("Просто пишите — собеседник видит текст сразу…") },
            )
            Spacer(Modifier.height(16.dp))
            Text("@${partnerNickname ?: "Собеседник"} пишет", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = peerText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("Здесь появляется текст собеседника…") },
            )
        }
    }
}
