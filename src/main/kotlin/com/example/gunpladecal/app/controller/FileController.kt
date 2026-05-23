package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.service.ManualService
import com.example.gunpladecal.app.service.ThumbnailService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/** PDF·썸네일 파일 제공 (관리자/사용자 공통, 인증 불필요) */
@RestController
@RequestMapping("/manuals")
class FileController(
    private val manualService: ManualService,
    private val thumbnailService: ThumbnailService,
) {
    /** PDF 스트리밍 */
    @GetMapping("/{id}/pdf")
    fun pdf(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        log.debug { "GET /manuals/$id/pdf" }
        val resource = manualService.getPdfResource(id)
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "inline; filename=\"$id.pdf\"")
            .body(resource)
    }

    /** 썸네일 URL 목록 반환 */
    @GetMapping("/{id}/thumbnails")
    fun listThumbnails(
        @PathVariable id: Long,
    ): List<String> {
        log.debug { "GET /manuals/$id/thumbnails" }
        return thumbnailService.findThumbnails(id).map { "/manuals/$id/thumbnails/${it.pageNumber}" }
    }

    /** 특정 페이지 썸네일 PNG 이미지 반환 */
    @GetMapping("/{id}/thumbnails/{page}")
    fun thumbnail(
        @PathVariable id: Long,
        @PathVariable page: Int,
    ): ResponseEntity<Resource> {
        log.debug { "GET /manuals/$id/thumbnails/$page" }
        val resource = thumbnailService.getThumbnailResource(id, page)
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noCache())
            .contentType(MediaType.IMAGE_PNG)
            .body(resource)
    }
}
