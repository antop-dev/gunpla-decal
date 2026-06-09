package ai.antop.gunpla.config

import ai.antop.gunpla.app.domain.ManualId
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** ManualId Jackson 직렬화·역직렬화 모듈 설정 */
@Configuration
class JacksonConfig {
    /** ManualId를 Base62 문자열로 직렬화/역직렬화하는 Jackson 모듈 빈 등록 */
    @Bean
    fun manualIdModule(): SimpleModule =
        SimpleModule().apply {
            addSerializer(ManualId::class.java, ManualIdSerializer())
            addDeserializer(ManualId::class.java, ManualIdDeserializer())
        }
}
