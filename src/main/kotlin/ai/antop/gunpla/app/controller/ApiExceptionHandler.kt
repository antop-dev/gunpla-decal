package ai.antop.gunpla.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/**
 * REST API 공통 예외 처리.
 * ResponseStatusException의 사유(reason)를 `{"message": "..."}` 형태로 내려 프론트에서 그대로 노출할 수 있게 한다.
 * 의도적으로 던진 예외만 대상으로 하므로 예상치 못한 예외의 내부 메시지는 노출되지 않는다.
 */
@RestControllerAdvice(annotations = [RestController::class])
class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(e: ResponseStatusException): ResponseEntity<Map<String, String>> =
        ResponseEntity
            .status(e.statusCode)
            .body(mapOf("message" to (e.reason ?: "요청을 처리할 수 없습니다")))
}
