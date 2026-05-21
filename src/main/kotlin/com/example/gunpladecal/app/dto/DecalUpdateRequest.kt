package com.example.gunpladecal.app.dto

import com.example.gunpladecal.app.domain.DecalShape

/** 데칼 수정 요청 (null 필드는 변경하지 않음) */
data class DecalUpdateRequest(
    val page: Int?,
    val decalNumber: String?,
    val x: Double?,
    val y: Double?,
    /** 데칼 배경색 (RGB hex, null이면 변경하지 않음) */
    val color: String?,
    /** 데칼 도형 타입 (null이면 변경하지 않음) */
    val shape: DecalShape?,
)
