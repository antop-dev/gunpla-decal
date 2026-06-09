package ai.antop.gunpla.app.event

import ai.antop.gunpla.app.domain.ManualId

/** 메뉴얼 상태 변경 이벤트. 공개 전환 또는 삭제 시 발행되어 캐시 무효화를 트리거한다 */
data class ManualChangedEvent(
    val manualId: ManualId,
)
