package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.domain.Grade
import com.example.gunpladecal.app.dto.AiDetectRequest
import com.example.gunpladecal.app.dto.DecalCreateRequest
import com.example.gunpladecal.app.dto.DecalUpdateRequest
import com.example.gunpladecal.app.dto.ManualUpdateRequest
import com.example.gunpladecal.app.service.ManualService
import com.example.gunpladecal.openai.AiDecalService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val log = KotlinLogging.logger {}

/** 관리자 페이지 전용 CRUD API (메뉴얼·데칼 등록/수정/삭제, AI 탐지) */
@RestController
@RequestMapping("/api/admin/manuals")
class AdminController(
    private val manualService: ManualService,
    private val aiDecalService: AiDecalService,
) {
    /** 메뉴얼 전체 목록 반환 (검색 없음) */
    @GetMapping
    fun list(): Any {
        log.debug { "GET /api/admin/manuals" }
        return manualService.getAllManuals()
    }

    /** 메뉴얼 등록 (등급·형식번호·제품명·PDF 파일 멀티파트 폼) */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestParam grade: Grade,
        @RequestParam modelNumber: String,
        @RequestParam productName: String,
        @RequestParam("pdf") pdf: MultipartFile,
    ): Any {
        log.debug { "POST /api/admin/manuals grade=$grade modelNumber=$modelNumber productName=$productName" }
        return manualService.createManual(grade, modelNumber, productName, pdf)
    }

    /** 메뉴얼 정보 수정 (등급·제품명) */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: ManualUpdateRequest,
    ): Any {
        log.debug { "PUT /api/admin/manuals/$id request=$request" }
        return manualService.updateManual(id, request)
    }

    /** 메뉴얼 삭제 (연관 데칼 및 PDF 파일 함께 제거) */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        log.debug { "DELETE /api/admin/manuals/$id" }
        manualService.deleteManual(id)
    }

    /** 데칼 등록 (PDF 페이지 내 좌표 지정) */
    @PostMapping("/{manualId}/decals")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDecal(
        @PathVariable manualId: Long,
        @RequestBody request: DecalCreateRequest,
    ): Any {
        log.debug { "POST /api/admin/manuals/$manualId/decals request=$request" }
        return manualService.addDecal(manualId, request)
    }

    /** 데칼 정보 수정 (번호·좌표·색상) */
    @PutMapping("/{manualId}/decals/{decalId}")
    fun updateDecal(
        @PathVariable manualId: Long,
        @PathVariable decalId: Long,
        @RequestBody request: DecalUpdateRequest,
    ): Any {
        log.debug { "PUT /api/admin/manuals/$manualId/decals/$decalId request=$request" }
        return manualService.updateDecal(manualId, decalId, request)
    }

    /** 데칼 삭제 */
    @DeleteMapping("/{manualId}/decals/{decalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDecal(
        @PathVariable manualId: Long,
        @PathVariable decalId: Long,
    ) {
        log.debug { "DELETE /api/admin/manuals/$manualId/decals/$decalId" }
        manualService.deleteDecal(manualId, decalId)
    }

    /** 현재 페이지 이미지를 AI(GPT-4o Vision)로 분석하여 데칼을 자동 탐지하고 DB에 저장 */
    @PostMapping("/{manualId}/pages/{pageNumber}/ai-detect")
    @ResponseStatus(HttpStatus.CREATED)
    fun aiDetect(
        @PathVariable manualId: Long,
        @PathVariable pageNumber: Int,
        @RequestBody request: AiDetectRequest,
    ): Any {
        log.debug { "POST /api/admin/manuals/$manualId/pages/$pageNumber/ai-detect" }
        return aiDecalService.detectAndSave(manualId, pageNumber, request.imageBase64)
    }
}
