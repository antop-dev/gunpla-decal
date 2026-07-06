package ai.antop.gunpla.app.controller

import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.dto.ManualAssemblyDto
import ai.antop.gunpla.app.dto.ManualSummaryDto
import ai.antop.gunpla.app.service.ManualAssemblyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 사용자 페이지 전용 읽기 API (메뉴얼 조회) */
@RestController
@RequestMapping("/api/user")
class UserApiController(
    private val manualAssemblyService: ManualAssemblyService,
) {
    /** 메뉴얼 목록 반환 (공개만). q가 있으면 등급명·제품명으로 검색 */
    @GetMapping("/manuals")
    fun list(
        @RequestParam(required = false) q: String?,
    ): List<ManualSummaryDto> = manualAssemblyService.getManuals(q, true)

    /** 메뉴얼 단건 조회 (공개만, 데칼·썸네일 목록 포함) */
    @GetMapping("/{manualId}")
    fun get(
        @PathVariable manualId: ManualId,
    ): ManualAssemblyDto = manualAssemblyService.getManual(manualId, onlyPublished = true, useCache = true)
}
