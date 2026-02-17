package com.notivest.alertengine.catalog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindParamType
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.Timeframe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AlertKindCatalogTest {
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val catalog = AlertKindCatalog(objectMapper)

    @Test
    fun `getCatalog returns deterministic version and sorted kinds`() {
        val first = catalog.getCatalog()
        val second = catalog.getCatalog()

        assertThat(first.version).matches("^[0-9a-f]{64}$")
        assertThat(first.version).isEqualTo(second.version)
        assertThat(first.kinds.map { it.kind.name }).isSorted
        assertThat(first.kinds.map { it.kind }).containsExactly(
            AlertKind.DRAWDOWN,
            AlertKind.MA_CROSS,
            AlertKind.PCT_CHANGE,
            AlertKind.PRICE_THRESHOLD,
            AlertKind.RSI,
            AlertKind.VOLUME_SPIKE,
        )
        assertThat(first.kinds).allMatch { it.timeframes == listOf(Timeframe.D1) }
    }

    @Test
    fun `maps JSON schema params with enum defaults bounds and exclusive flags`() {
        val response = catalog.getCatalog()

        val priceThreshold = response.kinds.first { it.kind == AlertKind.PRICE_THRESHOLD }
        val valueParam = priceThreshold.params.first { it.name == "value" }
        val operatorParam = priceThreshold.params.first { it.name == "operator" }
        assertThat(valueParam.type).isEqualTo(AlertKindParamType.NUMBER)
        assertThat(valueParam.required).isTrue()
        assertThat(valueParam.min).isEqualTo(0)
        assertThat(valueParam.exclusiveMin).isTrue()
        assertThat(valueParam.max).isEqualTo(1_000_000)
        assertThat(operatorParam.type).isEqualTo(AlertKindParamType.ENUM)
        assertThat(operatorParam.values).containsExactly("GTE", "LTE", "GT", "LT")

        val pctChange = response.kinds.first { it.kind == AlertKind.PCT_CHANGE }
        val basis = pctChange.params.first { it.name == "basis" }
        assertThat(basis.type).isEqualTo(AlertKindParamType.ENUM)
        assertThat(basis.defaultValue).isEqualTo("CLOSE")
        assertThat(basis.required).isFalse()

        val drawdown = response.kinds.first { it.kind == AlertKind.DRAWDOWN }
        val threshold = drawdown.params.first { it.name == "threshold" }
        val lookback = drawdown.params.first { it.name == "lookback" }
        assertThat(threshold.type).isEqualTo(AlertKindParamType.NUMBER)
        assertThat(threshold.exclusiveMin).isTrue()
        assertThat(threshold.max).isEqualTo(100)
        assertThat(lookback.type).isEqualTo(AlertKindParamType.INT)
        assertThat(lookback.min).isEqualTo(1)
        assertThat(lookback.max).isEqualTo(250)

        val rsi = response.kinds.first { it.kind == AlertKind.RSI }
        val period = rsi.params.first { it.name == "period" }
        assertThat(period.type).isEqualTo(AlertKindParamType.INT)
        assertThat(period.defaultValue).isEqualTo(14)
        assertThat(period.min).isEqualTo(1)
        assertThat(period.max).isEqualTo(200)

        val volumeSpike = response.kinds.first { it.kind == AlertKind.VOLUME_SPIKE }
        val percentile = volumeSpike.params.first { it.name == "percentile" }
        assertThat(percentile.type).isEqualTo(AlertKindParamType.NUMBER)
        assertThat(percentile.exclusiveMin).isTrue()
        assertThat(percentile.exclusiveMax).isTrue()
        assertThat(percentile.defaultValue).isEqualTo(0.95)
    }

    @Test
    fun `maps examples into timeframe and params map`() {
        val response = catalog.getCatalog()

        val maCross = response.kinds.first { it.kind == AlertKind.MA_CROSS }
        assertThat(maCross.examples).isNotEmpty
        assertThat(maCross.examples.first().timeframe).isEqualTo(Timeframe.D1)
        assertThat(maCross.examples.first().params).containsKeys("fast", "slow", "direction")
    }
}
