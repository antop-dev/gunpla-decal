package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.dto.DetectedDecalDto
import ai.antop.gunpla.config.AppProperties
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO

private val log = KotlinLogging.logger {}

/**
 * ONNX 히트맵 모델을 이용한 데칼 위치 탐지 서비스.
 * 학습 스크립트: scripts/train_detect_onnx.py
 */
@Service
class OnnxDetectService(
    private val appProperties: AppProperties,
) {
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** 모델 입력은 이 값의 배수여야 한다 (백본 다운샘플 배율) */
    private val sizeMultiple = 32

    /** 히트맵 해상도 = 입력 / outputStride */
    private val outputStride = 2

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    @PostConstruct
    fun init() {
        val modelPath = appProperties.onnx.detectModel
        if (modelPath.isNullOrBlank()) {
            log.info { "app.onnx.detect-model 미설정 — 데칼 위치 탐지 비활성" }
            return
        }
        val modelFilePath = Paths.get(modelPath)
        if (!Files.exists(modelFilePath)) {
            log.warn { "탐지 ONNX 모델 파일 없음: $modelPath" }
            return
        }
        env = OrtEnvironment.getEnvironment()
        session = env!!.createSession(modelFilePath.toAbsolutePath().toString())
        log.info { "탐지 ONNX 모델 로드 완료: path=$modelPath" }
    }

    @PreDestroy
    fun destroy() {
        session?.close()
    }

    val isAvailable: Boolean
        get() = session != null

    /**
     * 페이지 이미지에서 데칼 위치를 찾아 페이지 기준 백분율 좌표로 반환.
     * 모델 미로드 또는 추론 실패 시 빈 목록 반환.
     */
    fun detect(imageBytes: ByteArray): List<DetectedDecalDto> {
        val sess = session ?: return emptyList()
        val env = env ?: return emptyList()

        return try {
            val image = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: return emptyList()
            toTensor(env, image).use { tensor ->
                sess.run(mapOf("input" to tensor)).use { output ->
                    @Suppress("UNCHECKED_CAST")
                    val heatmap = (output[0].value as Array<Array<Array<FloatArray>>>)[0][0]
                    findPeaks(heatmap, image.width, image.height)
                }
            }
        } catch (e: Exception) {
            log.error(e) { "탐지 ONNX 추론 실패" }
            emptyList()
        }
    }

    /** 3x3 지역 최댓값이면서 임계값 이상인 셀을 원본 이미지 기준 백분율 좌표로 변환 */
    private fun findPeaks(
        heatmap: Array<FloatArray>,
        imageWidth: Int,
        imageHeight: Int,
    ): List<DetectedDecalDto> {
        val threshold = appProperties.onnx.detectThreshold.toFloat()
        val rows = heatmap.size
        val cols = heatmap[0].size
        val peaks = mutableListOf<DetectedDecalDto>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v = heatmap[r][c]
                if (v < threshold || !isLocalMax(heatmap, r, c)) {
                    continue
                }
                // 셀 (r, c) 의 중심은 입력 좌표 (c + 0.5) * stride
                val x = (c + 0.5) * outputStride / imageWidth * 100
                val y = (r + 0.5) * outputStride / imageHeight * 100
                if (x > 100 || y > 100) {
                    continue
                }
                peaks += DetectedDecalDto(x, y, v.toDouble())
            }
        }
        return peaks
    }

    /** 같은 값이 이웃한 평탄한 봉우리는 먼저 만난(위·왼쪽) 셀 하나만 남긴다 */
    private fun isLocalMax(
        heatmap: Array<FloatArray>,
        r: Int,
        c: Int,
    ): Boolean {
        val v = heatmap[r][c]
        for (dr in -1..1) {
            for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if ((dr == 0 && dc == 0) || nr !in heatmap.indices || nc !in heatmap[nr].indices) {
                    continue
                }
                val n = heatmap[nr][nc]
                val before = dr < 0 || (dr == 0 && dc < 0)
                if (n > v || (before && n == v)) {
                    return false
                }
            }
        }
        return true
    }

    /** 오른쪽·아래를 흰색으로 채워 32의 배수 크기로 맞춘 뒤 ImageNet 정규화한 [1,3,H,W] 텐서 생성 */
    private fun toTensor(
        env: OrtEnvironment,
        src: BufferedImage,
    ): OnnxTensor {
        val w = roundUp(src.width)
        val h = roundUp(src.height)
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.drawImage(src, 0, 0, null)
        g.dispose()

        val pixels = image.getRGB(0, 0, w, h, null, 0, w)
        val plane = w * h
        val buf = FloatBuffer.allocate(3 * plane)
        for (i in 0 until plane) {
            val rgb = pixels[i]
            buf.put(i, (((rgb shr 16) and 0xFF) / 255f - mean[0]) / std[0])
            buf.put(plane + i, (((rgb shr 8) and 0xFF) / 255f - mean[1]) / std[1])
            buf.put(2 * plane + i, ((rgb and 0xFF) / 255f - mean[2]) / std[2])
        }
        return OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, h.toLong(), w.toLong()))
    }

    private fun roundUp(size: Int): Int = (size + sizeMultiple - 1) / sizeMultiple * sizeMultiple
}
