CREATE TABLE admin (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    username   TEXT    NOT NULL UNIQUE,
    password   TEXT    NOT NULL,
    created_at TEXT    NOT NULL
);

INSERT INTO admin (username, password, created_at) VALUES ('admin', '{noop}admin', datetime('now'));

CREATE TABLE audit_log (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    manual_id  INTEGER NOT NULL,
    account    TEXT    NOT NULL,
    action     TEXT    NOT NULL,
    detail     TEXT,
    created_at TEXT    NOT NULL
);

CREATE INDEX idx_audit_log_manual_id ON audit_log (manual_id);
