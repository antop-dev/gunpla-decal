package com.example.gunpladecal.app.controller

import com.pig4cloud.captcha.base.Captcha
import com.pig4cloud.captcha.utils.CaptchaJakartaUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class CaptchaController {
    @GetMapping("/captcha")
    fun captcha(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        CaptchaJakartaUtil.out(130, 48, 5, Captcha.TYPE_ONLY_UPPER, request, response)
    }
}
