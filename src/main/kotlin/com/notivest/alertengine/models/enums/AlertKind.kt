package com.notivest.alertengine.models.enums

enum class AlertKind {

    // params: { "operator": "GTE|LTE|GT|LT", "value": number }
    PRICE_THRESHOLD,

    // params: { "operator": "GTE|LTE|GT|LT", "pct": number, "lookbackBars": int }
    PCT_CHANGE,

    // params: { "fast": int, "slow": int, "direction": "UP|DOWN" }
    MA_CROSS,

    // params: { "period": int, "threshold": number, "operator": "ABOVE|BELOW|CROSSING_UP|CROSSING_DOWN", "timeframe": "M1|..." }
    RSI,

    // params: { "lookback": int, "operator": "ABOVE_MA|ABOVE_PCTL", "multiplier": number?, "percentile": number? }
    VOLUME_SPIKE,

    // params: { "atrPeriod": int, "multiplier": number, "basis": "CLOSE|HL2|HLC3" }
    ATR_BREAKOUT,

    // params: { "distancePct": number }  // o bien: { "distanceATR": number, "atrPeriod": int }
    TRAILING_STOP,

    // ---------- fase 2 ----------

    // params: { "period": int, "stddev": number, "side": "UPPER|LOWER|BOTH" }
    BB_TOUCH,

    // params: { "fast": int, "slow": int, "signal": int, "direction": "UP|DOWN" }
    MACD_CROSS,

    // params: { "operator": "GTE|LTE|GT|LT", "pct": number }  // gap vs. cierre previo de sesión
    GAP_SESSION,

    // params: { "operator": "GTE|LTE|GT|LT", "pct": number, "lookbackBars": int }
    DRAWDOWN_FROM_MAX,

    // params: { "pattern": "ENGULFING|PINBAR|DOJI|..." }
    CANDLE_PATTERN,

    // ---------- portfolio ----------

    // params: { "operator": "GTE|LTE|GT|LT", "pct": number }  // PnL por posición
    POSITION_PNL,

    // params: { "operator": "GTE|LTE|GT|LT", "pct": number, "lookbackDays": int }
    PORTFOLIO_DRAWDOWN,

    // params: { "operator": "GTE|LTE|GT|LT", "pct": number, "targetSymbol": string }
    REBALANCE_DRIFT,

    // ---------- data externa ----------

    // params: { "daysTo": int, "direction": "BEFORE|AFTER" }  // ventana relativa a earnings
    EARNINGS_WINDOW,

    // params: { "operator": "GTE|LTE|GT|LT", "score": number, "lookbackHrs": int }
    NEWS_SENTIMENT
}
