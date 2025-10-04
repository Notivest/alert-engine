ALTER TABLE alert_rules
    ADD COLUMN notify_min_severity VARCHAR(10) NOT NULL DEFAULT 'INFO';