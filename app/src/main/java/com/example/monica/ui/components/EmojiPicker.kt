package com.example.monica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class EmojiCategory(
    val id: String,
    val icon: String,
    val emojis: List<String>,
)

private val emojiCategories = listOf(
    EmojiCategory(
        "smileys", "😀",
        listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "🥰", "😍", "🤩", "😘",
            "😋", "😜", "🤪", "🤗", "🤭", "🤫", "🤔", "😐",
            "🙄", "😴", "🥳", "😎", "🤓", "🥺", "😢", "😭",
            "😤", "😡", "🤬", "😈", "💀", "💩", "🤡", "🤯",
        ),
    ),
    EmojiCategory(
        "people", "👋",
        listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "✌️",
            "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇",
            "👍", "👎", "✊", "👊", "👏", "🙌", "🤝", "🙏",
            "💪", "👀", "💋", "🙋", "🤦", "🤷", "👨", "👩",
        ),
    ),
    EmojiCategory(
        "animals", "🐻",
        listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈",
            "🙉", "🙊", "🐔", "🐧", "🐦", "🦄", "🐝", "🦋",
            "🐢", "🐍", "🐙", "🐠", "🐬", "🐳", "🦈", "🐘",
        ),
    ),
    EmojiCategory(
        "food", "🍕",
        listOf(
            "🍏", "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓",
            "🍒", "🍑", "🥭", "🍍", "🥝", "🍅", "🥑", "🥕",
            "🍞", "🧀", "🍳", "🥞", "🥓", "🍗", "🍔", "🍟",
            "🍕", "🌮", "🍣", "🍜", "🍦", "🎂", "🍫", "☕",
        ),
    ),
    EmojiCategory(
        "activities", "⚽",
        listOf(
            "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🎱", "🏓",
            "🏸", "🏒", "⛳", "🏹", "🎣", "🥊", "🛹", "🎿",
            "🏆", "🥇", "🎭", "🎨", "🎬", "🎤", "🎧", "🎹",
            "🎸", "🎲", "🎯", "🎮", "🎉", "🎊", "🎈", "🎁",
        ),
    ),
    EmojiCategory(
        "symbols", "❤️",
        listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "💔", "💕", "💞", "💓", "💖", "💘", "💯",
            "✅", "❌", "⭕", "❗", "❓", "⚠️", "♻️", "✨",
            "⭐", "🔥", "💥", "💫", "🎵", "🔔", "💬", "💤",
        ),
    ),
)

@Composable
fun EmojiPicker(
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeId by remember { mutableStateOf(emojiCategories.first().id) }
    val active = emojiCategories.first { it.id == activeId }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                items(emojiCategories, key = { it.id }) { category ->
                    Text(
                        text = category.icon,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (category.id == activeId) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .clickable { activeId = category.id }
                            .padding(7.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(active.emojis, key = { it }) { emoji ->
                    Text(
                        text = emoji,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(emoji) }
                            .padding(6.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
