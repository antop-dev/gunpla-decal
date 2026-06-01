package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.service.ManualService
import com.example.gunpladecal.app.util.Base62
import com.example.gunpladecal.config.AppProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable

/** Thymeleaf 페이지 라우팅 컨트롤러 */
@Controller
class PageController(
    private val appProperties: AppProperties,
    private val manualService: ManualService,
    private val objectMapper: ObjectMapper,
) {
    @ModelAttribute("baseUrl")
    fun baseUrl(): String = appProperties.baseUrl

    /** 기본 JSON-LD: 메인 페이지용 WebSite 스키마 */
    @ModelAttribute("jsonLd")
    fun defaultJsonLd(): String =
        objectMapper.writeValueAsString(
            mapOf(
                "@context" to "https://schema.org",
                "@type" to "WebSite",
                "name" to "건담 메뉴얼",
                "description" to "건담프라 데칼 메뉴얼 목록",
                "url" to appProperties.baseUrl,
            ),
        )

    /** 사용자 메인 페이지 (index.html) */
    @GetMapping("/")
    fun index() = "index"

    /** 메뉴얼 직접 링크 진입 (/A, /1B 등 base62 ID). SEO 메타 태그 주입 후 JS가 동일 메뉴얼 로드 */
    @GetMapping("/{b62id:[0-9A-Za-z]+}")
    fun indexWithManual(
        @PathVariable b62id: String,
        model: Model,
    ): String {
        val id = Base62.decode(b62id) / 23
        runCatching { manualService.getManual(id, onlyPublished = true) }
            .onSuccess { manual ->
                model.addAttribute("manual", manual)
                model.addAttribute(
                    "jsonLd",
                    objectMapper.writeValueAsString(
                        mapOf(
                            "@context" to "https://schema.org",
                            "@type" to "Article",
                            "name" to "${manual.productName} | 건담 메뉴얼",
                            "description" to "${manual.grade.name} ${manual.modelNumber} ${manual.productName} 건담프라 데칼 메뉴얼",
                            "url" to "${appProperties.baseUrl}/${manual.b62id}",
                        ),
                    ),
                )
            }
        return "index"
    }

    /** 로그인 페이지 (이미 인증된 경우 /admin으로 리디렉션) */
    @GetMapping("/login")
    fun login(authentication: Authentication?): String = if (authentication?.isAuthenticated == true) "redirect:/admin" else "login"

    /** 관리자 페이지 (admin.html) */
    @GetMapping("/admin")
    fun admin() = "admin"
}
