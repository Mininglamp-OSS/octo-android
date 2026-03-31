create table conversation_extra
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id    VARCHAR(100)      not null default '',
    channel_type  smallint         not null default 0,
    browse_to     UNSIGNED BIG INT not null default 0,
    keep_message_seq  UNSIGNED BIG INT not null default 0,
    keep_offset_y    INTEGER not null default 0,
    draft     varchar(1000)   not null default '',
    version   bigint    NOT NULL default 0,
    draft_updated_at bigint    not null default 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_channel_conversation_extra ON conversation_extra (channel_id, channel_type);