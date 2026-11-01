package com.example.webview.browser

import org.json.JSONObject

data class VoiceFxStats(
    val inputDbfs: Double,
    val outputDbfs: Double,
    val rmsDbfs: Double,
    val peakDbfs: Double,
    val clip: Boolean,
    val gainReductionDb: Double,
    val sampleRate: Int,
    val latencyMs: Double,
    val blocks: List<String>,
    val spectrum: List<Int>,
    val origin: String,
) {
    companion object {
        fun fromJson(json: String): VoiceFxStats {
            val value = JSONObject(json)
            val blocks = value.optJSONArray("blocks")
            val spectrum = value.optJSONArray("spectrum")
            return VoiceFxStats(
                inputDbfs = value.optDouble("inputDbfs", -100.0),
                outputDbfs = value.optDouble("outputDbfs", -100.0),
                rmsDbfs = value.optDouble("rmsDbfs", -100.0),
                peakDbfs = value.optDouble("peakDbfs", -100.0),
                clip = value.optBoolean("clip"),
                gainReductionDb = value.optDouble("gainReductionDb"),
                sampleRate = value.optInt("sampleRate"),
                latencyMs = value.optDouble("latencyMs"),
                blocks = blocks?.let { array ->
                    (0 until array.length()).mapNotNull { index -> array.optString(index) }
                }.orEmpty(),
                spectrum = spectrum?.let { array ->
                    (0 until array.length()).map { index -> array.optInt(index) }
                }.orEmpty(),
                origin = value.optString("origin"),
            )
        }
    }
}
