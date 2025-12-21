package io.github.yaklede.elliott.wave.principle.coin.domain

enum class EntryReason {
    W2_COMPLETE_BREAK_W1_END,
    SWING_BREAKOUT,
    SWING_BREAKDOWN,
    FAST_BREAKOUT,
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
    TREND_STRENGTH_FILTER,
    VOLATILITY_FILTER,
    VOLUME_FILTER,
    VOL_EXPANSION_FILTER,
    FEE_EDGE_FILTER,
    STOP_DISTANCE,
    LOW_REWARD_RISK,
    LOW_SCORE,
    WEAK_BODY,
    NO_SETUP,
    REGIME_GATED,
    SHORT_GATE,
    RISK_KILLSWITCH,
    COOLDOWN,
}
