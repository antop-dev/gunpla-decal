package ai.antop.gunpla.app.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 데칼 위치 정보 엔티티. PDF 페이지 내 좌표(%)를 저장한다. */
@Entity
@Table(name = "decal")
class Decal(
    /** 소속 메뉴얼 (지연 로딩) */
    @Column(name = "manual_id")
    var manualId: Long,
    /** PDF 페이지 번호 (1-based) */
    @Column(nullable = false)
    var pageNumber: Int,
    /** 데칼 식별 번호 문자열 (예: 1, A, ア) */
    @Column(nullable = false)
    var decalNumber: String,
    /** PDF 캔버스 기준 가로 위치 (0~100 %) */
    @Column(nullable = false)
    var x: Double,
    /** PDF 캔버스 기준 세로 위치 (0~100 %) */
    @Column(nullable = false)
    var y: Double,
    /** 데칼 배경색 (RGB hex, 예: #ffffff) */
    @Column(nullable = false, length = 10)
    var color: String = "#ffffff",
    /** 데칼 도형 타입 (CIRCLE: 동그라미, SQUARE: 네모) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var shape: DecalShape = DecalShape.CIRCLE,
    /** 레코드 생성 일시 (변경 불가) */
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
)
