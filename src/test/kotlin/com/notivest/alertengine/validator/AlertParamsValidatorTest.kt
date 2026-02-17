package com.notivest.alertengine.validator

import com.fasterxml.jackson.databind.ObjectMapper
import com.notivest.alertengine.exception.InvalidParamsException
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.validation.AlertParamsValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlertParamsValidatorTest {

    private val mapper = ObjectMapper()
    private val validator = AlertParamsValidator(mapper)

    @Test
    fun `PRICE_THRESHOLD - acepta payload válido (GTE)`() {
        val params = mapOf("operator" to "GTE", "value" to 123.45)
        assertDoesNotThrow {
            validator.validate(AlertKind.PRICE_THRESHOLD, params)
        }
    }

    @Test
    fun `PRICE_THRESHOLD - rechaza value negativo (exclusiveMinimum 0)`() {
        val params = mapOf("operator" to "GTE", "value" to -1)
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_THRESHOLD, params)
        }
        assertTrue(ex.message!!.contains("value"), "El mensaje debería mencionar 'value'")
    }

    @Test
    fun `PRICE_THRESHOLD - rechaza propiedades adicionales`() {
        val params = mapOf("operator" to "GT", "value" to 100.0, "foo" to "bar")
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_THRESHOLD, params)
        }
        // networknt suele mencionar 'additionalProperties' o el nombre del campo extra
        assertTrue(
            ex.message!!.contains("additional", ignoreCase = true) ||
                    ex.message!!.contains("foo"),
            "Mensaje debería mencionar additionalProperties o 'foo'"
        )
    }

    @Test
    fun `PRICE_THRESHOLD - rechaza falta de campo requerido`() {
        // Falta 'operator'
        val params = mapOf("value" to 100.0)
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_THRESHOLD, params)
        }
        assertTrue(ex.message!!.contains("required", ignoreCase = true), "Mensaje debería mencionar campo requerido")
    }

    @Test
    fun `PRICE_THRESHOLD - acepta payload válido (LT)`() {
        val params = mapOf("operator" to "LT", "value" to 50.0)
        assertDoesNotThrow {
            validator.validate(AlertKind.PRICE_THRESHOLD, params)
        }
    }

    @Test
    fun `PRICE_THRESHOLD - rechaza tipo inválido en value`() {
        val params = mapOf("operator" to "LTE", "value" to "100") // string en vez de number
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_THRESHOLD, params)
        }
        assertTrue(
            ex.message!!.contains("number", ignoreCase = true) ||
                    ex.message!!.contains("type", ignoreCase = true),
            "Mensaje debería mencionar error de tipo (number esperado)"
        )
    }

    @Test
    fun `PRICE_THRESHOLD - rechaza value mayor al máximo`() {
        val params = mapOf("operator" to "GTE", "value" to 2_000_000)
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_THRESHOLD, params)
        }
        assertTrue(ex.message!!.contains("value"), "El mensaje debería mencionar 'value'")
    }

    @Test
    fun `MA_CROSS - rechaza fast mayor al máximo`() {
        val params = mapOf("fast" to 201, "slow" to 150, "direction" to "UP")
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.MA_CROSS, params)
        }
        assertTrue(ex.message!!.contains("fast"), "El mensaje debería mencionar 'fast'")
    }

    @Test
    fun `PCT_CHANGE - rechaza lookbackBars mayor al máximo`() {
        val params = mapOf("operator" to "GTE", "pct" to 3.0, "lookbackBars" to 300)
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PCT_CHANGE, params)
        }
        assertTrue(ex.message!!.contains("lookbackBars"), "El mensaje debería mencionar 'lookbackBars'")
    }

    @Test
    fun `DRAWDOWN - acepta payload válido`() {
        val params = mapOf("threshold" to 10.0, "lookback" to 30)
        assertDoesNotThrow {
            validator.validate(AlertKind.DRAWDOWN, params)
        }
    }

    @Test
    fun `DRAWDOWN - rechaza lookback mayor al máximo`() {
        val params = mapOf("threshold" to 10.0, "lookback" to 300)
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.DRAWDOWN, params)
        }
        assertTrue(ex.message!!.contains("lookback"), "El mensaje debería mencionar 'lookback'")
    }

    @Test
    fun `RSI - rechaza period mayor al máximo`() {
        val params = mapOf("period" to 201, "threshold" to 70.0, "operator" to "ABOVE")
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.RSI, params)
        }
        assertTrue(ex.message!!.contains("period"), "El mensaje debería mencionar 'period'")
    }

    @Test
    fun `RSI - acepta params sin timeframe`() {
        val params = mapOf("period" to 14, "threshold" to 70.0, "operator" to "ABOVE")
        assertDoesNotThrow {
            validator.validate(AlertKind.RSI, params)
        }
    }

    @Test
    fun `VOLUME_SPIKE - rechaza lookback mayor al máximo`() {
        val params = mapOf("operator" to "ABOVE_MA", "lookback" to 300, "multiplier" to 2.0)
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.VOLUME_SPIKE, params)
        }
        assertTrue(ex.message!!.contains("lookback"), "El mensaje debería mencionar 'lookback'")
    }

    @Test
    fun `VOLUME_SPIKE - rechaza multiplier no positivo`() {
        val params = mapOf("operator" to "ABOVE_MA", "lookback" to 20, "multiplier" to 0.0)
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.VOLUME_SPIKE, params)
        }
        assertTrue(ex.message!!.contains("multiplier"), "El mensaje debería mencionar 'multiplier'")
    }
}
