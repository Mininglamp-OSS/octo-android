ALTER TABLE 'channel' add column 'parent_channel_id' text;
ALTER TABLE 'channel' add column 'parent_channel_type' int default 0;
ALTER TABLE 'conversation' add column 'parent_channel_id' text;
ALTER TABLE 'conversation' add column 'parent_channel_type' int default 0;