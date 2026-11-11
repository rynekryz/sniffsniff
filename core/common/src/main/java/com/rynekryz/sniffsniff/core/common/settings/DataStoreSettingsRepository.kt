package com.rynekryz.sniffsniff.core.common.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

private object Keys {
    val themeMode = stringPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val pureBlack = booleanPreferencesKey("pure_black")
    val haptics = booleanPreferencesKey("haptics")
    val systemFont = booleanPreferencesKey("system_font")
    val appFilterMode = stringPreferencesKey("app_filter_mode")
    val filteredPackages = stringSetPreferencesKey("filtered_packages")
    val ipv6 = booleanPreferencesKey("ipv6")
    val customDns = stringPreferencesKey("custom_dns")
    val storePackets = booleanPreferencesKey("store_packets")
    val payloadMode = stringPreferencesKey("payload_mode")
    val maxPackets = intPreferencesKey("max_packets")
    val retentionDays = intPreferencesKey("retention_days")
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {
    private val store = context.settingsStore
    private val defaults = AppSettings()

    override val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            val next = transform(preferences.toSettings())
            preferences[Keys.themeMode] = next.themeMode.name
            preferences[Keys.dynamicColor] = next.dynamicColor
            preferences[Keys.pureBlack] = next.pureBlack
            preferences[Keys.haptics] = next.haptics
            preferences[Keys.systemFont] = next.systemFont
            preferences[Keys.appFilterMode] = next.appFilterMode.name
            preferences[Keys.filteredPackages] = next.filteredPackages
            preferences[Keys.ipv6] = next.ipv6
            preferences[Keys.customDns] = next.customDns
            preferences[Keys.storePackets] = next.storePackets
            preferences[Keys.payloadMode] = next.payloadMode.name
            preferences[Keys.maxPackets] = next.maxPackets
            preferences[Keys.retentionDays] = next.historyRetentionDays
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = enumOr(this[Keys.themeMode], defaults.themeMode),
        dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
        pureBlack = this[Keys.pureBlack] ?: defaults.pureBlack,
        haptics = this[Keys.haptics] ?: defaults.haptics,
        systemFont = this[Keys.systemFont] ?: defaults.systemFont,
        appFilterMode = enumOr(this[Keys.appFilterMode], defaults.appFilterMode),
        filteredPackages = this[Keys.filteredPackages] ?: defaults.filteredPackages,
        ipv6 = this[Keys.ipv6] ?: defaults.ipv6,
        customDns = this[Keys.customDns] ?: defaults.customDns,
        storePackets = this[Keys.storePackets] ?: defaults.storePackets,
        payloadMode = enumOr(this[Keys.payloadMode], defaults.payloadMode),
        maxPackets = this[Keys.maxPackets] ?: defaults.maxPackets,
        historyRetentionDays = this[Keys.retentionDays] ?: defaults.historyRetentionDays,
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
