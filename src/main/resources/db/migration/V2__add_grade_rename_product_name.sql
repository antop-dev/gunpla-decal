ALTER TABLE manual RENAME COLUMN name TO product_name;
ALTER TABLE manual DROP COLUMN description;
ALTER TABLE manual ADD COLUMN grade VARCHAR(10) NOT NULL DEFAULT 'HG' AFTER product_name;
