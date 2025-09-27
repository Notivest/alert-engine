package com.notivest.alert_engine.models.enums

enum class AlertKind {
    // params: { "price": Double }
    PRICE_ABOVE,
    // params: { "price": Double }
    PRICE_BELOW,
    // params: { "pct": Double, "lookbackBars": Int }
    PCT_CHANGE_ABOVE,
    // params: { "pct": Double, "lookbackBars": Int }
    PCT_CHANGE_BELOW,
    // params: { "fast": Int, "slow": Int, "direction": "UP"|"DOWN" }
    MA_CROSS,
    // params: { "period": Int, "level": Int }
    RSI_OVERBOUGHT,
    // params: { "period": Int, "level": Int }
    RSI_OVERSOLD,
    // params: { "multiplier": Double, "lookbackBars": Int }
    VOLUME_SPIKE
}