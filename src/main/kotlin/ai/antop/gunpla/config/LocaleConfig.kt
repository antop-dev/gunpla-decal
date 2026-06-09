package ai.antop.gunpla.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale

/** Accept-Language 헤더 기반 다국어 로케일 설정 */
@Configuration
class LocaleConfig : WebMvcConfigurer {
    /** Accept-Language 헤더로 로케일을 결정하며 기본값은 영어 */
    @Bean
    fun localeResolver(): LocaleResolver =
        AcceptHeaderLocaleResolver().apply {
            this.setDefaultLocale(Locale.ENGLISH)
        }
}
