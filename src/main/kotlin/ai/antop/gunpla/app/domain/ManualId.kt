package ai.antop.gunpla.app.domain

import ai.antop.gunpla.app.util.Base62

/**
 * 메뉴얼 공개 식별자. 내부적으로 Long을 보유하며, 외부(API/URL)에서는 Base62 인코딩된 문자열로 표현된다.
 * 캐시키로 사용하려면 equals와 hashCode가 구현되어 있어야 한다.
 */
data class ManualId(
    val value: Long,
) {
    override fun toString(): String = Base62.encode(value * 23)

    companion object {
        fun fromB62(b62: String): ManualId = ManualId(Base62.decode(b62) / 23)
    }
}
