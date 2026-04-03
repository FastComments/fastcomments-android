package com.fastcomments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fastcomments.ui.theme.FastcommentsexamplesimpleTheme

private data class DemoItem(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val description: String,
    val activityClass: Class<out Activity>
)

private data class DemoSection(
    val title: String,
    val items: List<DemoItem>
)

private val demoSections = listOf(
    DemoSection(
        title = "Comments",
        items = listOf(
            DemoItem(
                icon = Icons.Filled.Forum,
                iconColor = Color(0xFF2196F3),
                title = "Threaded Comments",
                description = "Live threaded commenting with SSO.",
                activityClass = MainActivity::class.java
            ),
            DemoItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                iconColor = Color(0xFF4CAF50),
                title = "Live Chat",
                description = "A streaming chat UI optimized for high volume discussions.",
                activityClass = LiveChatExampleActivity::class.java
            ),
            DemoItem(
                icon = Icons.Filled.Edit,
                iconColor = Color(0xFF3F51B5),
                title = "Custom Toolbar",
                description = "Global and per-instance custom toolbar buttons",
                activityClass = ToolbarShowcaseActivity::class.java
            )
        )
    ),
    DemoSection(
        title = "Feed",
        items = listOf(
            DemoItem(
                icon = Icons.AutoMirrored.Filled.Article,
                iconColor = Color(0xFFFF9800),
                title = "Social Feed",
                description = "Social Feed with SSO Configured",
                activityClass = FeedExampleActivity::class.java
            ),
            DemoItem(
                icon = Icons.Filled.GridView,
                iconColor = Color(0xFF9C27B0),
                title = "Feed Custom Buttons",
                description = "Custom toolbar buttons on the post creation form",
                activityClass = FeedExampleCustomButtonsActivity::class.java
            )
        )
    ),
    DemoSection(
        title = "Authentication",
        items = listOf(
            DemoItem(
                icon = Icons.Filled.Person,
                iconColor = Color(0xFF009688),
                title = "Simple SSO",
                description = "Client-side SSO for demos and testing",
                activityClass = SimpleSSOExampleActivity::class.java
            ),
            DemoItem(
                icon = Icons.Filled.Lock,
                iconColor = Color(0xFFF44336),
                title = "Secure SSO",
                description = "Production SSO with server-side token generation",
                activityClass = SecureSSOExampleActivity::class.java
            )
        )
    )
)

class DemoBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FastcommentsexamplesimpleTheme {
                DemoBrowserScreen(
                    onDemoSelected = { item ->
                        startActivity(Intent(this, item.activityClass))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoBrowserScreen(onDemoSelected: (DemoItem) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FastComments") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            demoSections.forEach { section ->
                item {
                    Text(
                        text = section.title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }
                items(section.items) { demoItem ->
                    DemoItemRow(
                        item = demoItem,
                        onClick = { onDemoSelected(demoItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoItemRow(item: DemoItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(item.iconColor, item.iconColor.copy(alpha = 0.8f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
