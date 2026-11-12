# 기본 게임 프레임 검증 기록

기준일: 2026-11-12, 한국 시간. 대상 프로젝트: `car_escape` / `RoadEscape` / `com.example.roadescape`.

**구현 완료 / debug·release 빌드 확인 / Pleos 에뮬레이터 시연 확인.** JVM 테스트 15개와 기기 테스트 15개가 모두 통과했습니다. Lint는 오류 0개·경고 14개입니다. 아래 결과는 개발용 모의 입력과 에뮬레이터 검증이며, 실제 차량·GPS·실차 주행 UX 지원은 확인하지 않았습니다.

## 관측한 환경

| 항목 | 관측 또는 설정값 | 구분 |
| --- | --- | --- |
| 호스트 | Windows / PowerShell, 작업 폴더 `C:\car_escape` | 로컬 관측 |
| JVM | Android Studio JBR OpenJDK `25.0.4` | `java -version` |
| Gradle | `9.1.0` | Wrapper `--version` 실행 성공 |
| AGP / Kotlin | `9.0.1` / `2.2.21` | 프로젝트 고정값 |
| compileSdk / targetSdk / minSdk | `36` / `34` / `28` | 프로젝트 고정값 |
| JVM 바이트코드 대상 | `17` | 프로젝트 고정값 |
| 설치 SDK Platform / Build Tools | `android-36` / `36.0.0` | 로컬 관측 |
| AVD | `Pleos_Connect_v2.1.0` | AVD 목록 관측 |
| 연결 기기 | `emulator-5554` / `device` | `adb devices` |
| 기기 플랫폼 | API `34` / Android `14` / `42dot Connect` | 연결 기기 관측 |
| 화면 | `2560 × 1440`, density `160` | 연결 기기 관측 |
| 차량·위치 SDK | 추가하지 않음 | Manifest·의존성 구성 |
| 도착·주차 입력 | debug 전용 모의 제공자 | 실제 차량·GPS 관측 아님 |

API 34 AVD의 실행 호환성과 compileSdk 36의 컴파일 조건은 별개입니다. 실제 Pleos 차량 신호·차량 제어·도로 주행 UX 승인은 이번 기록에서 검증하지 않습니다.

## 명령과 산출물

| 실행 항목 | 현재 기록 | 근거·추가 기록 위치 |
| --- | --- | --- |
| JBR `java -version` | 확인 | OpenJDK 25.0.4 |
| `.\gradlew.bat --version` | 성공 | Gradle 9.1.0 |
| `:app:dependencies` 의존성 조회 task | 성공 | 프로젝트 의존성 해석 확인; 앱 동작 검증과 구분 |
| `adb devices`, AVD·기기 정보 조회 | 확인 | 위 환경 표 |
| `:app:assembleDebug` | 성공 | `app-debug.apk`, 29,915,702 bytes |
| `:app:testDebugUnitTest` | 성공 | `GameCoreTest` 15개, 실패·오류 0 |
| `:app:lintDebug` | 성공 | 오류 0개·경고 14개; 보고서 보존 |
| `:app:assembleRelease` | 성공 | `app-release-unsigned.apk`, 21,709,730 bytes |
| `:app:assembleDebugAndroidTest` | 성공 | 실제 테스트 APK 생성 |
| `:app:connectedDebugAndroidTest` | 성공 | emulator-5554, 저장 8개·화면 3개·VM 순서 4개, 총 15/15 |
| debug 설치와 Activity 실행 | 성공 | `adb -s emulator-5554 install -r ...` 성공; 시작·완주·중단 화면 확인 |
| force-stop·재실행 | 성공 | 힌트 1·정답 공개·SKIPPED·단서 1개 유지, 주차 UNKNOWN |
| 손상 저장의 초기화 UI | 성공 | 손상 바이트 유지 → 사용자 확인 → 원본 .bak 보존 → 새 게임 이동; 테스트 후 기존 시뮬레이션 진행 복원 |
| release 설치·실행 | 성공 | 로컬 debug 키로만 서명한 검증용 release APK에서 개발 패널 없음·UNKNOWN 차단 확인; 배포 서명과 구분 |

