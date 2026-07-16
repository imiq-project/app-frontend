package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

@Composable
fun MobilityHomeScreen(
    userProfile: ClassificationResult? = null,
    userName: String = "",
    onPlanTrip: (DemoRoutingData.Place) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val s = LocalStrings.current
    val hour = remember { LocalDateTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> s.goodMorning
        in 12..17 -> s.goodAfternoon
        else -> s.goodEvening
    }
    val saved = listOf(
        Triple(s.savedHome, "Stadtfeld Ost", Icons.Default.Home),
        Triple(s.savedCampus, "Universitätsplatz", Icons.Default.School),
        Triple("Hauptbahnhof", "Central Station", Icons.Default.Train)
    )

    var searching by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Mob.bg)) {

        MobMap(
            modifier = Modifier.fillMaxSize(),
            center = MAGDEBURG,
            zoom = 14.2,
            interactive = true
        )

        // Top scrim for legibility
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Brush.verticalGradient(listOf(Mob.bg, Color.Transparent)))
        )

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(greeting, color = Mob.textSecondary, fontSize = 13.sp)
                Text(
                    userName.takeIf { it.isNotBlank() }?.let { "$it 👋" } ?: s.whereHeaded,
                    color = Mob.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
            }
            RoundIconButton(Icons.Default.Settings, onOpenSettings)
            Spacer(Modifier.width(10.dp))
            ProfileAvatar(userName, onOpenProfile)
        }

        // Re-center FAB sits just above the sheet
        RoundIconButton(
            Icons.Default.MyLocation,
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 320.dp)
        )

        // Bottom sheet
        BottomSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            greeting = greeting,
            saved = saved,
            onSearch = { searching = true },
            onPlanPlace = { idx -> onPlanTrip(DemoRoutingData.landmarks.getOrElse(idx) { DemoRoutingData.destination }) },
            onPersonalize = onOpenProfile
        )

        // Destination search overlay (in-house geocoder, Magdeburg-bounded)
        if (searching) {
            BackHandler(enabled = true) { searching = false }
            DestinationSearchOverlay(
                onPick = { place ->
                    searching = false
                    onPlanTrip(place)
                },
                onClose = { searching = false }
            )
        }
    }
}

@Composable
private fun BottomSheet(
    modifier: Modifier,
    greeting: String,
    saved: List<Triple<String, String, ImageVector>>,
    onSearch: () -> Unit,
    onPlanPlace: (Int) -> Unit,
    onPersonalize: () -> Unit
) {
    val s = LocalStrings.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Mob.surface)
            .border(
                1.dp, Mob.border,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 20.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
        Spacer(Modifier.height(16.dp))

        // "Where to?" hero field
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Mob.surfaceHi)
                .border(1.dp, Mob.borderHi, RoundedCornerShape(16.dp))
                .clickable { onSearch() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Mob.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(s.whereTo, color = Mob.textSecondary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Mob.primary.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Mob.primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.smart, color = Mob.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        MobSectionLabel(s.savedRecent)
        Spacer(Modifier.height(6.dp))

        saved.forEachIndexed { i, (label, sub, icon) ->
            SavedPlaceRow(label, sub, icon) { onPlanPlace(i) }
            if (i < saved.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Mob.border))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Personalization footer
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Mob.primary.copy(alpha = 0.08f))
                .border(1.dp, Mob.primary.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                .clickable { onPersonalize() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = Mob.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.personalizedRouting, color = Mob.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(s.rankedByPassport, color = Mob.textSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Mob.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SavedPlaceRow(label: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Mob.surfaceHi),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Mob.textSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Mob.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Mob.textMuted, fontSize = 12.sp)
        }
        Icon(Icons.Default.NorthEast, null, tint = Mob.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProfileAvatar(userName: String, onClick: () -> Unit) {
    val initial = userName.trim().firstOrNull()?.uppercase() ?: "Y"
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Mob.brandGradient)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Mob.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Mob.glass)
            .border(1.dp, Mob.border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Mob.textPrimary, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------------------
// Destination search: debounced lookup against the in-house geocode service
// (Magdeburg-bounded + cached in GeocodingApiService). Resolves typed text ->
// {lat, lon} and hands a real Place back via onPick. Falls back to curated
// landmarks while the box is empty. The geocoder matches complete words only
// (no prefix search), so results appear once a word is finished.
// ---------------------------------------------------------------------------
@Composable
private fun DestinationSearchOverlay(
    onPick: (DemoRoutingData.Place) -> Unit,
    onClose: () -> Unit
) {
    val s = LocalStrings.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // Debounced search: fire 500 ms after the user stops typing (the geocoder
    // resolves complete words, so waiting for a pause avoids dead mid-word calls).
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList(); loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(500)
        results = GeocodingApiService.search(q)
        loading = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mob.bg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Search bar
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Mob.glass)
                    .border(1.dp, Mob.border, CircleShape).clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Mob.textPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Mob.surfaceHi)
                    .border(1.dp, Mob.borderHi, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = Mob.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(s.searchPlaceMd, color = Mob.textMuted, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Mob.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(Mob.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close, "Clear", tint = Mob.textMuted,
                        modifier = Modifier.size(18.dp).clickable { query = "" }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            val q = query.trim()
            when {
                q.length < 2 -> {
                    MobSectionLabel(s.popularInMd)
                    Spacer(Modifier.height(6.dp))
                    DemoRoutingData.landmarks.forEach { place ->
                        PlaceResultRow(place.label, place.sub) { onPick(place) }
                    }
                }
                loading -> Text(
                    s.searching, color = Mob.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                results.isEmpty() -> Text(
                    String.format(s.noPlacesFound, q), color = Mob.textMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                else -> results.forEach { r ->
                    PlaceResultRow(r.label, r.sub) {
                        onPick(DemoRoutingData.Place(label = r.label, sub = r.sub, lat = r.lat, lon = r.lon))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceResultRow(label: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Mob.surfaceHi),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Place, null, tint = Mob.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Mob.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Mob.textMuted, fontSize = 12.sp)
        }
        Icon(Icons.Default.NorthEast, null, tint = Mob.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07090D, heightDp = 780)
@Composable
private fun MobilityHomePreview() {
    MobilityHomeScreen(userName = "Deniz")
}
