package com.example.gunpladecal.config

import com.example.gunpladecal.app.domain.ManualId
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

internal class ManualIdDeserializer : StdDeserializer<ManualId>(ManualId::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): ManualId = ManualId.fromB62(p.text)
}
