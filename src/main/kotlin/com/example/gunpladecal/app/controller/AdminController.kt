package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.domain.Grade
import com.example.gunpladecal.app.domain.ManualId
import com.example.gunpladecal.app.dto.DecalCreateRequestDto
import com.example.gunpladecal.app.dto.DecalResponseDto
import com.example.gunpladecal.app.dto.DecalUpdateRequestDto
import com.example.gunpladecal.app.dto.ManualDetailDto
import com.example.gunpladecal.app.dto.ManualSummaryDto
import com.example.gunpladecal.app.dto.ManualUpdateRequestDto
import com.example.gunpladecal.app.service.DecalService
import com.example.gunpladecal.app.service.ManualAssemblyService
import com.example.gunpladecal.app.service.ManualService
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

/** 관리자 페이지 전용 CRUD API (메뉴얼·데칼 등록/수정/삭제) */
@RestController
@RequestMapping("/api/admin/manuals")
class AdminController(
    private val manualService: ManualService,
    private val manualAssemblyService: ManualAssemblyService,
    private val decalService: DecalService,
) {
    /** 메뉴얼 전체 목록 반환 (q가 있으면 서버 필터링) */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
    ): List<ManualSummaryDto> = manualService.getAllManuals(q)

    /** 메뉴얼 등록 (파일 업로드 또는 URL 지정, 멀티파트 폼) */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestParam grade: Grade,
        @RequestParam modelNumber: String,
        @RequestParam productName: String,
        @RequestParam("pdf", required = false) pdf: MultipartFile?,
        @RequestParam("pdfUrl", required = false) pdfUrl: String?,
        @RequestParam(required = false) link: String?,
    ): ManualSummaryDto = manualAssemblyService.createManual(grade, modelNumber, productName, pdf, pdfUrl, link)

    /** 메뉴얼 정보 수정 (등급·제품명) */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: ManualId,
        @RequestBody request: ManualUpdateRequestDto,
    ): ManualSummaryDto = manualService.updateManual(id.value, request)

    /** 메뉴얼 단건 조회 (미공개 포함, 관리자 전용) */
    @GetMapping("/{id}")
    fun getManual(
        @PathVariable id: ManualId,
    ): ManualDetailDto = manualAssemblyService.getManual(id.value)

    /** 공개 여부 토글 */
    @PatchMapping("/{id}/published")
    fun togglePublished(
        @PathVariable id: ManualId,
    ): ManualSummaryDto = manualService.togglePublished(id.value)

    /** 메뉴얼 삭제 (연관 데칼 및 PDF·썸네일 파일 함께 제거) */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: ManualId,
    ) = manualAssemblyService.deleteManual(id.value)

    /** 데칼 등록 (PDF 페이지 내 좌표 지정) */
    @PostMapping("/{manualId}/decals")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDecal(
        @PathVariable manualId: ManualId,
        @RequestBody request: DecalCreateRequestDto,
    ): DecalResponseDto = decalService.addDecal(manualId.value, request)

    /** 데칼 정보 수정 (번호·좌표·색상) */
    @PutMapping("/{manualId}/decals/{decalId}")
    fun updateDecal(
        @PathVariable manualId: ManualId,
        @PathVariable decalId: Long,
        @RequestBody request: DecalUpdateRequestDto,
    ): DecalResponseDto = decalService.updateDecal(manualId.value, decalId, request)

    /** 데칼 삭제 */
    @DeleteMapping("/{manualId}/decals/{decalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDecal(
        @PathVariable manualId: ManualId,
        @PathVariable decalId: Long,
    ) = decalService.deleteDecal(manualId.value, decalId)
}
