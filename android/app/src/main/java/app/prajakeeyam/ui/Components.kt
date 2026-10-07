package app.prajakeeyam.ui

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.prajakeeyam.App
import app.prajakeeyam.AppContainer
import app.prajakeeyam.R
import app.prajakeeyam.data.CloudinaryUploader
import app.prajakeeyam.data.LocationsIndex
import app.prajakeeyam.data.Problem
import app.prajakeeyam.data.isNetworkError
import app.prajakeeyam.data.localName
import app.prajakeeyam.ui.theme.Ios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun rememberContainer(): AppContainer = (LocalContext.current.applicationContext as App).container

// ------------------------------------------------------------------ statuses
fun statusLabelRes(status: String): Int = when (status) {
    "resolved" -> R.string.status_resolved
    "in_progress" -> R.string.status_in_progress
    else -> R.string.status_open
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        "resolved" -> Ios.Green.copy(alpha = 0.15f) to Ios.GreenDark
        "in_progress" -> Ios.Blue.copy(alpha = 0.12f) to Ios.Blue
        else -> Ios.Orange.copy(alpha = 0.16f) to Ios.OrangeDark
    }
    Box(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(stringResource(statusLabelRes(status)), color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

// ------------------------------------------------------------------ names & dates
/** Resolves ids to display names in the current language. */
class Names(private val index: LocationsIndex?, private val lang: String?) {
    fun village(id: Int?) = id?.let { index?.villageById?.get(it) }?.let { localName(it.name, it.nameTe, lang) }
    fun mandal(id: Int) = index?.mandalById?.get(id)?.let { localName(it.name, it.nameTe, lang) }
    fun constituency(id: Int) = index?.constituencyById?.get(id)?.let { localName(it.name, it.nameTe, lang) }
    fun placeLine(villageId: Int?, mandalId: Int): String =
        listOfNotNull(village(villageId), mandal(mandalId)).joinToString(" · ")
}

@Composable
fun rememberNames(): Names {
    val c = rememberContainer()
    val index = c.locations.index.collectAsStateValue()
    val lang = c.prefs.language
    return remember(index, lang) { Names(index, lang) }
}

@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateValue(): T = collectAsState().value

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

fun relativeTime(iso: String): String {
    val millis = runCatching { isoFormat.parse(iso)?.time }.getOrNull() ?: return ""
    return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}

// ------------------------------------------------------------------ images (tiny loader, no library)
private val imageCache = LruCache<String, ImageBitmap>(24)

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, width: Int = 800, contentScale: ContentScale = ContentScale.Crop) {
    val http = rememberContainer().http
    val sized = CloudinaryUploader.resized(url, width)
    val bitmap by produceState<ImageBitmap?>(imageCache.get(sized), sized) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(sized).get().build()).execute().use { resp ->
                        val bytes = resp.body.bytes()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }.getOrNull()?.also { imageCache.put(sized, it) }
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(Ios.Fill))
    }
}

// ------------------------------------------------------------------ states
@Composable
fun LoadingBox(modifier: Modifier = Modifier.fillMaxSize()) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(4000)
        slow = true
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Ios.Secondary, strokeWidth = 2.5.dp)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(if (slow) R.string.server_waking else R.string.loading), style = MaterialTheme.typography.bodyMedium, color = Ios.Secondary)
    }
}

@Composable
fun ErrorBox(error: Throwable?, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    val text = when {
        error == null -> stringResource(R.string.error_generic)
        error.isNetworkError() -> stringResource(R.string.error_network)
        else -> error.message ?: stringResource(R.string.error_generic)
    }
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        IosCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Ios.Orange, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Ios.Label)
                Spacer(Modifier.height(16.dp))
                TintedButton(stringResource(R.string.retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ------------------------------------------------------------------ problem card
/** Feed card: author on top, category tile + title, support pill that works without opening the post. */
@Composable
fun ProblemCard(p: Problem, names: Names, onClick: () -> Unit, onSupport: () -> Unit) {
    IosCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.author.name, p.author.pictureUrl, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.author.name, style = MaterialTheme.typography.titleSmall, color = Ios.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(relativeTime(p.createdAt), names.placeLine(p.villageId, p.mandalId)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Ios.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusChip(p.status)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                CategoryTile(p.category, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium, color = Ios.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (p.description.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(p.description, style = MaterialTheme.typography.bodyMedium, color = Ios.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (p.photoUrl != null) {
                    Spacer(Modifier.width(12.dp))
                    RemoteImage(p.photoUrl, Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)), width = 220)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SupportPill(count = p.upvoteCount, supported = p.myUpvote, onClick = onSupport)
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Ios.Secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("${p.commentCount}", style = MaterialTheme.typography.labelMedium, color = Ios.Secondary)
                Spacer(Modifier.weight(1f))
                Text(stringResource(categoryLabelRes(p.category)), style = MaterialTheme.typography.bodySmall, color = Ios.Secondary)
            }
        }
    }
}
