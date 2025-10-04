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
}
