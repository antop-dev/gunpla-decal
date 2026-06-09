package ai.antop.gunpla.app.dto

/** 썸네일 서비스 내부 전달 객체. 파일 경로와 페이지 번호를 담는다 */
data class ThumbnailItemDto(
    /** DB 식별자 */
    val id: Long,
    /** PDF 페이지 번호 (1-based) */
    val pageNumber: Int,
    /** 썸네일 PNG 파일의 절대 경로 */
    val filPath: String,
)
