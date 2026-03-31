ALTER TABLE 'message'  add column 'flame'          smallint not null default 0;
ALTER TABLE 'message'  add column 'flame_second'   integer not null default 0;
ALTER TABLE 'message'  add column 'viewed'         smallint not null default 0;
ALTER TABLE 'message'  add column 'viewed_at'      integer not null default 0;
ALTER TABLE 'channel'  add column 'flame'          smallint not null default 0;
ALTER TABLE 'channel'  add column 'flame_second'   integer not null default 0;