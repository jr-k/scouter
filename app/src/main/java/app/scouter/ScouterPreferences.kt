package app.scouter

import android.content.Context

enum class PullDownAction {
    Minimize,
    Close,
}

enum class SheetControlsSide {
    Left,
    Right,
}

object ScouterPreferences {
    private const val PREFERENCES = "scouter_preferences"
    private const val KEY_PULL_DOWN_ACTION = "pull_down_action"
    private const val KEY_MINIMIZE_ON_ACTION = "minimize_on_action"
    private const val KEY_SHEET_CONTROLS_SIDE = "sheet_controls_side"

    fun pullDownAction(context: Context): PullDownAction {
        val stored = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_PULL_DOWN_ACTION, null)
        return PullDownAction.entries.firstOrNull { it.name == stored } ?: PullDownAction.Minimize
    }

    fun setPullDownAction(context: Context, action: PullDownAction) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PULL_DOWN_ACTION, action.name)
            .apply()
    }

    fun minimizeOnAction(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_MINIMIZE_ON_ACTION, false)

    fun setMinimizeOnAction(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MINIMIZE_ON_ACTION, enabled)
            .apply()
    }

    fun sheetControlsSide(context: Context): SheetControlsSide {
        val stored = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_SHEET_CONTROLS_SIDE, null)
        return SheetControlsSide.entries.firstOrNull { it.name == stored } ?: SheetControlsSide.Right
    }

    fun setSheetControlsSide(context: Context, side: SheetControlsSide) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHEET_CONTROLS_SIDE, side.name)
            .apply()
    }
}
