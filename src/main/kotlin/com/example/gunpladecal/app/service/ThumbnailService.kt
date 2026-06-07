package com.example.gunpladecal.app.service

import com.example.gunpladecal.app.domain.Manual
import com.example.gunpladecal.app.domain.ManualThumbnail
import com.example.gunpladecal.app.repository.ManualThumbnailRepository
import com.example.gunpladecal.config.AppProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO

private val log = KotlinLogging.logger {}

/** 메뉴얼 페이지 썸네일 생성·조회·삭제 서비스 */
@Service
class ThumbnailService(
    private val manualThumbnailRepository: ManualThumbnailRepository,
    private val manualService: ManualService,
    private val appProperties: AppProperties,
) {
    private val thumbHeight = 68

    /** PDF 각 페이지를 높이 68px 비율 PNG로 렌더링하여 저장하고 DB에 등록 */
    fun generateThumbnails(manual: Manual) {
        val pdfFile = File(manual.pdfPath)
        if (!pdfFile.exists()) return
        val uuidStr =
            Paths
                .get(manual.pdfPath)
                .fileName
                .toString()
                .removeSuffix(".pdf")

        Loader.loadPDF(pdfFile).use { doc ->
            val renderer = PDFRenderer(doc)
            val thumbnails = mutableListOf<ManualThumbnail>()

            for (pageIndex in 0 until doc.numberOfPages) {
                val page = doc.getPage(pageIndex)
                val scale = thumbHeight.toFloat() / page.cropBox.height

                val rawImage = renderer.renderImage(pageIndex, scale)
                val image = toRgb(rawImage)

                val pageNum = (pageIndex + 1).toString().padStart(2, '0')
                val filePath = Paths.get(appProperties.uploadDir, "$uuidStr.$pageNum.png").toAbsolutePath()

                ImageIO.write(image, "png", filePath.toFile())
                thumbnails +=
                    ManualThumbnail(
                        manualId = manual.id,
                        pageNumber = pageIndex + 1,
                        filePath = filePath.toString(),
                    )
            }
            manualThumbnailRepository.saveAll(thumbnails)
        }
    }

    /** 메뉴얼의 썸네일 목록 반환 (페이지 번호 오름차순) */
    @Transactional(readOnly = true)
    fun findThumbnails(manualId: Long): List<ManualThumbnail> = manualThumbnailRepository.findByManualIdOrderByPageNumber(manualId)

    /** 특정 페이지 썸네일 파일 리소스 반환. onlyPublished=true이면 미공개 시 404 */
    @Transactional(readOnly = true)
    fun getThumbnailResource(
        manualId: Long,
        pageNumber: Int,
        onlyPublished: Boolean = false,
    ): Resource {
        if (onlyPublished) {
            val manual = manualService.getManualEntity(manualId)
            if (!manual.published) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val thumbnail =
            manualThumbnailRepository.findByManualIdAndPageNumber(manualId, pageNumber)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val path = Paths.get(thumbnail.filePath)
        if (!Files.exists(path)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return FileSystemResource(path)
    }

    /** 메뉴얼의 썸네일 파일들을 디스크에서 삭제 (DB 레코드는 CASCADE로 제거됨) */
    fun deleteFiles(manualId: Long) {
        manualThumbnailRepository.findByManualIdOrderByPageNumber(manualId).forEach { thumb ->
            Files.deleteIfExists(Paths.get(thumb.filePath))
        }
    }

    /** 스프링 시작 후 PDF는 있으나 썸네일이 없는 메뉴얼에 대해 썸네일 자동 생성 */
    @EventListener(ApplicationReadyEvent::class)
    fun generateMissingThumbnails() {
        val manuals = manualService.getAllManualEntities()
        for (manual in manuals) {
            if (!manualThumbnailRepository.existsByManualId(manual.id)) {
                try {
                    generateThumbnails(manual)
                    log.info { "Generated thumbnails for manual ${manual.id}" }
                } catch (e: Exception) {
                    log.error(e) { "Failed to generate thumbnails for manual ${manual.id}" }
                }
            }
        }
    }

    private fun toRgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_RGB) return image
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return rgb
    }
}
