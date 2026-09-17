package com.eetu.youtubeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eetu.youtubeapp.data.AdBlockManager
import com.eetu.youtubeapp.data.FilterListSubscription
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val adBlockManager = remember { AdBlockManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var isAdBlockEnabled by remember { mutableStateOf(adBlockManager.isAdBlockEnabled()) }
    var useBuiltInFilters by remember { mutableStateOf(adBlockManager.useBuiltInFilters()) }
    var autoUpdateOnLaunch by remember { mutableStateOf(adBlockManager.isAutoUpdateOnLaunch()) }
    var subscriptions by remember { mutableStateOf(adBlockManager.getSubscriptions()) }
    var lastUpdateTime by remember { mutableStateOf(adBlockManager.getLastUpdateTime()) }

    var isUpdatingAll by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    var showAddListDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<FilterListSubscription?>(null) }
    var listToDelete by remember { mutableStateOf<FilterListSubscription?>(null) }

    fun refreshSubscriptions() {
        subscriptions = adBlockManager.getSubscriptions()
        lastUpdateTime = adBlockManager.getLastUpdateTime()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ad Blocker Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // --- SECTION: GENERAL ---
            Text(
                text = "General",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Master Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(text = "Enable Ad Blocker", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Blocks video ads, promotional popups, and sponsored banners",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAdBlockEnabled,
                    onCheckedChange = {
                        isAdBlockEnabled = it
                        adBlockManager.setAdBlockEnabled(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Use Built-in Filters Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(text = "Use built-in filters", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Includes offline curated YouTube filters without needing network downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useBuiltInFilters,
                    onCheckedChange = {
                        useBuiltInFilters = it
                        adBlockManager.setUseBuiltInFilters(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-update on Launch Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(text = "Automatic updates on launch", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Automatically check and update enabled filter lists once daily when the app opens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoUpdateOnLaunch,
                    onCheckedChange = {
                        autoUpdateOnLaunch = it
                        adBlockManager.setAutoUpdateOnLaunch(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- SECTION: FILTER LISTS ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Lists",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FilledTonalButton(
                    onClick = { showAddListDialog = true }
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add List")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Last updated: $lastUpdateTime",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    if (!isUpdatingAll) {
                        isUpdatingAll = true
                        updateStatusMessage = "Downloading and updating filter lists..."
                        scope.launch {
                            val result = adBlockManager.updateAllFilters()
                            isUpdatingAll = false
                            if (result.isSuccess) {
                                refreshSubscriptions()
                                updateStatusMessage = "All filter lists updated successfully!"
                            } else {
                                refreshSubscriptions()
                                updateStatusMessage = "Update failed: ${result.exceptionOrNull()?.localizedMessage}"
                            }
                        }
                    }
                },
                enabled = !isUpdatingAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isUpdatingAll) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Updating...")
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update All Lists Now")
                }
            }

            if (updateStatusMessage != null) {
                Text(
                    text = updateStatusMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (updateStatusMessage?.startsWith("Update failed") == true) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subscriptions Cards
            subscriptions.forEach { sub ->
                OutlinedCard(
                    onClick = { listToEdit = sub },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = sub.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (sub.ruleCount > 0) {
                                        Text(
                                            text = "${sub.ruleCount} rules",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (sub.lastUpdate > 0L) {
                                        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(sub.lastUpdate))
                                        Text(
                                            text = if (sub.ruleCount > 0) " • Updated $formattedDate" else "Updated $formattedDate",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    } else {
                                        Text(
                                            text = if (sub.ruleCount > 0) " • Default" else "Not downloaded yet",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            Switch(
                                checked = sub.isEnabled,
                                onCheckedChange = { enabled ->
                                    adBlockManager.toggleSubscription(sub.id, enabled)
                                    refreshSubscriptions()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { listToEdit = sub }
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Edit List",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }

                            if (sub.isRemovable) {
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = { listToDelete = sub },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove List",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- DIALOG: ADD CUSTOM LIST ---
    if (showAddListDialog) {
        var customName by remember { mutableStateOf("") }
        var customUrl by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }
        var addError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showAddListDialog = false },
            title = { Text("Add Filter List") },
            text = {
                Column {
                    Text(
                        text = "Enter the URL of an AdBlock/uBlock Origin compatible filter list (plain text or JSON format).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("List Name (optional)") },
                        placeholder = { Text("e.g. My Custom Filters") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Filter List URL") },
                        placeholder = { Text("https://example.com/filters.txt") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = addError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedUrl = customUrl.trim()
                        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://"))) {
                            addError = "Please enter a valid HTTP or HTTPS URL"
                            return@Button
                        }
                        isSubmitting = true
                        val newSub = adBlockManager.addCustomSubscription(customName, trimmedUrl)
                        refreshSubscriptions()
                        scope.launch {
                            adBlockManager.updateSubscription(newSub.id)
                            refreshSubscriptions()
                            isSubmitting = false
                            showAddListDialog = false
                        }
                    },
                    enabled = customUrl.isNotBlank() && !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Add & Download")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddListDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- DIALOG: EDIT LIST ---
    listToEdit?.let { sub ->
        var editName by remember(sub) { mutableStateOf(sub.name) }
        var editUrl by remember(sub) { mutableStateOf(sub.url) }
        var editError by remember { mutableStateOf<String?>(null) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) listToEdit = null },
            title = { Text("Edit Filter List") },
            text = {
                Column {
                    Text(
                        text = "Edit the list name or update its filter URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("List Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("Filter List URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = editError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedUrl = editUrl.trim()
                        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://"))) {
                            editError = "Please enter a valid HTTP or HTTPS URL"
                            return@Button
                        }
                        isSaving = true
                        val urlChanged = sub.url != trimmedUrl
                        adBlockManager.editSubscription(sub.id, editName, trimmedUrl)
                        refreshSubscriptions()

                        if (urlChanged && sub.isEnabled) {
                            scope.launch {
                                adBlockManager.updateSubscription(sub.id)
                                refreshSubscriptions()
                                isSaving = false
                                listToEdit = null
                            }
                        } else {
                            isSaving = false
                            listToEdit = null
                        }
                    },
                    enabled = editUrl.isNotBlank() && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { listToEdit = null },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- DIALOG: CONFIRM DELETE LIST ---
    listToDelete?.let { sub ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Remove Filter List") },
            text = {
                Text("Are you sure you want to remove '${sub.name}'? Its local cached rules will be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        adBlockManager.removeSubscription(sub.id)
                        refreshSubscriptions()
                        listToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
