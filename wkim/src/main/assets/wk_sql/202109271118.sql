create table robot(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  robot_id   VARCHAR(40)    not null default '',
  status     smallint       not null default 1,
  version    BIGINT         not null DEFAULT 0,
  inline_on smallint not null default 0,
  placeholder VARCHAR(255) not null default '',
  username VARCHAR(40) not null default '',
  created_at text,
  updated_at text
);

CREATE UNIQUE INDEX `robot_id_robot_index` on `robot` (`robot_id`);

create table `robot_menu`
(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  robot_id   VARCHAR(40)    not null default '',
  cmd        VARCHAR(100)   not null default '',
  remark     VARCHAR(100)   not null default '',
  type       VARCHAR(100)   not null default '',
  created_at text,
  updated_at text
);
CREATE INDEX `bot_id_robot_menu_index` on `robot_menu` (`robot_id`);