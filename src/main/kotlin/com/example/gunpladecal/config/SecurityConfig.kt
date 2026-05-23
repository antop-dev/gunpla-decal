package com.example.gunpladecal.config

import com.example.gunpladecal.app.repository.AdminRepository
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

@Configuration
@EnableWebSecurity
class SecurityConfig {
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

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun authenticationManager(userDetailsService: UserDetailsService): AuthenticationManager =
        ProviderManager(
            DaoAuthenticationProvider(userDetailsService).apply {
                setPasswordEncoder(passwordEncoder())
            },
        )

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
                        "/api/manuals/**",
                        "/manuals/**",
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
