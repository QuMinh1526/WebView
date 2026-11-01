package com.example.webview.browser

import org.json.JSONArray
import org.json.JSONObject

data class VoiceFxPreset(
    val id: String,
    val name: String,
    val normalizedParameters: Map<String, Int>,
    val enabledBlocks: Set<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put(
            "parameters",
            JSONObject().also { json ->
                normalizedParameters.forEach { (key, value) ->
                    json.put(key, value.coerceIn(0, VoiceFxScale.EXTENDED_MAX))
                }
            },
        )
        .put("blocks", JSONArray(enabledBlocks.sorted()))

    companion object {
        private val presetBlocks = setOf(
            "noiseGate", "highPass", "lowPass", "eq", "compressor", "pitch",
            "distortion", "bitcrusher", "chorus", "robot", "echo", "reverb",
        )

        val builtIns = listOf(
            VoiceFxPreset("off", "Tắt", emptyMap(), emptySet()),
            VoiceFxPreset(
                "clear", "Giọng to rõ",
                mapOf("outputGain" to 100, "noiseGateDb" to 55, "highPassHz" to 18,
                    "lowPassHz" to 90, "compressorRatio" to 35),
                presetBlocks - setOf("distortion", "bitcrusher", "chorus", "robot", "echo", "reverb"),
            ),
            VoiceFxPreset(
                "podcast", "Podcast",
                mapOf("outputGain" to 90, "highPassHz" to 20, "lowPassHz" to 88,
                    "eqLowDb" to 42, "eq300Db" to 45, "eq1kDb" to 56,
                    "eq35kDb" to 62, "eqHighDb" to 52, "compressorRatio" to 48),
                presetBlocks - setOf("distortion", "bitcrusher", "chorus", "robot", "echo"),
            ),
            VoiceFxPreset(
                "radio", "Radio / Điện thoại",
                mapOf("highPassHz" to 28, "lowPassHz" to 54, "compressorRatio" to 58),
                setOf("highPass", "lowPass", "eq", "compressor"),
            ),
            VoiceFxPreset(
                "cave", "Vang hang động",
                mapOf("reverbMix" to 58, "reverbRoom" to 85, "reverbDecay" to 90),
                setOf("reverb"),
            ),
            VoiceFxPreset(
                "robot", "Robot",
                mapOf("robotMix" to 65, "bitDepth" to 45),
                setOf("robot", "bitcrusher"),
            ),
            VoiceFxPreset(
                "deep", "Trầm",
                mapOf("pitchSemitones" to 32, "formantSemitones" to 35),
                setOf("pitch"),
            ),
            VoiceFxPreset(
                "bright", "Cao",
                mapOf("pitchSemitones" to 68, "formantSemitones" to 65),
                setOf("pitch"),
            ),
            VoiceFxPreset(
                "female", "Giọng nữ",
                mapOf("pitchSemitones" to 68, "formantSemitones" to 70),
                setOf("pitch"),
            ),
            VoiceFxPreset(
                "male", "Giọng nam",
                mapOf("pitchSemitones" to 32, "formantSemitones" to 30),
                setOf("pitch"),
            ),
        )

        fun fromJson(json: JSONObject): VoiceFxPreset {
            val values = json.optJSONObject("parameters")
                ?: throw IllegalArgumentException("Preset parameters are missing.")
            val parameters = values.keys().asSequence().associateWith { key ->
                values.optInt(key, 0).coerceIn(0, VoiceFxScale.EXTENDED_MAX)
            }
            val blocks = json.optJSONArray("blocks") ?: JSONArray()
            return VoiceFxPreset(
                id = json.optString("id").takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("Preset id is missing."),
                name = json.optString("name").takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("Preset name is missing."),
                normalizedParameters = parameters,
                enabledBlocks = (0 until blocks.length())
                    .mapNotNull { blocks.optString(it).takeIf(String::isNotBlank) }
                    .toSet(),
            )
        }

        fun export(presets: List<VoiceFxPreset>): String = JSONObject()
            .put("schema", 1)
            .put("presets", JSONArray().also { array ->
                presets.forEach { array.put(it.toJson()) }
            })
            .toString()

        fun parseExport(json: String): List<VoiceFxPreset> {
            val root = JSONObject(json)
            require(root.optInt("schema") == 1) { "Unsupported preset file version." }
            val items = root.optJSONArray("presets")
                ?: throw IllegalArgumentException("Preset list is missing.")
            return (0 until items.length()).map { index ->
                fromJson(items.getJSONObject(index))
            }
        }
    }
}
