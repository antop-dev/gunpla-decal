package ai.antop.gunpla.app.dto

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.domain.ManualId

/** 메뉴얼 단건 조회 응답 (데칼 목록 포함) */
data class ManualAssemblyDto(
    /** Base62 인코딩된 공개 식별자 */
    val id: ManualId,
    /** 건담프라 등급 (HG/RG/MG/PG/ETC) */
    val grade: Grade,
    /** 형식번호 (영문자·숫자·하이픈·슬래시 허용) */
    val modelNumber: String,
    /** 제품명 */
    val productName: String,
    /** 데칼 목록 (데칼 번호 오름차순) */
    val decals: List<DecalItemDto>,
    /** 썸네일 URL 목록 (페이지 번호 오름차순) */
    val thumbnails: List<String> = emptyList(),
    /** 외부 링크 (선택, https://로 시작) */
    val link: String? = null,
    /** 공개 여부 (관리자 페이지에서 게시 상태 표시에 사용) */
    val published: Boolean = false,
)
