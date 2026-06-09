package ai.antop.gunpla.config

import ai.antop.gunpla.app.domain.ManualId
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

/** JSON Base62 문자열을 ManualId로 역직렬화하는 Jackson 역직렬화기 */
internal class ManualIdDeserializer : StdDeserializer<ManualId>(ManualId::class.java) {
    /** Base62 문자열을 ManualId.fromB62()로 파싱 */
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): ManualId = ManualId.fromB62(p.text)
}
