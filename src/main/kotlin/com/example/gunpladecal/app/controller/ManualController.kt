package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.service.ManualService
import com.example.gunpladecal.app.util.Base62
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

/** 사용자 페이지 전용 읽기 API (메뉴얼 조회) */
@RestController
@RequestMapping("/api/manuals")
class ManualController(private val manualService: ManualService) {
    /** 메뉴얼 목록 반환 (공개만). q가 있으면 등급명·제품명으로 검색 */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
    ): Any {
        log.debug { "GET /api/manuals?q=$q" }
        return manualService.getAllManuals(q, onlyPublished = true)
    }

    /** 메뉴얼 단건 조회 (공개만, 데칼 목록 포함) */
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): Any {
        log.debug { "GET /api/manuals/$id" }
        return manualService.getManual(id, onlyPublished = true)
    }

    /** 메뉴얼 단건 조회 - base62 ID (공개만, 사용자 페이지 전용) */
    @GetMapping("/b/{b62id}")
    fun getByB62(
        @PathVariable b62id: String,
    ): Any {
        val id = Base62.decode(b62id) / 23
        log.debug { "GET /api/manuals/b/$b62id (id=$id)" }
        return manualService.getManual(id, onlyPublished = true)
    }
}
