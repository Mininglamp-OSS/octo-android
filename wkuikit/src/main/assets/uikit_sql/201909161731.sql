create table user_contact
(
id INTEGER PRIMARY KEY AUTOINCREMENT,
phone text,
zone text,
name text,
vercode text,
is_friend int default 0,
uid text
);