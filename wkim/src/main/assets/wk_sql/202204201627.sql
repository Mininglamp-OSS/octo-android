create table reminders
(
    id    INTEGER   PRIMARY KEY AUTOINCREMENT,
    reminder_id   INTEGER       not null default 0,
    message_id    TEXT not null default '',
    message_seq    UNSIGNED BIG INT not null default 0,
    channel_id    VARCHAR(100)      not null default '',
    channel_type  smallint         not null default 0,
    uid    VARCHAR(100)      not null default '',
    `type`        integer          not null default 0,
    `text`          varchar(255)    not null default '',
    `data`          varchar(1000)   not null default '',
    is_locate       smallint        not null default 0,
    version       bigint            NOT NULL default 0,
    done          smallint          not null default 0,
    needUpload    smallint         not null default 0
);
CREATE INDEX IF NOT EXISTS idx_channel ON reminders(channel_id, channel_type);
CREATE UNIQUE INDEX IF NOT EXISTS uidx_reminder ON reminders(reminder_id);
