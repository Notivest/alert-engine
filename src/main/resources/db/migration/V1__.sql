CREATE TABLE alert_event
(
    id           UUID         NOT NULL,
    rule_id      UUID         NOT NULL,
    triggered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    payload      JSONB        NOT NULL,
    fingerprint  VARCHAR(120) NOT NULL,
    sent         BOOLEAN      NOT NULL,
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_alert_event PRIMARY KEY (id)
);

CREATE TABLE alert_rules
(
    id                UUID        NOT NULL,
    user_id           UUID        NOT NULL,
    symbol            VARCHAR(20) NOT NULL,
    kind              VARCHAR(30) NOT NULL,
    params            JSON       NOT NULL,
    timeframe         VARCHAR(10) NOT NULL,
    status            VARCHAR(15) NOT NULL,
    debounce_secs     BIGINT,
    last_triggered_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_alert_rules PRIMARY KEY (id)
);

ALTER TABLE alert_event
    ADD CONSTRAINT uq_alertevent_rule_fingerprint UNIQUE (rule_id, fingerprint);

CREATE INDEX ix_rules_status ON alert_rules (status);

CREATE INDEX ix_rules_timeframe ON alert_rules (timeframe);

CREATE INDEX ix_rules_user_symbol ON alert_rules (user_id, symbol);

ALTER TABLE alert_event
    ADD CONSTRAINT FK_ALERT_EVENT_ON_RULE FOREIGN KEY (rule_id) REFERENCES alert_rules (id);