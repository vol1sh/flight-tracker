CREATE TABLE email_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_iata VARCHAR(12) NOT NULL,
    flight_date DATE NOT NULL,
    email VARCHAR(320) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    confirmation_token_hash VARCHAR(64),
    unsubscribe_token_hash VARCHAR(64),
    token_expires_at TIMESTAMPTZ,
    last_requested_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,

    CONSTRAINT chk_subscription_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'UNSUBSCRIBED'))
);
CREATE UNIQUE INDEX ux_email_subscription
ON email_subscriptions (
    flight_iata,
    flight_date,
    LOWER(email)
)
WHERE status <> 'UNSUBSCRIBED';

CREATE INDEX idx_subscription_flight
ON email_subscriptions (flight_iata, flight_date)
WHERE status = 'ACTIVE';


CREATE TABLE email_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL
        REFERENCES email_subscriptions(id) ON DELETE CASCADE,
    event_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    next_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,

    CONSTRAINT uq_notification_event UNIQUE(subscription_id, event_key)
);

CREATE INDEX idx_email_notifications_pending
ON email_notifications (status, next_attempt_at)
WHERE status = 'PENDING';