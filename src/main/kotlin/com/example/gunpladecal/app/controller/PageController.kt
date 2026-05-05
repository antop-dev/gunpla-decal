package com.example.gunpladecal.app.controller

import com.example.gunpladecal.openai.OpenAiAvailabilityChecker
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/** Thymeleaf 페이지 라우팅 컨트롤러 */
@Controller
class PageController(
    private val openAiAvailabilityChecker: OpenAiAvailabilityChecker,
) {
    /** 사용자 메인 페이지 (index.html) */
    @GetMapping("/")
    fun index() = "index"

    /** 관리자 페이지 (admin.html) - OpenAI 가용성 여부를 모델에 전달 */
    @GetMapping("/admin")
    fun admin(model: Model): String {
        model.addAttribute("openAiAvailable", openAiAvailabilityChecker.available)
        return "admin"
    }
}
