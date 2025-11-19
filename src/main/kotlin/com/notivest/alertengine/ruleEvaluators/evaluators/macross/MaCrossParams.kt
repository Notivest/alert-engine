package com.notivest.alertengine.ruleEvaluators.evaluators.macross

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.math.max

enum class MaCrossDirection {
    UP, DOWN
}

data class MaCrossParams @JsonCreator constructor(
    @JsonProperty("fast") val fast: Int,
    @JsonProperty("slow") val slow: Int,
    @JsonProperty("direction") val direction: MaCrossDirection,
) {
    init {
        require(fast >= 1) { "fast must be >= 1" }
        require(slow >= 2) { "slow must be >= 2" }
        require(slow > fast) { "slow must be greater than fast" }
    }

    fun requiredCandles(): Int = max(fast, slow) + 1
}
