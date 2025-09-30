package com.notivest.alertengine.ruleEvaluators.exceptions

import com.notivest.alertengine.models.enums.AlertKind


class DuplicateRuleEvaluatorException(
    kind: AlertKind,
    classNames: List<String>
) : IllegalStateException(
    "Multiple evaluators found for $kind: ${classNames.joinToString(", ")}"
)