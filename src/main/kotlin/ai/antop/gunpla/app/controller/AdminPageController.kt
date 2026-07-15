package ai.antop.gunpla.app.controller

import ai.antop.gunpla.app.service.OnnxDecalService
import ai.antop.gunpla.config.AppProperties
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute

/** 관리자 페이지 Thymeleaf 라우팅 컨트롤러 */
@Controller
class AdminPageController(
    private val appProperties: AppProperties,
    private val onnxDecalService: OnnxDecalService,
) {
    @ModelAttribute("hasAi")
    fun hasAi(): Boolean = !appProperties.openAiKey.isNullOrBlank()

    @ModelAttribute("hasOnnx")
    fun hasOnnx(): Boolean = onnxDecalService.isAvailable

    @GetMapping("/admin")
    fun admin() = "admin"
}
