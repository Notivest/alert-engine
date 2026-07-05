package com.notivest.alertengine.observability

import com.notivest.alertengine.security.JwtUserIdResolver
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter(JwtUserIdResolver("user_id"))

    @AfterEach
    fun tearDown() {
        CorrelationContext.clear()
    }

    @Test
    fun `reuses incoming correlation id and clears MDC afterwards`() {
        val request = MockHttpServletRequest().apply {
            addHeader(CorrelationContext.HEADER_CORRELATION_ID, "corr-123")
        }
        val response = MockHttpServletResponse()
        var correlationIdInsideChain: String? = null

        filter.doFilter(
            request,
            response,
            FilterChain { _, _ -> correlationIdInsideChain = MDC.get(CorrelationContext.MDC_CORRELATION_ID) },
        )

        assertThat(correlationIdInsideChain).isEqualTo("corr-123")
        assertThat(response.getHeader(CorrelationContext.HEADER_CORRELATION_ID)).isEqualTo("corr-123")
        assertThat(MDC.get(CorrelationContext.MDC_CORRELATION_ID)).isNull()
    }

    @Test
    fun `generates correlation id when request does not include one`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        assertThat(response.getHeader(CorrelationContext.HEADER_CORRELATION_ID)).isNotBlank()
    }
}
