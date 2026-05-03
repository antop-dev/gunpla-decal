package com.example.gunpladecal.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** Thymeleaf 페이지 라우팅 컨트롤러 */
@Controller
class PageController {
    /** 사용자 메인 페이지 (index.html) */
    @GetMapping("/")
    fun index() = "index"

    /** 관리자 페이지 (admin.html) */
    @GetMapping("/admin")
    fun admin() = "admin"
}
