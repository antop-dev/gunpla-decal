package ai.antop.gunpla.app.controller

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.dto.DecalCreateRequestDto
import ai.antop.gunpla.app.dto.DecalItemDto
import ai.antop.gunpla.app.dto.DecalUpdateRequestDto
import ai.antop.gunpla.app.dto.ManualAssemblyDto
import ai.antop.gunpla.app.dto.ManualSummaryDto
import ai.antop.gunpla.app.dto.ManualUpdateRequestDto
import ai.antop.gunpla.app.service.AdminService
import ai.antop.gunpla.app.service.ManualTaskService
import ai.antop.gunpla.app.service.SseService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** 관리자 페이지 전용 CRUD API (메뉴얼·데칼 등록/수정/삭제) */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val adminService: AdminService,
    private val sseService: SseService,
    private val manualTaskService: ManualTaskService,
) {
    /** SSE 연결. 메뉴얼 등록 결과를 실시간으로 수신한다 */
    @GetMapping("/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sse(): SseEmitter = sseService.connect()

    /** 메뉴얼 전체 목록 반환 (q가 있으면 서버 필터링) */
    @GetMapping("/manuals")
    fun manualList(
        @RequestParam(required = false) q: String?,
    ): List<ManualSummaryDto> = adminService.getManuals(q)

    /** 메뉴얼 등록 요청 수신 후 즉시 202 반환. 실제 처리(PDF 저장·썸네일 생성)는 비동기로 진행되며 결과는 SSE로 전달된다 */
    @PostMapping("/manuals", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun create(
        @RequestParam grade: Grade,
        @RequestParam modelNumber: String,
        @RequestParam productName: String,
        @RequestParam("pdf", required = false) pdf: MultipartFile?,
        @RequestParam("pdfUrl", required = false) pdfUrl: String?,
        @RequestParam(required = false) link: String?,
    ) {
        val pdfBytes = pdf?.takeIf { !it.isEmpty }?.bytes
        manualTaskService.createManual(grade, modelNumber, productName, pdfBytes, pdfUrl, link)
    }

    /** 메뉴얼 정보 수정 (등급·제품명) */
    @PutMapping("/manuals/{manualId}")
    fun update(
        @PathVariable manualId: ManualId,
        @RequestBody request: ManualUpdateRequestDto,
    ) = adminService.updateManual(manualId, request)

    /** 메뉴얼 단건 조회 (미공개 포함, 관리자 전용) */
    @GetMapping("/manuals/{manualId}")
    fun getManual(
        @PathVariable manualId: ManualId,
    ): ManualAssemblyDto = adminService.getManual(manualId)

    /** 공개 여부 설정 */
    @PatchMapping("/{manualId}/published")
    fun togglePublished(
        @PathVariable manualId: ManualId,
        @RequestParam published: Boolean,
    ) = adminService.updatePublished(manualId, published)

    /** 메뉴얼 삭제 (연관 데칼 및 PDF·썸네일 파일 함께 제거) */
    @DeleteMapping("/manuals/{manualId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable manualId: ManualId,
    ) = adminService.deleteManual(manualId)

    /** 데칼 등록 (PDF 페이지 내 좌표 지정) */
    @PostMapping("/manuals/{manualId}/decals")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDecal(
        @PathVariable manualId: ManualId,
        @RequestBody request: DecalCreateRequestDto,
    ): DecalItemDto = adminService.addDecal(manualId, request)

    /** 데칼 정보 수정 (번호·좌표·색상) */
    @PutMapping("/decals/{decalId}")
    fun updateDecal(
        @PathVariable decalId: Long,
        @RequestBody request: DecalUpdateRequestDto,
    ): DecalItemDto = adminService.updateDecal(decalId, request)

    /** 데칼 삭제 */
    @DeleteMapping("/decals/{decalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDecal(
        @PathVariable decalId: Long,
    ) = adminService.deleteDecal(decalId)

    /** AI(GPT-4o mini)로 PDF 좌표 주변 데칼 번호 인식 */
    @PostMapping("/manuals/{manualId}/recognize")
    fun recognize(
        @PathVariable manualId: ManualId,
        @RequestBody request: DecalRecognizeRequest,
    ): DecalRecognizeResponse {
        val character = adminService.recognizeDecalNumber(manualId, request.page, request.x, request.y)
        return DecalRecognizeResponse(character != null, character)
    }
}

/** AI 데칼 번호 인식 결과 응답 */
data class DecalRecognizeResponse(
    /** 인식 성공 여부 */
    val found: Boolean,
    /** 인식된 데칼 번호 문자열. found=false이면 null */
    val character: String?,
)

/** AI 데칼 번호 인식 요청 */
data class DecalRecognizeRequest(
    /** PDF 페이지 번호 (1-based) */
    val page: Int,
    /** PDF 캔버스 기준 가로 위치 (0~100 %) */
    val x: Double,
    /** PDF 캔버스 기준 세로 위치 (0~100 %) */
    val y: Double,
)
