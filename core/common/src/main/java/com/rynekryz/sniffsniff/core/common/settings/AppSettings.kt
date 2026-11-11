package com.rynekryz.sniffsniff.core.common.settings

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppFilterMode { ALL, ONLY_SELECTED, EXCLUDE_SELECTED }

enum class PayloadMode { HEADERS, PREVIEW, FULL }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val haptics: Boolean = true,
    val systemFont: Boolean = false,
    val appFilterMode: AppFilterMode = AppFilterMode.ALL,
    val filteredPackages: Set<String> = emptySet(),
    val ipv6: Boolean = true,
    val customDns: String = "",
    val storePackets: Boolean = true,
    val payloadMode: PayloadMode = PayloadMode.PREVIEW,
    val maxPackets: Int = 5000,
    val historyRetentionDays: Int = 7,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
