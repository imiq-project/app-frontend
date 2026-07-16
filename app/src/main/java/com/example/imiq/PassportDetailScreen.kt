package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

// =====================================================================
//  PassportDetailScreen
//  Full read-only display of the saved cognitive passport. Renders
//  each section of the schema as a card, with the raw JSON at the
//  bottom inside an expandable container.
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportDetailScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    val passportJson = remember { PassportStore.load() }
    val cp = remember(passportJson) {
        try {
            passportJson
                ?.let { Json.parseToJsonElement(it).jsonObject }
                ?.get("cognitive_passport")?.jsonObject
        } catch (_: Exception) { null }
    }

    MobBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Mob.glass)
                        .border(1.dp, Mob.border, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Mob.textPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(s.cognitivePassportTitle, color = Mob.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (cp == null) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataCard(cp)
                    cp["profile"]?.jsonObject?.let { ProfileCard(it) }
                    cp["deliberation"]?.jsonObject?.let { DeliberationCard(it) }
                    cp["dissonance_triad"]?.jsonObject?.let { DissonanceCard(it) }
                    cp["routing_parameters"]?.jsonObject?.let { RoutingCard(it) }
                    cp["xai_summary"]?.jsonObject?.let { XaiCard(it) }
                    cp["agent_profile"]?.jsonObject?.let { AgentProfileCard(it) }
                    cp["spatial_context"]?.jsonObject?.let { SpatialContextCard(it) }
                    cp["top_needs_ranking"]?.jsonArray?.let { TopNeedsCard(it) }
                    passportJson?.let { RawJsonCard(it) }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

// =====================================================================
//  Empty state
// =====================================================================

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val s = LocalStrings.current
        Text("📭", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(s.noPassportTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            s.noPassportSub,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================
//  Section cards
// =====================================================================

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    MobGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mob.primary)
        subtitle?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, fontSize = 11.sp, color = Mob.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MetadataCard(cp: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdMetadata, s.pdMetadataSub) {
        KvRow("Agent ID", cp["agent_id"]?.jsonPrimitive?.content)
        KvRow("Version", cp["version"]?.jsonPrimitive?.content)
        KvRow("Model", cp["model"]?.jsonPrimitive?.content)
        KvRow("Generated at", cp["timestamp"]?.jsonPrimitive?.content)
        val topology = cp["topology"]?.jsonObject
        topology?.let {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            KvRow("Needs", it["n_needs"]?.jsonPrimitive?.intOrNull?.toString())
            KvRow("Modes", it["n_modes"]?.jsonPrimitive?.intOrNull?.toString())
            KvRow("Total nodes", it["n_nodes"]?.jsonPrimitive?.intOrNull?.toString())
        }
    }
}

@Composable
private fun ProfileCard(profile: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdProfile, s.pdProfileSub) {
        profile["needs"]?.jsonObject?.let { needs ->
            Text("Needs (0–1)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            needs.forEach { (k, v) ->
                val f = v.jsonPrimitive.doubleOrNull ?: 0.0
                BarRow(label = k, value = f.toFloat())
            }
        }
        profile["environmental_tolerances"]?.jsonObject?.let { tols ->
            Spacer(Modifier.height(8.dp))
            Text("Environmental tolerances (0–1)",
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            tols.forEach { (k, v) ->
                val f = v.jsonPrimitive.doubleOrNull ?: 0.0
                BarRow(label = k, value = f.toFloat())
            }
            profile["tolerance_note"]?.jsonPrimitive?.contentOrNull?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        profile["values"]?.jsonObject?.let { values ->
            Spacer(Modifier.height(8.dp))
            Text("Schwartz values (0–1)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            values.forEach { (k, v) ->
                val f = v.jsonPrimitive.doubleOrNull ?: 0.0
                BarRow(label = k, value = f.toFloat())
            }
        }
    }
}

@Composable
private fun DeliberationCard(delib: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdDeliberation, s.pdDeliberationSub) {
        val finalChoice = delib["final_choice"]?.jsonPrimitive?.contentOrNull
        finalChoice?.let {
            Text("Chose ${prettyMode(it)}", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
        delib["probabilities"]?.jsonObject?.let { probs ->
            Text("Mode probabilities", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            probs.forEach { (mode, p) ->
                BarRow(label = prettyMode(mode),
                       value = (p.jsonPrimitive.doubleOrNull ?: 0.0).toFloat(),
                       showPercent = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        KvRow("Confidence",
            delib["confidence"]?.jsonPrimitive?.doubleOrNull?.let { "${"%.2f".format(it * 100)}%" })
        KvRow("Reaction time",
            delib["reaction_time_seconds"]?.jsonPrimitive?.doubleOrNull?.let { "${"%.2f".format(it)}s" })
        KvRow("Decision difficulty", delib["decision_difficulty"]?.jsonPrimitive?.contentOrNull)
        KvRow("Convergence",
            delib["convergence_achieved"]?.jsonPrimitive?.booleanOrNull?.let {
                if (it) "achieved" else "not achieved"
            })
    }
}

@Composable
private fun DissonanceCard(diss: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdDissonance, s.pdDissonanceSub) {
        KvRow("Dissonance type", diss["dissonance_type"]?.jsonPrimitive?.contentOrNull)
        KvRow("Base preference",
            diss["base_preference"]?.jsonPrimitive?.contentOrNull?.let { prettyMode(it) })
        KvRow("Final choice",
            diss["final_choice"]?.jsonPrimitive?.contentOrNull?.let { prettyMode(it) })
        KvRow("Preference shifted",
            diss["preference_shifted"]?.jsonPrimitive?.booleanOrNull?.let {
                if (it) "yes" else "no"
            })
        diss["shift_explanation"]?.jsonPrimitive?.contentOrNull?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))
        Text("Structural (C) — cognitive ambivalence",
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        KvRow("C_structural",
            diss["C_structural"]?.jsonPrimitive?.doubleOrNull?.let { "%.4f".format(it) })
        KvRow("Interpretation", diss["C_interpretation"]?.jsonPrimitive?.contentOrNull)

        Spacer(Modifier.height(8.dp))
        Text("Behavioral (D) — outcome divergence",
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        KvRow("D_behavioral",
            diss["D_behavioral"]?.jsonPrimitive?.intOrNull?.toString())
        KvRow("Interpretation", diss["D_behavioral_interpretation"]?.jsonPrimitive?.contentOrNull)
        KvRow("D_behavioral continuous",
            diss["D_behavioral_continuous"]?.jsonPrimitive?.doubleOrNull?.let { "%.4f".format(it) })
        KvRow("Continuous interp",
            diss["D_behavioral_continuous_interpretation"]?.jsonPrimitive?.contentOrNull)

        Spacer(Modifier.height(8.dp))
        Text("Environmental (D_env)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        KvRow("D_environmental",
            diss["D_environmental"]?.jsonPrimitive?.doubleOrNull?.let { "%.4f".format(it) })
        KvRow("Note", diss["D_environmental_note"]?.jsonPrimitive?.contentOrNull)

        diss["extended_process_diagnostics"]?.jsonObject?.let { ext ->
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Extended diagnostics", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            ext["cognitive"]?.jsonObject?.let { sub ->
                Text("Cognitive", fontWeight = FontWeight.Medium, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary)
                sub.forEach { (k, v) -> KvRow(k, renderJsonValue(v)) }
                Spacer(Modifier.height(6.dp))
            }
            ext["affective"]?.jsonObject?.let { sub ->
                Text("Affective", fontWeight = FontWeight.Medium, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary)
                sub.forEach { (k, v) -> KvRow(k, renderJsonValue(v)) }
                Spacer(Modifier.height(6.dp))
            }
            ext["cognitive_affective"]?.jsonObject?.let { sub ->
                Text("Cognitive-affective", fontWeight = FontWeight.Medium, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary)
                sub.forEach { (k, v) -> KvRow(k, renderJsonValue(v)) }
            }
        }
    }
}

@Composable
private fun RoutingCard(routing: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdRouting, s.pdRoutingSub) {
        routing["mode_weights"]?.jsonObject?.let { mw ->
            Text("Mode weights", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            mw.forEach { (mode, p) ->
                BarRow(label = prettyMode(mode),
                       value = (p.jsonPrimitive.doubleOrNull ?: 0.0).toFloat(),
                       showPercent = true)
            }
        }
        routing["utility_coefficients"]?.jsonObject?.let { uc ->
            Spacer(Modifier.height(10.dp))
            Text("Utility coefficients", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            uc.forEach { (k, v) ->
                KvRow(k, (v.jsonPrimitive.doubleOrNull ?: 0.0).let { "%.4f".format(it) })
            }
        }
        routing["contextual_flags"]?.jsonObject?.let { cf ->
            Spacer(Modifier.height(10.dp))
            Text("Contextual flags", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            cf.forEach { (k, v) ->
                val b = v.jsonPrimitive.booleanOrNull
                KvRow(k, if (b == true) "✓ on" else "—")
            }
        }
    }
}

@Composable
private fun XaiCard(xai: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdXai, s.pdXaiSub) {
        xai["decision_narrative"]?.jsonPrimitive?.contentOrNull?.let {
            Text(it, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
        }
        xai["key_drivers"]?.jsonArray?.let { arr ->
            if (arr.isNotEmpty()) {
                Text("Key drivers", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary)
                arr.forEach { Text("• ${it.jsonPrimitive.content}", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
            }
        }
        xai["key_inhibitors"]?.jsonArray?.let { arr ->
            if (arr.isNotEmpty()) {
                Text("Key inhibitors", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error)
                arr.forEach { Text("• ${it.jsonPrimitive.content}", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
            }
        }
        KvRow("Confidence level",
            xai["confidence_level"]?.jsonPrimitive?.doubleOrNull?.let { "%.2f".format(it * 100) + "%" })
    }
}

@Composable
private fun AgentProfileCard(ap: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdAgent, null) {
        ap.forEach { (k, v) -> KvRow(k, renderJsonValue(v)) }
    }
}

@Composable
private fun SpatialContextCard(sc: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdSpatial, s.pdSpatialSub) {
        sc.forEach { (k, v) -> KvRow(k, renderJsonValue(v)) }
    }
}

@Composable
private fun TopNeedsCard(needs: JsonArray) {
    val s = LocalStrings.current
    SectionCard(s.pdTopNeeds, null) {
        needs.forEachIndexed { idx, e ->
            Text("${idx + 1}. ${e.jsonPrimitive.content}", fontSize = 13.sp)
        }
    }
}

@Composable
private fun RawJsonCard(raw: String) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.pdRawJson, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary)
                    Text(s.pdRawJsonSub,
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        prettyPrintJson(raw),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// =====================================================================
//  Small helpers
// =====================================================================

@Composable
private fun KvRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value ?: "—",
            modifier = Modifier.weight(1.2f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BarRow(label: String, value: Float, showPercent: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Text(
                if (showPercent) "${"%.1f".format(value * 100)}%"
                else "%.3f".format(value),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        LinearProgressIndicator(
            progress = value.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(4.dp)
        )
    }
}

private fun prettyMode(key: String): String = when (key.lowercase()) {
    "car"  -> "Car"
    "bike" -> "Bicycle"
    "pt"   -> "Public Transport"
    "walk" -> "Walking"
    else   -> key
}

private fun renderJsonValue(e: JsonElement): String = when (e) {
    is JsonPrimitive -> {
        if (e.isString) e.content
        else e.doubleOrNull?.let { d ->
            if (d == d.toLong().toDouble()) d.toLong().toString()
            else "%.4f".format(d)
        } ?: e.content
    }
    is JsonArray  -> e.joinToString(", ") { renderJsonValue(it) }
    is JsonObject -> e.toString()
}

private fun prettyPrintJson(raw: String): String = try {
    val parsed = Json.parseToJsonElement(raw)
    Json { prettyPrint = true; prettyPrintIndent = "  " }
        .encodeToString(JsonElement.serializer(), parsed)
} catch (_: Exception) { raw }
