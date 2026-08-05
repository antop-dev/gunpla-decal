package ai.antop.gunpla.app.service

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.Base64

private val log = KotlinLogging.logger {}

/** GPT-4o mini를 이용한 데칼 번호 OCR 서비스 */
@Service
class OpenAiService(
    private val openAIClient: OpenAIClient,
) {
    // 숫자 1~3자리, 영어 대문자 1글자, 히라가나/카타카나 1글자만 유효한 데칼 번호로 허용
    private val validPattern = Regex("^[0-9]{1,3}$|^[A-Z]$|^[ぁ-ゖァ-ヶ]$")

    /**
     * 크롭 이미지를 GPT-4o mini로 분석하여 데칼 번호 반환.
     * 인식 실패 또는 유효하지 않은 형식(validPattern 불일치)이면 null 반환.
     */
    fun recognizeDecalNumber(imageBytes: ByteArray): String? {
        val result = callOpenAiForNumber(Base64.getEncoder().encodeToString(imageBytes)) ?: return null
        val trimmed = result.trim()
        return if (validPattern.matches(trimmed)) trimmed else null
    }

    /**
     * 크롭 이미지를 GPT-4o mini로 분석하여 주요 색상(HEX) 반환.
     * 흰색 글자와 대비되는 메인 컬러를 찾는다. 인식 실패 시 null 반환.
     */
    fun recognizeDecalColor(imageBytes: ByteArray): String? {
        val result = callOpenAiForColor(Base64.getEncoder().encodeToString(imageBytes)) ?: return null
        val trimmed = result.trim().uppercase()
        val hexPattern = Regex("^[0-9A-F]{6}$")
        return if (hexPattern.matches(trimmed)) trimmed else null
    }

    /**
     * Base64 인코딩된 이미지를 GPT-4o mini에 전송하여 데칼 번호 문자열 반환.
     * API 오류 시 null 반환.
     */
    private fun callOpenAiForNumber(imageBase64: String): String? {
        val prompt =
            """
            이 이미지는 건담프라 데칼 시트의 일부입니다. 이미지 중앙에 있는 데칼 번호를 인식해주세요.
            데칼 번호 형식 (정확히 하나):
            - 숫자 1~3자리 (예: 1, 23, 156)
            - 영어 대문자 1글자 (예: A)
            - 일본어 히라가나 또는 카타카나 1글자 (예: ア, あ)
            인식되는 번호가 여러개일 경우 위치상 가장 가운데 있는 번호만 인식해주세요.
            번호만 답해주세요. 인식이 불가능하거나 확실하지 않으면 빈 문자열을 반환하세요.
            """.trimIndent()
        return callOpenAiVision(imageBase64, prompt, maxTokens = 20L)
    }

    /**
     * Base64 인코딩된 이미지를 GPT-4o mini에 전송하여 흰색 글자와 대비되는 주요 색상(HEX) 반환.
     * API 오류 시 null 반환.
     */
    private fun callOpenAiForColor(imageBase64: String): String? {
        val prompt =
            """
            이 이미지는 건담프라 데칼 시트의 일부입니다.
            이미지에서 데칼의 주요 배경 색상을 찾아주세요.
            흰색 글자(#FFFFFF)가 위에 인쇄되는 배경색이므로, 반드시 어둡거나 채도가 높은 색상을 선택해야 합니다.
            흰색·밝은 회색·밝은 파스텔 계열은 절대 선택하지 마세요.
            가장 면적이 넓거나 가장 선명한 어두운 색을 하나 골라 HEX 코드 6자리(# 없이, 대문자)만 답해주세요. 예: FF0000
            찾을 수 없으면 빈 문자열을 반환하세요.
            """.trimIndent()
        return callOpenAiVision(imageBase64, prompt, maxTokens = 10L)
    }

    private fun callOpenAiVision(
        imageBase64: String,
        prompt: String,
        maxTokens: Long,
    ): String? {
        val params =
            ChatCompletionCreateParams
                .builder()
                .model(ChatModel.GPT_4O_MINI)
                .maxCompletionTokens(maxTokens)
                .addUserMessageOfArrayOfContentParts(
                    listOf(
                        ChatCompletionContentPart.ofText(
                            ChatCompletionContentPartText.builder().text(prompt).build(),
                        ),
                        ChatCompletionContentPart.ofImageUrl(
                            ChatCompletionContentPartImage
                                .builder()
                                .imageUrl(
                                    ChatCompletionContentPartImage.ImageUrl
                                        .builder()
                                        .url("data:image/png;base64,$imageBase64")
                                        .build(),
                                ).build(),
                        ),
                    ),
                ).build()
        return try {
            val response = openAIClient.chat().completions().create(params)
            response
                .choices()
                .firstOrNull()
                ?.message()
                ?.content()
                ?.orElse("") ?: ""
        } catch (e: Exception) {
            log.error(e) { "OpenAI API 오류" }
            null
        }
    }
}
