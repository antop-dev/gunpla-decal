package com.example.gunpladecal

import org.springframework.boot.context.properties.ConfigurationProperties

/** application.yml의 app.* 설정을 바인딩하는 프로퍼티 클래스 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    /** OpenAI API 키 (환경변수 또는 application.yml에서 주입) */
    val openapiKey: String,
    /** PDF 업로드 파일 저장 디렉터리 경로 */
    val uploadDir: String,
)
