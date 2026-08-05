package ai.antop.gunpla.app.service

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertIs

class JpxSafetyValidatorTest {
    @Test
    fun `프리싱크트 미지정(기본값)이면 안전으로 판정한다`() {
        val codestream = buildCodestream(tileWidth = 5000, tileHeight = 5000, numDecompLevels = 5, precinctExponents = null)

        val result = JpxSafetyValidator.validate(codestream)

        assertIs<JpxValidationResult.Safe>(result)
    }

    @Test
    fun `타일 크기 대비 프리싱크트 지수가 비정상적으로 작으면 위험으로 판정한다`() {
        // 이번 사고 재현: 타일 5000x5000에 프리싱크트 지수 0(=1x1) -> 그리드 25,000,000
        val codestream =
            buildCodestream(
                tileWidth = 5000,
                tileHeight = 5000,
                numDecompLevels = 0,
                precinctExponents = listOf(0 to 0),
            )

        val result = JpxSafetyValidator.validate(codestream)

        assertIs<JpxValidationResult.Unsafe>(result)
    }

    @Test
    fun `합리적인 프리싱크트 지수는 안전으로 판정한다`() {
        // 타일 5000x5000, 프리싱크트 지수 8(=256x256) -> 그리드 20x20=400
        val codestream =
            buildCodestream(
                tileWidth = 5000,
                tileHeight = 5000,
                numDecompLevels = 0,
                precinctExponents = listOf(8 to 8),
            )

        val result = JpxSafetyValidator.validate(codestream)

        assertIs<JpxValidationResult.Safe>(result)
    }

    @Test
    fun `JP2 박스 포맷(jp2c에 감싸진 코드스트림)도 올바르게 검증한다`() {
        // 실제 인코더(OpenJPEG 등)는 대부분 raw 코드스트림이 아니라 jP/ftyp/jp2h/jp2c 박스 구조로 감싸서 출력한다.
        val codestream =
            buildCodestream(
                tileWidth = 5000,
                tileHeight = 5000,
                numDecompLevels = 0,
                precinctExponents = listOf(0 to 0),
            )
        val wrapped = wrapInJp2Box(codestream)

        val result = JpxSafetyValidator.validate(wrapped)

        assertIs<JpxValidationResult.Unsafe>(result)
    }

    /** codestream을 최소 jP + jp2c 박스 구조로 감싼다 (jj2000/jai-imageio가 실제로 받는 JP2 파일 포맷 흉내) */
    private fun wrapInJp2Box(codestream: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()

        fun writeU32(v: Int) {
            out.write((v shr 24) and 0xFF)
            out.write((v shr 16) and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        fun writeBox(
            type: String,
            content: ByteArray,
        ) {
            writeU32(8 + content.size)
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(content)
        }

        // 시그니처 박스 (jP  ), 내용은 0x0D0A870A 고정
        writeBox("jP  ", byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A))
        // ftyp 박스는 내용이 검증 로직과 무관하므로 최소 더미로 채운다
        writeBox("ftyp", "jp2 ".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, 0))
        writeBox("jp2c", codestream)

        return out.toByteArray()
    }

    /** SOC + SIZ(단일 타일=이미지 전체, 컴포넌트 1개) + COD 마커만으로 구성된 최소 JPEG2000 코드스트림을 만든다 */
    private fun buildCodestream(
        tileWidth: Int,
        tileHeight: Int,
        numDecompLevels: Int,
        precinctExponents: List<Pair<Int, Int>>?,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        fun writeU8(v: Int) = out.write(v and 0xFF)

        fun writeU16(v: Int) {
            writeU8(v shr 8)
            writeU8(v)
        }

        fun writeU32(v: Long) {
            writeU16(((v shr 16) and 0xFFFF).toInt())
            writeU16((v and 0xFFFF).toInt())
        }

        // SOC
        writeU16(0xFF4F)

        // SIZ (컴포넌트 1개, 단일 타일 = 이미지 전체)
        writeU16(0xFF51)
        val csiz = 1
        writeU16(38 + 3 * csiz) // Lsiz
        writeU16(0) // Rsiz
        writeU32(tileWidth.toLong()) // Xsiz
        writeU32(tileHeight.toLong()) // Ysiz
        writeU32(0) // XOsiz
        writeU32(0) // YOsiz
        writeU32(tileWidth.toLong()) // XTsiz
        writeU32(tileHeight.toLong()) // YTsiz
        writeU32(0) // XTOsiz
        writeU32(0) // YTOsiz
        writeU16(csiz) // Csiz
        repeat(csiz) {
            writeU8(7) // Ssiz (8bit unsigned)
            writeU8(0) // XRsiz
            writeU8(0) // YRsiz
        }

        // COD
        writeU16(0xFF52)
        val precinctBytes = precinctExponents?.size ?: 0
        writeU16(12 + precinctBytes) // Lcod
        writeU8(if (precinctExponents != null) 0x01 else 0x00) // Scod: bit0 = user-defined precincts
        writeU8(0) // SGcod: progression order
        writeU16(1) // SGcod: number of layers
        writeU8(0) // SGcod: MCT
        writeU8(numDecompLevels) // SPcod: decomposition levels
        writeU8(4) // SPcod: code-block width exponent
        writeU8(4) // SPcod: code-block height exponent
        writeU8(0) // SPcod: code-block style
        writeU8(0) // SPcod: transformation
        precinctExponents?.forEach { (ppx, ppy) ->
            writeU8((ppy shl 4) or ppx)
        }

        return out.toByteArray()
    }
}
