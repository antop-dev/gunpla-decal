package ai.antop.gunpla.common.shorty

/** Shorty 짧은 링크 생성 응답 */
data class ShortyLinkResponseDto(
    /** 발급된 짧은 링크 코드 */
    val code: String,
    /** 발급된 짧은 URL. 이 값을 DB에 저장한다 */
    val shortUrl: String,
    /** 원본 URL */
    val targetUrl: String,
)
