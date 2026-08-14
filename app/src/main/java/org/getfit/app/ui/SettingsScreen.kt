package org.getfit.app.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.getfit.app.settings.Settings
import org.getfit.app.settings.UnitSystem
import org.getfit.app.update.UpdateChannel
import org.getfit.app.update.UpdateVerdict

@Composable
fun SettingsScreen(env: AppEnv, settings: Settings) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp),
        )

        // -- You --------------------------------------------------------
        SettingsCard("You") {
            OutlinedTextField(
                value = settings.displayNameRaw,
                onValueChange = { scope.launch { env.settingsRepository.setDisplayName(it) } },
                label = { Text("Name") },
                placeholder = { Text(Settings.DEFAULT_NAME) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitSystem.entries.forEach { unit ->
                    FilterChip(
                        selected = settings.units == unit,
                        onClick = { scope.launch { env.settingsRepository.setUnits(unit) } },
                        label = { Text(unit.label) },
                    )
                }
            }

            // Held locally while it is being typed, so the field shows exactly
            // what was typed rather than the parsed value read back — clearing
            // it should leave it empty, not snap to whatever was stored.
            var targetText by remember(settings.calorieTarget) {
                mutableStateOf(settings.calorieTarget?.toString() ?: "")
            }
            OutlinedTextField(
                value = targetText,
                onValueChange = { typed ->
                    targetText = typed.filter { it.isDigit() }.take(5)
                    scope.launch {
                        env.settingsRepository.setCalorieTarget(targetText.toIntOrNull())
                    }
                },
                label = { Text("Daily calorie target") },
                placeholder = { Text("No target") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // -- Meals ------------------------------------------------------
        SettingsCard("Meals from Claude") {
            SwitchRow(
                label = "Check the inbox on launch",
                blurb = "Collect meals the meal-photo skill has published.",
                checked = settings.syncMealInbox,
                onChange = { scope.launch { env.settingsRepository.setSyncMealInbox(it) } },
            )
        }

        // -- Updates ----------------------------------------------------
        SettingsCard("Updates") {
            val channel = remember { env.updater.installedChannel() }
            val version = remember { env.updater.installedVersionName() }

            Text(
                "Installed: v$version — ${channel.label} channel",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (channel == UpdateChannel.DEV) {
                    "This is a dev build. It updates from dev prereleases only, " +
                        "and installs alongside a production copy rather than over it."
                } else {
                    "This is a production build. It updates from full releases only."
                },
                style = MaterialTheme.typography.labelSmall,
            )

            SwitchRow(
                label = "Check on launch",
                blurb = "Only speaks up when there is something to install.",
                checked = settings.checkForUpdatesOnLaunch,
                onChange = { scope.launch { env.settingsRepository.setCheckForUpdatesOnLaunch(it) } },
            )

            var status by remember { mutableStateOf<String?>(null) }
            var busy by remember { mutableStateOf(false) }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        status = "Checking…"
                        when (val verdict = env.updater.check()) {
                            is UpdateVerdict.UpToDate ->
                                status = "You're on the newest ${channel.label.lowercase()} build."

                            is UpdateVerdict.Refused -> status = verdict.reason

                            is UpdateVerdict.Install -> {
                                if (env.updater.needsInstallPermission()) {
                                    status = "Allow installs from this app, then check again."
                                    env.updater.requestInstallPermission()
                                } else {
                                    status = "Downloading ${verdict.release.tag}…"
                                    val error = env.updater.downloadAndInstall(verdict.release)
                                    status = error ?: "Handing over to the installer…"
                                }
                            }
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Check for updates") }

            status?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    blurb: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weighted, so a long blurb wraps instead of pushing the switch off the
        // edge of the screen.
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(blurb, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
