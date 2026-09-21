package ai.antop.gunpla.app.domain

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
    /** 업로드된 PDF 파일명 (app.upload-dir 기준 상대 경로) */
    @Column(name = "pdf_path", nullable = false, columnDefinition = "TEXT")
    var pdfPath: String,
    /** PDF 페이지 수. 썸네일 파일 경로를 유도하는 데 사용한다 */
    @Column(name = "page_count", nullable = false)
    var pageCount: Int = 0,
    /** PDF 파일 크기(바이트). PDF가 저장되거나 페이지 삭제로 다시 쓰인 뒤 파일에서 읽어 갱신한다 */
    @Column(name = "file_size", nullable = false)
    var fileSize: Long = 0,
    /** 외부 링크 (선택, https://로 시작) */
    @Column(name = "link", nullable = true, columnDefinition = "TEXT")
    var link: String? = null,
    /** 공개 여부 (false: 미공개, true: 공개) */
    @Column(nullable = false)
    var published: Boolean = false,
    /**
     * PDF·썸네일 파일의 버전. 파일 내용이 실제로 바뀔 때만 증가시켜 브라우저 캐시를 무효화한다.
     * 등급·제품명 등 메타데이터만 수정되면 파일은 그대로이므로 증가시키지 않는다.
     */
    @Column(name = "resource_version", nullable = false)
    var resourceVersion: Int = 0,
    /** 레코드 생성 일시 (변경 불가) */
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    /** 레코드 최종 수정 일시 (@PreUpdate 훅으로 자동 갱신) */
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
) {
    /** 엔티티 수정 시 updatedAt 자동 갱신 */
    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
