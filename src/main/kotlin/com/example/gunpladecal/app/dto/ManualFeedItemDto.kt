package com.example.gunpladecal.app.dto

import com.example.gunpladecal.app.domain.Grade
import com.example.gunpladecal.app.domain.ManualId
import java.time.LocalDateTime

/** sitemap.xml / RSS / Atom 피드 생성에 필요한 메뉴얼 정보 */
data class ManualFeedItemDto(
    /** Base62 인코딩된 공개 식별자 */
    val id: ManualId,
    /** 건담프라 등급 (HG/RG/MG/PG/ETC) */
    val grade: Grade,
    /** 형식번호 (영문자·숫자·하이픈·슬래시 허용) */
    val modelNumber: String,
    /** 제품명 */
    val productName: String,
    /** 레코드 생성 일시 */
    val createdAt: LocalDateTime,
    /** 레코드 최종 수정 일시 */
    val updatedAt: LocalDateTime,
)
