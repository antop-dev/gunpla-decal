package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.ManualId
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

/**
 * 메뉴얼 페이지 썸네일 생성·조회·삭제 서비스.
 * 썸네일 파일명은 "{PDF파일명}.{페이지2자리}.png" 규칙으로 유도하므로 DB에 경로를 보관하지 않는다.
 */
@Service
class ThumbnailService(
    private val manualService: ManualService,
    private val appProperties: AppProperties,
) {
    private val thumbHeight = 68

    /** PDF 각 페이지를 높이 68px 비율 PNG로 디스크에 렌더링하고 렌더링한 페이지 수를 반환 */
    fun renderThumbnailFiles(pdfPath: String): Int {
        val pdfFilePath = Paths.get(pdfPath)
        if (!Files.exists(pdfFilePath)) {
            log.warn { "PDF 파일 없음, 썸네일 생성 건너뜀: path=$pdfPath" }
            return 0
        }
        val pdfFileName = pdfFilePath.fileName.toString()

        return Loader.loadPDF(pdfFilePath.toFile()).use { doc ->
            val totalPages = doc.numberOfPages
            log.info { "썸네일 렌더링 시작: totalPages=$totalPages, pdf=$pdfPath" }
            val renderer =
                PDFRenderer(doc).apply {
                    isSubsamplingAllowed = true
                }
            (0 until totalPages).forEach { pageIndex ->
                val page = doc.getPage(pageIndex)
                // 페이지 회전(90/270)을 반영하면 렌더 이미지 높이는 cropBox.width 기준으로 정해지므로 유효 높이로 scale 계산
                val effectiveHeight =
                    if (page.rotation == 90 || page.rotation == 270) page.cropBox.width else page.cropBox.height
                val scale = thumbHeight.toFloat() / effectiveHeight
                val image = toRgb(renderer.renderImage(pageIndex, scale))
                val filePath = Paths.get(appProperties.uploadDir, thumbnailFileName(pdfFileName, pageIndex + 1))
                Files.newOutputStream(filePath).use { ImageIO.write(image, "png", it) }
                log.info { "썸네일 렌더링: page=${pageIndex + 1}/$totalPages, file=${filePath.fileName}" }
            }
            totalPages
        }
    }

    /** 특정 페이지 썸네일 파일 리소스 반환. 페이지 범위를 벗어나거나 파일이 없으면 404 */
    @Transactional(readOnly = true)
    fun getThumbnailResource(
        manualId: ManualId,
        pageNumber: Int,
    ): Resource {
        val manual = manualService.getManualEntity(manualId)
        if (pageNumber !in 1..manual.pageCount) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val path = Paths.get(appProperties.uploadDir, thumbnailFileName(manual.pdfPath, pageNumber))
        if (!Files.exists(path)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return FileSystemResource(path)
    }

    /** 메뉴얼의 썸네일 파일들을 디스크에서 삭제 */
    @Transactional(readOnly = true)
    fun deleteThumbnails(manualId: ManualId) {
        val manual = manualService.getManualEntity(manualId)
        (1..manual.pageCount).forEach { pageNumber ->
            Files.deleteIfExists(Paths.get(appProperties.uploadDir, thumbnailFileName(manual.pdfPath, pageNumber)))
        }
    }

    /** 썸네일 PNG 파일명. PDF 파일명과 페이지 번호로 결정된다 (예: "abc.pdf" 3페이지 -> "abc.03.png") */
    private fun thumbnailFileName(
        pdfFileName: String,
        pageNumber: Int,
    ): String = "${pdfFileName.removeSuffix(".pdf")}.${pageNumber.toString().padStart(2, '0')}.png"

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
