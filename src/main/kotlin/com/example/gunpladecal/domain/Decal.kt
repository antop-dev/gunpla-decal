package com.example.gunpladecal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 데칼 위치 정보 엔티티. PDF 페이지 내 좌표(%)를 저장한다. */
@Entity
@Table(name = "decal")
class Decal(
    /** 소속 메뉴얼 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_id", nullable = false)
    val manual: Manual,
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
    /** 데칼 배경색 (WHITE 또는 BLACK) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var color: DecalColor = DecalColor.WHITE,
    /** 레코드 생성 일시 (변경 불가) */
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
