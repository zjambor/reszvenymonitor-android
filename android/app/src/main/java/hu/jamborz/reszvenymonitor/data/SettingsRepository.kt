package hu.jamborz.reszvenymonitor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import hu.jamborz.reszvenymonitor.BuildConfig
import hu.jamborz.reszvenymonitor.domain.FxConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "monitor_settings")

/**
 * A webes localStorage megfelelője: megjelenítési deviza és az utoljára nézett
 * ticker. A deviza olvasásakor a webes loadDisplayCurrency szabálya érvényes:
 * mentett érték → BuildConfig alapérték → USD, és csak a DISPLAY_CURRENCIES
 * listában szereplő érték fogadható el.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
        val LAST_TICKER = stringPreferencesKey("last_ticker")
    }

    private fun sanitizeCurrency(value: String?): String {
        if (value != null && value in FxConverter.DISPLAY_CURRENCIES) return value
        val default = BuildConfig.DEFAULT_DISPLAY_CURRENCY
        return if (default in FxConverter.DISPLAY_CURRENCIES) default else "USD"
    }

    val displayCurrency: Flow<String> = context.settingsDataStore.data
        .map { sanitizeCurrency(it[Keys.DISPLAY_CURRENCY]) }

    suspend fun setDisplayCurrency(currency: String) {
        if (currency !in FxConverter.DISPLAY_CURRENCIES) return
        context.settingsDataStore.edit { it[Keys.DISPLAY_CURRENCY] = currency }
    }

    /** Az utoljára kiválasztott ticker (null → a BuildConfig alapértéke dönt). */
    val lastTicker: Flow<String?> = context.settingsDataStore.data
        .map { it[Keys.LAST_TICKER] }

    suspend fun setLastTicker(symbol: String) {
        context.settingsDataStore.edit { it[Keys.LAST_TICKER] = symbol }
    }
}
