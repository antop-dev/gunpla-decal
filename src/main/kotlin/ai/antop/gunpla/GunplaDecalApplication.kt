package ai.antop.gunpla

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/** 건담프라 데칼 관리 시스템 Spring Boot 진입점 */
@SpringBootApplication
@ConfigurationPropertiesScan
class GunplaDecalApplication

/** 애플리케이션 실행 */
fun main(args: Array<String>) {
    runApplication<GunplaDecalApplication>(*args)
}
