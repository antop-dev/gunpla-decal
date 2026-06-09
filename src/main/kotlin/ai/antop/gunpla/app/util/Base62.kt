package ai.antop.gunpla.app.util

/** Long 정수를 Base62 문자열로 인코딩·디코딩하는 유틸리티. ManualId 공개 식별자 변환에 사용 */
object Base62 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62L

    /** Long 값을 Base62 문자열로 인코딩. 0이면 "0" 반환 */
    fun encode(n: Long): String {
        if (n == 0L) {
            return "0"
        }
        val sb = StringBuilder()
        var v = n
        while (v > 0) {
            sb.append(ALPHABET[(v % BASE).toInt()])
            v /= BASE
        }
        return sb.reverse().toString()
    }

    /** Base62 문자열을 Long으로 디코딩. 유효하지 않은 문자가 있으면 IllegalArgumentException 발생 */
    fun decode(s: String): Long {
        var result = 0L
        for (c in s) {
            val idx = ALPHABET.indexOf(c)
            require(idx >= 0) { "Invalid base62 character: $c" }
            result = result * BASE + idx
        }
        return result
    }
}
