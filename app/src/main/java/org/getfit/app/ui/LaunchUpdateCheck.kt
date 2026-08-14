package org.getfit.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.getfit.app.nutrition.InboxResult
import org.getfit.app.update.Release
import org.getfit.app.update.launchOffer

/**
 * The two things the app does on its own at launch, and the only one of them
 * allowed to say anything.
 *
 * The update check speaks only when there is a build to install — see
 * [launchOffer]. Everything else is silence: an up-to-date app has nothing to
 * report, and a phone with no signal has nothing to apologise for.
 *
 * The inbox poll never speaks at all. Meals it collects appear in the Food tab,
 * which is where somebody would look for them; a dialog on launch to announce
 * yesterday's lunch would be an interruption with nothing to decide in it.
 *
 * Both read their setting straight out of the store rather than from the state
 * the screens are drawn with. That state starts life as the defaults and is
 * replaced a moment later by what is actually stored, so a check driven off it
 * would fire on the default — on — before ever learning the setting had been
 * turned off.
 */
@Composable
fun LaunchUpdateCheck(env: AppEnv) {
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Release?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // Kept apart from [status] on purpose: inferring "still working" from there
    // being a message would leave both buttons disabled after a failed
    // download, which leaves its reason on screen, and the dialog could not
    // then be shut.
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val stored = env.settingsRepository.settings.first()

        if (stored.syncMealInbox) {
            // Quietly. Whatever turns up is in the Food tab when it is next opened.
            val result = env.inbox.fetchNew(env.nutrition.importedIds())
            if (result is InboxResult.Found) env.nutrition.addAll(result.entries)
        }

        if (stored.checkForUpdatesOnLaunch) {
            release = launchOffer(env.updater.check())
        }
    }

    val pending = release
    if (pending == null || dismissed) return

    AlertDialog(
        onDismissRequest = { if (!working) dismissed = true },
        title = { Text("Update available") },
        text = {
            Text(
                text = status ?: "${pending.tag} is out on the " +
                    "${pending.channel.label.lowercase()} channel. You're on " +
                    "v${env.updater.installedVersionName()}.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    scope.launch {
                        working = true
                        if (env.updater.needsInstallPermission()) {
                            // Android has to be told this app may install one
                            // first. Say so and stand down: they will be looking
                            // at a system screen, not at this.
                            status = "Allow installs from this app, then check " +
                                "again in Settings."
                            env.updater.requestInstallPermission()
                        } else {
                            status = "Downloading ${pending.tag}…"
                            val error = env.updater.downloadAndInstall(pending)
                            status = error ?: "Handing over to the installer…"
                        }
                        working = false
                    }
                },
            ) { Text("Install") }
        },
        dismissButton = {
            TextButton(
                enabled = !working,
                onClick = { dismissed = true },
            ) { Text("Not now") }
        },
    )
}
