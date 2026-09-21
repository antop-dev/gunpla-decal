package ai.antop.gunpla.app.dto

/** 메뉴얼 페이지 삭제 요청 */
data class ManualPageDeleteRequestDto(
    /** 삭제할 페이지 번호 목록 (1-based) */
    val pages: List<Int>,
)
