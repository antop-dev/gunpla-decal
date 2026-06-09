package ai.antop.gunpla.app.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 관리자 계정 엔티티. Spring Security 인증에 사용되는 아이디·비밀번호를 저장한다 */
@Entity
@Table(name = "admin")
class Admin(
    @Column(nullable = false, unique = true, length = 50)
    var username: String,
    @Column(nullable = false)
    var password: String,
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
)