실제로 생성된 빌드 산출물과 보고서 위치는 다음과 같습니다.

- debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- release 미서명 APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- JVM 테스트 보고서: `app/build/reports/tests/testDebugUnitTest/index.html`
- JVM 테스트 XML: `app/build/test-results/testDebugUnitTest/`
- Lint 보고서: `app/build/reports/lint-results-debug.html`
- 기기 테스트 보고서: `app/build/reports/androidTests/connected/debug/`
- 기기 테스트 XML: `app/build/outputs/androidTest-results/connected/debug/`

debug APK SHA-256: `3879b01ae3201562c4ac71e14176b2a1e8ec2681cb18d55e7d62e648510bdaac`.

실행한 전체 빌드는 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest`이며 최종 성공했습니다. 이어 `:app:lintDebug :app:connectedDebugAndroidTest`를 실행해 15개 기기 테스트의 실패·건너뜀 0개를 확인했습니다. 실행 중 `dumpsys connectivity`의 `Active default network: none`을 확인했으며, 앱에는 INTERNET·위치·차량 권한이 없습니다.

원시 기기 로그와 스크린샷 등 로컬 검증 산출물은 `.gitignore`의 `verification-artifacts/`에 두며 원격에 올리지 않습니다.

## 수용 시나리오 T01~T16

상태는 실제 자동·수동 실행 근거에 따라 `통과 / 실패 / 부분 확인 / 미실행`으로 기록합니다. 순수 Kotlin 테스트를 통과해도 화면 가림이나 Pleos 터치 동작까지 확인한 것으로 해석하지 않습니다.

| ID | 시나리오와 기대 결과 | 상태 | 근거 또는 남은 확인 |
| --- | --- | --- | --- |
| T01 | 방문 전 또는 UNKNOWN 제출은 변경 없이 이유를 표시 | 통과 | reducer 게이트·화면 중단 안내 |
| T02 | 1번 방문 + PARKED이면 1번만 플레이 가능 | 통과 | 화면 테스트에서 2·3번 잠금과 1번 입력 확인 |
| T03 | `0427` 정답, `427` 형식 오류, `0428` 오답 | 통과 | 순수 판정·오답 횟수 테스트와 화면 정답 제출 |
| T04 | 같은 기호라도 순서가 다르면 오답 | 통과 | 순서·길이·반복 기호 단위 테스트 및 화면 완주 |
| T05 | 조합 하나만 일치 / 둘 다 일치 / 범위 밖을 구분 | 통과 | 조합·누락 키·범위 테스트 및 화면 채널 3/레벨 2 제출 |
| T06 | 중복 제출·지난 화면 이벤트가 진행·보상을 중복 변경하지 않음 | 통과 | 도메인 멱등성·실제 VM 이전 세션 거절·저장 원장 확인 |
| T07 | 1번 완료 후 2번을 새로 방문해야 플레이 가능 | 통과 | 화면 테스트에서 2번 중단 → 새 도착 → 입력 허용 |
| T08 | 정답 공개 후 재시작·건너뛰면 공개 이력·SKIPPED·단서 유지 | 통과 | Activity 재생성·건너뛰기 테스트 및 실제 force-stop 후 보존 |
| T09 | 힌트 재열람·재시작에서 공개 단계 중복 증가 없음 | 통과 | 동일 단계 멱등성 테스트·저장 복원·화면의 힌트 1/2 확인 |
| T10 | DRIVING·UNKNOWN이면 퍼즐·힌트·단서 가림과 명령 차단, PARKED 복귀 | 통과 | 퍼즐·힌트 UI 테스트, 빠른 주차 변경/배경 복귀 VM 테스트; 단서함에서도 PARKED 표시 → DRIVING·UNKNOWN 가림 수동 확인 |
| T11 | force-stop 후 저장된 진행·보상 복원, 주차는 UNKNOWN | 통과 | 데이터 삭제 없이 전후 JSON 일치와 UNKNOWN·이어하기 화면 확인 |
| T12 | 저장 실패·손상 JSON·알 수 없는 버전에 자동 초기화 없음 | 통과 | 실제 DataStore 쓰기 실패·손상·미래 버전·초기화 실패 복구 테스트 및 손상 시작 화면 수동 확인 |
| T13 | 저장·제출과 새 게임 요청이 순서대로 처리되고 세션이 섞이지 않음 | 통과 | 실제 VM 큐의 이전 세션 제출·저장 중 정차 상태 변경 검증 |
| T14 | 세 경유지 완료 시 해결·건너뜀·힌트 내역 정확 | 통과 | 화면의 해결 3/건너뜀 0/힌트 0/공개 0과 단서 3개 확인; 혼합 해결·건너뜀은 도메인 테스트 |
| T15 | 기기 네트워크를 끈 상태에서도 새 게임부터 완료 | 통과 | 기본 네트워크가 없는 AVD에서 새 게임 → 3종 자물쇠 → 완료 UI 테스트 |
| T16 | release에 개발 패널·가짜 제공자 없음, UNKNOWN으로 플레이 차단 | 통과 | release DEX에 Simulated 제공자 없음·Unavailable 제공자 존재; 실제 설치 후 개발 패널 없음·UNKNOWN·퍼즐 중단·제출 없음 확인 |

테스트 근거는 `GameCoreTest` 15개, `GameRepositoryTest` 8개, `GameFlowTest` 3개, `GameViewModelOrderingTest` 4개입니다. 마지막 기기 실행의 테스트 수는 15개이며 JVM 테스트와 합하면 30개입니다. 저장 테스트는 별도 임시 파일에서 실제 DataStore round-trip·직렬화 실패를 확인했습니다.

수동 검증은 별도로 힌트 1개·정답 공개·1번 SKIPPED·단서 1개의 진행을 만들고 수행했습니다. `am force-stop --user 10 com.example.roadescape` 전후의 저장 JSON이 일치했고 SHA-256은 `3f60b6fa8ca8c2a24211405913564361b04d1c230a651a106dc497da08e3f300`이었습니다. 재실행 화면은 UNKNOWN·이어하기를 표시했습니다. 손상 JSON 주입 검증에서는 오류 표시 중 원본 12바이트를 그대로 유지했고, 사용자가 새 게임을 확인한 뒤 .bak 보존과 새 세션 이동을 확인했습니다. 검증 후 원래 시뮬레이션 진행을 복원했습니다.

release 런타임 검증은 미서명 빌드 산출물을 기존 로컬 debug 키로 서명한 복사본에서 수행했습니다. 이는 설치 확인용이며 배포용 서명·출시 승인으로 해석하지 않습니다. 검증 후 사용할 APK는 원래 debug 빌드로 되돌렸습니다. `force-stop-restored.png`, `corrupt-save-error.png`, `corrupt-save-reset.png`, `clues-hidden-unknown.png`, `release-paused.png`를 로컬 검증 폴더에 보존했습니다.

## 수동 재현과 촬영 순서

1. `adb devices`에서 대상을 확인하고 debug APK를 설치한 뒤 런처에서 `차량 방탈출 데모`를 엽니다.
2. 새 게임을 시작합니다. UNKNOWN에서 퍼즐 내용을 볼 수 없는지, 방문 없이 PARKED만 지정해도 도착 안내가 나오는지 확인합니다.
3. 개발 패널에서 현재 경유지를 도착 처리하고 PARKED로 지정합니다. 1번 퍼즐에서 `427`, `0428`, `0427`을 차례로 제출해 형식 오류·오답·정답과 카운트를 확인합니다.
4. 단서함에서 단서 A의 출처를 확인합니다. 2번의 새 방문 필요 상태를 확인하고 방향 자물쇠를 진행합니다.
5. 힌트와 정답 공개를 사용한 뒤 DRIVING·UNKNOWN을 지정해 내용이 가려지는지 확인합니다. PARKED로 복귀해 진행 이력을 확인합니다.
6. 한 경유지는 건너뛰고 나머지는 직접 해결합니다. 완료 화면에서 해결·건너뜀·힌트·정답 공개 수를 대조합니다.
7. 저장 완료 후 앱을 force-stop하고 재실행합니다. 앱 데이터를 지우지 않은 채 이어하기의 진행 복원과 UNKNOWN 초기 상태를 확인합니다.
8. 홈으로 앱을 백그라운드에 보냈다 돌아와 주차 재확인이 필요한지 확인합니다. 가능하면 네트워크를 끈 상태의 새 게임 완주도 별도로 기록합니다.

force-stop·재실행 명령 예시입니다. 기기 ID는 실제 대상을 사용합니다.

```powershell
adb -s emulator-5554 shell am force-stop com.example.roadescape
adb -s emulator-5554 shell am start -n com.example.roadescape/.MainActivity
```

30~60초 시연은 `개발용 시뮬레이션` 배지가 보이도록 새 게임 → 도착·PARKED → 숫자 제출 → 단서함 → DRIVING 가림 → PARKED 복귀 → 나머지 해결/건너뛰기 → 완료 내역 순서로 촬영할 수 있습니다. 촬영 계획 자체는 시연 성공 근거가 아닙니다.

## 오류·제한·출처

- 첫 Lint 실행은 로컬 `local.properties`의 Windows 드라이브 `:` 미이스케이프로 실패했습니다. 로컬 경로를 올바른 properties 형식으로 수정한 뒤 전체 빌드·Lint를 재실행해 성공했습니다. 해당 파일은 Git에서 제외합니다.
- 첫 기기 실행은 14/15 통과였습니다. 실패한 힌트 복원 테스트의 실제 텍스트는 `힌트 1 / 2 공개`였고, 비교식이 문장 전체를 숫자 `1`과 비교하고 있었습니다. 문자열 리소스로 만든 정확한 기대값으로 수정한 뒤 **전체 15/15** 통과를 확인했습니다.
- Lint 경고 14개는 고정한 구버전 도구·라이브러리·target SDK 권고 13개와 Compose Modifier 매개변수 순서 1개입니다. 오류를 숨기는 baseline이나 비활성화 설정은 추가하지 않았습니다.
- 최초 `am start -W`는 콜드 스타트 중 약 16.7초 대기 후 timeout을 반환했습니다. 이후 Activity RESUMED와 실제 시작 화면을 확인했고, 세 경유지의 기기 테스트·수동 조작도 통과했습니다. 실제 차량의 시작 성능을 검증한 결과는 아닙니다.
- 기기 테스트 실행기는 이 앱과 테스트 APK를 정리하므로, 수동 시연 전 debug APK를 다시 설치하고 새 시뮬레이션 진행을 만들었습니다. 기존 다른 앱·AVD는 초기화하지 않았습니다.
- Ubuntu에서의 실제 실행, API 28 기기 실행, 작은 화면·큰 글꼴의 전 범위 회귀, 실차·실제 차량 SDK 신호·정책 승인은 **미실행**입니다. Ubuntu 명령은 README에 제공하며 현재 빌드·기기 관측은 Windows의 API 34 Pleos AVD 결과입니다.

정적 정답은 APK에 포함됩니다. 이 앱은 보안 자물쇠가 아니라 게임 데모이며, 실제 차량·관광지·GPS 데이터나 서버를 사용하지 않습니다. `ParkingStateProvider`·`ArrivalProvider`만 향후 교체 경계로 마련했습니다.

Wrapper 4개 파일은 기존 로컬 `my-car-buddy` 프로젝트에서 복사했고 기존 라이선스 고지를 유지했습니다. 배포본은 SHA-256이 설정된 Gradle 9.1.0이며 `--version` 실행을 확인했습니다. 개인 SDK 경로와 기기 원시 로그는 공유 소스에 넣지 않습니다.

참고한 기준: [개발 가이드라인](PLEOS_CODEX_DEVELOPMENT_GUIDELINES.md), [Pleos 앱 시작 안내](https://document.pleos.ai/en/docs/pleos-only/getting-started), [Pleos 에뮬레이터 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [AGP 9.0 공식 안내](https://developer.android.com/build/releases/agp-9-0-0-release-notes). 게임 규칙·56dp 터치 영역·로컬 저장 방식은 프로젝트 설계값이며 Pleos 공식 정책 승인으로 표시하지 않습니다.
