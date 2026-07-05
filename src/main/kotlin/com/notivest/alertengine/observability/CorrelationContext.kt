package com.notivest.alertengine.observability

import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.util.StringUtils
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import reactor.core.publisher.Mono

object CorrelationContext {
    const val HEADER_CORRELATION_ID = "X-Correlation-Id"
    const val HEADER_REQUEST_ID = "X-Request-Id"
    const val MDC_CORRELATION_ID = "correlationId"
    const val MDC_USER_ID = "userId"

    fun setCorrelationId(correlationId: String) {
        MDC.put(MDC_CORRELATION_ID, correlationId)
    }

    fun currentCorrelationId(): String? =
        MDC.get(MDC_CORRELATION_ID)?.takeIf(StringUtils::hasText)

    fun setUserId(userId: String) {
        MDC.put(MDC_USER_ID, userId)
    }

    fun clear() {
        MDC.remove(MDC_CORRELATION_ID)
        MDC.remove(MDC_USER_ID)
    }

    fun copyTo(headers: HttpHeaders) {
        val correlationId = currentCorrelationId() ?: return
        if (!headers.containsKey(HEADER_CORRELATION_ID)) {
            headers.set(HEADER_CORRELATION_ID, correlationId)
        }
    }

    fun propagationFilter(): ExchangeFilterFunction =
        ExchangeFilterFunction { request, next ->
            val correlationId = currentCorrelationId()
            if (!StringUtils.hasText(correlationId) || request.headers().containsKey(HEADER_CORRELATION_ID)) {
                return@ExchangeFilterFunction next.exchange(request)
            }

            val mutatedRequest =
                ClientRequest.from(request)
                    .headers { headers -> headers.set(HEADER_CORRELATION_ID, correlationId) }
                    .build()

            next.exchange(mutatedRequest).switchIfEmpty(Mono.empty())
        }
}
