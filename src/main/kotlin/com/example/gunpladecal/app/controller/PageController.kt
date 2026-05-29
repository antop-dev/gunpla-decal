package com.example.gunpladecal.app.controller

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/** Thymeleaf 페이지 라우팅 컨트롤러 */
@Controller
class PageController {
    /** 사용자 메인 페이지 (index.html) */
    @GetMapping("/")
    fun index() = "index"

    /** 메뉴얼 직접 링크 진입 (/A, /1B 등 base62 ID). JS가 pathname을 읽어 해당 메뉴얼 로드 */
    @GetMapping("/{b62id:[0-9A-Za-z]+}")
    fun indexWithManual(
        @PathVariable b62id: String,
    ) = "index"

    /** 로그인 페이지 (이미 인증된 경우 /admin으로 리디렉션) */
    @GetMapping("/login")
    fun login(authentication: Authentication?): String = if (authentication?.isAuthenticated == true) "redirect:/admin" else "login"

    /** 관리자 페이지 (admin.html) */
    @GetMapping("/admin")
    fun admin() = "admin"
}
