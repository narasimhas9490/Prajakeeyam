package app.prajakeeyam.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.prajakeeyam.R
import app.prajakeeyam.ui.theme.Ios

/** First launch: app mark, name, and a grouped list with the two languages. */
@Composable
fun LanguageScreen(onPicked: (String) -> Unit) {
    ScreenBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).background(Ios.Blue), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(124.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = Ios.Label, textAlign = TextAlign.Center)
            Text(stringResource(R.string.app_name_latin), style = MaterialTheme.typography.bodyLarge, color = Ios.Secondary)
            Spacer(Modifier.weight(1f))
            SectionHeader(stringResource(R.string.pick_language), Modifier.fillMaxWidth())
            val options = listOf("te" to "తెలుగు", "en" to "English")
            options.forEachIndexed { i, (code, label) ->
                GroupRow(index = i, count = options.size, onClick = { onPicked(code) }) {
                    IconTile(Icons.Outlined.Translate, if (code == "te") Ios.Blue else Ios.Indigo)
                    Spacer(Modifier.width(14.dp))
                    Text(label, fontSize = 19.sp, color = Ios.Label, modifier = Modifier.weight(1f))
                    Chevron()
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.about_independent),
                style = MaterialTheme.typography.bodySmall, color = Ios.Secondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
