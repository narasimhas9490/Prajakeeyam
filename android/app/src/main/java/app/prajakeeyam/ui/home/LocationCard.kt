package app.prajakeeyam.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.prajakeeyam.R
import app.prajakeeyam.data.localName
import app.prajakeeyam.location.LocationFinder
import app.prajakeeyam.ui.Chevron
import app.prajakeeyam.ui.GroupRow
import app.prajakeeyam.ui.IconTile
import app.prajakeeyam.ui.IosCard
import app.prajakeeyam.ui.PrimaryButton
import app.prajakeeyam.ui.RowText
import app.prajakeeyam.ui.TintedButton
import app.prajakeeyam.ui.theme.Ios

sealed class LocState {
    data object Idle : LocState()
    data object Searching : LocState()
    data class Found(val result: LocationFinder.Resolved) : LocState()
    data object NotFound : LocState()
    data object NoLocation : LocState()
    data object NoInternet : LocState()
    data object Denied : LocState()
}

/** The "use my location" block at the top of the village picker. */
@Composable
fun LocationCard(state: LocState, lang: String?, onUse: () -> Unit, onConfirm: (LocationFinder.Resolved) -> Unit, onReject: (LocationFinder.Resolved) -> Unit) {
    val context = LocalContext.current
    when (state) {
        LocState.Idle -> GroupRow(index = 0, count = 1, onClick = onUse) {
            IconTile(Icons.Outlined.MyLocation, Ios.Blue)
            Spacer(Modifier.width(12.dp))
            RowText(stringResource(R.string.loc_use), stringResource(R.string.loc_use_hint), Modifier.weight(1f))
            Chevron()
        }
        LocState.Searching -> IosCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Ios.Blue, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.loc_searching), style = MaterialTheme.typography.bodyLarge, color = Ios.Label)
            }
        }
        is LocState.Found -> {
            val r = state.result
            val village = r.village?.let { localName(it.name, it.nameTe, lang) }
            IosCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.loc_is_this), style = MaterialTheme.typography.bodySmall, color = Ios.Secondary)
                    Spacer(Modifier.height(4.dp))
                    Text(village ?: localName(r.mandal.name, r.mandal.nameTe, lang), style = MaterialTheme.typography.titleLarge, color = Ios.Label)
                    Text(
                        listOfNotNull(if (village != null) localName(r.mandal.name, r.mandal.nameTe, lang) else null, localName(r.constituency.name, r.constituency.nameTe, lang)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = Ios.Secondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(stringResource(R.string.loc_yes), onClick = { onConfirm(r) }, modifier = Modifier.weight(1f))
                        TintedButton(stringResource(R.string.loc_no), onClick = { onReject(r) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        LocState.NotFound, LocState.NoInternet, LocState.Denied, LocState.NoLocation -> IosCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(
                        when (state) {
                            LocState.NoLocation -> R.string.loc_off
                            LocState.NoInternet -> R.string.error_network
                            LocState.Denied -> R.string.loc_denied
                            else -> R.string.loc_not_found
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = Ios.Label,
                )
                Row {
                    TextButton(onClick = onUse) { Text(stringResource(R.string.retry), color = Ios.Blue) }
                    if (state == LocState.NoLocation) {
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }) {
                            Text(stringResource(R.string.loc_open_settings), color = Ios.Blue)
                        }
                    }
                }
            }
        }
    }
}
