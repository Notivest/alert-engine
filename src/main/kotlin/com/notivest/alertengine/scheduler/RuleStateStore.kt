package com.notivest.alertengine.scheduler

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class RuleStateStore {

    private val lastToTsByRule: MutableMap<UUID, Instant> = ConcurrentHashMap()

    fun getLastToTs(ruleId: UUID): Instant? = lastToTsByRule[ruleId]

    fun updateLastToTs(ruleId: UUID, toTs: Instant?) {
        if (toTs != null) {
            lastToTsByRule[ruleId] = toTs
        }
    }
}