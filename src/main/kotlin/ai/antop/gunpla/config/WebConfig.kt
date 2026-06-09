package ai.antop.gunpla.config

import ai.antop.gunpla.app.domain.ManualId
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** MVC 전환기 설정. URL 경로 변수의 Base62 문자열을 ManualId로 변환 */
@Configuration
class WebConfig : WebMvcConfigurer {
    /** String → ManualId 변환기를 등록하여 @PathVariable 바인딩 지원 */
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(String::class.java, ManualId::class.java) { ManualId.fromB62(it) }
    }
}
