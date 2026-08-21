package ai.antop.gunpla.common.shorty

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

/** Shorty 짧은 URL 서비스 Feign 클라이언트 */
@FeignClient(name = "shorty", url = "\${shorty.url:}")
interface ShortyClient {
    /** 원본 URL로 짧은 링크를 생성한다 */
    @PostMapping("/api/v1/links", consumes = ["application/json"])
    fun createLink(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestBody request: ShortyLinkRequestDto,
    ): ShortyLinkResponseDto
}
