package app.prajakeeyam.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.prajakeeyam.R
import app.prajakeeyam.data.ApiException
import app.prajakeeyam.ui.rememberContainer
import kotlinx.coroutines.launch

/** Shown whenever an action needs an account. Calls [onSignedIn] after a successful login. */
@Composable
fun SignInDialog(onDismiss: () -> Unit, onSignedIn: () -> Unit) {
    val container = rememberContainer()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failedText = stringResource(R.string.sign_in_failed)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column {
                Text(stringResource(R.string.sign_in_needed), style = MaterialTheme.typography.bodyLarge)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(enabled = !busy && activity != null, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        container.auth.signIn(activity!!)
                        onSignedIn()
                    } catch (e: ApiException) {
                        error = e.message
                    } catch (e: Exception) {
                        error = failedText
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(if (busy) R.string.loading else R.string.sign_in_google)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
