CREATE UNIQUE INDEX ux_subscription_confirmation_token
    ON email_subscriptions (confirmation_token_hash) WHERE confirmation_token_hash IS NOT NULL;
CREATE UNIQUE INDEX ux_subscription_unsubscribe_token
    ON email_subscriptions (unsubscribe_token_hash) WHERE unsubscribe_token_hash IS NOT NULL;

CREATE INDEX idx_subscription_email ON email_subscriptions (lower(email));
CREATE INDEX idx_recent_confirmation ON email_notifications (subscription_id, created_at)
    WHERE event_type = 'CONFIRMATION';
