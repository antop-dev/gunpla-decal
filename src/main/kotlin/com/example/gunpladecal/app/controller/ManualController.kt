package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.domain.ManualId
import com.example.gunpladecal.app.dto.ManualDetailDto
import com.example.gunpladecal.app.dto.ManualSummaryDto
import com.example.gunpladecal.app.service.ManualAssemblyService
import com.example.gunpladecal.app.service.ManualService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 사용자 페이지 전용 읽기 API (메뉴얼 조회) */
@RestController
@RequestMapping("/api/manuals")
class ManualController(
    private val manualService: ManualService,
    private val manualAssemblyService: ManualAssemblyService,
) {
    /** 메뉴얼 목록 반환 (공개만). q가 있으면 등급명·제품명으로 검색 */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
    ): List<ManualSummaryDto> = manualService.getAllManuals(q, onlyPublished = true)

    /** 메뉴얼 단건 조회 (공개만, 데칼 목록 포함) */
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: ManualId,
    ): ManualDetailDto = manualAssemblyService.getManual(id.value, onlyPublished = true)
}
