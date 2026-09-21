package ai.antop.gunpla.app.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.action.PDAction
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * PDF에서 특정 페이지를 제거한다.
 * 삭제된 페이지를 가리키는 아웃라인(북마크)·링크 주석·OpenAction도 함께 정리한다.
 * 끊어진 참조를 남기면 해당 페이지 객체가 저장 파일에 그대로 남으므로 반드시 필요한 처리다.
 */
@Service
class PdfPageRemovalService {
    /** pdfPath에서 pages(1-based) 페이지를 제거하고 원본 경로에 덮어쓴 뒤, 남은 페이지 수를 반환한다 */
    fun removePages(
        pdfPath: Path,
        pages: List<Int>,
    ): Int =
        Loader.loadPDF(pdfPath.toFile()).use { doc ->
            // 페이지를 제거하면 인덱스로는 더 이상 찾을 수 없으므로 COS 객체를 미리 확보해 둔다
            val removed = pages.map { doc.getPage(it - 1).cosObject }
            pages.sortedDescending().forEach { doc.removePage(it - 1) }

            cleanOutline(doc, removed)
            cleanLinkAnnotations(doc, removed)
            cleanOpenAction(doc, removed)

            val tempFile = Files.createTempFile(pdfPath.parent, "page-removed-", ".pdf")
            try {
                Files.newOutputStream(tempFile).buffered().use { doc.save(it) }
                Files.move(tempFile, pdfPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(tempFile)
            }
            doc.numberOfPages
        }

    /**
     * 아웃라인에서 삭제된 페이지를 가리키는 항목을 제거한다.
     * PDFBox에는 아웃라인 항목 제거 API가 없어 살아남은 항목만 새 트리로 옮겨 담는다.
     */
    private fun cleanOutline(
        doc: PDDocument,
        removed: List<COSDictionary>,
    ) {
        val outline = doc.documentCatalog.documentOutline ?: return
        val rebuilt = PDDocumentOutline()
        copySurvivingItems(outline, rebuilt, doc, removed)
        doc.documentCatalog.documentOutline = if (rebuilt.hasChildren()) rebuilt else null
    }

    /**
     * source의 자식 중 살아남은 항목만 target 아래로 복사한다.
     * 삭제 대상 항목은 버리되 그 하위 항목이 살아있으면 상위로 끌어올려 보존한다.
     */
    private fun copySurvivingItems(
        source: PDOutlineNode,
        target: PDOutlineNode,
        doc: PDDocument,
        removed: List<COSDictionary>,
    ) {
        source.children().forEach { item ->
            if (pointsToRemovedPage(item.destination, item.action, doc, removed)) {
                copySurvivingItems(item, target, doc, removed)
            } else {
                val copy = PDOutlineItem(COSDictionary(item.cosObject))
                // 새 트리에 연결하려면 기존 형제·부모·자식 연결 정보를 비워야 한다
                LINK_KEYS.forEach { copy.cosObject.removeItem(it) }
                target.addLast(copy)
                copySurvivingItems(item, copy, doc, removed)
            }
        }
    }

    /** 남은 페이지들의 링크 주석 중 삭제된 페이지를 가리키는 것을 제거한다 */
    private fun cleanLinkAnnotations(
        doc: PDDocument,
        removed: List<COSDictionary>,
    ) {
        doc.pages.forEach { page ->
            val annotations = page.annotations
            val surviving =
                annotations.filterNot { annotation ->
                    annotation is PDAnnotationLink &&
                        pointsToRemovedPage(annotation.destination, annotation.action, doc, removed)
                }
            if (surviving.size != annotations.size) {
                page.annotations = surviving
            }
        }
    }

    /** 문서 열기 동작(OpenAction)이 삭제된 페이지를 가리키면 제거한다 */
    private fun cleanOpenAction(
        doc: PDDocument,
        removed: List<COSDictionary>,
    ) {
        val openAction = doc.documentCatalog.openAction ?: return
        val dangling =
            when (openAction) {
                is PDDestination -> pointsToRemovedPage(openAction, null, doc, removed)
                is PDAction -> pointsToRemovedPage(null, openAction, doc, removed)
                else -> false
            }
        if (dangling) {
            doc.documentCatalog.openAction = null
        }
    }

    /**
     * 목적지(또는 GoTo 동작의 목적지)가 삭제된 페이지를 가리키는지 판별한다.
     * 페이지를 해석할 수 없는 목적지(외부 문서·페이지 번호 참조 등)는 보존한다.
     */
    private fun pointsToRemovedPage(
        destination: PDDestination?,
        action: PDAction?,
        doc: PDDocument,
        removed: List<COSDictionary>,
    ): Boolean {
        val target = destination ?: (action as? PDActionGoTo)?.destination ?: return false
        val pageDestination =
            when (target) {
                is PDPageDestination -> target
                is PDNamedDestination -> doc.documentCatalog.findNamedDestinationPage(target)
                else -> null
            } ?: return false
        val page = pageDestination.page ?: return false
        return removed.any { it === page.cosObject }
    }

    companion object {
        private val LINK_KEYS = listOf(COSName.PREV, COSName.NEXT, COSName.FIRST, COSName.LAST, COSName.PARENT, COSName.COUNT)
    }
}
