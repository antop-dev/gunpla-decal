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

    /** PDF 각 페이지를 높이 68px 비율 PNG로 렌더링하여 저장하고 DB에 등록 */
    fun generateThumbnails(
        manualId: ManualId,
        pdfPath: String,
    ) {
        val pdfFilePath = Paths.get(pdfPath)
        if (!Files.exists(pdfFilePath)) {
            log.warn { "PDF 파일 없음, 썸네일 생성 건너뜀: manualId=$manualId, path=$pdfPath" }
            return
        }
        val uuidStr =
            pdfFilePath
                .fileName
                .toString()
                .removeSuffix(".pdf")

        Loader.loadPDF(pdfFilePath.toFile()).use { doc ->
            val totalPages = doc.numberOfPages
            log.debug { "썸네일 생성 시작: manualId=$manualId, totalPages=$totalPages, pdf=$pdfPath" }

            val renderer = PDFRenderer(doc)
            val thumbnails = mutableListOf<Thumbnail>()

            for (pageIndex in 0 until totalPages) {
                val page = doc.getPage(pageIndex)
                val scale = thumbHeight.toFloat() / page.cropBox.height

                val rawImage = renderer.renderImage(pageIndex, scale)
                val image = toRgb(rawImage)

                val pageNum = (pageIndex + 1).toString().padStart(2, '0')
                val filePath = Paths.get(appProperties.uploadDir, "$uuidStr.$pageNum.png")

                Files.newOutputStream(filePath).use { outputStream ->
                    ImageIO.write(image, "png", outputStream)
                }
                log.debug { "썸네일 생성: manualId=$manualId, page=${pageIndex + 1}/$totalPages, file=${filePath.fileName}" }

                thumbnails +=
                    Thumbnail(
                        manualId = manualId.value,
                        pageNumber = pageIndex + 1,
                        filePath = filePath.toAbsolutePath().toString(),
                    )
            }
            thumbnailRepository.saveAll(thumbnails)
            log.debug { "썸네일 생성 완료: manualId=$manualId, totalPages=$totalPages" }
        }
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
