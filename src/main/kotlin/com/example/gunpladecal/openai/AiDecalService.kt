package com.example.gunpladecal.openai

import com.example.gunpladecal.app.domain.Decal
import com.example.gunpladecal.app.domain.DecalColor
import com.example.gunpladecal.app.domain.DecalShape
import com.example.gunpladecal.app.dto.DecalResponse
import com.example.gunpladecal.app.repository.DecalRepository
import com.example.gunpladecal.app.repository.ManualRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import kotlin.math.hypot

private val log = KotlinLogging.logger {}

/** AI(OpenAI GPT-4o Vision)를 이용한 데칼 자동 탐지 서비스 */
@Service
@Transactional
class AiDecalService(
    private val openAiClient: OpenAIClient,
    private val manualRepository: ManualRepository,
    private val decalRepository: DecalRepository,
    private val objectMapper: ObjectMapper,
) {
    /**
     * 현재 페이지 이미지를 AI에게 전달하여 데칼 번호와 위치를 탐지한 후 DB에 저장한다.
     * x,y 좌표 기준 거리 5 이내인 기존 마커와 겹치는 위치는 중복으로 처리하여 저장하지 않는다.
     */
    fun detectAndSave(
        manualId: Long,
        pageNumber: Int,
        imageBase64: String,
    ): List<DecalResponse> {
        log.debug { "AI 데칼 탐지 시작: manualId=$manualId, page=$pageNumber" }
        val manual =
            manualRepository
                .findById(manualId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }

        // 현재 페이지에 이미 등록된 데칼 목록 (중복 체크에 사용)
        val existing = decalRepository.findByManualIdAndPageNumber(manualId, pageNumber)

        // OpenAI Vision API로 데칼 탐지
        val detected = callVisionApi(imageBase64)
        log.debug { "AI 탐지 결과: ${detected.size}개" }

        // 기존 마커와 거리 5 이내인 위치 제외 후 DB 저장
        val saved =
            detected
                .filter { ai -> !isDuplicate(ai.x, ai.y, existing) }
                .map { ai ->
                    Decal(
                        manual = manual,
                        pageNumber = pageNumber,
                        decalNumber = ai.decalNumber,
                        x = ai.x,
                        y = ai.y,
                        color = ai.color,
                        shape = ai.shape,
                    )
                }.let { decalRepository.saveAll(it) }
                .map { it.toResponse() }

        log.debug { "저장된 데칼: ${saved.size}개" }
        return saved
    }

    /** GPT-4o Vision API를 호출하여 이미지에서 데칼 번호·위치를 탐지 */
    private fun callVisionApi(imageBase64: String): List<AiDecal> {
        val prompt =
            """
            이 이미지는 건담 프라모델 메뉴얼의 PDF 페이지입니다. 이 페이지에서 데칼(스티커) 번호 표시를 모두 찾아주세요.
            데칼 번호란 메뉴얼 도면에서 화살표나 선으로 부품 위치를 가리키며 함께 표시된 숫자/문자입니다.
            데칼 번호는 동그라미 또는 네모(정사각형) 안에 표시됩니다. 배경색은 여러가지가 있지만 검은색 이외에는 하얀색으로 인식해주세요.

            데칼 번호 형식:
            - 숫자: 1, 2, 3, ... (최대 3자리)
            - 영문 대문자: A, B, C, ...
            - 일본어 가나: ア, イ, ウ, ...

            다음 JSON 형식으로만 응답하세요.
            마크다운이나 코드 블록 없이 순수 JSON 포멧으로만 응답하세요.
            예: {"decals":[{"decalNumber":"번호","x":가로위치,"y":세로위치,"color":"WHITE또는BLACK","shape":"CIRCLE또는SQUARE"}]}

            규칙:
            - x, y는 이미지 전체 너비·높이 기준 0~100 퍼센트 위치
            - color: 데칼 배경(스티커 바탕)이 검정이면 BLACK, 다른 배경은 WHITE
            - shape: 번호를 감싸는 도형이 동그라미면 CIRCLE, 네모(정사각형)면 SQUARE
            - 이미지에서 찾을 수 없으면 {"decals":[]} 반환
            """.trimIndent()

        val params =
            ChatCompletionCreateParams
                .builder()
                .model(ChatModel.GPT_5_CHAT_LATEST)
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
                                        .url(imageBase64)
                                        .build(),
                                ).build(),
                        ),
                    ),
                ).build()

        val response = openAiClient.chat().completions().create(params)
        val content =
            response
                .choices()
                .firstOrNull()
                ?.message()
                ?.content()
                ?.orElse(null)
                ?: return emptyList()

        log.debug { "AI 응답: $content" }
        return parseAiResponse(content)
    }

    /** AI 응답 JSON을 파싱하여 유효 범위(0~100%) 내 데칼만 반환한다. */
    private fun parseAiResponse(content: String): List<AiDecal> =
        try {
            objectMapper
                .readValue<AiResponse>(content)
                .decals
                .filter { it.x in 0.0..100.0 && it.y in 0.0..100.0 }
        } catch (e: Exception) {
            log.warn { "AI 응답 파싱 실패: ${e.message}" }
            emptyList()
        }

    /**
     * 기존 마커와 유클리드 거리가 5 이내이면 중복으로 판정.
     * 같은 위치에 데칼이 이미 등록된 경우 AI가 재탐지해도 저장하지 않기 위한 기준.
     */
    private fun isDuplicate(
        x: Double,
        y: Double,
        existing: List<Decal>,
    ): Boolean = existing.any { hypot(it.x - x, it.y - y) < 5.0 }

    private fun Decal.toResponse() = DecalResponse(id, pageNumber, decalNumber, x, y, color, shape)

    /** AI 응답 최상위 래퍼 */
    private data class AiResponse(
        val decals: List<AiDecal>,
    )

    /** AI가 탐지한 데칼 위치 정보 */
    private data class AiDecal(
        val decalNumber: String,
        val x: Double,
        val y: Double,
        val color: DecalColor = DecalColor.WHITE,
        val shape: DecalShape = DecalShape.CIRCLE,
    )
}
