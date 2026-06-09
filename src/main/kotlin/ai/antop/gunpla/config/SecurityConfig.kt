package ai.antop.gunpla.config

import ai.antop.gunpla.app.repository.AdminRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/** Spring Security 설정 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    /** AdminRepository를 조회하여 ROLE_ADMIN 권한의 UserDetails를 구성하는 서비스 빈 */
    @Bean
    fun userDetailsService(adminRepository: AdminRepository): UserDetailsService =
        UserDetailsService { username ->
            adminRepository
                .findByUsername(username)
                ?.let {
                    User
                        .builder()
                        .username(it.username)
                        .password(it.password)
                        .roles("ADMIN") // ROLE_ 접두사가 자동으로 붙는다
                        .accountExpired(false)
                        .accountLocked(false)
                        .disabled(false)
                        .build()
                } ?: throw UsernameNotFoundException(username)
        }

    /** DelegatingPasswordEncoder 빈 등록 (bcrypt 기본) */
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /** DaoAuthenticationProvider를 사용하는 AuthenticationManager 빈 등록 */
    @Bean
    fun authenticationManager(userDetailsService: UserDetailsService): AuthenticationManager =
        ProviderManager(
            DaoAuthenticationProvider(userDetailsService).apply {
                setPasswordEncoder(passwordEncoder())
            },
        )

    /**
     * HTTP 보안 필터 체인 구성.
     * - 공개 경로: /, /login, /captcha, /css/{all}, /js/{all}, /vendor/{all}, /api/user/{all}, /resource/{all}, /actuator/{all}
     * - /admin: 인증 필요
     * - Base62 단축 URL: 인증 불필요
     * - API 경로: CSRF 검증 제외
     * - 캡차 인증 필터를 UsernamePasswordAuthenticationFilter 위치에 삽입
     */
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authenticationManager: AuthenticationManager,
    ): SecurityFilterChain {
        val captchaFilter = CaptchaAuthenticationFilter(authenticationManager)

        return http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/",
                        "/login",
                        "/captcha",
                        "/css/**",
                        "/js/**",
                        "/vendor/**",
                        "/api/user/**",
                        "/resource/**",
                        "/actuator/**",
                    ).permitAll()
                // /admin은 인증 필요 — 아래 /* 와일드카드보다 먼저 평가되어야 함
                auth.requestMatchers("/admin").authenticated()
                // base62 메뉴얼 직접 링크 (/4S 등) 허용
                auth.requestMatchers("/*").permitAll()
                auth.anyRequest().authenticated()
            }.addFilterAt(captchaFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { request, response, authException ->
                    if (request.requestURI.startsWith("/api/")) {
                        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN)
                    } else {
                        LoginUrlAuthenticationEntryPoint("/login").commence(request, response, authException)
                    }
                }
            }.logout { logout ->
                logout.logoutUrl("/logout")
                logout.logoutSuccessUrl("/login?logout")
                logout.permitAll()
            }.csrf { csrf ->
                csrf.ignoringRequestMatchers("/api/**")
            }.build()
    }
}
