CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    minimum_severity VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_alerts_event_type CHECK (event_type IN ('BREAKING_NEWS', 'MARKET_MOVEMENT', 'NATURAL_DISASTER')),
    CONSTRAINT ck_alerts_category CHECK (category IN ('NEWS', 'MARKET', 'WEATHER', 'DISASTER', 'SECURITY', 'HEALTH')),
    CONSTRAINT ck_alerts_minimum_severity CHECK (minimum_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_alerts_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE alert_channels (
    alert_id UUID NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    channel_type VARCHAR(50) NOT NULL,
    target VARCHAR(320) NOT NULL,
    PRIMARY KEY (alert_id, channel_type),
    CONSTRAINT ck_alert_channels_channel_type CHECK (channel_type IN ('EMAIL', 'SLACK'))
);

CREATE TABLE world_events (
    id UUID PRIMARY KEY,
    source_event_id VARCHAR(200) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_world_events_event_type CHECK (event_type IN ('BREAKING_NEWS', 'MARKET_MOVEMENT', 'NATURAL_DISASTER')),
    CONSTRAINT ck_world_events_category CHECK (category IN ('NEWS', 'MARKET', 'WEATHER', 'DISASTER', 'SECURITY', 'HEALTH')),
    CONSTRAINT ck_world_events_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    alert_id UUID NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES world_events (id) ON DELETE CASCADE,
    channel_type VARCHAR(50) NOT NULL,
    target VARCHAR(320) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claimed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_reason TEXT,
    CONSTRAINT ck_notification_deliveries_channel_type CHECK (channel_type IN ('EMAIL', 'SLACK')),
    CONSTRAINT ck_notification_deliveries_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT uq_notification_deliveries_identity UNIQUE (alert_id, event_id, channel_type, target)
);

CREATE INDEX idx_alerts_matching ON alerts (status, event_type, category, minimum_severity);
CREATE INDEX idx_world_events_matching ON world_events (event_type, category, severity, occurred_at);
CREATE INDEX idx_notification_deliveries_status ON notification_deliveries (status);