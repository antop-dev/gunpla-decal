package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.dto.DecalCreateRequestDto
import ai.antop.gunpla.app.dto.DecalItemDto
import ai.antop.gunpla.app.dto.DecalUpdateRequestDto
import ai.antop.gunpla.app.dto.ManualSummaryDto
import ai.antop.gunpla.app.dto.ManualUpdateRequestDto
import ai.antop.gunpla.app.event.ManualChangedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 관리자 페이지 비즈니스 로직. 메뉴얼·데칼·썸네일 CRUD를 조율한다 */
@Service
class AdminService(
    private val manualAssemblyService: ManualAssemblyService,
    private val manualService: ManualService,
    private val decalService: DecalService,
    private val thumbnailService: ThumbnailService,
    private val openAiService: OpenAiService,
    private val onnxDecalService: OnnxDecalService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** 메뉴얼 목록 검색 (미공개 포함). 각 조건은 null·빈 값이면 무시 */
    @Transactional(readOnly = true)
    fun searchManuals(
        grade: Grade?,
        published: Boolean?,
        modelNumber: String?,
        productName: String?,
    ): List<ManualSummaryDto> = manualService.searchManuals(grade, published, modelNumber, productName)

    /** 메뉴얼 단건 조회 (미공개 포함, 캐시 미적용) */
    @Transactional(readOnly = true)
    fun getManual(manualId: ManualId) =
        manualAssemblyService.getManual(
            manualId = manualId,
            onlyPublished = false,
            useCache = false,
        )

    /** 메뉴얼 정보 수정 (등급·형식번호·제품명, null 필드는 변경하지 않음) */
    @Transactional
    fun updateManual(
        manualId: ManualId,
        request: ManualUpdateRequestDto,
    ) {
        manualService.updateManual(manualId, request)
    }

    /** 공개 여부 설정. published=true이면 캐시 무효화 이벤트가 발행된다 */
    @Transactional
    fun updatePublished(
        manualId: ManualId,
        published: Boolean,
    ) {
        manualService.updatePublished(manualId, published)
    }

    /** 메뉴얼 삭제: 캐시 제거 후 데칼·썸네일 파일·PDF·DB 레코드 삭제 */
    @Transactional
    fun deleteManual(manualId: ManualId) {
        decalService.deleteDecals(manualId)
        thumbnailService.deleteThumbnails(manualId)
        manualService.deleteManual(manualId)
        eventPublisher.publishEvent(ManualChangedEvent(manualId))
    }

    /** 데칼 등록 (PDF 페이지 내 좌표 지정) */
    fun addDecal(
        manualId: ManualId,
        request: DecalCreateRequestDto,
    ): DecalItemDto = decalService.addDecal(manualId, request)

    /** 데칼 정보 수정 (번호·좌표·색상) */
    fun updateDecal(
        decalId: Long,
        request: DecalUpdateRequestDto,
    ): DecalItemDto = decalService.updateDecal(decalId, request)

    /** 데칼 삭제 */
    fun deleteDecal(decalId: Long) {
        decalService.deleteDecal(decalId)
    }

    /** 많이 사용된 일본어 문자 상위 20개 반환 */
    fun getJapaneseTop20(): List<String> = decalService.getJapaneseTop20()

    /**
     * AI(GPT-4o mini)를 이용하여 전달받은 크롭 이미지에서 데칼 번호 인식.
     * 인식 실패 또는 유효하지 않은 형식이면 null 반환.
     */
    fun recognizeDecalNumber(imageBytes: ByteArray): String? = openAiService.recognizeDecalNumber(imageBytes)

    /**
     * ONNX EfficientNet-B0 모델을 이용하여 전달받은 크롭 이미지에서 데칼 번호 인식.
     * 모델 미로드 또는 인식 실패 시 null 반환.
     */
    fun recognizeDecalNumberOnnx(imageBytes: ByteArray): String? = onnxDecalService.recognizeDecalNumber(imageBytes)

    /**
     * AI(GPT-4o mini)를 이용하여 전달받은 크롭 이미지에서 주요 색상(HEX) 인식.
     * 인식 실패 시 null 반환.
     */
    fun recognizeDecalColor(imageBytes: ByteArray): String? = openAiService.recognizeDecalColor(imageBytes)
}
