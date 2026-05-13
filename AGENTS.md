# AGENTS.md

## 코드 컨벤션

### 코드 브레이싱

`if`, `for`, `while` 등 다음 코드가 한 줄 일 때에도 브레이서를 무조건 표기합니다.

- 잘못된 코드
    ```kotlin
    if (x == 0) return
    ```
- 올바른 코드
    ```kotlin
    if (x == 0) {
        return
    }
    ```

## 기타

- 대화는 항상 한글로 합니다.
- 현재 작업 디렉토리에서 일어나는 명령어, 파일IO는 물어보지 않고 바로 수행합니다.
- 백엔드 코트린 파일(`*.kt`) 수정 시 ktlint 포메팅을 맞춥니다. `./gradlew ktlintFormat`
