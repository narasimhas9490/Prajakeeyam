@file:OptIn(ExperimentalMaterial3Api::class)

package app.prajakeeyam.ui.feed

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.prajakeeyam.R
import app.prajakeeyam.data.Problem
import app.prajakeeyam.ui.ErrorBox
import app.prajakeeyam.ui.FrostedBottomBar
import app.prajakeeyam.ui.FrostedTopBar
import app.prajakeeyam.ui.LoadingBox
import app.prajakeeyam.ui.PrimaryButton
import app.prajakeeyam.ui.ProblemCard
import app.prajakeeyam.ui.ScreenBackground
import app.prajakeeyam.ui.Segmented
import app.prajakeeyam.ui.auth.SignInDialog
import app.prajakeeyam.ui.rememberContainer
import app.prajakeeyam.ui.rememberNames
import app.prajakeeyam.ui.theme.Ios

@Composable
fun FeedScreen(onOpenProblem: (Int) -> Unit, onNewProblem: () -> Unit, onChangePlace: () -> Unit, onProfile: () -> Unit) {
    val container = rememberContainer()
    val place = container.prefs.place
    if (place == null) {
        LaunchedEffect(Unit) { onChangePlace() }
        return
    }
    val vm: FeedViewModel = viewModel(key = "feed-$place", factory = viewModelFactory { initializer { FeedViewModel(container, place) } })
    val state by vm.state.collectAsState()
    val user by container.auth.user.collectAsState()
    val names = rememberNames()
    var pendingAfterSignIn by remember { mutableStateOf<(() -> Unit)?>(null) }
    val listState = rememberLazyListState()

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= state.items.size - 3
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) vm.loadMore() }

    fun requireSignIn(action: () -> Unit) {
        if (user == null) pendingAfterSignIn = action else action()
    }

    val title = names.village(place.villageId) ?: names.mandal(place.mandalId) ?: stringResource(R.string.app_name)
    val subtitle = listOfNotNull(
        if (place.villageId != null) names.mandal(place.mandalId) else null,
        names.constituency(place.constituencyId),
    ).joinToString(" · ")

    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                FrostedTopBar(title = title, subtitle = subtitle, actions = {
                    TextButton(onClick = onChangePlace) { Text(stringResource(R.string.change), color = Ios.Blue, style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = onProfile) { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.profile), tint = Ios.Blue) }
                })
            },
            bottomBar = {
                FrostedBottomBar {
                    PrimaryButton(
                        text = stringResource(R.string.post_problem),
                        onClick = { requireSignIn(onNewProblem) },
                        modifier = Modifier.fillMaxWidth(),
                        leading = { Icon(Icons.Default.Add, contentDescription = null) },
                    )
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                val scopes = buildList {
                    if (place.villageId != null) add(Scope.VILLAGE to stringResource(R.string.tab_village))
                    add(Scope.MANDAL to stringResource(R.string.tab_mandal))
                    add(Scope.CONSTITUENCY to stringResource(R.string.tab_constituency))
                }
                Segmented(scopes, state.scope, onSelect = vm::setScope, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Segmented(listOf("new" to stringResource(R.string.sort_new), "top" to stringResource(R.string.sort_top_short)), state.sort, onSelect = vm::setSort, modifier = Modifier.width(150.dp))
                }
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(refresh = true) }, modifier = Modifier.fillMaxSize()) {
                    when {
                        state.loading -> LoadingBox()
                        state.error != null && state.items.isEmpty() -> ErrorBox(state.error, onRetry = { vm.load() })
                        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            if (state.items.isEmpty()) {
                                item {
                                    Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Outlined.Campaign, contentDescription = null, tint = Ios.Tertiary, modifier = Modifier.size(56.dp))
                                            Spacer(Modifier.height(12.dp))
                                            Text(stringResource(R.string.feed_empty), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Ios.Secondary)
                                        }
                                    }
                                }
                            }
                            items(state.items, key = { it.id }) { p: Problem ->
                                ProblemCard(p, names, onClick = { onOpenProblem(p.id) }, onSupport = { requireSignIn { vm.toggleSupport(p.id) } })
                            }
                            if (state.loadingMore) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ios.Secondary, strokeWidth = 2.5.dp) } }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
    pendingAfterSignIn?.let { action ->
        SignInDialog(onDismiss = { pendingAfterSignIn = null }, onSignedIn = { pendingAfterSignIn = null; action() })
    }
}
