package ai.antop.gunpla.app.controller

import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.service.ManualAssemblyService
import ai.antop.gunpla.config.AppProperties
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
    private val manualAssemblyService: ManualAssemblyService,
    private val objectMapper: ObjectMapper,
) {
    /** 모든 뷰에 baseUrl 모델 속성 주입 */
    @ModelAttribute("baseUrl")
    fun baseUrl(): String = appProperties.baseUrl

    /** 모든 뷰에 GA4 측정 ID 주입 (미설정 시 null → 템플릿에서 GA4 비활성) */
    @ModelAttribute("ga4")
    fun ga4(): String? = appProperties.ga4

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
    @GetMapping("/{id:[0-9A-Za-z]+}")
    fun indexWithManual(
        @PathVariable id: ManualId,
        model: Model,
    ): String {
        runCatching { manualAssemblyService.getManual(id, onlyPublished = true) }
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
                            "url" to "${appProperties.baseUrl}/${manual.id}",
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
