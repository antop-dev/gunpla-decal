package ai.antop.gunpla.app.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val log = KotlinLogging.logger {}

/**
 * PDF 안의 JPX(JPEG2000) 이미지를 검증·재인코딩해 jj2000 디코더로 인한 OOM 위험을 제거한다.
 * [JpxSafetyValidator]로 위험하다고 판정된 이미지가 있으면 예외를 던져 등록을 거부한다.
 */
@Service
class PdfJpxNormalizationService {
    private val jpegQuality = 0.9f

    /** PDF 파일을 안전한 형태로 정규화(원본 경로에 덮어쓰기)한다. JPX 이미지를 하나라도 변환했으면 true 반환 */
    fun normalize(pdfFile: File): Boolean {
        var converted = false

        Loader.loadPDF(pdfFile).use { doc ->
            for (page in doc.pages) {
                converted = normalizeResources(doc, page.resources) || converted
            }
            if (converted) {
                val tempFile = File.createTempFile("jpx-normalized-", ".pdf", pdfFile.parentFile)
                try {
                    doc.save(tempFile)
                    Files.move(
                        tempFile.toPath(),
                        pdfFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } finally {
                    tempFile.delete()
                }
            }
        }
        return converted
    }

    /** 리소스(및 중첩된 Form XObject)를 재귀적으로 순회하며 JPX 이미지를 검증·교체한다 */
    private fun normalizeResources(
        doc: PDDocument,
        resources: PDResources?,
    ): Boolean {
        if (resources == null) return false
        var converted = false

        for (name in resources.xObjectNames.toList()) {
            when (val xobject = resources.getXObject(name)) {
                is PDImageXObject -> {
                    if (isJpxEncoded(xobject)) {
                        val replacement = normalizeJpxImage(doc, xobject)
                        resources.put(name, replacement)
                        converted = true
                    }
                }
                is PDFormXObject -> {
                    converted = normalizeResources(doc, xobject.resources) || converted
                }
            }
        }
        return converted
    }

    private fun isJpxEncoded(image: PDImageXObject): Boolean {
        val filters = image.cosObject.filters ?: return false
        return when (filters) {
            is COSArray -> filters.toList().any { it == COSName.JPX_DECODE }
            else -> filters == COSName.JPX_DECODE
        }
    }

    /** 위험한 JPX 이미지면 예외를 던지고, 안전하면 디코딩 후 JPEG로 재인코딩한 이미지를 반환한다 */
    private fun normalizeJpxImage(
        doc: PDDocument,
        image: PDImageXObject,
    ): PDImageXObject {
        val rawBytes = image.cosObject.createRawInputStream().use { it.readBytes() }
        when (val result = JpxSafetyValidator.validate(rawBytes)) {
            is JpxValidationResult.Unsafe -> {
                log.warn { "위험한 JPX 이미지 감지: ${result.reason}" }
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF에 안전하게 처리할 수 없는 이미지가 포함되어 있습니다")
            }
            JpxValidationResult.Safe -> {
                val bufferedImage = image.image
                return JPEGFactory.createFromImage(doc, bufferedImage, jpegQuality)
            }
        }
    }
}
