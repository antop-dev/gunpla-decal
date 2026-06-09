package ai.antop.gunpla.app.controller

import com.pig4cloud.captcha.base.Captcha
import com.pig4cloud.captcha.utils.CaptchaJakartaUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** 캡차 이미지 생성 컨트롤러 */
@Controller
class CaptchaController {
    /** 130×48 대문자 5자리 캡차 이미지를 HTTP 응답으로 직접 스트리밍 */
    @GetMapping("/captcha")
    fun captcha(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        CaptchaJakartaUtil.out(130, 48, 5, Captcha.TYPE_ONLY_UPPER, request, response)
    }
}
