package com.notivest.alertengine.controller

import com.notivest.alertengine.catalog.AlertKindCatalog
import com.notivest.alertengine.controllers.AlertKindCatalogController
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindCatalogResponse
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindDefinition
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindExample
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindParam
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindParamType
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.Timeframe
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(controllers = [AlertKindCatalogController::class])
class AlertKindCatalogControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    lateinit var catalog: AlertKindCatalog

    @Test
    fun `GET - devuelve catalogo con cache headers`() {
        val response = AlertKindCatalogResponse(
            version = "2024-12-06",
            kinds = listOf(
                AlertKindDefinition(
                    kind = AlertKind.RSI,
                    description = "Relative Strength Index threshold",
                    timeframes = listOf(Timeframe.D1),
                    params = listOf(
                        AlertKindParam(
                            name = "period",
                            type = AlertKindParamType.INT,
                            required = true,
                        ),
                    ),
                    examples = listOf(
                        AlertKindExample(
                            timeframe = Timeframe.D1,
                            params = mapOf("period" to 14),
                        ),
                    ),
                ),
            ),
        )

        whenever(catalog.getCatalog()).thenReturn(response)

        mvc.get("/alert-kinds") {
            with(jwt())
        }.andExpect {
            status { isOk() }
            header { string("ETag", "\"${response.version}\"") }
            header { string("Cache-Control", containsString("max-age=")) }
            jsonPath("$.version") { value(response.version) }
            jsonPath("$.kinds[0].kind") { value("RSI") }
        }
    }

    @Test
    fun `401 si no hay JWT`() {
        mvc.get("/alert-kinds")
            .andExpect { status { isUnauthorized() } }
    }
}
