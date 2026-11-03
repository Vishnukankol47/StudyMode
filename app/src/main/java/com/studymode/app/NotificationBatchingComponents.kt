package com.studymode.app

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun NotificationBatchingSettingsCard(
    store: LocalStudyStore,
    onSettingsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(NotificationBatchingService.isNotificationAccessGranted(context))
    }
    var enabled by remember { mutableStateOf(store.isNotificationBatchingEnabled()) }
    var focusOnly by remember { mutableStateOf(store.isNotificationBatchingFocusOnly()) }
    var deliverOnEnd by remember { mutableStateOf(store.isDeliverDigestOnFocusEnd()) }
    val batchedCount by remember { mutableStateOf(store.getBatchedNotificationsCount()) }

    var showInboxDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1713)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2922)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header with icon and switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF162E20)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bell),
                            contentDescription = "Notification Batching",
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Notification Silencer & Digest",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (enabled) "Batching active during study" else "Notifications deliver normally",
                            color = Color(0xFF8BA393),
                            fontSize = 12.sp,
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked && !hasPermission) {
                            NotificationBatchingService.openNotificationAccessSettings(context)
                        }
                        enabled = isChecked
                        store.setNotificationBatchingEnabled(isChecked)
                        onSettingsChanged()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0D1F15),
                        checkedTrackColor = Color(0xFF4ADE80),
                        uncheckedThumbColor = Color(0xFF6B7280),
                        uncheckedTrackColor = Color(0xFF1F2937),
                    ),
                )
            }

            // Permission Warning Banner if enabled but permission missing
            if (enabled && !hasPermission) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF332010),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Access Required",
                                color = Color(0xFFFBBF24),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Android requires notification listener access to intercept and silence alerts.",
                                color = Color(0xFFE5E7EB),
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                NotificationBatchingService.openNotificationAccessSettings(context)
                                hasPermission = NotificationBatchingService.isNotificationAccessGranted(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Grant", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1A261E), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Option: Focus Only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Batch only during focus sessions",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "When turned off, non-essential notifications are batched all day.",
                            color = Color(0xFF7E9A88),
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = focusOnly,
                        onCheckedChange = {
                            focusOnly = it
                            store.setNotificationBatchingFocusOnly(it)
                            onSettingsChanged()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0D1F15),
                            checkedTrackColor = Color(0xFF4ADE80),
                            uncheckedThumbColor = Color(0xFF6B7280),
                            uncheckedTrackColor = Color(0xFF1F2937),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option: Deliver on session complete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Deliver digest on session completion",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Alerts you with a summarized digest as soon as your timer completes.",
                            color = Color(0xFF7E9A88),
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = deliverOnEnd,
                        onCheckedChange = {
                            deliverOnEnd = it
                            store.setDeliverDigestOnFocusEnd(it)
                            onSettingsChanged()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0D1F15),
                            checkedTrackColor = Color(0xFF4ADE80),
                            uncheckedThumbColor = Color(0xFF6B7280),
                            uncheckedTrackColor = Color(0xFF1F2937),
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Batched Notifications Inbox Button
            Button(
                onClick = { showInboxDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132A1E)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bell),
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (batchedCount > 0) "Open Batched Inbox ($batchedCount waiting)" else "Open Batched Inbox",
                        color = Color(0xFF4ADE80),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (showInboxDialog) {
        NotificationDigestInboxDialog(
            store = store,
            onDismiss = {
                showInboxDialog = false
                onSettingsChanged()
            },
        )
    }
}

@Composable
fun NotificationDigestInboxDialog(
    store: LocalStudyStore,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(store.getBatchedNotifications()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0B140F),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3827)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Batched Inbox",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${items.size}",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Text(
                            text = "Silenced alerts kept safe while you focused",
                            color = Color(0xFF7E9A88),
                            fontSize = 12.sp,
                        )
                    }

                    if (items.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                store.clearBatchedNotifications()
                                items = emptyList()
                            }
                        ) {
                            Text("Clear All", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF132A1E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_bell),
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Inbox is completely calm",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No distracting alerts held right now.",
                                color = Color(0xFF6B8775),
                                fontSize = 12.sp,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.id }) { notif ->
                            BatchedNotificationRow(
                                item = notif,
                                onOpenApp = {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(notif.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Done", color = Color(0xFF05170B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun BatchedNotificationRow(
    item: BatchedNotification,
    onOpenApp: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF121F17),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B3324)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppPackageIcon(
                        packageName = item.packageName,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.appName,
                        color = Color(0xFF4ADE80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                val timeAgo = DateUtils.getRelativeTimeSpanString(
                    item.timestampMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()

                Text(
                    text = timeAgo,
                    color = Color(0xFF7E9A88),
                    fontSize = 11.sp,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.text,
                    color = Color(0xFFB5C9BC),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "Open App →",
                    color = Color(0xFF4ADE80),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onOpenApp() }
                )
            }
        }
    }
}
