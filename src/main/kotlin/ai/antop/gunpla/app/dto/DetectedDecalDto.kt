package ai.antop.gunpla.app.dto

/** 탐지 모델이 찾은 데칼 위치 후보 */
data class DetectedDecalDto(
    /** PDF 캔버스 기준 가로 위치 (0~100 %) */
    val x: Double,
    /** PDF 캔버스 기준 세로 위치 (0~100 %) */
    val y: Double,
    /** 탐지 신뢰도 (0~1) */
    val score: Double,
)
