package com.example.gunpladecal.openai

import com.example.gunpladecal.config.AppProperties
import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/** 앱 시작 시 OpenAI API 가용성을 체크하고 결과를 캐싱하는 컴포넌트 */
@Component
class OpenAiAvailabilityChecker(
    private val appProperties: AppProperties,
    private val openAiClient: OpenAIClient,
) {
    private var _available: Boolean = false

    /** OpenAI API 사용 가능 여부 (앱 시작 시 1회 체크 후 캐싱) */
    val available: Boolean get() = _available

    @EventListener(ApplicationReadyEvent::class)
    fun check() {
        // API 키 미설정이면 호출 없이 비활성화
        if (appProperties.openapiKey == "none") {
            log.info { "OpenAI API 키 미설정 - AI 기능 비활성화" }
            return
        }
        _available =
            try {
                // 최소 비용으로 API 키 유효성 및 잔액 확인
                openAiClient
                    .chat()
                    .completions()
                    .create(
                        ChatCompletionCreateParams
                            .builder()
                            .model(ChatModel.GPT_4O_MINI)
                            .maxCompletionTokens(1)
                            .addUserMessage("1")
                            .build(),
                    )
                true
            } catch (e: Exception) {
                log.warn { "OpenAI API 사용 불가: ${e.message}" }
                false
            }
        log.info { "OpenAI API 가용성: $available" }
    }
}
