ALTER TABLE conversation ADD COLUMN space_id TEXT NOT NULL DEFAULT '';
ALTER TABLE message ADD COLUMN space_id TEXT NOT NULL DEFAULT '';
ALTER TABLE channel ADD COLUMN space_id TEXT NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_conversation_space ON conversation (space_id, last_msg_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_message_space ON message (space_id, channel_id, channel_type);
CREATE INDEX IF NOT EXISTS idx_channel_space ON channel (space_id, channel_id, channel_type);
