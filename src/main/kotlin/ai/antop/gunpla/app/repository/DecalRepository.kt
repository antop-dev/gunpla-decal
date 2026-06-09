package ai.antop.gunpla.app.repository

import ai.antop.gunpla.app.domain.Decal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/** 데칼 JPA 저장소 */
interface DecalRepository : JpaRepository<Decal, Long> {
    /** 메뉴얼에 속한 데칼 목록 반환 (decalNumber 오름차순) */
    fun findByManualIdOrderByDecalNumber(manualId: Long): List<Decal>

    /** 메뉴얼에 속한 데칼 전체 삭제 (JPQL 벌크 삭제) */
    @Modifying
    @Query("DELETE FROM Decal d WHERE d.manualId = :manualId")
    fun deleteDecalsByManualIdQuery(manualId: Long)
}
