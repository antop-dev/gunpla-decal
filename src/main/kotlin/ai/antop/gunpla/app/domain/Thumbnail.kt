package ai.antop.gunpla.app.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 메뉴얼 페이지 썸네일 엔티티. PNG 파일 경로와 페이지 번호를 저장한다. */
@Entity
@Table(name = "manual_thumbnail")
class Thumbnail(
    @Column(name = "manual_id", nullable = false)
    var manualId: Long,
    @Column(name = "page_number", nullable = false)
    var pageNumber: Int,
    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    var filePath: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
)
