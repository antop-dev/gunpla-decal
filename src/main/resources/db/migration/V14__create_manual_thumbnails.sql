CREATE TABLE manual_thumbnail (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    manual_id   INTEGER NOT NULL,
    page_number INTEGER NOT NULL,
    file_path   TEXT    NOT NULL,
    CONSTRAINT fk_manual_thumbnail_manual FOREIGN KEY (manual_id) REFERENCES manual (id) ON DELETE CASCADE
);

CREATE INDEX idx_manual_thumbnail_manual_id ON manual_thumbnail (manual_id);
