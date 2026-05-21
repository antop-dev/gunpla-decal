CREATE TABLE japanese_char_usage (
    id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    character TEXT    NOT NULL UNIQUE,
    count     INTEGER NOT NULL DEFAULT 0
);
