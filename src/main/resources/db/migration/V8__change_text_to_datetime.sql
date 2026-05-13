create table manual_dg_tmp
(
    id           INTEGER           not null
        primary key autoincrement,
    product_name TEXT              not null,
    pdf_path     TEXT              not null,
    created_at   DATETIME          not null,
    updated_at   DATETIME          not null,
    grade        TEXT default 'HG' not null,
    model_number TEXT default ''   not null
);

insert into manual_dg_tmp(id, product_name, pdf_path, created_at, updated_at, grade, model_number)
select id, product_name, pdf_path, created_at, updated_at, grade, model_number
from manual;

drop table manual;

alter table manual_dg_tmp
    rename to manual;

create table admin_dg_tmp
(
    id         INTEGER  not null
        primary key autoincrement,
    username   TEXT     not null
        unique,
    password   TEXT     not null,
    created_at DATETIME not null
);

insert into admin_dg_tmp(id, username, password, created_at)
select id, username, password, created_at
from admin;

drop table admin;

alter table admin_dg_tmp
    rename to admin;

create table audit_log_dg_tmp
(
    id         INTEGER  not null
        primary key autoincrement,
    manual_id  INTEGER  not null,
    account    TEXT     not null,
    action     TEXT     not null,
    detail     TEXT,
    created_at DATETIME not null
);

insert into audit_log_dg_tmp(id, manual_id, account, action, detail, created_at)
select id, manual_id, account, action, detail, created_at
from audit_log;

drop table audit_log;

alter table audit_log_dg_tmp
    rename to audit_log;

create index idx_audit_log_manual_id
    on audit_log (manual_id);

create table decal_dg_tmp
(
    id           INTEGER               not null
        primary key autoincrement,
    manual_id    INTEGER               not null
        constraint fk_decal_manual
            references manual
            on delete cascade,
    page_number  INTEGER               not null,
    decal_number TEXT                  not null,
    x            REAL                  not null,
    y            REAL                  not null,
    created_at   DATETIME              not null,
    color        TEXT default 'WHITE'  not null,
    shape        TEXT default 'CIRCLE' not null
);

insert into decal_dg_tmp(id, manual_id, page_number, decal_number, x, y, created_at, color, shape)
select id,
       manual_id,
       page_number,
       decal_number,
       x,
       y,
       created_at,
       color,
       shape
from decal;

drop table decal;

alter table decal_dg_tmp
    rename to decal;

create index idx_decal_manual_id
    on decal (manual_id);

