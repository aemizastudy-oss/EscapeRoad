# Escape Road

Pleos Connect용 차량 여행 미스터리 앱입니다. `release/v0.2.0`의 「시역(屍疫)」 화면을 유지하며 수원 5개 관광지의 길 안내·안전 상태·관광 시작 저장과 정차용 자물쇠 체험을 제공합니다. **자물쇠 추가 후 Debug·Release 빌드, JVM 51개·자물쇠 기기 UI 11개, Lint 오류 0개를 확인했습니다.** 기기 재검증 중 화면 진입 오류 1개와 단독 재실행 통과, LIVE 직접 조작 결과는 검증 로그에 구분했습니다. 이전 관광 UI 3개 검증은 [검증 로그](docs/verification-log.md)에 별도 이력으로 남겼습니다. 실제 관광 방문·길 안내 완료·가청 음성 성공과는 구분합니다.

- 앱 ID: `com.team.carescape`
- 앱 버전: `0.2.0` (`versionCode=2`), 공유 브랜치: `release/v0.2.0`
- 도구 설정: AGP 9.0.1, Kotlin 2.2.21, Gradle 9.1.0, compileSdk 36 / targetSdk 34 / minSdk 28
- 현재 LIVE 인증은 위치 정확도의 미터 단위 근거가 없어 보류합니다. SDK 준비 상태를 실제 방문·길 안내·발화 성공으로 표시하지 않습니다.
- 자세한 구현 범위·권한·제약은 [SDK 연결 안내](docs/sdk-ui-integration.md), 제품 요구는 [ROAD_ESCAPE_sdk.md](docs/ROAD_ESCAPE_sdk.md), 이전 시연 이력은 [검증 로그](docs/verification-log.md)를 참고하세요.

## 자물쇠 체험

안전한 정차가 확인되면 **로비 → 자물쇠 체험**에서 네 종류를 선택합니다. 이번 화면은 입력 연습이며 기존 시역의 정답·챕터·관광 기록을 바꾸지 않습니다. 화면의 **제출**을 누를 때만 현재 전체 답안을 읽어 표시하며 자동 정답 판정은 하지 않습니다.

| 종류 | 조작 |
| --- | --- |
| 색상 | 4개 다이얼을 위아래로 밀거나 눌러 변경. 각 칸은 빨강 → 노랑 → 초록 → 파랑으로 순환 |
| 방향 | 가운데 원에서 상하좌우로 밀었다가 놓으면 한 방향 추가. 아래에 순서대로 표시하며 초기화로 전체 삭제 |
| 영어 | 자물쇠를 눌러 키보드로 입력. A–Z만 대문자로 5칸에 표시. 키보드 완료는 제출하지 않음 |
| 숫자 | 4개 다이얼을 위아래로 밀거나 눌러 0–9 순환. 앞자리 0도 유지 |

방향 입력의 짧은 움직임·원 밖 시작·취소·멀티터치는 답으로 읽지 않습니다. 방향은 입력이 있을 때, 영어는 5글자를 채웠을 때 제출할 수 있습니다. 입력은 영구 저장하지 않으며 체험을 새로 열면 초기값으로 시작합니다. P·실제 정지·CarUx·foreground 조건이 풀리면 화면과 키보드를 닫고 변경·제출을 거절합니다.

영어 입력의 키보드 배열은 차량 키보드 설정에 따르므로 영문 입력 모드로 전환해야 할 수 있습니다. 앱은 입력된 ASCII 영문만 받습니다.

이미지 원본은 `docs/img/{color_lock,direction_lock,english_lock,num_lock}.png`이고, 앱은 동일 파일을 `app/src/main/res/drawable-nodpi/`에서 읽습니다. 자물쇠 외형은 원본 PNG이며 창의 값과 터치 영역만 Compose로 겹칩니다. 재사용 컴포넌트는 `ui/locks/DialLocks.kt`, `ui/locks/EnglishDialLock.kt`입니다. 각각 `value`, `onValueChange`, `onSubmit`, `modifier`, `enabled`, 선택적 `feedbackContent`를 받으며 화면 위치와 제출 후 동작은 호출자가 정합니다.

## 빌드와 실행

Android Studio에서 저장소 최상위 폴더를 열고 Gradle Sync를 진행합니다. Android SDK Platform 36과 `android.car` 컴파일 라이브러리가 필요합니다. Gradle JDK는 설치한 Android Studio에서 프로젝트 설정에 맞게 선택하고, 개인 SDK 경로는 `local.properties`에 둡니다. Pleos Maven 저장소의 의존성을 받을 수 있어야 합니다.

Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Ubuntu:

```bash
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

위 명령은 재현용 안내입니다. 이번 실행 환경은 Windows·JBR 25·Pleos API 34이며 Ubuntu 실행은 미확인입니다. Android Studio에서 Gradle Sync 후 `app` 실행 구성을 선택하고 Pleos 에뮬레이터/기기로 Run합니다. 결과 APK 경로는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 일반 Android 기기에서 Pleos 서비스·차량 기능이 제공된다고 가정하지 않습니다.

## DEBUG 시연과 재개

1. 오른쪽 위 개발 패널에서 **시연 MOCK**을 선택하고 **P 정차**를 명시적으로 입력합니다. 최초/복귀 상태를 자동 P로 만들지 않습니다.
2. 시역을 시작하고 **시연 이동 시작** 후 **도착한 것으로 하기 (시연)** 또는 개발 패널의 **현재 장소 위치 시연**을 누릅니다. 실제 위치·길 안내가 아닙니다.
3. 도착 화면에서 **관광 시작**을 눌러 저장을 완료합니다. 앱을 닫아도 시작 기록·챕터·획득 단서를 복구합니다.
4. 실제 15분을 기다리거나 DEBUG의 **15분 경과 시연**을 직접 누릅니다. 이 동작은 MOCK 기록에만 표시하며 LIVE에는 적용되지 않습니다.
5. 복귀·재실행·위치 표본 만료 후 **P 정차**와 **현재 장소 위치 시연**을 다시 입력하고 관광 후 진행 버튼을 누릅니다. 다음 장소도 같은 흐름이며 마지막 장소에도 15분 조건이 적용됩니다.

MOCK/LIVE 진행은 별도 저장합니다. LIVE 실패 시 자동으로 시연 입력을 섞지 않습니다. release에는 개발 패널·모의 P·시간 생략이 없습니다. 현재 거리 숫자나 지정 주차장을 안내하지 않으며 합법적인 주차 위치는 이용자가 확인합니다.

## 실제 SDK 확인

**SDK 상태 → 권한 확인**으로 플랫폼이 허용하는 런타임 권한을 요청하고 연결 상태를 확인합니다. 게임과 음성은 foreground·최신 P·정지·CarUx 허용을 모두 요구합니다. background에서는 구독·음성을 멈추고 재진입 때 상태를 다시 확인합니다.

현재 음성은 Pleos ON_DEVICE이며 주행 중 스토리 재생은 보류합니다. Vehicle은 읽기 전용 battery/brake 보조 상태만 연결했습니다. SpeechToText 어댑터는 있지만, 현재 시역에는 음성 정답·녹음 시작 UI가 없습니다. 서버·카메라 방문 증빙·외부 위치/음성 서비스는 추가하지 않았습니다.
