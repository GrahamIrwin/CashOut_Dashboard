package com.cashoutdashboard.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** The span a tip goal covers. */
enum class GoalPeriod(val label: String) {
    WEEK("Weekly"),
    PAY_PERIOD("Per pay period"),
    MONTH("Monthly"),
    YEAR("Yearly"),
}

data class AppSettings(
    /** Use the cash take-home amount (when entered) instead of POS tips in dashboard stats. */
    val useTakeHome: Boolean = false,
    /** Any date that starts a 2-week pay period; used for the "Pay period" filter. */
    val payPeriodAnchor: String? = null,
    val goalAmount: Double? = null,
    val goalPeriod: GoalPeriod = GoalPeriod.MONTH,
    /** Show the simple "what to do next" dashboard instead of every tab, chart and stat. */
    val simpleDashboard: Boolean = false,
) {
    val payPeriodAnchorDate: LocalDate? get() = payPeriodAnchor?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        useTakeHome = prefs.getBoolean("useTakeHome", false),
        payPeriodAnchor = prefs.getString("payAnchor", null),
        goalAmount = prefs.getFloat("goalAmount", -1f).takeIf { it > 0 }?.toDouble(),
        goalPeriod = runCatching { GoalPeriod.valueOf(prefs.getString("goalPeriod", null) ?: "") }.getOrDefault(GoalPeriod.MONTH),
        simpleDashboard = prefs.getBoolean("simpleDashboard", false),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putBoolean("useTakeHome", s.useTakeHome)
            .putString("payAnchor", s.payPeriodAnchor)
            .putFloat("goalAmount", s.goalAmount?.toFloat() ?: -1f)
            .putString("goalPeriod", s.goalPeriod.name)
            .putBoolean("simpleDashboard", s.simpleDashboard)
            .remove("advancedDashboard").remove("engine").remove("apiKey").remove("model")
            .apply()
        _settings.value = s
    }
}
