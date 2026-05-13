package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.service.ManualService
import com.example.gunpladecal.app.util.Base62
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/** 사용자 페이지 전용 읽기 API (메뉴얼 조회 및 PDF 스트리밍) */
@RestController
@RequestMapping("/api/manuals")
class ManualController(private val manualService: ManualService) {
    /** 메뉴얼 목록 반환. q가 있으면 등급명·제품명으로 검색 */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
    ): Any {
        log.debug { "GET /api/manuals?q=$q" }
        return manualService.getAllManuals(q)
    }

    /** 메뉴얼 단건 조회 (데칼 목록 포함) */
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): Any {
        log.debug { "GET /api/manuals/$id" }
        return manualService.getManual(id)
    }

    /** PDF 파일 스트리밍 (inline 표시, pdf.js가 직접 요청) */
    @GetMapping("/{id}/pdf")
    fun pdf(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        log.debug { "GET /api/manuals/$id/pdf" }
        val resource = manualService.getPdfResource(id)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "inline; filename=\"manual.pdf\"")
            .body(resource)
    }

    /** 메뉴얼 단건 조회 - base62 ID (사용자 페이지 전용) */
    @GetMapping("/b/{b62id}")
    fun getByB62(
        @PathVariable b62id: String,
    ): Any {
        val id = Base62.decode(b62id) / 23
        log.debug { "GET /api/manuals/b/$b62id (id=$id)" }
        return manualService.getManual(id)
    }

    /** PDF 파일 스트리밍 - base62 ID (사용자 페이지 전용) */
    @GetMapping("/b/{b62id}/pdf")
    fun pdfByB62(
        @PathVariable b62id: String,
    ): ResponseEntity<Resource> {
        val id = Base62.decode(b62id) / 23
        log.debug { "GET /api/manuals/b/$b62id/pdf (id=$id)" }
        val resource = manualService.getPdfResource(id)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "inline; filename=\"manual.pdf\"")
            .body(resource)
    }
}
