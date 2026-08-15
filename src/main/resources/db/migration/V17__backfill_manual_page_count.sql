UPDATE manual
SET page_count = (SELECT COUNT(*) FROM manual_thumbnail t WHERE t.manual_id = manual.id);
