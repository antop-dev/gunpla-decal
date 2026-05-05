package com.example.gunpladecal.app.repository

import com.example.gunpladecal.app.domain.Decal
import com.example.gunpladecal.app.domain.Manual
import org.springframework.data.jpa.repository.JpaRepository

/** 메뉴얼 데이터 접근 인터페이스 */
interface ManualRepository : JpaRepository<Manual, Long> {
    /** 등록일 최신순으로 전체 메뉴얼 목록 반환 */
    fun findAllByOrderByIdDesc(): List<Manual>
}

/** 데칼 데이터 접근 인터페이스 */
interface DecalRepository : JpaRepository<Decal, Long> {
    /** 특정 메뉴얼의 데칼을 번호 오름차순으로 반환 */
    fun findByManualIdOrderByDecalNumber(manualId: Long): List<Decal>

    /** 특정 메뉴얼의 특정 페이지 데칼 목록 반환 (AI 탐지 중복 체크에 사용) */
    fun findByManualIdAndPageNumber(
        manualId: Long,
        pageNumber: Int,
    ): List<Decal>

    /** 특정 메뉴얼에 속한 데칼 전체 삭제 (메뉴얼 삭제 시 연쇄 호출) */
    fun deleteByManualId(manualId: Long)
}
