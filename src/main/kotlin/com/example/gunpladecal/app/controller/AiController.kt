package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.service.ManualService
import com.example.gunpladecal.app.service.OpenAiService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/admin/ai")
class AiController(
    private val manualService: ManualService,
    private val openAiService: OpenAiService,
) {
    @GetMapping("/available")
    fun available(): Map<String, Boolean> {
        log.debug { "GET /api/admin/ai/available" }
        return mapOf("available" to openAiService.isAvailable())
    }

    @PostMapping("/recognize")
    fun recognize(
        @RequestBody request: AiRecognizeRequest,
    ): AiRecognizeResponse {
        log.debug { "POST /api/admin/ai/recognize request=$request" }
        val pdfFile = manualService.getPdfResource(request.manualId).file
        val character = openAiService.recognizeDecalNumber(pdfFile, request.page, request.x, request.y)
        return AiRecognizeResponse(character != null, character)
    }
}
