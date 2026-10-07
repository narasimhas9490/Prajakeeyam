@file:OptIn(ExperimentalMaterial3Api::class)

package app.prajakeeyam.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.prajakeeyam.R
import app.prajakeeyam.ui.Avatar
import app.prajakeeyam.ui.ErrorBox
import app.prajakeeyam.ui.FrostedBottomBar
import app.prajakeeyam.ui.FrostedTopBar
import app.prajakeeyam.ui.Hairline
import app.prajakeeyam.ui.IosCard
import app.prajakeeyam.ui.IosTextField
import app.prajakeeyam.ui.LoadingBox
import app.prajakeeyam.ui.PrimaryButton
import app.prajakeeyam.ui.RemoteImage
import app.prajakeeyam.ui.ScreenBackground
import app.prajakeeyam.ui.SectionHeader
import app.prajakeeyam.ui.StatusChip
import app.prajakeeyam.ui.TintedButton
import app.prajakeeyam.ui.auth.SignInDialog
import app.prajakeeyam.ui.CategoryTile
import app.prajakeeyam.ui.categoryLabelRes
import app.prajakeeyam.ui.relativeTime
import app.prajakeeyam.ui.rememberContainer
import app.prajakeeyam.ui.rememberNames
import app.prajakeeyam.ui.theme.Ios

@Composable
fun ProblemDetailScreen(problemId: Int, onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: DetailViewModel = viewModel(key = "problem-$problemId", factory = viewModelFactory { initializer { DetailViewModel(container, problemId) } })
    val state by vm.state.collectAsState()
    val user by container.auth.user.collectAsState()
    val names = rememberNames()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) } // runs after sign-in
    var menuOpen by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(context.getString(it))
            vm.consumeMessage()
        }
    }

    fun requireSignIn(action: () -> Unit) {
        if (user == null) pendingAction = action else action()
    }

    val p = state.problem
    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                FrostedTopBar(
                    title = p?.let { stringResource(categoryLabelRes(it.category)) } ?: "",
                    subtitle = p?.let { names.placeLine(it.villageId, it.mandalId) },
                    onBack = onBack,
                    actions = {
                        if (p != null) {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more), tint = Ios.Blue) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!p.isOwner) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.report)) }, onClick = { menuOpen = false; requireSignIn { showReport = true } })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.block_user)) }, onClick = { menuOpen = false; requireSignIn { vm.blockAuthor() } })
                                }
                                if (p.isOwner || user?.isAdmin == true) {
                                    if (p.status != "resolved") {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.mark_resolved)) }, onClick = { menuOpen = false; vm.setStatus("resolved") })
                                    } else {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.reopen)) }, onClick = { menuOpen = false; vm.setStatus("open") })
                                    }
                                    if (user?.isAdmin == true && p.status != "in_progress") {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.status_in_progress)) }, onClick = { menuOpen = false; vm.setStatus("in_progress") })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_post)) }, onClick = { menuOpen = false; confirmDelete = true })
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (p != null) {
                    FrostedBottomBar {
                        Row(Modifier.fillMaxWidth().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                            IosTextField(
                                value = commentText,
                                onValueChange = { commentText = it.take(1000) },
                                placeholder = stringResource(R.string.write_comment),
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                fill = Ios.Fill,
                            )
                            IconButton(enabled = commentText.isNotBlank() && !state.sending, onClick = {
                                requireSignIn {
                                    vm.addComment(commentText.trim())
                                    commentText = ""
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send), tint = if (commentText.isNotBlank()) Ios.Blue else Ios.Tertiary) }
                        }
                    }
                }
            },
        ) { padding ->
            when {
                state.loading -> LoadingBox(Modifier.padding(padding).fillMaxSize())
                p == null -> ErrorBox(state.error, onRetry = { vm.load() }, Modifier.padding(padding).fillMaxSize())
                else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            IosCard(Modifier.fillMaxWidth()) {
                                if (p.photoUrl != null) {
                                    RemoteImage(p.photoUrl, Modifier.fillMaxWidth().height(230.dp), width = 1000)
                                }
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Avatar(p.author.name, p.author.pictureUrl, 40.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(p.author.name, style = MaterialTheme.typography.titleSmall, color = Ios.Label)
                                            Text(relativeTime(p.createdAt), style = MaterialTheme.typography.bodySmall, color = Ios.Secondary)
                                        }
                                        StatusChip(p.status)
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CategoryTile(p.category, size = 28.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(categoryLabelRes(p.category)), style = MaterialTheme.typography.labelMedium, color = Ios.Secondary)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(p.title, style = MaterialTheme.typography.titleLarge, color = Ios.Label)
                                    Spacer(Modifier.height(8.dp))
                                    Text(p.description, style = MaterialTheme.typography.bodyLarge, color = Ios.Label)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        names.placeLine(p.villageId, p.mandalId) + (names.constituency(p.constituencyId)?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall, color = Ios.Secondary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (p.myUpvote) {
                                PrimaryButton(
                                    text = stringResource(R.string.you_supported) + " · " + stringResource(R.string.supporters, p.upvoteCount),
                                    onClick = { vm.toggleUpvote() },
                                    modifier = Modifier.fillMaxWidth(),
                                    leading = { Icon(Icons.Default.ThumbUp, contentDescription = null) },
                                )
                            } else {
                                TintedButton(
                                    text = stringResource(R.string.same_problem) + " · " + stringResource(R.string.supporters, p.upvoteCount),
                                    onClick = { requireSignIn { vm.toggleUpvote() } },
                                    modifier = Modifier.fillMaxWidth(),
                                    leading = { Icon(Icons.Default.ThumbUp, contentDescription = null) },
                                )
                            }
                        }
                        SectionHeader(stringResource(R.string.comments) + " (${p.commentCount})")
                    }
                    itemsIndexed(state.comments, key = { _, c -> c.id }) { i, c ->
                        val top = if (i == 0) 16.dp else 0.dp
                        val bottom = if (i == state.comments.size - 1) 16.dp else 0.dp
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
                                .then(Modifier.padding(0.dp)),
                        ) {
                            IosCard(Modifier.fillMaxWidth(), onClick = null) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                                    Avatar(c.author.name, c.author.pictureUrl, 30.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(c.author.name, style = MaterialTheme.typography.titleSmall, color = Ios.Label)
                                            Text(relativeTime(c.createdAt), style = MaterialTheme.typography.bodySmall, color = Ios.Secondary)
                                        }
                                        Text(c.body, style = MaterialTheme.typography.bodyMedium, color = Ios.Label)
                                    }
                                }
                                if (i < state.comments.size - 1) Hairline(Modifier.padding(start = 56.dp))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    pendingAction?.let { action ->
        SignInDialog(onDismiss = { pendingAction = null }, onSignedIn = { pendingAction = null; action() })
    }
    if (showReport) {
        ReportDialog(onDismiss = { showReport = false }, onPick = { reason -> showReport = false; vm.report(reason) })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_post_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(R.string.delete), color = Ios.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val reasons = listOf("spam" to R.string.reason_spam, "abuse" to R.string.reason_abuse, "false" to R.string.reason_false, "other" to R.string.reason_other)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_why)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { (key, label) ->
                    TintedButton(stringResource(label), onClick = { onPick(key) }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
