package ai.antop.gunpla.app.service

/** JPX(JPEG2000) 코드스트림 헤더 검증 결과 */
sealed class JpxValidationResult {
    data object Safe : JpxValidationResult()

    data class Unsafe(
        val reason: String,
    ) : JpxValidationResult()
}

/**
 * JPEG2000 코드스트림의 SIZ/COD/COC 마커만 파싱해 디코딩 없이 위험 여부를 판정한다.
 *
 * jj2000(pure-Java JPEG2000 디코더)은 COD/COC의 프리싱크트(precinct) 크기 지수가
 * 비정상적으로 작으면(타일 대비 프리싱크트 그리드가 거대해지면) TagTreeDecoder에서
 * 수백MB 크기의 int[] 하나를 할당해 OOM을 유발할 수 있다. 이 값은 /Width, /Height 같은
 * PDF 딕셔너리 값으로는 알 수 없고 코드스트림 내부를 직접 봐야 한다.
 */
object JpxSafetyValidator {
    /** 해상도 레벨 하나의 프리싱크트 그리드(가로 개수 * 세로 개수) 상한 */
    private const val MAX_PRECINCT_GRID = 65_536L

    /** 타일 전체 픽셀 수 상한 (프리싱크트 지정이 없어도 타일 자체가 비정상적으로 크면 차단) */
    private const val MAX_TILE_PIXELS = 64_000_000L

    private const val SOC = 0xFF4F
    private const val SIZ = 0xFF51
    private const val COD = 0xFF52
    private const val COC = 0xFF53
    private const val SOT = 0xFF90

    /** 프리싱크트 미지정 시 기본 지수. 2^15 크기이므로 사실상 타일당 1개 */
    private const val DEFAULT_PRECINCT_EXPONENT = 15

    fun validate(codestream: ByteArray): JpxValidationResult =
        try {
            validateInternal(codestream)
        } catch (e: Exception) {
            JpxValidationResult.Unsafe("코드스트림 파싱 중 오류: ${e.message}")
        }

    private fun validateInternal(rawBytes: ByteArray): JpxValidationResult {
        val codestream =
            unwrapJp2Codestream(rawBytes)
                ?: return JpxValidationResult.Unsafe("JP2 코드스트림을 찾을 수 없음")
        val reader = MarkerReader(codestream)
        if (!reader.expectMarker(SOC)) {
            return JpxValidationResult.Unsafe("SOC 마커를 찾을 수 없음")
        }
        if (!reader.expectMarker(SIZ)) {
            return JpxValidationResult.Unsafe("SIZ 마커를 찾을 수 없음")
        }

        val siz = reader.readSiz() ?: return JpxValidationResult.Unsafe("SIZ 세그먼트 파싱 실패")
        if (siz.tileWidth <= 0 || siz.tileHeight <= 0) {
            return JpxValidationResult.Unsafe("타일 크기가 유효하지 않음 (${siz.tileWidth}x${siz.tileHeight})")
        }
        if (siz.tileWidth.toLong() * siz.tileHeight.toLong() > MAX_TILE_PIXELS) {
            return JpxValidationResult.Unsafe("타일 픽셀 수가 한도를 초과함 (${siz.tileWidth}x${siz.tileHeight})")
        }

        // 메인 헤더(첫 SOT 이전)의 COD/COC 세그먼트를 모두 검사한다. 하나라도 위험하면 Unsafe.
        while (true) {
            val marker = reader.nextMarker() ?: break
            if (marker == SOT) break
            when (marker) {
                COD, COC -> {
                    val precinctExponents =
                        reader.readPrecinctExponents(marker) ?: return JpxValidationResult.Unsafe(
                            "${if (marker == COD) "COD" else "COC"} 세그먼트 파싱 실패",
                        )
                    for ((ppx, ppy) in precinctExponents) {
                        val grid = precinctGrid(siz.tileWidth, siz.tileHeight, ppx, ppy)
                        if (grid > MAX_PRECINCT_GRID) {
                            return JpxValidationResult.Unsafe(
                                "프리싱크트 그리드가 한도를 초과함 (grid=$grid, ppx=$ppx, ppy=$ppy)",
                            )
                        }
                    }
                }
                else -> reader.skipSegment()
            }
        }

        return JpxValidationResult.Safe
    }

    /**
     * PDF의 JPXDecode 스트림은 raw 코드스트림(SOC로 시작) 또는 JP2 파일 포맷(박스 구조)일 수 있다.
     * 박스 포맷이면 최상위 jp2c(Contiguous Codestream) 박스 내용을 codestream으로 꺼낸다.
     */
    private fun unwrapJp2Codestream(data: ByteArray): ByteArray? {
        if (data.size >= 2 && (data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xFF) == 0x4F) {
            return data
        }

        var pos = 0
        while (pos + 8 <= data.size) {
            val lBox = readU32BE(data, pos)
            val tBox = String(data, pos + 4, 4, Charsets.US_ASCII)
            var contentStart = pos + 8
            var boxLength = lBox
            if (lBox == 1L) {
                if (pos + 16 > data.size) return null
                boxLength = readU64BE(data, pos + 8)
                contentStart = pos + 16
            } else if (lBox == 0L) {
                boxLength = (data.size - pos).toLong()
            }
            val boxEnd = pos + boxLength
            if (boxLength < (contentStart - pos) || boxEnd > data.size || boxEnd < contentStart) return null

            if (tBox == "jp2c") {
                return data.copyOfRange(contentStart, boxEnd.toInt())
            }
            pos = boxEnd.toInt()
        }
        return null
    }

