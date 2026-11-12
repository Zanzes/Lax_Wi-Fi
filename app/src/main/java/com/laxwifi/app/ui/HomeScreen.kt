@file:OptIn(ExperimentalMaterial3Api::class)

package com.laxwifi.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laxwifi.app.MonitorService
import com.laxwifi.app.MonitorState
import com.laxwifi.app.Repo
import com.laxwifi.app.SavedNetwork
import com.laxwifi.app.WifiAction
import com.laxwifi.app.bestMatch
import com.laxwifi.app.currentWifiFingerprint

@Composable
fun HomeScreen(onEdit: (String) -> Unit) {
    val networks by Repo.networks.collectAsStateWithLifecycle()
    val status by MonitorState.status.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lax Wi-Fi", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add current network") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    text = when {
                        !status.onWifi -> "Not on Wi-Fi"
                        else -> networks.firstOrNull { it.id == status.matchedId }
                            ?.let { "Connected to ${it.name}" }
                            ?: "On an unsaved Wi-Fi network"
                    },
                    highlighted = status.matchedId != null,
                )
            }
            item { PermissionsCard() }
            if (networks.isEmpty()) {
                item {
                    Text(
                        "No networks yet. Connect to a Wi-Fi network, then tap \"Add current network\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                item {
                    Text(
                        "Networks",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(networks, key = { it.id }) { network ->
                    NetworkCard(
                        network = network,
                        connected = network.id == status.matchedId,
                        onClick = { onEdit(network.id) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddNetworkDialog(
            existing = networks,
            onDismiss = { showAdd = false },
            onAdded = { id ->
                showAdd = false
                onEdit(id)
            },
        )
    }
}

@Composable
private fun StatusCard(text: String, highlighted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) scheme.primaryContainer else scheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, if (highlighted) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Right now", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                color = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface,
            )
        }
    }
}

@Composable
private fun NetworkCard(network: SavedNetwork, connected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        border = BorderStroke(1.dp, if (connected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    network.name.ifBlank { "Unnamed network" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (connected) {
                    Text("Connected", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "On connect: ${summary(network.onConnect)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                "On disconnect: ${summary(network.onDisconnect)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private fun summary(actions: List<WifiAction>): String =
    if (actions.isEmpty()) "nothing" else actions.joinToString(", ") { it.summary }

// ---- Permissions ----

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun dndAllowed(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

private fun batteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private fun open(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val notifications = remember(tick) { notificationsAllowed(context) }
    val dnd = remember(tick) { dndAllowed(context) }
    val battery = remember(tick) { batteryUnrestricted(context) }
    if (notifications && dnd && battery) return

    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            if (!notifications) {
                SetupRow("Notifications", "Needed for notification actions") {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (!dnd) {
                SetupRow("Do Not Disturb access", "Needed to switch to Silent or back") {
                    open(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                }
            }
            if (!battery) {
                SetupRow("Unrestricted battery", "Keeps monitoring alive in the background") {
                    open(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClick) { Text("Allow") }
    }
}

// ---- Add network ----

@Composable
private fun AddNetworkDialog(
    existing: List<SavedNetwork>,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit,
) {
    val context = LocalContext.current
    val fingerprint = remember { currentWifiFingerprint(context) }
    var name by remember { mutableStateOf("") }

    if (fingerprint == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Not on Wi-Fi") },
            text = { Text("Connect to the Wi-Fi network you want to add, then try again.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }

    val duplicate = remember { existing.bestMatch(fingerprint) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add current network") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Home, Office…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Lax Wi-Fi never uses Location, so it can't read the network's name. " +
                        "It recognises this network by its router instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (duplicate != null) {
                    Text(
                        "This looks the same as \"${duplicate.name}\", which is already saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val network = SavedNetwork(name = name.trim(), fingerprint = fingerprint)
                    Repo.upsert(network)
                    MonitorService.start(context, reevaluate = true)
                    onAdded(network.id)
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
