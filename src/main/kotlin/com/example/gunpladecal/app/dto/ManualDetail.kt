package com.example.gunpladecal.app.dto

import com.example.gunpladecal.app.domain.Grade

/** 메뉴얼 단건 조회 응답 (데칼 목록 포함) */
data class ManualDetail(
    val id: Long,
    val b62id: String,
    val grade: Grade,
    /** 형식번호 (영문자·숫자·하이픈·슬래시 허용) */
    val modelNumber: String,
    val productName: String,
    val decals: List<DecalResponse>,
    val link: String? = null,
    val published: Boolean = false,
)
