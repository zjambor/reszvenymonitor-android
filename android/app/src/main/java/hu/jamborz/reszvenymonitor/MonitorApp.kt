package hu.jamborz.reszvenymonitor

import android.app.Application
import hu.jamborz.reszvenymonitor.data.ApiGuard
import hu.jamborz.reszvenymonitor.data.AuthRepository
import hu.jamborz.reszvenymonitor.data.DetailsRepository
import hu.jamborz.reszvenymonitor.data.FxRepository
import hu.jamborz.reszvenymonitor.data.PortfolioRepository
import hu.jamborz.reszvenymonitor.data.PriceRepository
import hu.jamborz.reszvenymonitor.data.SettingsRepository
import hu.jamborz.reszvenymonitor.data.SupabaseModule
import hu.jamborz.reszvenymonitor.data.TickerRepository
import io.github.jan.supabase.SupabaseClient

/**
 * Application-szintű, kézi DI-konténer (TERV-ANDROID.md: nincs DI-keretrendszer).
 * A gráf lusta: a Supabase-kliens az első hozzáféréskor épül fel, és a
 * repository-k innen kapják a függőségeiket.
 */
class MonitorApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: android.content.Context) {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context.applicationContext) }
    val supabase: SupabaseClient by lazy { SupabaseModule.create() }
    val authRepository: AuthRepository by lazy { AuthRepository(supabase) }
    private val guard: ApiGuard by lazy { ApiGuard(authRepository) }
    val tickerRepository: TickerRepository by lazy { TickerRepository(supabase, guard) }
    val portfolioRepository: PortfolioRepository by lazy { PortfolioRepository(supabase, guard) }
    val priceRepository: PriceRepository by lazy { PriceRepository(supabase, guard) }
    val fxRepository: FxRepository by lazy { FxRepository(supabase, guard) }
    val detailsRepository: DetailsRepository by lazy { DetailsRepository(supabase, guard) }
}
