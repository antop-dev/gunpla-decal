package ai.antop.gunpla.app.dto

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.domain.ManualId
import java.time.LocalDateTime

/** 서비스 레이어 내부에서 사용하는 메뉴얼 전체 정보 전달 객체 (PDF 경로 포함) */
data class ManualItemDto(
    /** Base62 인코딩된 공개 식별자 */
    val id: ManualId,
    /** 건담프라 등급 (HG/RG/MG/PG/ETC) */
    val grade: Grade,
    /** 형식번호 */
    val modelNumber: String,
    /** 제품명 */
    val productName: String,
    /** 업로드된 PDF 파일의 절대 경로 */
    val pdfPath: String,
    /** 외부 링크 (선택, https://로 시작) */
    val link: String?,
    /** 공개 여부 */
    val published: Boolean,
    /** 레코드 생성 일시 */
    val createdAt: LocalDateTime,
    /** 레코드 최종 수정 일시 */
    val updatedAt: LocalDateTime,
)
