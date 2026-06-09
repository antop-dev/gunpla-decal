package ai.antop.gunpla.config

import ai.antop.gunpla.app.domain.ManualId
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

/** ManualId를 Base62 문자열로 직렬화하는 Jackson 직렬화기 */
internal class ManualIdSerializer : StdSerializer<ManualId>(ManualId::class.java) {
    /** ManualId.toString() (Base62) 값을 JSON 문자열로 출력 */
    override fun serialize(
        value: ManualId,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) = gen.writeString(value.toString())
}
