package ai.antop.gunpla.app.dto

import ai.antop.gunpla.app.domain.DecalShape

/** ONNX 분류기의 데칼 번호·모양 인식 결과. 각 값은 신뢰도가 임계값 미만이면 null */
data class DecalOnnxResultDto(
    /** 인식된 데칼 번호 */
    val number: String?,
    /** 인식된 데칼 도형 (모양 출력이 없는 예전 모델이면 항상 null) */
    val shape: DecalShape?,
)
