package com.example.monica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MessageSelectionToolbar(
    count: Int,
    onClose: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
            }
            Text(
                text = "$count ${if (count == 1) "сообщение" else "сообщений"}",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                fontSize = 13.sp,
            )
            CompactToolbarAction(
                label = "Ответить",
                enabled = count == 1,
                onClick = onReply,
                icon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                },
            )
            CompactToolbarAction(
                label = "Переслать",
                enabled = count > 0,
                onClick = onForward,
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                },
            )
            IconButton(onClick = {}, enabled = false) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "Ещё")
            }
        }
    }
}

@Composable
private fun CompactToolbarAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
        modifier = Modifier.widthIn(min = 58.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(label, fontSize = 10.sp, maxLines = 1)
        }
    }
}
