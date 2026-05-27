package com.example.gunpladecal.app.service

import com.example.gunpladecal.config.AppProperties
import com.openai.client.OpenAIClient
import com.openai.errors.OpenAIException
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.Base64
import javax.imageio.ImageIO

private val log = KotlinLogging.logger {}

@Service
class OpenAiService(
    private val openAIClient: OpenAIClient,
    private val appProperties: AppProperties,
) {
    private val lock = Any()
    private var availableCache = false
    private var cacheExpiry = Instant.EPOCH

    fun isAvailable(): Boolean =
        synchronized(lock) {
            if (Instant.now().isBefore(cacheExpiry)) return availableCache
            availableCache = probe()
            cacheExpiry = Instant.now().plusSeconds(3600)
            availableCache
        }

    private fun probe(): Boolean {
        if (appProperties.openAiKey.isNullOrBlank()) return false
        return try {
            val params =
                ChatCompletionCreateParams
                    .builder()
                    .model(ChatModel.GPT_4O_MINI)
                    .maxCompletionTokens(1)
                    .addUserMessageOfArrayOfContentParts(
                        listOf(
                            ChatCompletionContentPart.ofText(
                                ChatCompletionContentPartText.builder().text("hi").build(),
                            ),
                        ),
                    ).build()
            openAIClient.chat().completions().create(params)
            log.debug { "OpenAI 가용성 체크 성공" }
            true
        } catch (e: OpenAIException) {
            log.warn(e) { "OpenAI 가용성 체크 실패" }
            false
        }
    }

    // 숫자 1~3자리, 영어 대문자 1글자, 히라가나/카타카나 1글자만 유효한 데칼 번호로 허용
    private val validPattern = Regex("^[0-9]{1,3}$|^[A-Z]$|^[ぁ-ゖァ-ヶ]$")

    fun recognizeDecalNumber(
        pdfFile: File,
        page: Int,
        x: Double,
        y: Double,
    ): String? {
        val imageBase64 = renderAndCrop(pdfFile, page, x, y) ?: return null
        val result = callOpenAi(imageBase64) ?: return null
        val trimmed = result.trim()
        return if (validPattern.matches(trimmed)) trimmed else null
    }

    private fun renderAndCrop(
        pdfFile: File,
        page: Int,
        x: Double,
        y: Double,
    ): String? =
        try {
            Loader.loadPDF(pdfFile).use { doc ->
                val renderer = PDFRenderer(doc)
                // 3배 확대 렌더링: 저해상도 PDF에서 번호 인식률을 높이기 위함
                val image: BufferedImage = renderer.renderImage(page - 1, 3.0f)
                // 좌표는 퍼센트(%) 기준이므로 픽셀 환산 후, 중심에서 cropHalf 픽셀 반경으로 잘라냄
                val cropHalf = 20
                val cx = (x / 100.0 * image.width).toInt()
                val cy = (y / 100.0 * image.height).toInt()
                val x1 = (cx - cropHalf).coerceAtLeast(0)
                val y1 = (cy - cropHalf).coerceAtLeast(0)
                val x2 = (cx + cropHalf).coerceAtMost(image.width)
                val y2 = (cy + cropHalf).coerceAtMost(image.height)
                val cropped = image.getSubimage(x1, y1, x2 - x1, y2 - y1)
                val baos = ByteArrayOutputStream()
                ImageIO.write(cropped, "png", baos)
                Base64.getEncoder().encodeToString(baos.toByteArray())
            }
        } catch (e: Exception) {
            log.error(e) { "PDF 렌더링 실패: ${pdfFile.path}" }
            null
        }

    private fun callOpenAi(imageBase64: String): String? {
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
        val params =
            ChatCompletionCreateParams
                .builder()
                .model(ChatModel.GPT_4O_MINI)
                // 번호 하나만 반환하므로 토큰을 최소로 제한
                .maxCompletionTokens(20)
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
            log.debug { "OpenAI 응답: $response" }
            response
                .choices()
                .firstOrNull()
                ?.message()
                ?.content()
                ?.orElse("") ?: ""
        } catch (e: OpenAIException) {
            log.error(e) { "OpenAI API 오류" }
            null
        }
    }
}
