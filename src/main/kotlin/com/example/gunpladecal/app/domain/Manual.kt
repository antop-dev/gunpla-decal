package com.example.gunpladecal.app.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 건담프라 메뉴얼 엔티티. PDF 파일 경로와 기본 정보를 저장한다. */
@Entity
@Table(name = "manual")
class Manual(
    /** 건담프라 등급 (HG, RG, MG, PG, ETC) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var grade: Grade,
    /** 형식번호 (영문자·숫자·하이픈만 허용, 예: RX-78-2) */
    @Column(name = "model_number", nullable = false, length = 50)
    var modelNumber: String,
    /** 제품명 (예: 1/144 RX-78-2 건담) */
    @Column(name = "product_name", nullable = false)
    var productName: String,
    /** 서버 업로드 디렉터리 내 PDF 파일명 (UUID 기반) */
    @Column(nullable = false)
    var pdfFilename: String,
    /** 레코드 생성 일시 (변경 불가) */
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    /** 레코드 최종 수정 일시 (@PreUpdate 훅으로 자동 갱신) */
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    /** 엔티티 수정 시 updatedAt 자동 갱신 */
    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
