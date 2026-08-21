package ai.antop.gunpla.common.shorty

/** Shorty 짧은 링크 생성 요청 */
data class ShortyLinkRequestDto(
    /** 단축할 원본 URL */
    val url: String,
)
