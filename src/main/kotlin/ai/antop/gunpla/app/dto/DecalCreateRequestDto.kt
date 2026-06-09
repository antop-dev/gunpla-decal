package ai.antop.gunpla.app.dto

import ai.antop.gunpla.app.domain.DecalShape

/** 데칼 등록 요청 */
data class DecalCreateRequestDto(
    /** PDF 페이지 번호 (1-based) */
    val pageNumber: Int,
    /** 데칼 식별 번호 문자열 (예: 1, A, ア) */
    val decalNumber: String,
    /** PDF 캔버스 기준 가로 위치 (0~100 %) */
    val x: Double,
    /** PDF 캔버스 기준 세로 위치 (0~100 %) */
    val y: Double,
    /** 데칼 배경색 (RGB hex, 예: #ffffff) */
    val color: String = "#ffffff",
    /** 데칼 도형 타입 (CIRCLE: 동그라미, SQUARE: 네모) */
    val shape: DecalShape = DecalShape.CIRCLE,
)
