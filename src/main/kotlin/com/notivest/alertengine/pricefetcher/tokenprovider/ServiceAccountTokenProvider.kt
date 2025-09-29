package com.notivest.alertengine.pricefetcher.tokenprovider

import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ServiceAccountTokenProvider() : AuthTokenProvider {
    @Volatile private var cached: Pair<String, Instant>? = null
    override suspend fun getToken(): String? {
        val now = Instant.now()
        cached?.let { (tok, exp) ->
            if (exp.isAfter(now.plusSeconds(60))) return tok // margen 60s
        }
//       // val newTok = client.obtainToken() // devuelve token y exp
//        cached = newTok
//        return newTok.first
        return "token"
    }
}