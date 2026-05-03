-- 메뉴얼 테이블에 형식번호 컬럼 추가 (등급 뒤에 위치)
ALTER TABLE manual
    ADD COLUMN model_number VARCHAR(50) NOT NULL DEFAULT '' COMMENT '형식번호 (예: HG-RX-78-2)' AFTER grade;
