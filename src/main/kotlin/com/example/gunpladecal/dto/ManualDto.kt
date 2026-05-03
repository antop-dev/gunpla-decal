package com.example.gunpladecal.dto

import com.example.gunpladecal.domain.DecalColor
import com.example.gunpladecal.domain.Grade

/** 메뉴얼 목록 조회 응답 (목록 표시에 필요한 최소 필드만 포함) */
data class ManualSummary(
    val id: Long,
    val grade: Grade,
    /** 형식번호 (영문자·숫자·하이픈만 허용) */
    val modelNumber: String,
    val productName: String,
)

/** 메뉴얼 단건 조회 응답 (데칼 목록 포함) */
data class ManualDetail(
    val id: Long,
    val grade: Grade,
    /** 형식번호 (영문자·숫자·하이픈만 허용) */
    val modelNumber: String,
    val productName: String,
    val decals: List<DecalResponse>,
)

/** 메뉴얼 수정 요청 (null 필드는 변경하지 않음) */
data class ManualUpdateRequest(
    val grade: Grade?,
    /** 형식번호 (null이면 변경하지 않음) */
    val modelNumber: String?,
    val productName: String?,
)

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
    val color: DecalColor,
)

/** 데칼 등록 요청 */
data class DecalCreateRequest(
    /** PDF 페이지 번호 (1-based) */
    val page: Int,
    val decalNumber: String,
    /** PDF 캔버스 기준 가로 위치 (0~100 %) */
    val x: Double,
    /** PDF 캔버스 기준 세로 위치 (0~100 %) */
    val y: Double,
    val color: DecalColor = DecalColor.WHITE,
)

/** 데칼 수정 요청 (null 필드는 변경하지 않음) */
data class DecalUpdateRequest(
    val page: Int?,
    val decalNumber: String?,
    val x: Double?,
    val y: Double?,
    val color: DecalColor?,
)
