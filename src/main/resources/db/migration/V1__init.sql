CREATE TABLE manual
(
    id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,
    description  TEXT,
    pdf_filename TEXT    NOT NULL,
    created_at   TEXT    NOT NULL,
    updated_at   TEXT    NOT NULL
);

CREATE TABLE decal
(
    id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    manual_id    INTEGER NOT NULL,
    page_number  INTEGER NOT NULL,
    decal_number TEXT    NOT NULL,
    x            REAL    NOT NULL,
    y            REAL    NOT NULL,
    created_at   TEXT    NOT NULL,
    CONSTRAINT fk_decal_manual FOREIGN KEY (manual_id) REFERENCES manual (id) ON DELETE CASCADE
);

CREATE INDEX idx_decal_manual_id ON decal (manual_id);
