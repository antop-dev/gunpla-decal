package com.example.gunpladecal.app.dto

import com.example.gunpladecal.app.domain.DecalShape

/** 데칼 조회 응답 */
data class DecalResponse(
    val id: Long,
    /** PDF 페이지 번호 (1-based) */
    val page: Int,
    val decalNumber: String,
    /** PDF 캔버스 기준 가로 위치 (0~100 %) */
    val x: Double,
    /** PDF 캔버스 기준 세로 위치 (0~100 %) */
    val y: Double,
    /** 데칼 배경색 (RGB hex, 예: #ffffff) */
    val color: String,
    /** 데칼 도형 타입 (CIRCLE: 동그라미, SQUARE: 네모) */
    val shape: DecalShape,
)
