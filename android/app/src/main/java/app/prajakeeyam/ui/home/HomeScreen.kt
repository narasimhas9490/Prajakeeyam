package app.prajakeeyam.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.HolidayVillage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.prajakeeyam.R
import app.prajakeeyam.data.Constituency
import app.prajakeeyam.data.District
import app.prajakeeyam.data.LocationsIndex
import app.prajakeeyam.data.Mandal
import app.prajakeeyam.data.Place
import app.prajakeeyam.data.localName
import app.prajakeeyam.location.LocationFinder
import app.prajakeeyam.ui.Chevron
import app.prajakeeyam.ui.ErrorBox
import app.prajakeeyam.ui.FrostedTopBar
import app.prajakeeyam.ui.GroupRow
import app.prajakeeyam.ui.IconTile
import app.prajakeeyam.ui.IosTextField
import app.prajakeeyam.ui.LoadingBox
import app.prajakeeyam.ui.PrimaryButton
import app.prajakeeyam.ui.RowText
import app.prajakeeyam.ui.ScreenBackground
import app.prajakeeyam.ui.SectionHeader
import app.prajakeeyam.ui.collectAsStateValue
import app.prajakeeyam.ui.rememberContainer
import app.prajakeeyam.ui.theme.Ios
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

private val DistrictIcon = Icons.Outlined.Map to Color(0xFF636366)
private val ConstituencyIcon = Icons.Outlined.AccountBalance to Ios.Indigo
private val MandalIcon = Icons.Outlined.HolidayVillage to Ios.Teal
private val VillageIcon = Icons.Outlined.Home to Ios.Green

