package com.example.gunpladecal.app.dto

import com.example.gunpladecal.app.domain.Grade

/** 메뉴얼 수정 요청 (null 필드는 변경하지 않음) */
data class ManualUpdateRequestDto(
    /** 건담프라 등급 (null이면 변경하지 않음) */
    val grade: Grade?,
    /** 형식번호 (null이면 변경하지 않음) */
    val modelNumber: String?,
    /** 제품명 (null이면 변경하지 않음) */
    val productName: String?,
    /** 외부 링크 (null이면 변경하지 않음, 빈 문자열이면 제거) */
    val link: String? = null,
)
