CREATE INDEX IF NOT EXISTS idx_message_flame ON message(is_deleted) WHERE flame=1;
