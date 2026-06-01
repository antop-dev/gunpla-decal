package com.example.gunpladecal.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** application.yml의 app.* 설정을 바인딩하는 프로퍼티 클래스 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    /** SQLite 데이터베이스 파일 경로 (환경변수 DB_PATH 또는 application.yml에서 설정) */
    val dbPath: String,
    /** PDF 업로드 파일 저장 디렉터리 경로 */
    val uploadDir: String,
    /** OpenAI API 키 (환경변수 openai.api-key 또는 application.yml에서 설정) */
    val openAiKey: String?,

    val baseUrl: String
)
