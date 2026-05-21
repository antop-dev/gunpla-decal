package com.example.gunpladecal.app.dto

import com.example.gunpladecal.app.domain.Grade

/** 메뉴얼 수정 요청 (null 필드는 변경하지 않음) */
data class ManualUpdateRequest(
    val grade: Grade?,
    /** 형식번호 (null이면 변경하지 않음) */
    val modelNumber: String?,
    val productName: String?,
    val link: String? = null,
)
