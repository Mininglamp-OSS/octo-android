create table prohibit_words
(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sid int default 0,
    content text,
    `version` bigint default 0,
    is_deleted smallint default 0,
    created_at text
)