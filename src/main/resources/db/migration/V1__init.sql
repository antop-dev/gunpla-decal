CREATE TABLE manual
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    pdf_filename VARCHAR(255) NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE decal
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    manual_id    BIGINT       NOT NULL,
    page_number  INT          NOT NULL,
    decal_number VARCHAR(100) NOT NULL,
    x            DOUBLE       NOT NULL,
    y            DOUBLE       NOT NULL,
    created_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_decal_manual FOREIGN KEY (manual_id) REFERENCES manual (id) ON DELETE CASCADE
);

CREATE INDEX idx_decal_manual_id ON decal (manual_id);
