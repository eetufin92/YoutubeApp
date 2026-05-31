package com.eetu.youtubeapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eetu.youtubeapp.data.SponsorBlockManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sponsorBlockManager = remember { SponsorBlockManager(context) }
    
    val categories = listOf(
        "sponsor" to "Sponsors",
        "selfpromo" to "Self Promotion",
        "interaction" to "Interaction Reminders",
        "intro" to "Intros",
        "outro" to "Outros",
        "preview" to "Previews",
        "music_offtopic" to "Music Offtopic",
        "poi_highlight" to "Highlights (Button)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SponsorBlock Settings") },
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
                .padding(16.dp)
        ) {
            Text(
                text = "Skip Categories",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            categories.forEach { (id, label) ->
                var enabled by remember { mutableStateOf(sponsorBlockManager.shouldSkip(id)) }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, modifier = Modifier.weight(1.0f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { 
                            enabled = it
                            sponsorBlockManager.setShouldSkip(id, it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Notice duration",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            var noticeDuration by remember { mutableFloatStateOf(sponsorBlockManager.getNoticeDuration().toFloat()) }

            Text(
                text = "Show skip/highlight alerts for ${noticeDuration.toInt()} seconds",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Slider(
                value = noticeDuration,
                onValueChange = { 
                    noticeDuration = it
                    sponsorBlockManager.setNoticeDuration(it.toInt())
                },
                valueRange = 2f..20f,
                steps = 17,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
