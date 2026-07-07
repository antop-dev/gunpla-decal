DELETE FROM japanese_char_usage;

INSERT INTO japanese_char_usage (character, count)
SELECT decal_number as character, count(*) as decal_count
FROM decal
WHERE decal_number GLOB '[ぁ-ゖァ-ヺ]'
GROUP BY decal_number;
