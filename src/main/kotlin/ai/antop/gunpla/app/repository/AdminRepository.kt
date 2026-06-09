package ai.antop.gunpla.app.repository

import ai.antop.gunpla.app.domain.Admin
import org.springframework.data.jpa.repository.JpaRepository

/** 관리자 계정 JPA 저장소 */
interface AdminRepository : JpaRepository<Admin, Long> {
    /** 아이디로 관리자 조회. 존재하지 않으면 null 반환 */
    fun findByUsername(username: String): Admin?
}
