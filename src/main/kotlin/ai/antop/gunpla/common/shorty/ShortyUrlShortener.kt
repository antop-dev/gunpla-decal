package ai.antop.gunpla.common.shorty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

private val shortyLog = KotlinLogging.logger {}

/** Shorty 짧은 URL 발급 */
@Component
class ShortyUrlShortener(
    // shorty.url 이 비어 있으면 Feign 빈 생성이 실패하므로, 실제 호출 시점까지 생성을 미룬다
    @field:Lazy private val shortyClient: ShortyClient,
    private val shortyProperties: ShortyProperties,
) {
    /**
     * URL을 짧은 URL로 변환한다.
     * 설정이 비어 있거나(shorty.url · shorty.client-key) 발급에 실패하면 원본 URL을 그대로 반환한다.
     */
    fun shorten(url: String): String {
        if (!shortyProperties.enabled) {
            shortyLog.debug { "[Shorty] 설정이 없어 짧은 URL 발급을 건너뛴다 - url=$url" }
            return url
        }
        return try {
            shortyClient.createLink(shortyProperties.clientKey, ShortyLinkRequestDto(url)).shortUrl
        } catch (e: Exception) {
            shortyLog.warn(e) { "[Shorty] 짧은 URL 발급 실패, 원본 URL을 사용한다 - url=$url" }
            url
        }
    }
}
