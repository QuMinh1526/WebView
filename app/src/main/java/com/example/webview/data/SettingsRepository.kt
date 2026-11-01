package com.example.webview.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.webview.browser.VoiceFxPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException
import java.util.Base64

private val Context.browserDataStore by preferencesDataStore(name = "browser_settings")

class SettingsRepository(context: Context) {
    private val store: DataStore<Preferences> = context.applicationContext.browserDataStore

    val settings: Flow<BrowserSettings> = store.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            BrowserSettings(
                desktopByDefault = preferences[DESKTOP] ?: false,
                acceptThirdPartyCookies = preferences[THIRD_PARTY] ?: true,
                blockPopups = preferences[BLOCK_POPUPS] ?: true,
                domainBlocklistEnabled = preferences[BLOCKLIST_ENABLED] ?: false,
                blockedDomains = preferences[BLOCKED_DOMAINS]
                    .orEmpty()
                    .split('\n')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet(),
                biometricLock = preferences[BIOMETRIC] ?: false,
                searchTemplate = preferences[SEARCH_TEMPLATE]
                    ?: "https://www.google.com/search?q=%s",
            )
        }

    val voiceFxSettings: Flow<VoiceFxSettings> = store.data.map { preferences ->
        VoiceFxSettings(
            enabled = preferences[VOICE_FX_ENABLED] ?: false,
            presetId = preferences[VOICE_FX_PRESET] ?: "off",
            normalizedParameters = decodeIntMap(preferences[VOICE_FX_PARAMETERS].orEmpty()),
            enabledBlocks = preferences[VOICE_FX_BLOCKS].orEmpty()
                .split('\n').filter(String::isNotBlank).toSet(),
            allowedOrigins = preferences[VOICE_FX_ORIGINS].orEmpty()
                .split('\n').mapNotNull(VoiceFxOrigin::normalize).toSet(),
            hudVisible = preferences[VOICE_FX_HUD_VISIBLE] ?: true,
            customPresets = preferences[VOICE_FX_CUSTOM_PRESETS]
                ?.let(VoiceFxPreset::parseExport).orEmpty(),
        )
    }

    suspend fun updateVoiceFx(transform: (VoiceFxSettings) -> VoiceFxSettings) {
        store.edit { preferences ->
            val old = VoiceFxSettings(
                enabled = preferences[VOICE_FX_ENABLED] ?: false,
                presetId = preferences[VOICE_FX_PRESET] ?: "off",
                normalizedParameters = decodeIntMap(
                    preferences[VOICE_FX_PARAMETERS].orEmpty(),
                ),
                enabledBlocks = preferences[VOICE_FX_BLOCKS].orEmpty()
                    .split('\n').filter(String::isNotBlank).toSet(),
                allowedOrigins = preferences[VOICE_FX_ORIGINS].orEmpty()
                    .split('\n').mapNotNull(VoiceFxOrigin::normalize).toSet(),
                hudVisible = preferences[VOICE_FX_HUD_VISIBLE] ?: true,
                customPresets = preferences[VOICE_FX_CUSTOM_PRESETS]
                    ?.let(VoiceFxPreset::parseExport).orEmpty(),
            )
            val next = transform(old)
            preferences[VOICE_FX_ENABLED] = next.enabled
            preferences[VOICE_FX_PRESET] = next.presetId
            preferences[VOICE_FX_PARAMETERS] = encodeIntMap(next.normalizedParameters)
            preferences[VOICE_FX_BLOCKS] = next.enabledBlocks.joinToString("\n")
            preferences[VOICE_FX_ORIGINS] = next.allowedOrigins
                .mapNotNull(VoiceFxOrigin::normalize).sorted().joinToString("\n")
            preferences[VOICE_FX_HUD_VISIBLE] = next.hudVisible
            preferences[VOICE_FX_CUSTOM_PRESETS] = VoiceFxPreset.export(next.customPresets)
        }
    }

    suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        store.edit { preferences ->
            val old = BrowserSettings(
                desktopByDefault = preferences[DESKTOP] ?: false,
                acceptThirdPartyCookies = preferences[THIRD_PARTY] ?: true,
                blockPopups = preferences[BLOCK_POPUPS] ?: true,
                domainBlocklistEnabled = preferences[BLOCKLIST_ENABLED] ?: false,
                blockedDomains = preferences[BLOCKED_DOMAINS]
                    .orEmpty()
                    .split('\n')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet(),
                biometricLock = preferences[BIOMETRIC] ?: false,
                searchTemplate = preferences[SEARCH_TEMPLATE]
                    ?: "https://www.google.com/search?q=%s",
            )
            val next = transform(old)
            preferences[DESKTOP] = next.desktopByDefault
            preferences[THIRD_PARTY] = next.acceptThirdPartyCookies
            preferences[BLOCK_POPUPS] = next.blockPopups
            preferences[BLOCKLIST_ENABLED] = next.domainBlocklistEnabled
            preferences[BLOCKED_DOMAINS] = next.blockedDomains.joinToString("\n")
            preferences[BIOMETRIC] = next.biometricLock
            preferences[SEARCH_TEMPLATE] = next.searchTemplate
        }
    }

    suspend fun hasSiteGrant(origin: String, resources: Set<String>): Boolean {
        val saved = store.data.first()[permissionKey(origin)]
            .orEmpty()
            .split(',')
            .filter(String::isNotBlank)
            .toSet()
        return saved.containsAll(resources)
    }

    suspend fun saveSiteGrant(origin: String, resources: Set<String>) {
        store.edit { preferences ->
            val key = permissionKey(origin)
            val saved = preferences[key].orEmpty()
                .split(',')
                .filter(String::isNotBlank)
                .toMutableSet()
            preferences[key] = (saved + resources).joinToString(",")
        }
    }

    suspend fun revokeSiteGrant(origin: String) {
        store.edit { it.remove(permissionKey(origin)) }
    }

    suspend fun clearSiteGrants() {
        store.edit { preferences ->
            preferences.asMap().keys
                .filter { it.name.startsWith(SITE_PERMISSION_PREFIX) }
                .forEach { key -> preferences.remove(key) }
        }
    }

    val siteGrants: Flow<Map<String, Set<String>>> = store.data.map { preferences ->
        preferences.asMap()
            .filterKeys { it.name.startsWith(SITE_PERMISSION_PREFIX) }
            .mapNotNull { (key, value) ->
                val origin = decodeOrigin(key.name.removePrefix(SITE_PERMISSION_PREFIX))
                    ?: return@mapNotNull null
                origin to value.toString()
                    .split(',')
                    .filter(String::isNotBlank)
                    .toSet()
            }
            .toMap()
    }

    companion object {
        private val DESKTOP = booleanPreferencesKey("desktop_by_default")
        private val THIRD_PARTY = booleanPreferencesKey("third_party_cookies")
        private val BLOCK_POPUPS = booleanPreferencesKey("block_popups")
        private val BLOCKLIST_ENABLED = booleanPreferencesKey("blocklist_enabled")
        private val BLOCKED_DOMAINS = stringPreferencesKey("blocked_domains")
        private val BIOMETRIC = booleanPreferencesKey("biometric_lock")
        private val SEARCH_TEMPLATE = stringPreferencesKey("search_template")
        private val VOICE_FX_ENABLED = booleanPreferencesKey("voice_fx_enabled")
        private val VOICE_FX_PRESET = stringPreferencesKey("voice_fx_preset")
        private val VOICE_FX_PARAMETERS = stringPreferencesKey("voice_fx_parameters")
        private val VOICE_FX_BLOCKS = stringPreferencesKey("voice_fx_blocks")
        private val VOICE_FX_ORIGINS = stringPreferencesKey("voice_fx_origins")
        private val VOICE_FX_HUD_VISIBLE = booleanPreferencesKey("voice_fx_hud_visible")
        private val VOICE_FX_CUSTOM_PRESETS = stringPreferencesKey("voice_fx_custom_presets")
        private const val SITE_PERMISSION_PREFIX = "site_permission_"

        private fun permissionKey(origin: String) = stringPreferencesKey(
            SITE_PERMISSION_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(origin.toByteArray(Charsets.UTF_8)),
        )

        private fun decodeOrigin(encoded: String): String? = runCatching {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        }.getOrNull()

        private fun decodeIntMap(value: String): Map<String, Int> {
            if (value.isBlank()) return emptyMap()
            val json = JSONObject(value)
            return json.keys().asSequence().associateWith { key ->
                json.optInt(key).coerceIn(0, VoiceFxScaleLimit)
            }
        }

        private fun encodeIntMap(values: Map<String, Int>): String =
            JSONObject().also { json ->
                values.forEach { (key, value) ->
                    json.put(key, value.coerceIn(0, VoiceFxScaleLimit))
                }
            }.toString()

        private const val VoiceFxScaleLimit = 1000
    }
}
