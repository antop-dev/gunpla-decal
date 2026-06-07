package com.example.gunpladecal.app.dto

import com.example.gunpladecal.app.domain.DecalShape

/** 데칼 수정 요청 (null 필드는 변경하지 않음) */
data class DecalUpdateRequestDto(
    /** PDF 페이지 번호 (null이면 변경하지 않음) */
    val page: Int?,
    /** 데칼 식별 번호 문자열 (null이면 변경하지 않음) */
    val decalNumber: String?,
    /** PDF 캔버스 기준 가로 위치 (null이면 변경하지 않음) */
    val x: Double?,
    /** PDF 캔버스 기준 세로 위치 (null이면 변경하지 않음) */
    val y: Double?,
    /** 데칼 배경색 (RGB hex, null이면 변경하지 않음) */
    val color: String?,
    /** 데칼 도형 타입 (null이면 변경하지 않음) */
    val shape: DecalShape?,
)
