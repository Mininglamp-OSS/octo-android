CREATE INDEX IF NOT EXISTS idx_message_channel_order_seq ON message(channel_id, channel_type, order_seq);
CREATE INDEX IF NOT EXISTS idx_message_channel_msg_seq ON message(channel_id, channel_type, message_seq);
