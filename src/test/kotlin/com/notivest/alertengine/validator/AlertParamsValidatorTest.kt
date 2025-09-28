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
    fun `PRICE_ABOVE - acepta payload válido`() {
        val params = mapOf("price" to 100.0, "tolerance" to 0.5)
        assertDoesNotThrow {
            validator.validate(AlertKind.PRICE_ABOVE, params)
        }
    }

    @Test
    fun `PRICE_ABOVE - rechaza price negativo`() {
        val params = mapOf("price" to -1)
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_ABOVE, params)
        }
        assertTrue(ex.message!!.contains("price"), "El mensaje debería mencionar 'price'")
    }

    @Test
    fun `PRICE_ABOVE - rechaza propiedades adicionales`() {
        val params = mapOf("price" to 100.0, "foo" to "bar")
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_ABOVE, params)
        }
        // networknt suele mencionar 'additionalProperties' o el nombre del campo extra
        assertTrue(ex.message!!.contains("additional"), "Mensaje debería mencionar additionalProperties")
    }

    @Test
    fun `PRICE_ABOVE - rechaza falta de price`() {
        val params = emptyMap<String, Any>()
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_ABOVE, params)
        }
        assertTrue(ex.message!!.contains("required"), "Mensaje debería mencionar campo requerido")
    }

    @Test
    fun `PRICE_BELOW - acepta payload válido`() {
        val params = mapOf("price" to 50.0)
        assertDoesNotThrow {
            validator.validate(AlertKind.PRICE_BELOW, params)
        }
    }

    @Test
    fun `PRICE_BELOW - rechaza tipo inválido`() {
        val params = mapOf("price" to "100") // string en vez de number
        val ex = assertThrows(InvalidParamsException::class.java) {
            validator.validate(AlertKind.PRICE_BELOW, params)
        }
        assertTrue(ex.message!!.contains("string found, number expected"), "Mensaje debería mencionar error de tipo")
    }
}