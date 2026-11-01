package com.example.webview.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.webview.browser.VoiceFxPreset
import com.example.webview.browser.VoiceFxRange
import com.example.webview.browser.VoiceFxScale
import com.example.webview.data.SettingsRepository
import com.example.webview.data.VoiceFxOrigin
import com.example.webview.data.VoiceFxSettings
import com.example.webview.ui.components.ActionButton
import com.example.webview.ui.components.AcrylicChip
import com.example.webview.ui.components.AcrylicPanel
import com.example.webview.ui.components.AcrylicSlider
import com.example.webview.ui.components.AcrylicSwitch
import com.example.webview.ui.theme.ActionBlue
import com.example.webview.ui.theme.Canvas as CanvasColor
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.SurfaceRaised
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.TextSecondary
import com.example.webview.ui.theme.UiSpacing
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay

private enum class StudioSection(val label: String) {
    BASIC("Cơ bản"),
    FILTERS("Bộ lọc & EQ"),
    EFFECTS("Hiệu ứng"),
    PRESETS("Preset"),
}

@Composable
fun MiniStudioScreen(
    settingsRepository: SettingsRepository,
    currentOrigin: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val ranges = remember(context) {
        val json = context.assets.open("voicefx-mapping.json")
            .bufferedReader().use { it.readText() }
        VoiceFxScale.parseTable(json)
    }
    val settings by settingsRepository.voiceFxSettings.collectAsState(
        initial = VoiceFxSettings(),
    )
    var draft by remember(settings) { mutableStateOf(settings) }
    var section by rememberSaveable { mutableStateOf(StudioSection.BASIC) }
    var originInput by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var presetName by rememberSaveable { mutableStateOf("") }
    var importText by rememberSaveable { mutableStateOf("") }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager

    LaunchedEffect(draft) {
        delay(120)
        settingsRepository.updateVoiceFx { draft }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasColor)
            .padding(horizontal = UiSpacing.Medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mini Studio",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Safe microphone processing", color = TextSecondary, fontSize = 12.sp)
            }
            AcrylicSwitch(
                checked = draft.enabled,
                onCheckedChange = { enabled ->
                    draft = draft.copy(enabled = enabled)
                    message = if (enabled && draft.allowedOrigins.isEmpty()) {
                        "Add an HTTPS origin before processing can run."
                    } else ""
                },
            )
        }

        AcrylicPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Output is limited to approximately −12 dBFS. This digital limit cannot guarantee a safe acoustic volume; lower your device volume.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(UiSpacing.Small))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "HUD meters",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                )
                AcrylicSwitch(
                    checked = draft.hudVisible,
                    onCheckedChange = { draft = draft.copy(hudVisible = it) },
                )
            }
            Text(
                if (currentOrigin == null) "Current page: no HTTPS origin"
                else "Current page: $currentOrigin",
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Local speaker monitoring is omitted; acoustic output cannot be guaranteed safe by a digital limiter.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(UiSpacing.Small))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
        ) {
            StudioSection.entries.forEach { item ->
                AcrylicChip(
                    label = item.label,
                    selected = item == section,
                    onClick = { section = item },
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = UiSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            when (section) {
                StudioSection.BASIC -> {
                    ParameterControl(
                        "Output gain",
                        "outputGain",
                        draft,
                        ranges,
                        null,
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Noise gate",
                        "noiseGateDb",
                        draft,
                        ranges,
                        "noiseGate",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Compressor",
                        "compressorRatio",
                        draft,
                        ranges,
                        "compressor",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Pitch",
                        "pitchSemitones",
                        draft,
                        ranges,
                        "pitch",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Formant",
                        "formantSemitones",
                        draft,
                        ranges,
                        "pitch",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Reverb",
                        "reverbMix",
                        draft,
                        ranges,
                        "reverb",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Room size",
                        "reverbRoom",
                        draft,
                        ranges,
                        "reverb",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Reverb decay",
                        "reverbDecay",
                        draft,
                        ranges,
                        "reverb",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Echo",
                        "echoMix",
                        draft,
                        ranges,
                        "echo",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Echo time",
                        "echoMs",
                        draft,
                        ranges,
                        "echo",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                }

                StudioSection.FILTERS -> {
                    ParameterControl("High-pass", "highPassHz", draft, ranges, "highPass") {
                            key, position -> draft = draft.setParameter(key, position)
                    }
                    ParameterControl("Low-pass", "lowPassHz", draft, ranges, "lowPass") {
                            key, position -> draft = draft.setParameter(key, position)
                    }
                    Text(
                        "Five-band equalizer · ±12 dB",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                    ) {
                        listOf(
                            "100 Hz" to "eqLowDb",
                            "300 Hz" to "eq300Db",
                            "1 kHz" to "eq1kDb",
                            "3.5 kHz" to "eq35kDb",
                            "8 kHz" to "eqHighDb",
                        ).forEach { (label, key) ->
                            VerticalEqControl(
                                label = label,
                                key = key,
                                settings = draft,
                                range = ranges.getValue(key),
                                onValue = { position ->
                                    draft = draft.setParameter(key, position)
                                },
                            )
                        }
                    }
                    BlockToggle("Equalizer", "eq", draft) { enabled ->
                        draft = draft.setBlock("eq", enabled)
                    }
                }

                StudioSection.EFFECTS -> {
                    ParameterControl(
                        "Distortion",
                        "distortion",
                        draft,
                        ranges,
                        "distortion",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Bit depth",
                        "bitDepth",
                        draft,
                        ranges,
                        "bitcrusher",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Chorus",
                        "chorusMix",
                        draft,
                        ranges,
                        "chorus",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    ParameterControl(
                        "Robot modulation",
                        "robotMix",
                        draft,
                        ranges,
                        "robot",
                    ) { key, position -> draft = draft.setParameter(key, position) }
                    Text(
                        "Limiter and output ceiling are always enabled.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                StudioSection.PRESETS -> {
                    PresetPanel(
                        settings = draft,
                        onApply = { preset ->
                            draft = draft.copy(
                                presetId = preset.id,
                                enabled = preset.id != "off",
                                normalizedParameters = preset.normalizedParameters
                                    .mapValues { (_, value) -> value.coerceIn(0, 100) },
                                enabledBlocks = preset.enabledBlocks,
                            )
                            message = "Preset applied."
                        },
                        onSave = { showSaveDialog = true },
                        onDelete = { id ->
                            draft = draft.copy(
                                customPresets = draft.customPresets.filterNot { it.id == id },
                            )
                        },
                        onExport = {
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "Mini Studio presets",
                                    VoiceFxPreset.export(draft.customPresets),
                                ),
                            )
                            message = "Custom presets copied as JSON."
                        },
                        onImport = { showImportDialog = true },
                    )
                }
            }

            if (section != StudioSection.PRESETS) {
                OriginPanel(
                    origins = draft.allowedOrigins,
                    currentOrigin = currentOrigin,
                    input = originInput,
                    onInput = { originInput = it },
                    onAdd = {
                        val origin = VoiceFxOrigin.normalize(originInput)
                        if (origin == null) {
                            message = "Enter an exact HTTPS origin, without a path or wildcard."
                        } else {
                            draft = draft.copy(allowedOrigins = draft.allowedOrigins + origin)
                            originInput = ""
                            message = "Origin added."
                        }
                    },
                    onRemove = { origin ->
                        draft = draft.copy(allowedOrigins = draft.allowedOrigins - origin)
                    },
                    onAddCurrent = {
                        if (currentOrigin == null) {
                            message = "The current page does not have an HTTPS origin."
                        } else {
                            draft = draft.copy(
                                allowedOrigins = draft.allowedOrigins + currentOrigin,
                            )
                            message = "Current origin added."
                        }
                    },
                )
            }
        }

        if (message.isNotBlank()) {
            Text(
                message,
                modifier = Modifier.padding(vertical = UiSpacing.Small),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showSaveDialog) {
        StudioTextDialog(
            title = "Save preset",
            value = presetName,
            onValueChange = { presetName = it },
            confirm = "Save",
            onConfirm = {
                val name = presetName.trim()
                if (name.isNotEmpty()) {
                    val preset = VoiceFxPreset(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        normalizedParameters = draft.normalizedParameters
                            .mapValues { (_, value) -> value.coerceIn(0, 100) },
                        enabledBlocks = draft.enabledBlocks,
                    )
                    draft = draft.copy(customPresets = draft.customPresets + preset)
                    presetName = ""
                    message = "Preset saved."
                }
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    if (showImportDialog) {
        StudioTextDialog(
            title = "Import preset JSON",
            value = importText,
            onValueChange = { importText = it },
            confirm = "Import",
            multiline = true,
            onConfirm = {
                runCatching { VoiceFxPreset.parseExport(importText) }
                    .onSuccess { imported ->
                        val safe = imported.map { preset ->
                            preset.copy(
                                normalizedParameters = preset.normalizedParameters
                                    .mapValues { (_, value) -> value.coerceIn(0, 100) },
                            )
                        }
                        draft = draft.copy(
                            customPresets = (draft.customPresets + safe)
                                .distinctBy(VoiceFxPreset::id),
                        )
                        message = "Presets imported."
                    }
                    .onFailure { error ->
                        message = error.message ?: "Preset JSON could not be imported."
                    }
                importText = ""
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false },
        )
    }
}

@Composable
private fun ParameterControl(
    title: String,
    key: String,
    settings: VoiceFxSettings,
    ranges: Map<String, VoiceFxRange>,
    block: String?,
    onValue: (String, Int) -> Unit,
) {
    val enabled = block == null || block in settings.enabledBlocks
    val position = settings.normalizedParameters[key] ?: defaultPosition(key)
    val range = ranges.getValue(key)
    val actual = VoiceFxScale.map(position, extended = false, range = range)
    AcrylicPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    "${formatVoiceFxValue(key, actual)} · $position / 100",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (block != null) {
                AcrylicSwitch(
                    checked = enabled,
                    onCheckedChange = { onValue("$block:block", if (it) 1 else 0) },
                )
            }
        }
        AcrylicSlider(
            value = position.coerceIn(0, 100) / 100f,
            enabled = enabled,
            onValueChange = { onValue(key, (it * 100).toInt().coerceIn(0, 100)) },
        )
    }
}

@Composable
private fun VerticalEqControl(
    label: String,
    key: String,
    settings: VoiceFxSettings,
    range: VoiceFxRange,
    onValue: (Int) -> Unit,
) {
    val position = settings.normalizedParameters[key] ?: 50
    val actual = VoiceFxScale.map(position, false, range)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(176.dp),
            contentAlignment = Alignment.Center,
        ) {
            AcrylicSlider(
                value = position.coerceIn(0, 100) / 100f,
                onValueChange = { onValue((it * 100).toInt().coerceIn(0, 100)) },
                modifier = Modifier
                    .width(152.dp)
                    .graphicsLayer { rotationZ = 270f },
            )
        }
        Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
        Text(
            String.format(Locale.ROOT, "%.1f dB", actual),
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BlockToggle(
    title: String,
    block: String,
    settings: VoiceFxSettings,
    onChanged: (Boolean) -> Unit,
) {
    AcrylicPanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, modifier = Modifier.weight(1f))
            AcrylicSwitch(block in settings.enabledBlocks, onChanged)
        }
    }
}

@Composable
private fun PresetPanel(
    settings: VoiceFxSettings,
    onApply: (VoiceFxPreset) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val presets = VoiceFxPreset.builtIns + settings.customPresets
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Small)) {
        presets.forEach { preset ->
            AcrylicPanel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            if (preset.id == "off") "Bypass"
                            else "${preset.enabledBlocks.size} processing blocks",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    ActionButton("Use", onClick = { onApply(preset) })
                    if (preset in settings.customPresets) {
                        IconButton(onClick = { onDelete(preset.id) }) {
                            Icon(Icons.Default.Delete, "Delete preset", tint = TextSecondary)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)) {
            ActionButton("Save current", onClick = onSave, modifier = Modifier.weight(1f))
            ActionButton(
                "Export",
                onClick = onExport,
                primary = false,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                "Import",
                onClick = onImport,
                primary = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OriginPanel(
    origins: Set<String>,
    currentOrigin: String?,
    input: String,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onAddCurrent: () -> Unit,
) {
    AcrylicPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Enabled origins",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Only exact HTTPS origins are eligible. Microphone permission is still requested by the page.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(UiSpacing.Small))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudioTextField(
                input,
                onInput,
                hint = "https://meet.example.com",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, "Add origin", tint = ActionBlue)
            }
        }
        if (currentOrigin != null && currentOrigin !in origins) {
            ActionButton(
                "Allow current origin",
                onClick = onAddCurrent,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        origins.sorted().forEach { origin ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    origin,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    maxLines = 1,
                )
                IconButton(onClick = { onRemove(origin) }) {
                    Icon(Icons.Default.Delete, "Remove $origin", tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun StudioTextDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirm: String,
    multiline: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AcrylicPanel(radius = UiSpacing.SheetCorner) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(UiSpacing.Medium))
            StudioTextField(
                value,
                onValueChange,
                hint = if (multiline) "Paste JSON" else "Preset name",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (multiline) 180.dp else UiSpacing.TouchTarget),
                multiline = multiline,
            )
            Spacer(Modifier.height(UiSpacing.Medium))
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)) {
                ActionButton(confirm, onConfirm, modifier = Modifier.weight(1f))
                ActionButton(
                    "Cancel",
                    onDismiss,
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
) {
    val shape = RoundedCornerShape(UiSpacing.CardCorner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceRaised)
            .border(BorderStroke(UiSpacing.Outline, EdgeTint), shape)
            .padding(horizontal = UiSpacing.Medium, vertical = UiSpacing.Small),
    ) {
        if (value.isEmpty()) {
            Text(hint, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = !multiline,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            keyboardOptions = KeyboardOptions(
                imeAction = if (multiline) ImeAction.Default else ImeAction.Done,
            ),
        )
    }
}

private fun VoiceFxSettings.setParameter(key: String, position: Int): VoiceFxSettings {
    if (key.endsWith(":block")) {
        return setBlock(key.removeSuffix(":block"), position != 0)
    }
    return copy(normalizedParameters = normalizedParameters + (key to position.coerceIn(0, 100)))
}

private fun VoiceFxSettings.setBlock(block: String, enabled: Boolean): VoiceFxSettings {
    val blocks = enabledBlocks.toMutableSet()
    if (enabled) blocks.add(block) else blocks.remove(block)
    return copy(enabledBlocks = blocks)
}

private fun defaultPosition(key: String): Int = when (key) {
    "outputGain" -> 100
    "highPassHz", "noiseGateDb", "distortion", "chorusMix", "robotMix", "echoMix",
    "reverbMix", "reverbDecay", "compressorRatio" -> 0
    "lowPassHz" -> 100
    "bitDepth" -> 100
    else -> 50
}

internal fun formatVoiceFxValue(key: String, value: Double): String {
    val (format, unit) = when (key) {
        "outputGain" -> "%.2f" to "×"
        "noiseGateDb", "eqLowDb", "eq300Db", "eq1kDb", "eq35kDb", "eqHighDb" ->
            "%.1f" to "dB"
        "highPassHz", "lowPassHz" -> "%.0f" to "Hz"
        "pitchSemitones", "formantSemitones" -> "%.1f" to "st"
        "compressorRatio" -> "%.1f" to "×"
        "echoMs" -> "%.0f" to "ms"
        "reverbDecay" -> "%.1f" to "s"
        "bitDepth" -> "%.0f" to "bit"
        else -> "%.0f" to "%"
    }
    return String.format(Locale.ROOT, format, value) + " " + unit
}
