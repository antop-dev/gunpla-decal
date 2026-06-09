package ai.antop.gunpla.app.repository

import ai.antop.gunpla.app.domain.Manual
import org.springframework.data.jpa.repository.JpaRepository

/** 메뉴얼 JPA 저장소 */
interface ManualRepository : JpaRepository<Manual, Long> {
    /** 전체 메뉴얼 목록 반환 (id 내림차순) */
    fun findAllByOrderByIdDesc(): List<Manual>
}
