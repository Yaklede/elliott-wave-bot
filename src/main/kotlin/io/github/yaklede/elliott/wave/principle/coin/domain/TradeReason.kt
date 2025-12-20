package io.github.yaklede.elliott.wave.principle.coin.domain

enum class EntryReason {
    W2_COMPLETE_BREAK_W1_END,
    SWING_BREAKOUT,
}

enum class ExitReason {
    STOP_INVALIDATION,
    TAKE_PROFIT,
    TIME_STOP,
    TRAIL_STOP,
    MANUAL_EXIT,
}

enum class RejectReason {
    TREND_FILTER,
    VOLATILITY_FILTER,
    VOLUME_FILTER,
    STOP_DISTANCE,
    LOW_SCORE,
    NO_SETUP,
    REGIME_GATED,
    RISK_KILLSWITCH,
    COOLDOWN,
}
