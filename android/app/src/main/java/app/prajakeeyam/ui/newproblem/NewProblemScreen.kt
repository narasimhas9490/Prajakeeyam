package app.prajakeeyam.ui.newproblem

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.prajakeeyam.R
import app.prajakeeyam.ui.CATEGORIES
import app.prajakeeyam.ui.CategoryTile
import app.prajakeeyam.ui.FrostedBottomBar
import app.prajakeeyam.ui.FrostedTopBar
import app.prajakeeyam.ui.IosCard
import app.prajakeeyam.ui.IosTextField
import app.prajakeeyam.ui.PrimaryButton
import app.prajakeeyam.ui.ScreenBackground
import app.prajakeeyam.ui.SectionHeader
import app.prajakeeyam.ui.TintedButton
import app.prajakeeyam.ui.categoryLabelRes
import app.prajakeeyam.ui.rememberContainer
import app.prajakeeyam.ui.rememberNames
import app.prajakeeyam.ui.theme.Ios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun NewProblemScreen(onBack: () -> Unit, onPosted: () -> Unit) {
    val container = rememberContainer()
    val place = container.prefs.place
    if (place == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val vm: NewProblemViewModel = viewModel(factory = viewModelFactory { initializer { NewProblemViewModel(container, place) } })
    val state by vm.state.collectAsState()
    val names = rememberNames()
    val context = LocalContext.current

    LaunchedEffect(state.posted) { if (state.posted) onPosted() }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { vm.setPhoto(it) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setPhoto(it) }
    }
    val tooShort = stringResource(R.string.validation_short)

    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { FrostedTopBar(title = stringResource(R.string.new_problem), subtitle = names.placeLine(place.villageId, place.mandalId), onBack = onBack) },
            bottomBar = {
                FrostedBottomBar {
                    if (state.submitting) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = Ios.Blue, trackColor = Ios.Fill)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(if (state.uploading) R.string.uploading_photo else R.string.posting), style = MaterialTheme.typography.bodySmall, color = Ios.Secondary)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (state.error != null) {
                        Text(state.error!!, color = Ios.Red, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    PrimaryButton(text = stringResource(R.string.submit), enabled = !state.submitting, onClick = { vm.submit(tooShortMessage = tooShort) }, modifier = Modifier.fillMaxWidth())
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                SectionHeader(stringResource(R.string.field_category))
                IosCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CATEGORIES.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { cat ->
                                    val selected = state.category == cat
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) Ios.Blue.copy(alpha = 0.12f) else Ios.FillSoft)
                                            .clickable { vm.setCategory(cat) },
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            CategoryTile(cat, size = 26.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(categoryLabelRes(cat)),
                                                color = if (selected) Ios.Blue else Ios.Label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SectionHeader(stringResource(R.string.field_description))
                IosCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IosTextField(value = state.title, onValueChange = vm::setTitle, placeholder = stringResource(R.string.field_title), singleLine = true, modifier = Modifier.fillMaxWidth())
                        IosTextField(value = state.description, onValueChange = vm::setDescription, placeholder = stringResource(R.string.field_description), minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
                    }
                }

                if (container.uploader.isConfigured) {
                    SectionHeader(stringResource(R.string.add_photo))
                    IosCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            val photo = state.photoUri
                            if (photo != null) {
                                LocalImage(photo, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)))
                                TextButton(onClick = { vm.setPhoto(null) }) { Text(stringResource(R.string.remove_photo), color = Ios.Red) }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TintedButton(
                                        stringResource(R.string.add_photo),
                                        modifier = Modifier.weight(1f),
                                        leading = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(dir, "capture_${System.currentTimeMillis()}.jpg"))
                                            cameraUri = uri
                                            takePicture.launch(uri)
                                        },
                                    )
                                    TintedButton(
                                        "",
                                        modifier = Modifier.weight(0.4f),
                                        leading = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(22.dp)) },
                                        onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LocalImage(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}
