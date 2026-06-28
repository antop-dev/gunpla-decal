package ai.antop.gunpla.app.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList

private val sseLog = KotlinLogging.logger {}

/** 관리자 SSE 연결 관리 및 이벤트 브로드캐스트 서비스 */
@Service
class SseService {
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    fun connect(): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        emitters.add(emitter)
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }
        return emitter
    }

    fun sendToAll(
        eventName: String,
        data: Any,
    ) {
        val dead = mutableListOf<SseEmitter>()
        emitters.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON),
                )
            } catch (e: Exception) {
                sseLog.debug { "SSE 전송 실패, emitter 제거: ${e.message}" }
                dead.add(emitter)
            }
        }
        emitters.removeAll(dead.toSet())
    }
}
