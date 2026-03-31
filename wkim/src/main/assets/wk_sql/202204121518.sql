create table IF NOT EXISTS message_extra
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id    TEXT,
    channel_id    TEXT,
    channel_type  smallint         not null default 0,
    readed        integer          not null default 0,
    readed_count  integer          not null default 0,
    unread_count  integer          not null default 0,
    revoke        smallint         not null default 0,
    revoker       TEXT,
    extra_version bigint           not null default 0,
    is_mutual_deleted    smallint         not null default 0,
    content_edit  TEXT,
    edited_at     integer          not null default 0,
    needUpload    smallint         not null default 0
);
CREATE UNIQUE INDEX IF NOT EXISTS message_extra_idx ON message_extra(message_id);
CREATE INDEX IF NOT EXISTS idx_message_extra ON message_extra(channel_id, channel_type);
