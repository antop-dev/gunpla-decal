package ai.antop.gunpla.app.dto

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.domain.ManualId
import java.time.LocalDateTime

/** 메뉴얼 목록 조회 응답 (목록 표시에 필요한 최소 필드만 포함) */
data class ManualSummaryDto(
    /** Base62 인코딩된 공개 식별자 */
    val id: ManualId,
    /** 건담프라 등급 (HG/RG/MG/PG/ETC) */
    val grade: Grade,
    /** 형식번호 (영문자·숫자·하이픈·슬래시 허용) */
    val modelNumber: String,
    /** 제품명 */
    val productName: String,
    /** 외부 링크 (선택, https://로 시작) */
    val link: String? = null,
    /** 공개 여부 */
    val published: Boolean = false,
    /** 레코드 생성 일시 */
    val createdAt: LocalDateTime,
    /** 레코드 최종 수정 일시 */
    val updatedAt: LocalDateTime,
)
