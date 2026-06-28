package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.dto.ManualSummaryDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

private val taskLog = KotlinLogging.logger {}

/** 메뉴얼 등록 비동기 작업. PDF 저장·썸네일 생성을 단일 작업으로 처리하고 SSE로 결과를 브로드캐스트한다 */
@Service
class ManualTaskService(
    private val manualService: ManualService,
    private val thumbnailService: ThumbnailService,
    private val sseService: SseService,
) {
    @Async("manualTaskExecutor")
    fun createManual(
        grade: Grade,
        modelNumber: String,
        productName: String,
        pdfBytes: ByteArray?,
        pdfUrl: String?,
        link: String?,
    ) {
        taskLog.info { "[메뉴얼 등록] 시작 - grade=$grade, modelNumber=$modelNumber, productName=$productName" }
        try {
            taskLog.info { "[1/5] PDF 파일 저장 시작" }
            val pdfPath = manualService.savePdfFile(pdfBytes, pdfUrl)
            taskLog.info { "[1/5] PDF 파일 저장 완료 - path=$pdfPath" }

            taskLog.info { "[2/5] 썸네일 렌더링 시작" }
            val thumbnailFiles = thumbnailService.renderThumbnailFiles(pdfPath.toString())
            taskLog.info { "[2/5] 썸네일 렌더링 완료 - ${thumbnailFiles.size}페이지" }

            taskLog.info { "[3/5] 메뉴얼 DB 저장 시작" }
            val manual = manualService.saveManualRecord(grade, modelNumber, productName, pdfPath.toString(), link)
            taskLog.info { "[3/5] 메뉴얼 DB 저장 완료 - manualId=${manual.id}" }

            taskLog.info { "[4/5] 썸네일 DB 저장 시작 - manualId=${manual.id}, count=${thumbnailFiles.size}" }
            thumbnailService.saveThumbnailRecords(manual.id, thumbnailFiles)
            taskLog.info { "[4/5] 썸네일 DB 저장 완료 - manualId=${manual.id}" }

            taskLog.info { "[5/5] SSE 이벤트 발송 - manualId=${manual.id}" }
            sseService.sendToAll("manual-created", manual.toSummary())
            taskLog.info { "[메뉴얼 등록] 완료 - manualId=${manual.id}" }
        } catch (e: Exception) {
            val message =
                when (e) {
                    is ResponseStatusException -> e.reason ?: "등록에 실패했습니다"
                    else -> e.message ?: "등록에 실패했습니다"
                }
            taskLog.error(e) { "[메뉴얼 등록] 실패 - $message" }
            sseService.sendToAll("manual-failed", mapOf("message" to message))
        }
    }

    private fun ai.antop.gunpla.app.dto.ManualItemDto.toSummary() =
        ManualSummaryDto(
            id = id,
            grade = grade,
            modelNumber = modelNumber,
            productName = productName,
            link = link,
            published = published,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
