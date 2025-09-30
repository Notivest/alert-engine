package com.notivest.alertengine.ruleEvaluators.exceptions

import com.notivest.alertengine.models.enums.AlertKind

class MissingRuleEvaluatorException(
    kind: AlertKind
) : NoSuchElementException("Missing evaluator for alert $kind")
