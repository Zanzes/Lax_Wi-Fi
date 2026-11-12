@file:OptIn(ExperimentalMaterial3Api::class)

package com.laxwifi.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laxwifi.app.ActionRunner
import com.laxwifi.app.Fingerprint
import com.laxwifi.app.MonitorService
import com.laxwifi.app.Repo
import com.laxwifi.app.RingerMode
import com.laxwifi.app.SavedNetwork
import com.laxwifi.app.WifiAction
import com.laxwifi.app.currentWifiFingerprint

@Composable
fun EditScreen(networkId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val networks by Repo.networks.collectAsStateWithLifecycle()
    val network = networks.firstOrNull { it.id == networkId }
    if (network == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var name by remember(networkId) { mutableStateOf(network.name) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(network.name.ifBlank { "Network" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete network")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    Repo.upsert(network.copy(name = it))
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ActionSection(
                title = "On connect",
                actions = network.onConnect,
                onChange = { Repo.upsert(network.copy(onConnect = it)) },
                onTest = { ActionRunner.run(context, network, network.onConnect) },
            )
            ActionSection(
                title = "On disconnect",
                actions = network.onDisconnect,
                onChange = { Repo.upsert(network.copy(onDisconnect = it)) },
                onTest = { ActionRunner.run(context, network, network.onDisconnect) },
            )

            FingerprintCard(
                fingerprint = network.fingerprint,
                onRecapture = {
                    val current = currentWifiFingerprint(context)
                    if (current == null) {
                        Toast.makeText(context, "Not connected to Wi-Fi", Toast.LENGTH_SHORT).show()
                    } else {
                        Repo.upsert(network.copy(fingerprint = current))
                        MonitorService.start(context, reevaluate = true)
                        Toast.makeText(context, "Updated from current Wi-Fi", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${network.name.ifBlank { "this network" }}?") },
            text = { Text("Its actions will no longer run.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.delete(network.id)
                    MonitorService.start(context, reevaluate = true)
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ActionSection(
    title: String,
    actions: List<WifiAction>,
    onChange: (List<WifiAction>) -> Unit,
    onTest: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (actions.isNotEmpty()) {
                TextButton(onClick = onTest) { Text("Test") }
            }
        }

        if (actions.isEmpty()) {
            Text(
                "Nothing happens yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        actions.forEachIndexed { index, action ->
            key(action.id) {
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActionRow(
                    action = action,
                    onUpdate = { updated -> onChange(actions.map { if (it.id == updated.id) updated else it }) },
                    onRemove = { onChange(actions.filterNot { it.id == action.id }) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add action")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Set ringer mode") },
                    onClick = {
                        menuOpen = false
                        onChange(actions + WifiAction.SetRinger())
                    },
                )
                DropdownMenuItem(
                    text = { Text("Show notification") },
                    onClick = {
                        menuOpen = false
                        onChange(actions + WifiAction.Notify())
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    action: WifiAction,
    onUpdate: (WifiAction) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            when (action) {
                is WifiAction.SetRinger -> {
                    Text("Ringer mode", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (mode in RingerMode.entries) {
                            FilterChip(
                                selected = action.mode == mode,
                                onClick = { onUpdate(action.copy(mode = mode)) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }

                is WifiAction.Notify -> {
                    var text by remember(action.id) { mutableStateOf(action.text) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onUpdate(action.copy(text = it))
                        },
                        label = { Text("Notification text") },
                        supportingText = { Text("{network} is replaced with the network name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove action",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FingerprintCard(fingerprint: Fingerprint, onRecapture: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SectionCard {
        Text("How this network is recognised", style = MaterialTheme.typography.titleMedium, color = scheme.primary)
        Spacer(Modifier.height(8.dp))
        FingerprintLine("Router", fingerprint.gateway4)
        FingerprintLine("Router (IPv6)", fingerprint.gateway6)
        FingerprintLine("Subnet", fingerprint.subnet)
        FingerprintLine("DNS", fingerprint.dns.joinToString(", "))
        Spacer(Modifier.height(8.dp))
        Text(
            if (fingerprint.isStrong) {
                "Strong match: this router has its own IPv6 address."
            } else {
                "Weak match: another network with the same router address and subnet would also trigger these actions."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (fingerprint.isStrong) scheme.onSurfaceVariant else scheme.error,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRecapture) { Text("Update from current Wi-Fi") }
    }
}

@Composable
private fun FingerprintLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
    }
}
