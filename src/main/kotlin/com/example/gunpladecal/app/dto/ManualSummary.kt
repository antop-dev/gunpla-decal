package com.example.gunpladecal.app.dto

import com.example.gunpladecal.app.domain.Grade

/** 메뉴얼 목록 조회 응답 (목록 표시에 필요한 최소 필드만 포함) */
data class ManualSummary(
    val id: Long,
    val b62id: String,
    val grade: Grade,
    /** 형식번호 (영문자·숫자·하이픈·슬래시 허용) */
    val modelNumber: String,
    val productName: String,
    val link: String? = null,
    val published: Boolean = false,
)
