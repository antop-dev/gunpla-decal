package com.example.gunpladecal.config

import com.pig4cloud.captcha.utils.CaptchaJakartaUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

class CaptchaAuthenticationFilter(
    authenticationManager: AuthenticationManager,
) : UsernamePasswordAuthenticationFilter(authenticationManager) {
    init {
        setRequiresAuthenticationRequestMatcher { request ->
            request.method == HttpMethod.POST.name() && request.servletPath == "/login"
        }
        // formLogin() 미사용 시 기본값이 NullSecurityContextRepository여서 세션에 저장되지 않음
        setSecurityContextRepository(HttpSessionSecurityContextRepository())
        setAuthenticationSuccessHandler { request, response, _ ->
            response.status = HttpServletResponse.SC_OK
            response.setHeader(HttpHeaders.LOCATION, "${request.contextPath}/admin")
        }
        setAuthenticationFailureHandler { _, response, exception ->
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.writer.write(exception.message ?: "Bad credentials")
        }
    }

    override fun attemptAuthentication(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Authentication =
        try {
            val captcha = request.getParameter("captcha") ?: ""
            if (!CaptchaJakartaUtil.ver(captcha, request)) {
                CaptchaJakartaUtil.clear(request)
                throw BadCredentialsException("captcha")
            }
            super.attemptAuthentication(request, response)
        } finally {
            CaptchaJakartaUtil.clear(request)
        }
}
