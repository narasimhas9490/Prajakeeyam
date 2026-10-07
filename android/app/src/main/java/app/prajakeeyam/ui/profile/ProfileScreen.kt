package app.prajakeeyam.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.prajakeeyam.BuildConfig
import app.prajakeeyam.R
import app.prajakeeyam.ui.Avatar
import app.prajakeeyam.ui.Chevron
import app.prajakeeyam.ui.IconTile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import app.prajakeeyam.ui.FrostedTopBar
import app.prajakeeyam.ui.GroupRow
import app.prajakeeyam.ui.IosCard
import app.prajakeeyam.ui.PrimaryButton
import app.prajakeeyam.ui.RowText
import app.prajakeeyam.ui.ScreenBackground
import app.prajakeeyam.ui.SectionHeader
import app.prajakeeyam.ui.Segmented
import app.prajakeeyam.ui.auth.SignInDialog
import app.prajakeeyam.ui.rememberContainer
import app.prajakeeyam.ui.rememberNames
import app.prajakeeyam.ui.theme.Ios
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onBack: () -> Unit, onChangePlace: () -> Unit) {
    val container = rememberContainer()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by container.auth.user.collectAsState()
    val names = rememberNames()
    val place = container.prefs.place
    var showSignIn by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    ScreenBackground {
        Scaffold(containerColor = Color.Transparent, topBar = { FrostedTopBar(title = stringResource(R.string.profile), onBack = onBack) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                IosCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        if (user != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(user!!.name, user!!.pictureUrl, 56.dp)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(user!!.name, style = MaterialTheme.typography.titleMedium, color = Ios.Label)
                                    user!!.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ios.Secondary) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(enabled = !busy, onClick = { scope.launch { activity?.let { container.auth.signOut(it) } } }) {
                                Text(stringResource(R.string.sign_out), color = Ios.Blue, style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Text(stringResource(R.string.sign_in_needed), style = MaterialTheme.typography.bodyLarge, color = Ios.Label)
                            Spacer(Modifier.height(14.dp))
                            PrimaryButton(stringResource(R.string.sign_in_google), onClick = { showSignIn = true }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                SectionHeader(stringResource(R.string.my_village))
                GroupRow(index = 0, count = 1, onClick = onChangePlace) {
                    IconTile(Icons.Outlined.Home, Ios.Green)
                    Spacer(Modifier.width(12.dp))
                    RowText(
                        place?.let { names.placeLine(it.villageId, it.mandalId) } ?: "—",
                        place?.let { names.constituency(it.constituencyId) },
                        Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.change), color = Ios.Blue, style = MaterialTheme.typography.bodyMedium)
                    Chevron()
                }

                SectionHeader(stringResource(R.string.language))
                val current = container.prefs.language ?: "en"
                Segmented(
                    options = listOf("te" to "తెలుగు", "en" to "English"),
                    selected = current,
                    onSelect = { code -> if (current != code) { container.prefs.language = code; activity?.recreate() } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.about_independent),
                    style = MaterialTheme.typography.bodySmall, color = Ios.Secondary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                )
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))) } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.privacy_policy), color = Ios.Blue)
                }
                Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = Ios.Tertiary, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                if (user != null) {
                    TextButton(enabled = !busy, onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.delete_account), color = Ios.Red)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSignIn) SignInDialog(onDismiss = { showSignIn = false }, onSignedIn = { showSignIn = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    busy = true
                    scope.launch {
                        runCatching { activity?.let { container.auth.deleteAccount(it) } }
                        busy = false
                    }
                }) { Text(stringResource(R.string.delete), color = Ios.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
