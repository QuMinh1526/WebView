package com.example.webview.browser

import kotlin.math.pow

data class VoiceFxRange(
    val minimum: Double,
    val normalMaximum: Double,
    val extendedMaximum: Double,
    val curvePower: Double,
)

object VoiceFxScale {
    private val rangePattern = Regex(
        """"([A-Za-z0-9]+)"\s*:\s*\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)\s*]""",
    )

    fun parseTable(json: String): Map<String, VoiceFxRange> {
        val ranges = rangePattern.findAll(json).associate { match ->
            val (key, minimum, normalMax, extendedMax, curve) = match.destructured
            key to VoiceFxRange(
                minimum = minimum.toDouble(),
                normalMaximum = normalMax.toDouble(),
                extendedMaximum = extendedMax.toDouble(),
                curvePower = curve.toDouble(),
            )
        }
        require(ranges.isNotEmpty()) { "Voice FX mapping table is empty or invalid." }
        require(ranges.values.all {
            it.minimum.isFinite() && it.normalMaximum.isFinite() &&
                it.extendedMaximum.isFinite() && it.curvePower > 0.0
        }) { "Voice FX mapping table contains invalid bounds." }
        return ranges
    }

    fun map(position: Int, extended: Boolean, range: VoiceFxRange): Double {
        val bounded = position.coerceIn(0, if (extended) EXTENDED_MAX else NORMAL_MAX)
        val normalPosition = bounded.coerceAtMost(NORMAL_MAX) / NORMAL_MAX.toDouble()
        val normalValue = range.minimum +
            (range.normalMaximum - range.minimum) * normalPosition
        if (!extended || bounded <= NORMAL_MAX) return normalValue

        val fraction = (bounded - NORMAL_MAX).toDouble() /
            (EXTENDED_MAX - NORMAL_MAX).toDouble()
        val curved = fraction.pow(range.curvePower)
        return range.normalMaximum +
            (range.extendedMaximum - range.normalMaximum) * curved
    }

    fun normalizedParameters(
        positions: Map<String, Int>,
        extended: Boolean,
        ranges: Map<String, VoiceFxRange>,
    ): Map<String, Double> = positions.mapNotNull { (name, value) ->
        ranges[name]?.let { name to map(value, extended, it) }
    }.toMap()

    const val NORMAL_MAX = 100
    const val EXTENDED_MAX = 1000
}