    private fun readU32BE(
        data: ByteArray,
        offset: Int,
    ): Long {
        var v = 0L
        for (i in 0 until 4) {
            v = (v shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return v
    }

    private fun readU64BE(
        data: ByteArray,
        offset: Int,
    ): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return v
    }

    private fun precinctGrid(
        tileWidth: Int,
        tileHeight: Int,
        ppx: Int,
        ppy: Int,
    ): Long {
        val precinctWidth = 1L shl ppx
        val precinctHeight = 1L shl ppy
        val gridX = (tileWidth + precinctWidth - 1) / precinctWidth
        val gridY = (tileHeight + precinctHeight - 1) / precinctHeight
        return gridX * gridY
    }

    private data class Siz(
        val tileWidth: Int,
        val tileHeight: Int,
    )

    /** 코드스트림 바이트를 순차적으로 읽는 최소 파서 */
    private class MarkerReader(
        private val data: ByteArray,
    ) {
        private var pos = 0

        private fun readU8(): Int {
            require(pos < data.size) { "예상치 못한 코드스트림 끝" }
            return data[pos++].toInt() and 0xFF
        }

        private fun readU16(): Int {
            val hi = readU8()
            val lo = readU8()
            return (hi shl 8) or lo
        }

        private fun readU32(): Long {
            val hi = readU16()
            val lo = readU16()
            return (hi.toLong() shl 16) or lo.toLong()
        }

        fun expectMarker(expected: Int): Boolean {
            if (pos + 2 > data.size) return false
            val marker = readU16()
            return marker == expected
        }

        /** 다음 마커 코드를 반환. 코드스트림 끝이면 null */
        fun nextMarker(): Int? {
            if (pos + 2 > data.size) return null
            return readU16()
        }

        /** 현재 위치가 세그먼트 길이 필드(Lsiz/Lcod 등) 시작이라고 가정하고 세그먼트 전체를 건너뛴다 */
        fun skipSegment() {
            if (pos + 2 > data.size) {
                pos = data.size
                return
            }
            val length = readU16()
            pos += (length - 2).coerceAtLeast(0)
        }

        fun readSiz(): Siz? {
            if (pos + 2 > data.size) return null
            val length = readU16() // Lsiz
            val segmentEnd = pos + (length - 2)
            if (segmentEnd > data.size || segmentEnd < pos) return null

            if (pos + 2 > segmentEnd) return null
            readU16() // Rsiz, 사용 안 함
            if (pos + 16 > segmentEnd) return null
            readU32() // Xsiz
            readU32() // Ysiz
            readU32() // XOsiz
            readU32() // YOsiz
            if (pos + 8 > segmentEnd) return null
            val xtsiz = readU32()
            val ytsiz = readU32()
            if (xtsiz <= 0 || xtsiz > Int.MAX_VALUE || ytsiz <= 0 || ytsiz > Int.MAX_VALUE) return null

            pos = segmentEnd
            return Siz(xtsiz.toInt(), ytsiz.toInt())
        }

        /**
         * COD/COC 세그먼트에서 "사용자 정의 프리싱크트" 비트가 켜져 있으면 해상도 레벨별 (ppx, ppy) 목록을,
         * 꺼져 있으면 기본값(안전) 하나짜리 목록을 반환한다. 파싱 실패 시 null.
         */
        fun readPrecinctExponents(marker: Int): List<Pair<Int, Int>>? {
            if (pos + 2 > data.size) return null
            val length = readU16()
            val segmentEnd = pos + (length - 2)
            if (segmentEnd > data.size || segmentEnd < pos) return null

            if (marker == COC) {
                // Ccoc: Csiz<=256이면 1바이트, 아니면 2바이트. 컴포넌트 수를 모르므로 보수적으로 1바이트로 가정.
                if (pos + 1 > segmentEnd) return null
                pos += 1
            }

            val scodOffset = pos
            if (scodOffset + 1 > segmentEnd) return null
            val scod = readU8()
            val userDefinedPrecincts = (scod and 0x01) != 0

            if (marker == COD) {
                // SGcod: 4바이트 (progression order 1, layers 2, MCT 1)
                if (pos + 4 > segmentEnd) return null
                pos += 4
            }

            // SPcod/SPcoc 공통 필드: decomposition levels(1) + code-block width exp(1) + height exp(1) + style(1) + transform(1)
            if (pos + 5 > segmentEnd) return null
            val numDecompositionLevels = readU8()
            pos += 4 // code-block width/height exponent, style, transform

            if (!userDefinedPrecincts) {
                return listOf(DEFAULT_PRECINCT_EXPONENT to DEFAULT_PRECINCT_EXPONENT)
            }

            val levelCount = numDecompositionLevels + 1
            val exponents = mutableListOf<Pair<Int, Int>>()
            repeat(levelCount) {
                if (pos + 1 > segmentEnd) return null
                val byte = readU8()
                val ppx = byte and 0x0F
                val ppy = (byte shr 4) and 0x0F
                exponents.add(ppx to ppy)
            }

            pos = segmentEnd
            return exponents
        }
    }
}
