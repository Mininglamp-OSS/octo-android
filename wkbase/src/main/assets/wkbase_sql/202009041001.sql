create table apply_tab
(
id INTEGER PRIMARY KEY AUTOINCREMENT,
apply_uid text,
apply_name text,
token text,
status int default 0,
remark text,
created_at text,
extra text
);

create table cmd
(
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  message_id    text,
  message_seq   BIGINT default 0,
  client_msg_no text,
  timestamp     INTEGER default 0,
  cmd           text,
  param         text,
  sign         text,
  is_deleted    int default 0,
  created_at    text
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_client_msg_no_cmd ON cmd (client_msg_no);
CREATE UNIQUE INDEX IF NOT EXISTS idx_message_id_cmd ON cmd (message_id);