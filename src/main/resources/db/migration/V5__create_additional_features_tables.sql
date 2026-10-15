-- Conversation Summaries
CREATE TABLE IF NOT EXISTS conversation_summaries (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    summary_text        TEXT NOT NULL,
    from_message_index  INTEGER NOT NULL,
    to_message_index    INTEGER NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_conversation_summaries_conv_id ON conversation_summaries(conversation_id);

-- Prompt Templates
CREATE TABLE IF NOT EXISTS prompt_templates (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    content             TEXT NOT NULL,
    category            VARCHAR(100),
    is_public           BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(255),
    usage_count         INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS prompt_template_variables (
    template_id         BIGINT NOT NULL REFERENCES prompt_templates(id) ON DELETE CASCADE,
    variable_name       VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_prompt_template_variables_id ON prompt_template_variables(template_id);

-- Shared Conversations
CREATE TABLE IF NOT EXISTS shared_conversations (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    share_token         VARCHAR(64) UNIQUE NOT NULL,
    expires_at          TIMESTAMP,
    view_count          INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_shared_conversations_token ON shared_conversations(share_token);

-- Usage Records
CREATE TABLE IF NOT EXISTS usage_records (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    model               VARCHAR(100) NOT NULL,
    prompt_tokens       INTEGER NOT NULL DEFAULT 0,
    completion_tokens   INTEGER NOT NULL DEFAULT 0,
    total_tokens        INTEGER NOT NULL DEFAULT 0,
    cost                NUMERIC(10, 6) DEFAULT 0,
    timestamp           TIMESTAMP NOT NULL DEFAULT NOW(),
    request_type        VARCHAR(50) DEFAULT 'chat',
    response_time_ms    BIGINT
);
CREATE INDEX IF NOT EXISTS idx_usage_user_timestamp ON usage_records(user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_usage_timestamp ON usage_records(timestamp);

-- Webhook Configs
CREATE TABLE IF NOT EXISTS webhook_configs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    url                 VARCHAR(500) NOT NULL,
    events              VARCHAR(500) NOT NULL,
    secret              VARCHAR(128) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_webhook_configs_user ON webhook_configs(user_id);

-- Webhook Deliveries
CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id                  BIGSERIAL PRIMARY KEY,
    webhook_config_id   BIGINT NOT NULL REFERENCES webhook_configs(id) ON DELETE CASCADE,
    event               VARCHAR(100) NOT NULL,
    payload             TEXT NOT NULL,
    response_status     INTEGER,
    attempts            INTEGER NOT NULL DEFAULT 0,
    success             BOOLEAN NOT NULL DEFAULT FALSE,
    delivered_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_config_id ON webhook_deliveries(webhook_config_id);
