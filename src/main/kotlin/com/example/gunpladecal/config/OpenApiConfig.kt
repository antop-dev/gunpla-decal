package com.example.gunpladecal.config

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** OpenAI 클라이언트 빈 설정 */
@Configuration
class OpenApiConfig(
    private val appProperties: AppProperties,
) {
    /** AppProperties에서 API 키를 읽어 OpenAI OkHttp 클라이언트 빈 생성 */
    @Bean
    fun openAiClient(): OpenAIClient =
        OpenAIOkHttpClient
            .builder()
            .apiKey(appProperties.openapiKey)
            .build()
}
