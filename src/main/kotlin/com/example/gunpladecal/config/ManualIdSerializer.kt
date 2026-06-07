package com.example.gunpladecal.config

import com.example.gunpladecal.app.domain.ManualId
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

internal class ManualIdSerializer : StdSerializer<ManualId>(ManualId::class.java) {
    override fun serialize(
        value: ManualId,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) = gen.writeString(value.toString())
}