/** Pick a place: phone location, instant offline search, or drill down District > Constituency > Mandal > Village. */
@Composable
fun HomeScreen(canGoBack: Boolean, onBack: () -> Unit, onPlaceChosen: (Place) -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val index = container.locations.index.collectAsStateValue()
    val loadError = container.locations.error.collectAsStateValue()
    val lang = container.prefs.language

    var query by rememberSaveable { mutableStateOf("") }
    var district by remember { mutableStateOf<District?>(null) }
    var constituency by remember { mutableStateOf<Constituency?>(null) }
    var mandal by remember { mutableStateOf<Mandal?>(null) }
    var mandalNote by remember { mutableStateOf<String?>(null) }

    // ---- location detection
    val finder = remember { LocationFinder(context.applicationContext) }
    var locState by remember { mutableStateOf<LocState>(LocState.Idle) }

    fun openMandal(idx: LocationsIndex, m: Mandal, note: String?) {
        val c = idx.constituencyById[m.constituencyId] ?: return
        district = idx.districtById[c.districtId]
        constituency = c
        mandal = m
        mandalNote = note
    }

    fun detect() {
        scope.launch {
            locState = LocState.Searching
            val idx = container.locations.index.filterNotNull().first()
            if (!finder.isLocationEnabled()) { locState = LocState.NoLocation; return@launch }
            val fix = finder.currentLocation()
            if (fix == null) { locState = LocState.NotFound; return@launch }
            val addresses = try {
                finder.reverseGeocode(fix)
            } catch (e: IOException) {
                locState = LocState.NoInternet; return@launch
            } catch (e: Exception) {
                locState = LocState.NotFound; return@launch
            }
            val resolved = finder.resolve(addresses, idx)
            when {
                resolved == null -> locState = LocState.NotFound
                resolved.village == null -> { // mandal only: let the user pick the village inside it
                    locState = LocState.Idle
                    openMandal(idx, resolved.mandal, localName(resolved.mandal.name, resolved.mandal.nameTe, lang))
                }
                else -> locState = LocState.Found(resolved)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) detect() else locState = LocState.Denied
    }
    fun startLocation() {
        if (finder.hasPermission()) detect() else permissionLauncher.launch(LocationFinder.PERMISSIONS)
    }
    LaunchedEffect(Unit) {
        // First time the picker opens with no place chosen: ask once, automatically.
        if (!canGoBack && !container.prefs.locationAsked) {
            container.prefs.locationAsked = true
            startLocation()
        }
    }

    val drillBack: () -> Unit = {
        when {
            mandal != null -> { mandal = null; mandalNote = null }
            constituency != null -> constituency = null
            district != null -> district = null
        }
    }
    val inDrill = district != null
    BackHandler(enabled = inDrill) { drillBack() }

    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                FrostedTopBar(
                    title = when {
                        mandal != null -> localName(mandal!!.name, mandal!!.nameTe, lang)
                        constituency != null -> localName(constituency!!.name, constituency!!.nameTe, lang)
                        district != null -> localName(district!!.name, district!!.nameTe, lang)
                        else -> stringResource(R.string.choose_village)
                    },
                    subtitle = when {
                        mandal != null -> stringResource(R.string.mandal)
                        constituency != null -> stringResource(R.string.constituency)
                        district != null -> stringResource(R.string.district)
                        else -> stringResource(R.string.tagline)
                    },
                    onBack = if (inDrill) drillBack else if (canGoBack) onBack else null,
                )
            },
        ) { padding ->
            when {
                index == null && loadError != null -> ErrorBox(loadError, onRetry = { container.scope.launch { container.locations.load() } }, Modifier.padding(padding).fillMaxSize())
                index == null -> LoadingBox(Modifier.padding(padding).fillMaxSize())
                mandal != null -> VillageList(index, mandal!!, lang, mandalNote, Modifier.padding(padding), onPlaceChosen)
                constituency != null -> GroupedList(
                    items = (index.mandalsByConstituency[constituency!!.id] ?: emptyList()).sortedBy { it.name },
                    label = { localName(it.name, it.nameTe, lang) },
                    supporting = { m -> if (m.isUrban) stringResource(R.string.urban_no_villages) else stringResource(R.string.villages_count, index.villagesByMandal[m.id]?.size ?: 0) },
                    header = stringResource(R.string.mandals_count, index.mandalsByConstituency[constituency!!.id]?.size ?: 0),
                    icon = MandalIcon,
                    modifier = Modifier.padding(padding),
                ) { mandal = it }
                district != null -> GroupedList(
                    items = (index.constituenciesByDistrict[district!!.id] ?: emptyList()).sortedBy { it.name },
                    label = { localName(it.name, it.nameTe, lang) },
                    supporting = { c -> stringResource(R.string.mandals_count, index.mandalsByConstituency[c.id]?.size ?: 0) },
                    header = stringResource(R.string.constituency),
                    icon = ConstituencyIcon,
                    modifier = Modifier.padding(padding),
                ) { constituency = it }
                else -> Column(Modifier.padding(padding).fillMaxSize()) {
                    if (query.trim().length < 2) {
                        Spacer(Modifier.height(12.dp))
                        LocationCard(
                            state = locState,
                            lang = lang,
                            onUse = { startLocation() },
                            onConfirm = { r -> onPlaceChosen(Place(r.village?.id, r.mandal.id, r.constituency.id)) },
                            onReject = { r -> locState = LocState.Idle; openMandal(index, r.mandal, null) },
                        )
                    }
                    IosTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.search_hint),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        singleLine = true,
                        leading = { Icon(Icons.Default.Search, contentDescription = null, tint = Ios.Secondary) },
                        trailing = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = null, tint = Ios.Secondary) } },
                        fill = Ios.Fill,
                    )
                    if (query.trim().length >= 2) {
                        val hits = remember(query, index) { index.search(query) }
                        if (hits.isEmpty()) {
                            Text(stringResource(R.string.no_results), Modifier.padding(horizontal = 32.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Ios.Secondary)
                        }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
                            itemsIndexed(hits, key = { _, it -> (it.village?.id ?: 0) to it.mandal.id }) { i, hit ->
                                val title = hit.village?.let { localName(it.name, it.nameTe, lang) } ?: localName(hit.mandal.name, hit.mandal.nameTe, lang)
                                val sub = if (hit.village != null) {
                                    "${localName(hit.mandal.name, hit.mandal.nameTe, lang)} · ${localName(hit.constituency.name, hit.constituency.nameTe, lang)}"
                                } else {
                                    "${stringResource(R.string.mandal)} · ${localName(hit.constituency.name, hit.constituency.nameTe, lang)}"
                                }
                                val (icon, color) = if (hit.village != null) VillageIcon else MandalIcon
                                GroupRow(index = i, count = hits.size, onClick = { onPlaceChosen(Place(hit.village?.id, hit.mandal.id, hit.constituency.id)) }) {
                                    IconTile(icon, color)
                                    Spacer(Modifier.width(12.dp))
                                    RowText(title, sub, Modifier.weight(1f))
                                    Chevron()
                                }
                            }
                        }
                    } else {
                        GroupedList(
                            items = index.districts.sortedBy { it.name },
                            label = { localName(it.name, it.nameTe, lang) },
                            supporting = { d -> (index.constituenciesByDistrict[d.id]?.size ?: 0).toString() + " " + stringResource(R.string.constituency) },
                            header = stringResource(R.string.browse_by_constituency),
                            icon = DistrictIcon,
                        ) { district = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> GroupedList(
    items: List<T>,
    label: (T) -> String,
    supporting: @Composable (T) -> String,
    header: String,
    icon: Pair<ImageVector, Color>,
    modifier: Modifier = Modifier,
    onClick: (T) -> Unit,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionHeader(header) }
        itemsIndexed(items) { i, item ->
            GroupRow(index = i, count = items.size, onClick = { onClick(item) }) {
                IconTile(icon.first, icon.second)
                Spacer(Modifier.width(12.dp))
                RowText(label(item), supporting(item), Modifier.weight(1f))
                Chevron()
            }
        }
    }
}

@Composable
private fun VillageList(index: LocationsIndex, mandal: Mandal, lang: String?, note: String?, modifier: Modifier, onPlaceChosen: (Place) -> Unit) {
    val villages = (index.villagesByMandal[mandal.id] ?: emptyList()).sortedBy { it.name }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item {
            if (note != null) {
                Text(stringResource(R.string.loc_mandal_found, note), Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = Ios.Label)
                Spacer(Modifier.height(6.dp))
            }
            PrimaryButton(
                text = stringResource(R.string.mandal) + ": " + localName(mandal.name, mandal.nameTe, lang),
                onClick = { onPlaceChosen(Place(null, mandal.id, mandal.constituencyId)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (villages.isEmpty()) {
                Text(stringResource(R.string.urban_no_villages), Modifier.padding(horizontal = 32.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Ios.Secondary)
            } else {
                SectionHeader(stringResource(R.string.villages_count, villages.size))
            }
        }
        itemsIndexed(villages, key = { _, v -> v.id }) { i, v ->
            GroupRow(index = i, count = villages.size, onClick = { onPlaceChosen(Place(v.id, mandal.id, mandal.constituencyId)) }) {
                IconTile(VillageIcon.first, VillageIcon.second)
                Spacer(Modifier.width(12.dp))
                RowText(localName(v.name, v.nameTe, lang), null, Modifier.weight(1f))
                Chevron()
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
