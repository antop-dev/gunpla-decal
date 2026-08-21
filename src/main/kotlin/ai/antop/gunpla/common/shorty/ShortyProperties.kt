package ai.antop.gunpla.common.shorty

import org.springframework.boot.context.properties.ConfigurationProperties

// application.yml 의 shorty.* 설정을 바인딩하는 타입-안전 설정 클래스
// url 또는 clientKey 가 비어 있으면 단축 URL 변환을 건너뛴다 (ShortyUrlShortener 참고)
@ConfigurationProperties(prefix = "shorty")
data class ShortyProperties(
    // Shorty 서버 베이스 URL (예: http://localhost:30001)
    val url: String = "",
    // API 인증 키 — X-Api-Key 헤더로 전송
    val clientKey: String = "",
) {
    val enabled: Boolean
        get() = url.isNotBlank() && clientKey.isNotBlank()
}
