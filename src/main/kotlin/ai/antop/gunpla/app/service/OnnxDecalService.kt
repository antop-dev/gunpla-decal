package ai.antop.gunpla.app.service

import ai.antop.gunpla.config.AppProperties
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.stereotype.Service
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.FloatBuffer

private val log = KotlinLogging.logger {}

/** ONNX EfficientNet-B0 모델을 이용한 데칼 번호 분류 서비스 */
@Service
class OnnxDecalService(
    private val appProperties: AppProperties,
) {
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)
    private val inputSize = 224

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var labels: List<String> = emptyList()

    @PostConstruct
    fun init() {
        val onnxPath = appProperties.onnx.model
        val labelsPath = appProperties.onnx.labels
        if (onnxPath.isNullOrBlank()) {
            log.info { "app.onnx.model 미설정 — ONNX 서비스 비활성" }
            return
        }
        if (labelsPath.isNullOrBlank()) {
            log.info { "app.onnx.labels 미설정 — ONNX 서비스 비활성" }
            return
        }
        val onnxFile = File(onnxPath)
        val labelsFile = File(labelsPath)
        if (!onnxFile.exists()) {
            log.warn { "ONNX 모델 파일 없음: $onnxPath" }
            return
        }
        if (!labelsFile.exists()) {
            log.warn { "레이블 파일 없음: $labelsPath" }
            return
        }
        env = OrtEnvironment.getEnvironment()
        session = env!!.createSession(onnxFile.absolutePath)
        labels = jacksonObjectMapper().readValue<List<String>>(labelsFile)
        log.info { "ONNX 모델 로드 완료: path=$onnxPath, classes=${labels.size}" }
    }

    @PreDestroy
    fun destroy() {
        session?.close()
        env?.close()
    }

    /**
     * PDF 파일의 지정 페이지·좌표 주변 이미지를 ONNX 모델로 분류하여 데칼 번호 반환.
     * 모델 미로드 또는 추론 실패 시 null 반환.
     */
    fun recognizeDecalNumber(
        pdfFile: File,
        page: Int,
        x: Double,
        y: Double,
    ): String? {
        val sess = session ?: return null
        val env = env ?: return null
        if (labels.isEmpty()) return null

        return try {
            val image = renderAndCrop(pdfFile, page, x, y) ?: return null
            toTensor(env, image).use { tensor ->
                sess.run(mapOf("input" to tensor)).use { output ->
                    @Suppress("UNCHECKED_CAST")
                    val logits = (output[0].value as Array<FloatArray>)[0]
                    val probs = softmax(logits)
                    val idx = probs.indices.maxByOrNull { probs[it] } ?: return null
                    if (probs[idx] < appProperties.onnx.threshold) return null
                    labels.getOrNull(idx)
                }
            }
        } catch (e: Exception) {
            log.error(e) { "ONNX 추론 실패" }
            null
        }
    }

    val isAvailable: Boolean
        get() = session != null && labels.isNotEmpty()

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exp = FloatArray(logits.size) { kotlin.math.exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return FloatArray(exp.size) { exp[it] / sum }
    }

    private fun renderAndCrop(
        pdfFile: File,
        page: Int,
        x: Double,
        y: Double,
    ): BufferedImage? =
        try {
            Loader.loadPDF(pdfFile).use { doc ->
                val renderer = PDFRenderer(doc)
                val full = renderer.renderImage(page - 1, 3.0f)
                val cx = (x / 100.0 * full.width).toInt()
                val cy = (y / 100.0 * full.height).toInt()
                val half = 20
                val x1 = (cx - half).coerceAtLeast(0)
                val y1 = (cy - half).coerceAtLeast(0)
                val x2 = (cx + half).coerceAtMost(full.width)
                val y2 = (cy + half).coerceAtMost(full.height)
                val cropped = full.getSubimage(x1, y1, x2 - x1, y2 - y1)
                resize(cropped, inputSize, inputSize)
            }
        } catch (e: Exception) {
            log.error(e) { "PDF 렌더링 실패: ${pdfFile.path}" }
            null
        }

    private fun resize(
        src: BufferedImage,
        w: Int,
        h: Int,
    ): BufferedImage {
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return dst
    }

    private fun toTensor(
        env: OrtEnvironment,
        image: BufferedImage,
    ): OnnxTensor {
        val buf = FloatBuffer.allocate(3 * inputSize * inputSize)
        for (c in 0..2) {
            for (row in 0 until inputSize) {
                for (col in 0 until inputSize) {
                    val rgb = image.getRGB(col, row)
                    val v =
                        when (c) {
                            0 -> ((rgb shr 16) and 0xFF) / 255f
                            1 -> ((rgb shr 8) and 0xFF) / 255f
                            else -> (rgb and 0xFF) / 255f
                        }
                    buf.put((v - mean[c]) / std[c])
                }
            }
        }
        buf.rewind()
        return OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
    }
}
