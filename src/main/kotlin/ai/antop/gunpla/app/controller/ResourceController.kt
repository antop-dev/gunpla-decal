package ai.antop.gunpla.app.controller

import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.service.ResourceService
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

/** PDF·썸네일 파일 제공 (관리자/사용자 공통, 인증 불필요) */
@RestController
@RequestMapping("/resource")
class ResourceController(
    private val resourceService: ResourceService,
) {
    /** PDF 스트리밍 */
    @GetMapping("/{manualId}")
    fun pdf(
        @PathVariable manualId: ManualId,
    ): ResponseEntity<Resource> {
        val resource = resourceService.getManual(manualId)
        val disposition = ContentDisposition.builder("inline").filename("$manualId.pdf").build()
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(resource)
    }

    /** 특정 페이지 썸네일 PNG 이미지 반환 */
    @GetMapping("/{manualId}/thumbnails/{pageNumber}")
    fun thumbnail(
        @PathVariable manualId: ManualId,
        @PathVariable pageNumber: Int,
    ): ResponseEntity<Resource> {
        val resource = resourceService.getThumbnail(manualId, pageNumber)
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
            .contentType(MediaType.IMAGE_PNG)
            .body(resource)
    }
}
