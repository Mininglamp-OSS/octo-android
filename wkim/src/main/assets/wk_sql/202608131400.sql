CREATE INDEX IF NOT EXISTS idx_reminders_undone ON reminders(type, channel_id) WHERE done=0;
