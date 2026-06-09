package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.dto.DecalCreateRequestDto
import ai.antop.gunpla.app.dto.DecalItemDto
import ai.antop.gunpla.app.dto.DecalUpdateRequestDto
import ai.antop.gunpla.app.dto.ManualItemDto
import ai.antop.gunpla.app.dto.ManualSummaryDto
import ai.antop.gunpla.app.dto.ManualUpdateRequestDto
import ai.antop.gunpla.app.event.ManualChangedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/** 관리자 페이지 비즈니스 로직. 메뉴얼·데칼·썸네일 CRUD를 조율한다 */
@Service
class AdminService(
    private val manualAssemblyService: ManualAssemblyService,
    private val manualService: ManualService,
    private val decalService: DecalService,
    private val thumbnailService: ThumbnailService,
    private val openAiService: OpenAiService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** 메뉴얼 전체 목록 반환 (미공개 포함). q가 있으면 서버 필터링 */
    @Transactional(readOnly = true)
    fun getManuals(q: String? = null): List<ManualSummaryDto> = manualService.getAllManuals(q, false)

    /** 메뉴얼 단건 조회 (미공개 포함, 캐시 미적용) */
    @Transactional(readOnly = true)
    fun getManual(manualId: ManualId) =
        manualAssemblyService.getManual(
            manualId = manualId,
            onlyPublished = false,
            useCache = false,
        )

    /** 메뉴얼 등록: DB 저장 후 썸네일 생성 */
    @Transactional
    fun createManual(
        grade: Grade,
        modelNumber: String,
        productName: String,
        pdfFile: MultipartFile? = null,
        pdfUrl: String? = null,
        link: String? = null,
    ): ManualSummaryDto {
        val manual = manualService.createManual(grade, modelNumber, productName, pdfFile, pdfUrl, link)
        thumbnailService.generateThumbnails(manual.id, manual.pdfPath)
        return manual.toSummary()
    }

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

    /**
     * AI(GPT-4o mini)를 이용하여 PDF 좌표 주변 이미지에서 데칼 번호 인식.
     * 인식 실패 또는 유효하지 않은 형식이면 null 반환.
     */
    fun recognizeDecalNumber(
        manualId: ManualId,
        pageNumber: Int,
        x: Double,
        y: Double,
    ): String? {
        val pdf = manualService.getPdfResource(manualId)
        return openAiService.recognizeDecalNumber(pdf.file, pageNumber, x, y)
    }

    private fun ManualItemDto.toSummary() = ManualSummaryDto(id, grade, modelNumber, productName, link, published, createdAt, updatedAt)
}
