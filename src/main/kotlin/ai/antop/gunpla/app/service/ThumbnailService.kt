package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.domain.Thumbnail
import ai.antop.gunpla.app.dto.ThumbnailItemDto
import ai.antop.gunpla.app.repository.ThumbnailRepository
import ai.antop.gunpla.config.AppProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO

private val log = KotlinLogging.logger {}

/** 메뉴얼 페이지 썸네일 생성·조회·삭제 서비스 */
@Service
class ThumbnailService(
    private val thumbnailRepository: ThumbnailRepository,
    private val appProperties: AppProperties,
) {
    private val thumbHeight = 68

    /** PDF 각 페이지를 높이 68px 비율 PNG로 디스크에 렌더링. DB 레코드는 저장하지 않고 (페이지번호, 파일경로) 목록을 반환 */
    fun renderThumbnailFiles(pdfPath: String): List<Pair<Int, String>> {
        val pdfFilePath = Paths.get(pdfPath)
        if (!Files.exists(pdfFilePath)) {
            log.warn { "PDF 파일 없음, 썸네일 생성 건너뜀: path=$pdfPath" }
            return emptyList()
        }
        val uuidStr = pdfFilePath.fileName.toString().removeSuffix(".pdf")

        return Loader.loadPDF(pdfFilePath.toFile()).use { doc ->
            val totalPages = doc.numberOfPages
            log.info { "썸네일 렌더링 시작: totalPages=$totalPages, pdf=$pdfPath" }
            val renderer = PDFRenderer(doc)
            (0 until totalPages).map { pageIndex ->
                val page = doc.getPage(pageIndex)
                val scale = thumbHeight.toFloat() / page.cropBox.height
                val image = toRgb(renderer.renderImage(pageIndex, scale))
                val pageNum = (pageIndex + 1).toString().padStart(2, '0')
                val filePath = Paths.get(appProperties.uploadDir, "$uuidStr.$pageNum.png")
                Files.newOutputStream(filePath).use { ImageIO.write(image, "png", it) }
                log.info { "썸네일 렌더링: page=${pageIndex + 1}/$totalPages, file=${filePath.fileName}" }
                Pair(pageIndex + 1, filePath.toAbsolutePath().toString())
            }
        }
    }

    /** 썸네일 DB 레코드를 저장. 파일은 이미 디스크에 있어야 한다 */
    @Transactional
    fun saveThumbnailRecords(
        manualId: ManualId,
        files: List<Pair<Int, String>>,
    ) {
        val thumbnails =
            files.map { (pageNumber, filePath) ->
                Thumbnail(manualId = manualId.value, pageNumber = pageNumber, filePath = filePath)
            }
        thumbnailRepository.saveAll(thumbnails)
        log.info { "썸네일 DB 저장 완료: manualId=$manualId, count=${thumbnails.size}" }
    }

    /** 메뉴얼의 썸네일 페이지 번호 목록 반환 (오름차순) */
    @Transactional(readOnly = true)
    fun getThumbnails(manualId: ManualId): List<ThumbnailItemDto> =
        thumbnailRepository.findByManualIdOrderByPageNumber(manualId.value).map {
            ThumbnailItemDto(
                id = it.id,
                pageNumber = it.pageNumber,
                filPath = it.filePath,
            )
        }

    /** 특정 페이지 썸네일 파일 리소스 반환. onlyPublished=true이면 미공개 시 404 */
    @Transactional(readOnly = true)
    fun getThumbnailResource(
        manualId: ManualId,
        pageNumber: Int,
    ): Resource {
        val thumbnail =
            thumbnailRepository.findByManualIdAndPageNumber(manualId.value, pageNumber)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val path = Paths.get(thumbnail.filePath)
        if (!Files.exists(path)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return FileSystemResource(path)
    }

    /** 메뉴얼의 썸네일 파일들을 디스크에서 삭제 (DB 레코드는 CASCADE로 제거됨) */
    @Transactional
    fun deleteThumbnails(manualId: ManualId) {
        // 파일 삭제
        thumbnailRepository
            .findByManualIdOrderByPageNumber(manualId.value)
            .forEach { Files.deleteIfExists(Paths.get(it.filePath)) }
        // 데이터베이스에서 삭제
        thumbnailRepository.deleteThumbnailsByManualIdQuery(manualId.value)
    }

    /** RGBA·회색조 등 비RGB 이미지를 TYPE_INT_RGB로 변환. PDF 렌더링 결과가 투명도를 포함하는 경우 PNG 저장 전에 호출 */
    private fun toRgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_RGB) {
            return image
        }
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return rgb
    }
}
