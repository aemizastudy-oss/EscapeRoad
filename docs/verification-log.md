# 검증 로그

## 2026-11-14 · v0.2 기반 v0.3 스토리 통합 / release/v0.4.0

`integration/story-v0.3`는 v0.2 `2a63a9d8a67f8aaa0431f7bb1c420cbcb179bf12`에서 시작했습니다. 공통 Git 조상이 없는 v0.3 전체를 덮어쓰지 않고 `b003204dd5810be00627cb491627eec26afd9e1a`의 `app/src/main/assets/suwon_tour_package.json`에서 이야기·임시 문제를 기능 단위로 이식했습니다. 앱 ID `com.team.carescape`, 기존 SDK·권한·P/정지/CarUx/foreground 제한, 관광 위치·15분 검증, 4종 체험 자물쇠와 원본 이미지·폰트를 유지했습니다.

### 사용자 결정과 구현

- 문제는 v0.3의 **검증용 임시 답**을 사용합니다. 화성행궁 숫자 `0427`, 화홍문 방향 `↑→↓←`, 만석공원 가상 장치 `채널 3/레벨 2`, 박물관 숫자 `1500`, 호수공원 방향 `←↑→↓`입니다. 정식 현장 문제·실제 차량 조작·실제 관광 증거로 표시하지 않습니다.
- 관광 조건 충족 뒤 `PUZZLE`로 이동하며 자동 지급하지 않습니다. **화면의 제출**이 정답일 때만 해결 기록·단서·재료·다음 단계가 한 DataStore 갱신으로 저장됩니다. 오답 횟수와 열어 본 힌트도 저장합니다. 현재 세션·문제·초안과 최신 관광/안전 조건을 직렬 명령 경계에서 재확인합니다.
- 사용자 후속 결정에 따라 기존 v0.2 여행의 **현재 미완료 관광은 다시 시작**합니다. schema 1 원본을 `archivedTrips`에 보관하고 결정적인 새 세션을 만들며, 완료 챕터·단서·재료·여행 시작 epoch는 유지합니다. 기존 관광 시작 원본은 덮어쓰지 않고 새 세션에서는 새 관광 시작을 받습니다. 이미 완료한 여행은 완료 상태를 유지합니다.
- schema 1은 읽기 어댑터로만 해석하고 읽기 자체가 원본 파일을 쓰지 않습니다. 이후 정상 원자 저장부터 schema 2를 기록합니다. 완료 면제는 원본 archive의 완료 개수와 연결해 검증하며 가짜 퍼즐 해결 기록을 만들지 않습니다. 손상·미지원 데이터는 자동 초기화하지 않습니다.
- `IntegratedSiyeok`은 기존 단서·재료 식별 문구를 보존하면서 v0.3 본문·규칙·엔딩을 반영합니다. `StoryPuzzleScreen`은 저장/SDK 연결과 값·콜백만 받는 UI를 분리하며 숫자·방향은 v0.2 native 자물쇠를 재사용합니다. 가상 장치는 앱 화면의 값만 바꿉니다.

### 단계별 검증

| 검증 | 결과 |
| --- | --- |
| 후보 빌드 질문 | “v0.4 통합 후보의 빌드·단위 테스트·UI 테스트 APK·Lint가 통과하는가?” → 통과. Debug·Release·JVM·테스트 APK·Lint, 143.477초, exit 0 |
| JVM | 71개, 실패·오류·건너뜀 0. 저장 17·콘텐츠 6·입력 7·구형 퍼즐 11·스토리 10·통합 퍼즐 9·관광 정책 11 |
| 첫 기기 시도 | 관광 시작 저장 직후 `tourRemainingMillis`가 다음 tick 전까지 null이어서 테스트가 실패. 저장 성공 직후 `refreshTour()`를 호출해 시간·게이트를 함께 공개하도록 수정 |
| 수정 검증 질문 | “관광 시작 저장 후 시간·게이트 즉시 갱신 수정이 빌드와 단위 테스트를 통과하는가?” → 통과. Debug·JVM·테스트 APK 13.983초, exit 0 |
| 화성행궁 우선 기기 검증 | `firstPalacePuzzlePersistsWrongAttemptHintAndTourStartBeforeUiCorrectSubmission`: 재실행 `OK (1 test)`. 오답·힌트 저장, Activity 재생성 뒤 새 P/위치 필요, 실제 다이얼 `0427` 제출과 단서 1회 지급·원본 관광 clock 보존 확인 |
| 전체 코스·회귀 기기 검증 | `StoryIntegrationTest` 3개 + `TourLifecycleTest` 3개 + `LockWorkshopLifecycleTest` 1개, `OK (7 tests)`. 다섯 임시 정답과 마지막 엔딩, 순서·중복 수여 방지, D/foreground/위치 무효화·재개·MOCK/LIVE 분리 및 기존 체험 회귀 통과 |
| 데이터 원본 | 테스트 전 기존 앱 파일 2개와 APK를 백업. 테스트 후 앱을 중단하고 원본 여행 파일을 복원해 SHA-256 바이트 일치를 확인 |
| 원본 프로젝트 최종 질문 | “원본 v0.4 프로젝트의 Debug·Release 빌드, 71개 단위 테스트, Lint가 통과하는가?” → 통과. 동일 관리 래퍼로 `C:\car_escape`에서 Debug·Release·JVM·Lint, 90.761초, exit 0. 두 APK 메타데이터 `0.4.0`/versionCode 4 확인 |
| Lint | 원본 프로젝트 최종 오류 0·경고 8. 후보의 동일 결과와 구분 확인 |

기기 검증은 Pleos API 34 `emulator-5554`/사용자 10의 **MOCK 관광 입력**입니다. 첫 챕터 검증 뒤에만 전체 코스 테스트를 실행합니다. 실제 LIVE 방문·위치 정확도 인증·가청 음성·길 안내 완료·Ubuntu 실행을 성공으로 표시하지 않습니다. 위치 정확도 계약 보류는 유지합니다.

검증 산출물·원시 로그·APK·원본 데이터 백업은 Git 밖의 로컬 `.gradle/car-escape-story-v04-20261114/verification`에 보관하며 업로드하지 않습니다.

## 2026-11-14 · release/v0.2.0 공유 및 자물쇠 재검증

- 현재 공유 브랜치: `release/v0.2.0`. 앱 버전 `0.2.0`, versionCode `2`. 기존 시역 UI·SDK 읽기·모드별 관광 저장과 정차용 자물쇠 4종을 포함합니다.
- 직전 기기 자동 테스트는 11개 중 10개 통과, 방향 비활성 테스트 1개는 `No compose hierarchies found`로 실패했습니다. 해당 시각 내비게이션이 foreground로 전환된 로그를 관측했으며, 같은 테스트를 단독 재실행해 `OK (1 test)`를 확인했습니다. 이 재검증을 전체 11개 연속 통과로 표시하지 않습니다.
- 이어 실제 Console Park·0km/h·DDS ready·browser active에서 LIVE 모드를 선택하고 체험 화면을 직접 조작했습니다. 색상 `노랑 · 빨강 · 빨강 · 빨강`, 방향 `↑ → ↓ ←`와 초기화, 영어 `WMWWW`, 숫자 `0003` 제출 표시를 각각 확인했습니다. 제출 전에는 답을 표시하지 않았습니다.
- 영어 소프트 키보드 실제 표시와 넓은 W/M의 5칸 표시를 스크린샷으로 확인했습니다. 이 에뮬레이터의 `CarIME`는 기본 한글 배열로 열리며, 영문 배열의 자동 선택은 확인하지 못했습니다. ASCII 영문 입력·대문자 정규화와 IME 완료 시 미제출은 자동 테스트로 검증했습니다.
- 테스트 전후 여행 파일 목록/해시는 동일했고, 앱 코드·기존 여행 진행은 테스트를 위해 바꾸지 않았습니다. 이전 절의 ADB offline 때문에 미확인이었던 LIVE 직접 조작은 이번 재검증에서 확인됐습니다. 단, 실차·실제 관광 방문·가청 음성·길 안내 완료는 이번 검증에 포함하지 않습니다.
- 로컬 스크린샷·원시 테스트 로그·백업·APK·개인 SDK 설정·키는 업로드하지 않습니다. 기존 문서의 44개/기기 3개·51개/기기 11개 통과 기록은 해당 작업 시점의 이력으로 보존합니다.
- 최종 검증 질문 “v0.2.0의 Debug·Release 빌드, 단위 테스트, Lint가 통과하는가?” → 통과. 관리 래퍼를 통해 원본 프로젝트의 `gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --offline`를 실행했습니다. 두 APK 메타데이터의 versionName `0.2.0`·versionCode `2`, JVM 51개 통과(실패/오류/건너뜀 0), Lint 오류 0·경고 8을 확인했습니다. 버전 변경만으로 기존 기기 테스트를 새 APK에서 다시 실행한 것으로 표시하지 않습니다.

## 2026-11-13 · 원본 이미지 기반 정차용 자물쇠 4종

**구현 완료 / 후보 Debug·Release 빌드 확인 / JVM 51개·자물쇠 기기 UI 11개 통과 / Lint 오류 0개.** 사용자 답변에 따라 로비의 ‘자물쇠 체험’에 4종을 배치했습니다. 색상·숫자는 4칸, 영어는 A–Z 대문자 5칸, 방향은 초기화 전까지 누적합니다. 실제 이야기의 정답·배치·관광 기록은 변경하지 않습니다.

- `docs/img` PNG 4개를 변형하지 않고 `drawable-nodpi`에 복사했습니다. 네 원본/패키징 파일의 SHA-256 일치를 확인했습니다. 창의 값·터치 영역만 Compose로 겹칩니다.
- 색상은 빨강/노랑/초록/파랑, 숫자는 0–9로 순환합니다. 탭은 다음 값, 상하 드래그는 한 번 놓을 때 한 단계이며 드래그 중 창이 따라 움직입니다. 조작만으로 제출하지 않습니다.
- 방향은 가운데 원에서 시작한 상하좌우 스와이프를 놓을 때 한 방향을 추가합니다. 28dp 미만, 원 밖 시작, 취소·멀티터치는 입력하지 않습니다. 순서는 아래에 번호와 화살표로 표시하며 긴 입력은 가로로 스크롤합니다.
- 영어 자물쇠 전체가 실제 `BasicTextField`이며 키보드 입력을 5개 창에 표시합니다. ASCII 영문만 대문자로 정규화하며 미완성 제출을 막습니다. IME 완료는 키보드만 닫고, 화면의 ‘제출’만 전체 답안을 전달합니다. 좁은 창에서도 W/M이 잘리지 않도록 글자 크기·자간을 이미지 배율에 맞췄습니다.
- 공통 컴포넌트는 값·변경/제출 콜백·루트 modifier·enabled·피드백 슬롯을 받습니다. 체험 화면의 임시 상태와 ViewModel의 현재 안전/화면 조건 검증을 분리했습니다. P·실제 정지·CarUx·foreground가 풀리면 화면과 IME를 닫고 새 입력·제출을 거절합니다.

| 검증 | 실제 결과 |
| --- | --- |
| 환경 | 기존 Windows/JBR 25/Gradle 9.1.0/AGP 9.0.1/Kotlin 2.2.21, Pleos API 34 emulator-5554/user 10 |
| 빌드 질문 | “Do all four locks compile with their test APK, pass JVM tests, and pass debug lint?” → 통과. 관리 래퍼 `gradle_with_jbr.py` → 프로젝트 `gradlew.bat`로 `assembleDebug assembleRelease testDebugUnitTest assembleDebugAndroidTest lintDebug --offline`, 122.877초, exit 0 |
| 원본 프로젝트 재빌드 | “원본 프로젝트에 적용한 자물쇠 코드로 Debug APK를 빌드할 수 있는가?” → 통과. 동일 관리 래퍼로 `C:\car_escape`의 `gradlew.bat :app:assembleDebug --offline`, 21.973초, exit 0. `app/build/outputs/apk/debug/app-debug.apk` 생성 |
| 단위 테스트 | 기존 44개 + 입력 정규화·방향 경계 7개 = 51개. 실패·오류·건너뜀 0 |
| 기기 UI | `LockComponentsTest` 10개 + `LockWorkshopLifecycleTest` 1개, `OK (11 tests)`. 탭·실제 스와이프·순서/초기화·영어 정규화·IME 완료 미제출·비활성·주행/foreground 차단 확인 |
| Lint | 오류 0, 경고 8. 억제·baseline·새 라이브러리를 추가하지 않음 |
| 데이터 | 기기 원본 APK 백업 후 `install -r`. 테스트 전후 여행 파일 목록/해시 동일(둘 다 여행 파일 없음). 저장소를 초기화하거나 기존 앱을 제거하지 않음 |
| 추가 LIVE 화면 캡처 | 테스트 완료 후 ADB 셸이 응답하지 않았고 대상 연결만 재연결했으나 offline으로 남아 미확인. 에뮬레이터 강제 종료·데이터 초기화는 하지 않음. 앞서 통과한 UI 테스트와 별개이며 수동 LIVE 화면·소프트 키보드 가시성 성공으로 표시하지 않음 |

실행 명령은 다음과 같습니다. 이 테스트는 실제 여행 저장소를 수정하지 않습니다.

```text
adb -s emulator-5554 shell am instrument --user 10 -w -r -e class com.team.carescape.ui.LockComponentsTest,com.team.carescape.ui.LockWorkshopLifecycleTest com.team.carescape.test/androidx.test.runner.AndroidJUnitRunner
```

로컬 증거는 Git에서 제외한 `.gradle/car-escape-locks-20261113/verification`에 보관했습니다. SDK 실차·실제 관광 방문·Ubuntu 실행은 이번 검증에 포함하지 않습니다.

## 2026-11-13 · 기존 시역 UI의 SDK·관광 저장 연결

**구현 완료 / Debug·Release 빌드 확인 / JVM 44개·기기 UI 3개 통과 / Lint 오류 0개.** 기존 `release/v0.1.0`의 `com.team.carescape` 화면·시역 이야기·이미지·폰트를 유지하고, [요구 문서](ROAD_ESCAPE_sdk.md)에 맞춰 SDK 연결·설정 UI와 관광 15분·영구 저장을 추가했습니다. 문서는 `main`에서 원본 그대로 복원했으며 상세 API·제약·공유 경계는 [SDK 연결 안내](sdk-ui-integration.md)에 기록했습니다. 아래 2026-11-12 SIM 기록은 이전 구현 이력입니다.

| 검증 | 실제 결과 |
| --- | --- |
| 환경 | Windows, JBR 25, Gradle 9.1.0, AGP 9.0.1, Kotlin 2.2.21, compile/target/min SDK 36/34/28 |
| 최종 빌드 | `assembleDebug assembleRelease testDebugUnitTest assembleDebugAndroidTest lintDebug`, 오프라인 기존 캐시 사용, `sdk-candidate-final-build.log`에서 41초 성공 |
| 실제 프로젝트 재검증 | 검증된 소스를 `C:\car_escape`의 `release/v0.1.0`에 적용한 뒤 Debug·Release·JVM·Lint를 다시 실행, `project-build.log`에서 1분 27초 성공 |
| JVM | Content 6·PuzzleEngine 11·StoryEngine 8·TourPolicy 11·TripProgressStore 8, 총 44개. 실패·오류·건너뜀 0 |
| 기기 UI | Pleos API 34 `emulator-5554`, 사용자 10. `TourLifecycleTest` 3개, `OK (3 tests)`, 63.049초. 실패·오류·건너뜀 0 |
| UI 검증 내용 | 관광 시작 시각 보존·15분 전 차단·Activity 재생성 후 새 P와 위치 필요·MOCK 15분 시연 후 단서 진행; DRIVING/실제 stop-resume 중 화면·명령·음성 차단; LIVE 모의 입력 무시·MOCK/LIVE 기록 분리 |
| Lint | 오류 0·경고 8·힌트 3. baseline이나 오류 무시 설정을 추가하지 않음 |
| 실제 SDK 상태 화면 | 권한 변경 없이 NaviHelper 서비스 연결 준비·Vehicle 보조 표본 수신 확인. FusedLocation과 AAOS 안전은 권한 필요, 음성은 안전 상태 미확인으로 연결 전 |
| 원본 데이터 | 테스트 직전 기존 앱 파일 1개와 APK를 새로 백업. 테스트 종료 후 원본 SHA-256 일치 복원, 생성된 관광 기록 파일만 제거 |

기기 테스트는 설치 APK·테스트 APK의 SHA-256이 빌드 산출물과 같은지 확인한 뒤 다음 명령으로 실행했습니다.

```text
adb -s emulator-5554 shell am instrument --user 10 -w -r -e class com.team.carescape.ui.TourLifecycleTest com.team.carescape.test/androidx.test.runner.AndroidJUnitRunner
```

이번 구현에서 원시 위치·안전 표본·음성 원문은 영구 저장하지 않습니다. `trip-progress.json`만 모드별로 원자 저장하고, 손상·미지원 버전을 빈 기록으로 자동 덮어쓰지 않습니다. 시작 시각은 중복 버튼으로 초기화하지 않습니다. foreground 복귀와 모드 전환에서 안전 상태를 다시 반영하며, background에서는 SDK와 화면 갱신 타이머를 중단합니다.

SDK 어댑터는 기존 `main`의 검증된 API 경계를 현재 패키지로 이식했습니다. `LocationData.confidence`의 미터 정확도 계약을 확인하지 못해 **실제 LIVE 도착 인증은 보류**합니다. 권한·SDK READY 표시, MOCK 성공을 실제 방문·실차·가청 음성·길 안내 완료로 설명하지 않습니다. 이번 기기 UI는 대표 1챕터와 모드 전환을 확인했으며, 5개 챕터 전체 UI·실제 관광·시동 OFF/ON·Ubuntu 실행은 미확인입니다.

초기 컴파일의 foreground setter/메서드 JVM 이름 충돌과 개인 `local.properties`의 드라이브 문자 이스케이프 Lint 오류를 수정했습니다. 제한 환경의 Gradle 네이티브 실행 차단·권한 검토 시간 초과는 코드 실패와 구분했고, 승인 후 기존 캐시의 오프라인 Gradle로 검증했습니다. 로그·APK·앱 데이터·소스 원본 백업은 Git에서 제외한 로컬 검증 작업 영역에 보관합니다. 사용자 기기에서 UI 테스트를 직접 실행하기 전에는 별도 백업이 필요합니다.

## 2026-11-12 · Escape Road 화면 1차 에뮬레이터 확인

- 환경: Windows 11, Android Studio 내장 JDK(jbr), AVD `Pleos_Connect_v2.1.0` (2560×1440, 160dpi)
- 빌드: `./gradlew :app:testDebugUnitTest :app:assembleDebug` 성공 · 단위 테스트 17개 통과
- 차량 입력: **시뮬레이션 데이터만 사용** (화면 오른쪽 위 `SIM` 패널). Pleos Vehicle SDK 실제 신호는 연결하지 않음.

| 확인 항목 | 결과 | 스크린샷 |
| --- | --- | --- |
| 로비 → 테마 선택 → 브리핑 | 동작 | `screenshots/01~03` |
| 퍼즐 1(숫자 자물쇠): 전조등 켜기 전 단서 가림 → SIM 전조등 켜짐 → 단서 드러남 → 2341 입력 → 해금 | 동작 | `04`, `05` |
| D 주행 전환 시 모든 화면을 '주행 중' 화면으로 덮음, P 복귀 시 퍼즐로 돌아옴 | 동작 | `06` |
| 퍼즐 2(객관식): 힌트 1개 펼침, 오답 반응, 정답 → 해금 | 동작 | `07` |
| 탈출 성공(별점·힌트 수·소요 시간) | 동작 | `08` |
| 음성 단서(Android 기본 TextToSpeech) | **실패**: "음성 엔진을 쓸 수 없습니다" 표시. 에뮬레이터에 한국어 TTS 엔진이 준비되지 않은 것으로 보임 | `screenshots`에 없음 |

## 알려진 차이·한계

- 벽지·양피지 질감 이미지는 아직 넣지 않았다. 한글 글꼴은 아래처럼 나눔명조를 넣었고, 시안의 영문 장식 글꼴(Pinyon Script·Cormorant 등)은 넣지 않았다.
- 부팅 직후 내비게이션 앱이 앞으로 올라와 앱을 가리는 경우가 있다. 다시 실행하면 돌아온다.
- 앱 창 크기에 맞춰 시안 기준 1280×720을 비율대로 확대한다(`EscapeApp`의 밀도 조정).

## 2026-11-12 · 나눔명조 글꼴 포함

- 출처: Google Fonts 저장소 `google/fonts` `ofl/nanummyeongjo` (Regular·Bold·ExtraBold, 각 약 3MB)
- 라이선스: SIL Open Font License 1.1 · Copyright (c) 2010 NHN Corporation · 예약 글꼴 이름 Nanum/NanumMyeongjo 등
- 원본 파일을 수정하지 않고 `app/src/main/res/font/`에 넣었다(파일 이름만 Android 리소스 규칙에 맞춰 소문자로 변경).
- 라이선스 원문을 APK 안(`app/src/main/assets/licenses/NanumMyeongjo-OFL.txt`)과 저장소(`docs/licenses/`)에 함께 둔다.
- 금지 사항: 글꼴 파일 단독 판매, 다른 라이선스로 재배포. 수정본을 배포하려면 이름을 바꿔야 한다.

## 2026-11-12 · 메인 테마 「시역(屍疫)」 스토리 적용

- 팀 결정: 시연 시나리오에서 **이야기만** 옮기고 장소 퀴즈·퍼즐·힌트·위협 게이지·최종 추리 입력·약병 숫자는 넣지 않았다. 퍼즐 자리는 비워 둔다.
- 테마 선택: 시역만 플레이 가능. CASE 001·꼬마 자동차 탐정단·THE LOST ROUTE는 카드만 보이고 고를 수 없다(콘텐츠와 테스트는 보존).
- 흐름: 코스 카드 → 브리핑(프롤로그·세계관 규칙) → [다음 목적지 → 주행 중 → 도착(P단) → 둘러보기 → 단서·재료] × 4 → 단서 네 장으로 마지막 장소 공개 → 광교호수공원 도착 → 엔딩(허구 고지 포함)
- 도착 판정: 안내 시작 뒤 실제로 움직였다가(정차 아님) P단 정차가 확인되면 도착. 시연용 "도착한 것으로 하기" 버튼은 정차 중에만 동작.
- 이동 중 음성 드라마는 음성으로만 읽도록 했으나 이 에뮬레이터에는 음성 엔진이 없어 소리는 확인하지 못했다.
- 확인: 빌드 성공 · 단위 테스트 24개 통과(StoryEngineTest 7개 추가) · 에뮬레이터에서 처음부터 엔딩까지 진행(SIM 시뮬레이션 데이터). 스크린샷 `screenshots/siyeok/01~10`.

## 2026-11-14 · 시역 정식 퍼즐판(flow 3) 구현

- 내용: 장소별 퍼즐 단계(총 30단계), 사건 기록부(문서 14·인물 6), 힌트 비용·함정에 따른 시귀 각성도, 진상 추리, 진·일반·배드 엔딩. 설계와 정답은 [시역 정식 퍼즐판](story-puzzles.md).
- 저장: 새 여행은 flow 3. v0.4.0 임시 자물쇠 여행(flow 2)과 v0.2 이관 기록은 그대로 읽고 이어 간다. 단계·힌트·각성도는 뒤로 갈 수 없게 검증한다.
- 확인한 것
  - Debug·Release 빌드 성공, Lint 오류 0개(경고 19).
  - 단위 테스트 80개 통과(새 `SiyeokCaseTest` 9개: 전 단계 정답·진/일반/배드 엔딩·함정 각성도·문서 해금·저장 검증).
  - 기기 테스트 `StoryIntegrationTest` 3개 통과(Pleos Connect v2.1.0 AVD, MOCK): 첫 단계 오답·힌트 저장 → 재생성 후 위치 재확인 → 단계 진행, 다섯 장소 전 단계 → 진 엔딩 화면.
  - 에뮬레이터 직접 조작(MOCK): 화성행궁 5단계 완료·사건 기록부·화홍문 판문 화면. 스크린샷 `screenshots/case-flow/01~05`.
- 확인하지 못한 것
  - 와이퍼·비상등·깜빡이·전조등·문·창문 실제 신호 연동(화면 버튼으로 대체 입력).
  - 오디오 증언의 실제 음성 출력(에뮬레이터 TTS 미확인), 현장 관찰 문항의 실제 현장 대조.
  - 일반 엔딩 후 진상 추리 재도전 기능은 없음.
- 알려진 점: MOCK 위치 표본은 30초 뒤 만료되는 기존 정책이라, 시연 중 단계를 오래 풀면 퍼즐 화면이 가려진다. SIM 패널의 「현재 장소 위치 시연」을 다시 누르면 돌아온다.

## 2026-11-14 · 시역 인물과 대사

- 내용: 다섯 장소의 이동 중·도착·단서 해금 장면을 인물 대사로 바꿨다(서도윤·연이·한철·박 서방·김씨·학예사·조사국). 퍼즐 단계마다 말하는 인물 표시, 사건 기록부 관계도와 단계별로 늘어나는 인물 기록 추가. 기획과 어긋난 옛 문구(쇠살문, 시간 순서 조각, ‘물길의 근원’, ‘광교산이 물의 머리’)를 정리했다. 도착 화면은 대사만 스크롤하고 ‘관광 시작’ 버튼은 늘 화면 안에 둔다.
- 확인한 것
  - 단위 테스트 82개 통과(새 2개: 모든 대사·단계 화자가 등록된 인물인지, 도착 시 인물 등장과 진상 후 관계 변화).
  - 에뮬레이터 직접 조작(MOCK): 화성행궁 도착 대사, 단계 화자 표시, 기록부 관계도. 스크린샷 `screenshots/case-flow/06~08`.
- 확인하지 못한 것: 대사의 실제 음성 출력, 인물 초상 그림(지금은 이름 첫 글자 인장).

## 2026-11-14 · 장면(드라마)·소지품·앱 안 자물쇠 (flow 4)

- 내용: 프롤로그(17줄)·도착·단서 해금·엔딩을 배경 그림과 인물 초상이 있는 장면으로 한 줄씩 넘긴다(배경 8종, 초상 7종은 코드로 그린 실루엣). 새 여행은 flow 4: 차량 조작은 판문(와이퍼·비상등)과 창문 연출만 남기고, 물길·봉화는 화면 누르기로 바꿨다. 소지품 11개, 소지품 쓰기 3단계, 십이지 시각 다이얼 1단계를 넣었다. flow 3 저장 기록은 그대로 읽는다.
- 확인한 것
  - 단위 테스트 90개 통과(새 `SiyeokFlow4Test` 8개: 차량 단계 2개만 남음, 전 단계 정답·힌트, 소지품 획득·사용, 오답 대사, 서랍 문서 해금, 장면 배경·화자, 저장 검증).
  - 에뮬레이터 직접 조작(MOCK): 프롤로그 장면, 화성행궁 도착 장면, 소지품 탭, 수문지기의 함(亥·子), 잠긴 서랍(서고 열쇠패 乙). 스크린샷 `screenshots/case-flow/09~14`.
  - 도착·단서 화면에서 주 버튼이 화면 밖으로 밀리던 문제를 고쳤다(글만 스크롤).
- 확인하지 못한 것: 장면 대사의 실제 음성 출력, 초상·배경의 전문 일러스트 교체.

## 2026-11-14 · 소지품 그림과 자세히 보기

- 내용: 소지품 11개를 물건 모양 그림으로 바꿨다(선장본 수첩, 밀랍 봉인 밀서, 찢긴 비방, 붉은 술 달린 나무 열쇠패, 청포 보자기, 청자 호리병, 곡식 자루, 부싯돌과 부시, 이어 붙인 소식지, 감초 다발, 청화 백자 약병). 사건 기록부 소지품 탭에서 누르면 크게 보이고, 수첩은 펼친 책장(두 쪽씩 넘김), 밀서·비방·소식지는 펼친 종이로 읽는다. 새 소지품을 얻으면 ‘New Item’ 창이 뜬다.
- 확인한 것: 단위 테스트 90개 통과. 에뮬레이터(MOCK)에서 소지품 탭 그림, 수첩 펼쳐 보기, 청포 보자기·흐르는 물 한 병 획득 창. 스크린샷 `screenshots/case-flow/15~17`.
- 확인하지 못한 것: 곡식 자루·부싯돌·소식지·감초·백신 그림은 화면으로 직접 보지 않았다(같은 그리기 코드, 빌드 통과).

## 2026-11-14 · 화성행궁 탐색 장면 (flow 5 시험판)

- 내용: 화성행궁을 퀴즈 목록 대신 장면을 직접 뒤지는 방식으로 바꿨다. 조사할 곳 8곳(기록 2, 자물쇠 3, 살펴보기 3), 숨은 곳 2곳, 순서 없는 자물쇠, 자물쇠별 힌트, 장면 힌트. 저장 기록에 푼 자물쇠·찾은 곳·자물쇠별 힌트를 남기고 되돌림을 막는다.
- 확인한 것: 단위 테스트 97개 통과(새 `SiyeokFlow5Test` 7개). 에뮬레이터(MOCK)에서 사발 → 담장 수첩 등장, 등불 → 일지 1장, 현판 壽 → 현판 뒤 봉투 → 水, 수첩 0213 → 소지품 창 → 단서 장면까지 직접 플레이. 스크린샷 `screenshots/case-flow/18~19`.
- 확인하지 못한 것: 나머지 네 장소는 아직 퀴즈 방식이다. 장면 그림은 코드로 그린 배경이라 조사할 곳이 잘 안 보일 수 있다.

## 2026-11-14 · 다섯 장소 모두 탐색 장면 (flow 5)

- 내용: 화홍문·만석거·수원광교박물관·광교호수공원도 탐색 장면으로 바꿨다. 확인용 퀴즈(오디오 증언, 두 기록 대조, 붉은 먹점, 물길 그림)는 빼고 장면과 문서로 옮겼다. 한 물건에 자물쇠 둘을 차례로 거는 방식(봉화함, 서도윤의 진상 추리), 장면 속 진상 추리(틀려도 진행, 일반 엔딩), 장면 소품 9종(수문 함, 궤짝, 물레, 비석, 지도, 봉화함, 약탕기, 안내판, 약병 선반)을 넣었다. 인물 초상 7장을 일러스트로 바꿨다.
- 확인한 것
  - 단위 테스트 98개 통과(`SiyeokFlow5Test`: 모든 자물쇠가 조사할 곳 하나에 걸림, 숨은 곳 조건, 문서·소지품 출처, 진상 추리 오답 → 일반 엔딩, 저장 검증·되돌림 거부).
  - 기기 테스트 4개 통과(Pleos Connect AVD, MOCK): `StoryIntegrationTest` 3개(다섯 장소 장면을 모두 풀어 진 엔딩까지, 정차·위치 조건), `ExploreSceneCaptureTest`(장면 배치 캡처). 캡처는 `screenshots/case-flow/20~23`.
- 확인하지 못한 것: 화홍문 이후 장면은 사람이 직접 눌러 플레이하지 않았다(기기 테스트와 캡처로 확인). 장면 배경은 아직 코드로 그린 그림이다.

## 2026-11-14 · 장면 배경 일러스트 적용

- 내용: 장면 배경 8장(조사국, 시간 이동, 행궁 해 질 녘·밤, 화홍문, 만석거, 박물관 수장고, 호수공원)을 `res/drawable-nodpi/scene_*.webp`로 넣었다. 조사할 곳 좌표를 새 그림에 맞춰 다시 잡았고, 그림이 틀에 그대로 맞도록 배경을 늘여 채운다(잘라 내지 않음). 그림에 이미 있는 물건(벽 지도, 수장고 궤짝)은 소품을 덧그리지 않고, 호수에는 산책로 끝 그림자 소품을 넣었다.
- 확인한 것: 단위 테스트 98개, 기기 테스트 4개 통과. 캡처로 다섯 장면의 조사할 곳 위치 확인(`screenshots/case-flow/24~28`).
- 화홍문 그림을 홍예 일곱 칸으로 다시 받아 교체하고 조사할 곳(첫째 홍예, 가운데 넷째 칸 앞 도르래, 성벽 위 함·종이, 성문 초소, 오른쪽 둑 궤짝)을 다시 맞췄다. 단위 98개·기기 4개 테스트 통과.

## 2026-11-14 · 찍어 풀기 막기, 일러스트 자리, 첫 장면 도움말

- 찍어 풀기: flow5부터 장소마다 첫 오답만 무료, 그다음 오답마다 각성도 +1. 보기가 적던 자물쇠를 바꿨다 — 영화정은 한자 바퀴 셋(迎·華·亭, 소리가 같은 글자 섞음), 약병은 번호 자물쇠 4172, 호수는 보기 5개, 수장고 색인은 보기 5개(乙/丙 칸·이름이 비슷한 미끼). 테스트: 장면 자물쇠의 보기는 4개 이상, 바퀴의 처음 위치는 정답이 아님.
- 소지품·소품 그림: `item_<id>` 그림 파일이 있으면 코드 그림 대신 쓴다. 사양과 생성용 문구는 [소지품·소품 사양](art/props.md).
- 첫 장면 도움말: 화성행궁 탐색 장면에 처음 들어오면 살펴보기·자물쇠·기록부와 소지품·막혔을 때를 알려 주는 창이 뜬다. 장면 위쪽 ? 버튼으로 다시 연다.
- 확인한 것: 단위 테스트 99개, 기기 테스트 `StoryIntegrationTest` 3개 통과. 에뮬레이터에서 도움말 창 확인.
- 확인하지 못한 것: 영화정 현판의 실제 한자 표기(迎華亭) 현장 확인, 세 바퀴 다이얼 화면(계산상 입력 칸 안에 들어감).

## 2026-11-14 · 소지품·장면 소품 일러스트 22종 적용

- 내용: [소지품·소품 사양](art/props.md)의 22개 자리에 일러스트를 넣었다(`res/drawable-nodpi/item_*.webp`, 투명 배경, 긴 변 512px로 잘라 줄임). 코드는 바꾸지 않았다.
- 확인한 것: 단위 테스트 통과. 기기 테스트 `ExploreSceneCaptureTest` 통과(Pleos Connect AVD), 다섯 장면 모두 소품이 조사할 곳 안에 그려짐.
- 박물관 봉화함은 서가 앞 허공에 떠 보여 사다리 옆 바닥(약탕기 왼쪽)으로 옮겼다. 캡처로 확인.
- 약병 선반 그림의 병 번호를 1~4에서 퍼즐 번호(2714·4127·4172·1724)로 바꿨다.

## 2026-11-14 · 장면 소품을 배경에 맞춤

- 장면 소품을 장면 조명 색으로 보정(장면별 색 곱하기·채도 낮춤, `ExploreScreen.kt` `sceneLight`)하고, 바닥 그림자를 깔고, 조사할 곳 상자 바닥에 붙여 그린다.
- 벽면·물 위에 떠 있던 소품을 실제 면 위로 옮겼다: 만석거 비석(둑 위), 수문 물레(둑 끝 물가), 화홍문 도르래(가운데 넷째 홍예 아래 물가)·궤짝(오른쪽 둑), 호수 안내판·약병 선반(산책로)·그림자(산책로 끝), 행궁 사발·수첩 크기 조정.
- 확인: 단위 테스트, `ExploreSceneCaptureTest` 통과. 다섯 장면 캡처로 위치 확인.

## 2026-11-14 · 엔딩을 드라마 장면으로

- 내용: 진·일반·배드 엔딩을 인물 대사와 장소 이동이 있는 드라마로 바꿨다(`SiyeokScenes.endingDrama`). 원천호수에서 약을 붓는 도입 → 화홍문(한철·박 서방) → 만석거(김씨) → 행궁(연이·서도윤) → 시간 이동 → 2026년 수장고(학예사) → 조사국 순서로, 엔딩마다 대사와 결말이 다르다. 마지막 줄은 화면 가운데 큰 제목 카드(The End · 엔딩 이름)로 보여 준다. 끝나면 기존 기록 화면(수사 시간·힌트·각성도)으로 넘어간다. flow 4 이상에 적용.
- 확인: 단위 테스트(새 `EndingDramaTest`: 세 엔딩의 시작·끝·장소 수, 화자 확인), 기기 캡처 테스트 `EndingSceneCaptureTest` 통과. 캡처 `screenshots/case-flow/29~32`.
- 확인하지 못한 것: 대사의 실제 음성 출력.

## 2026-11-15 · 여행 도우미(부가 서비스) 1차

- 내용: 로비에 「여행 도우미」 추가. 여행 정보 입력(일정·날짜·동행·관심사·식사, 별도 파일 저장) → 날짜별 일정(식사·숙소 자리, 휴관 경고) → 경유지별 탭(주차·맛집·카페·함께 보기·체험·숙소/야경) → 자세히·길 안내. 마지막 장소는 방탈출 공개 전 잠금(스포일러 보기 선택 가능). 네이버 지역 검색·TourAPI 클라이언트와 키 설정(`local.properties`, 저장소 미포함). 자세한 내용은 [여행 도우미](travel-guide.md).
- 확인: 단위 테스트 통과(새 `TravelGuideTest` 9개: 일정 나누기·숙소 자리, 스포일러 잠금과 저녁 위치, 날짜·휴관 경고, 탭 구성, 네이버·TourAPI 예시 응답 파싱, 키 없을 때 네트워크 미호출, 출처 합치기·거리순). 에뮬레이터(MOCK)에서 입력 화면 → 1박 2일 일정 → 탭 → ‘키 없음’ 안내, 마지막 장소 잠금 화면 확인.
- 확인하지 못한 것: 실제 API 응답(키 없음), 길 안내 실제 요청(시연 모드), 네이버·TourAPI 이용 조건.

## 2026-11-15 · 여행 도우미 화면 개편

- 내용: 여행 앱 구성을 참고해 다시 짰다 — 왼쪽 그림 코스 지도(경유지·그날 경로·추천 핀, 주변 확대 전환), 오른쪽 시간대 타임라인(이동 시간 어림, 식사·숙소 빈 칸 채우기), 아이콘 카테고리 칩, 담기·저장 화면, 둘러보기 화면. 담은 곳은 기기에만 저장. debug 전용 예시 데이터 버튼(실제 장소 아님 표시). 자세한 내용은 [여행 도우미](travel-guide.md).
- 확인: 단위 테스트 통과(새 `TravelPlanTest` 5개: 타임라인 시각·이동 시간, 아침 있는 날, 칸 이름, 담기·비우기·빼기, 예시 데이터 표시). 에뮬레이터(MOCK, 예시 데이터)에서 지도 핀·주변 확대, 점심 칸 채우기, 저장 목록 확인.
- 확인하지 못한 것: 실제 API 데이터로 본 화면(키 없음).

## 2026-11-15 · 진상 다시 추리하기, 사건 종결 보고서

- 진상 다시 추리하기: 일반 엔딩(진상 추리를 틀림, 각성도 8 미만)에서 엔딩 화면의 「진상 다시 추리하기 (1회)」로 두 문항을 다시 푼다. 한 번뿐이고 결과는 저장 후 바꿀 수 없다. 맞히면 진 엔딩으로 바뀌어 진 엔딩 드라마가 다시 재생된다. 각성도·오답 수는 바뀌지 않는다. 배드 엔딩은 대상이 아니다. 고른 답은 사건 기록부를 다녀와도 남는다.
- 사건 종결 보고서: 엔딩 화면의 「사건 종결 보고서」. 사건 이름, 날짜, 수사 시간(엔딩에 닿은 시각까지 · `endedAtEpochMillis` 새로 저장), 힌트·오답·각성도, 경유지별 관광 시작 시각과 단서, 모은 소지품·기록 수, 엔딩 문구 한 줄과 도장. 「사진으로 저장」은 사진첩 `Pictures/EscapeRoad`에 PNG로 저장(Android 10 이상).
- 엔딩 화면의 누적 수사 시간이 계속 늘어나던 문제를 엔딩 시각에서 멈추도록 고쳤다.
- 확인: 단위 테스트(새 `TruthRetryTest` 4개 · `ReportLineTest` 1개: 맞히면 진 엔딩·한 번만·틀리면 일반 유지·배드 불가·저장 검증과 되돌림 거부·엔딩 시각 검증), 기기 테스트 `StoryIntegrationTest` 3개, `CaseReportCaptureTest`(보고서 캡처 `screenshots/case-flow/33`).
- 에뮬레이터 직접 확인(MOCK, 아래 시연 바로가기 사용): 일반 엔딩 → 진상 다시 추리(정답) → 진 엔딩 드라마 → 보고서 「진상 다시 추리 성공」, 「사진으로 저장」으로 `Pictures/EscapeRoad`에 PNG 저장·이미지 확인. 다시 추리·보고서 버튼은 긴 엔딩 본문에 묻히지 않게 도장 아래로 옮겼다.

## 2026-11-15 · 시연 바로가기(debug·MOCK 전용)

- 내용: SIM 패널에 「시연 바로가기」 — 도착 1~5, 장면 1~5, 공개(마지막 장소), 엔딩 진·일반·배드. 실제 엔진으로 앞부분을 정답으로 자동 진행한 **새 시연 여행**으로 바꾼다(기존 시연 기록은 대체, LIVE 기록은 건드리지 않음). 관광 15분은 시연 우회로 표시하고, 지난 장소 관광 시각은 75분 간격으로 앞당겨 적는다. 일반 엔딩은 진상 추리 하나를 틀리고, 배드 엔딩은 주변 살피기를 많이 써서 각성도 8 이상으로 만든다. release 빌드에는 없다(`src/debug`).
- 확인: 단위 테스트(새 `DemoShortcutTest` 2개: 모든 바로가기가 말한 곳에 닿고 저장 검증 통과, 일반 엔딩은 다시 추리 가능·배드는 각성도 8 이상). 에뮬레이터에서 일반·진 엔딩 바로가기 사용.


## 2026-11-14 · 현재 release/v0.4.0에 네이버 Maps ID 연결

- 구현 완료: 사용자가 저장한 `nearby.properies.env`의 `naver.map.keyId`를 빌드 설정에서 읽고, 네이버 Maps Android SDK 3.24.1의 `NCP_KEY_ID`에 연결했다. 로컬 파일은 Git에서 제외하며 인증 값은 기록하지 않는다. 기존 Canvas 코스 지도를 실제 MapView로 교체하고 경유지/추천 장소 핀·방문 순서 연결선·주변 확대/전체 보기·카드 선택을 연결했다. 비공개 마지막 장소는 지도 입력에서 제외한다. 연결선은 실제 도로 경로가 아니다.
- 빌드 확인: 관리형 `gradle-run`의 질문 “네이버 Maps ID 연결 후 Debug 빌드·여행 단위 테스트·Lint가 통과하는가?”에 `:app:assembleDebug :app:testDebugUnitTest --tests com.team.carescape.travel.* :app:lintDebug` exit 0. 여행 단위 테스트 14개 통과. 기기에서 발견한 SDK 위젯의 AppCompat 테마 오류는 MapView에만 테마 Context를 적용해 수정했다. 후속 질문 “지도 SDK 위젯의 AppCompat 테마 보완 후 Debug 빌드와 Lint가 통과하는가?”의 `:app:assembleDebug :app:lintDebug`도 exit 0. Lint 오류 0개, 경고 61개와 Hint 4개는 남아 있으며 전체 경고 해결을 주장하지 않는다. MapView 메모리 콜백의 deprecated 경고도 남는다.
- 에뮬레이터 시연 확인: Windows / Android Studio JBR / Pleos Connect v2.1.0(API 34), emulator-5554, Android 사용자 10. 현재 앱은 MOCK의 명시적 P 정차 상태에서 확인했다. 실제 네이버 지도 타일·화성행궁 핀을 확인했고, 전체 보기·화홍문 선택·D 주행 시 지도 숨김·P 복귀 및 백그라운드 복귀 후 지도 재생성을 확인했다. 설치와 화면 검증 전후 방탈출 저장 JSON이 같았다. 기기 테스트 실행이라고 표기하지 않으며 ADB를 통한 수동 UI 시연이다.
- 네트워크 복구: 초기에는 `Active default network: none`, Wi-Fi 꺼짐으로 네이버 지도 인증이 DNS 대기 상태였다. `adb -s emulator-5554 shell cmd wifi set-wifi-enabled enabled`로 저장된 AndroidWifi 연결을 복구했고 네이버 서버 DNS와 지도 표시가 정상화됐다. 에뮬레이터 초기화·데이터 삭제는 하지 않았다. 수정 APK 프로세스에서 ThemeUtils 테마 오류·FATAL EXCEPTION·Authorization pending은 0건이었다.
- 미연결/확인 불가: 지역 검색용 인증과 TourAPI 키는 없고 `nearby.proxy.url`도 비어 있다. 따라서 실제 주차장·식당 검색 응답이나 내비게이션 경로 성공을 확인한 것은 아니다. Maps ID만으로 지역 검색 인증이 완료됐다고 표시하지 않는다. LIVE 차량 안전 조건과 위치 정확도 검증은 이 작업에 포함하지 않았다.
- 재현: Android Studio에서 `C:/car_escape` 열기 → Gradle Sync → app Run → 시연 MOCK / P 정차 → 로비의 여행 도우미. 지도 ID 변경 후에는 다시 빌드·Run한다. 사용법은 [여행 도우미](travel-guide.md)를 따른다.
- 로컬 증거: `C:/my-car-buddy/.gradle/car-escape-maps-20261114/validation-summary.json`, `device-result.json`, `map-final-loaded.png`, `map-final-route.png`, `map-final-selected.png`, `map-final-resumed.png`. 원시 앱 저장 백업은 로컬에만 보관한다.


## 2026-11-14 · NAVER API HUB 검색 설정 지원

- 구현 완료: 사용자가 발급받은 새 지역 검색 인증을 `nearby.properies.env`의 `naver.search.clientId`, `naver.search.clientSecret`에서 읽는다. 기존 Maps ID는 별도로 유지한다. API HUB 요청 URL과 X-NCP 인증 헤더를 적용하고 기존 장소 카드·지도 핀 파싱을 재사용한다. HUB 값이 하나라도 있으면 HUB를 선택하며 한 항목이 비어 있으면 네트워크를 호출하지 않고 누락 항목을 안내한다. HUB 설정이 없을 때 기존 Developers 인증은 유지한다. 예시 파일과 화면/사용법의 항목 이름을 맞췄다.
- 빌드 확인: 관리형 gradle-run의 질문 “API HUB 연결 코드의 Debug 빌드·여행 테스트·Lint가 통과하는가?”에 `:app:assembleDebug :app:testDebugUnitTest --tests com.team.carescape.travel.* :app:lintDebug :app:generateReleaseBuildConfig` exit 0. 여행 테스트 18개(신규 4개 포함) 통과, Lint 오류 0개. 신규 테스트는 HUB 우선 URL·헤더/검색어, Developers 호환, 불완전한 HUB 인증의 양쪽 API 호출 차단, 실제 카드 형태로의 예시 응답 파싱을 검증한다.
- 배포 경계: 이번 신규 HUB 필드는 로컬 Debug 시연에서만 사용한다. 생성된 Release BuildConfig의 신규 두 필드가 빈 값임을 확인했다. 기존 Developers/TourAPI 키 처리 전체를 변경한 것은 아니다. 배포용 중계 서버 연결은 후속 작업이며 현재 직접 호출은 `nearby.proxy.url`을 사용하지 않는다. 인증 값은 소스·문서·검증 요약에 기록하지 않았다.
- 확인 불가: 실제 API HUB 응답 성공은 미검증이다. 테스트 응답은 fixture이며 네이버 실시간 검색 성공으로 설명하지 않는다. 이 변경의 APK는 빌드만 했으며 에뮬레이터에 재설치하지 않았다. 실제 인증 저장 후 Android Studio에서 app의 debug 빌드로 Run하고 여행 도우미의 주차/맛집을 확인한다. 기존 지도 표시와 안전 제한의 기기 시연은 위 Maps 연결 기록을 따르며 이번 검색 변경의 기기 검증으로 대체하지 않는다.
- 사용법: [여행 도우미](travel-guide.md). 로컬 증거: `C:/my-car-buddy/.gradle/car-escape-hub-20261114/validation-summary.json`.


## 2026-11-14 · 실제 NAVER API HUB 검색 에뮬레이터 테스트

- 설정·빌드 확인: 사용자가 `nearby.properies.env`에 검색 인증 두 항목을 저장한 것을 값 출력 없이 확인했다. 관리형 gradle-run의 질문 “저장한 API HUB 인증으로 테스트용 Debug APK를 빌드할 수 있는가?”에 `:app:assembleDebug` exit 0(13.288초). 생성된 Debug 인증 항목이 채워졌음을 확인하고 APK를 업데이트 설치했다. 소스가 이전 성공 결과와 같아 여행 단위 테스트 18개·Lint 오류 0개 결과는 재사용했으며 이번에 다시 실행했다고 표시하지 않는다.
- 에뮬레이터 시연 확인: Windows / Pleos Connect v2.1.0(API 34), emulator-5554 / Android 사용자 10. 차량 입력은 MOCK의 명시적 P 정차, 검색은 실제 NAVER API HUB 응답을 사용했다. 예시 데이터 버튼은 누르지 않았다. 화성행궁에서 둘러보기 → 주차·맛집 각각 결과 5건, 첫 카드의 주소·출처·좌표/길 안내 버튼과 네이버 지도 핀을 확인했다. 길 안내 버튼은 실행하지 않았다.
- 검색 결과 예: 주차 탭의 화성행궁노상공영주차장·화성행궁 주차장·행리단주차장, 맛집 탭의 행궁동 블레스브런치바·계미굴 행궁점·얌얌차. 이 이름은 당시 API 검색 결과이며 영업 여부나 주차 가능 대수를 확인한 것은 아니다. 주차 탭에는 전기차 충전소와 주차장 입구도 포함돼 실제 주차장 시설만 남기는 분류 필터가 후속 개선 사항이다.
- 안전·데이터 확인: 시연 D 주행 전환 시 지도/검색을 안전 화면으로 가리고 P로 돌아오면 다시 표시한다. 테스트 전후 방탈출 저장 JSON이 같았다. 앱 프로세스의 FATAL EXCEPTION과 SDK 위젯 테마 오류는 0건이었다. 인증 파일은 Git 제외 상태를 유지한다.
- 로컬 증거: `C:/my-car-buddy/.gradle/car-escape-hub-test-20261114/search-result.json`, `parking-selected.png`, `food-selected.png`, `search-final.png`, `managed-result.json`. 실제 인증 값과 원시 여행 저장 백업은 저장소에 올리지 않는다.


## 2026-11-14 · 여행 도우미 함께 보기 삭제

- 구현 완료: 일정·둘러보기 공통 카테고리 목록에서 함께 보기를 제거하고 역사 관심사의 해당 탭 우선 배치도 제거했다. 해당 분류의 신규 추천 요청은 빈 계획으로 중단했다. 기존 저장 장소의 아이콘/분류를 복원하는 SIGHT enum과 저장된 프로필의 HISTORY 관심사 값은 보존했다. 체험·숙소의 기존 TourAPI 연결은 이번 삭제 범위에 포함하지 않았다.
- 빌드 확인: 관리형 gradle-run의 질문 “함께 보기 삭제 후 Debug 빌드와 여행 테스트가 통과하는가?”에 `:app:assembleDebug :app:testDebugUnitTest --tests com.team.carescape.travel.*` exit 0, 여행 테스트 18개 통과. 새 단위 테스트는 추가하지 않았다.
- 에뮬레이터 시연 확인: Pleos Connect v2.1.0(API 34), emulator-5554 / 사용자 10. 업데이트 APK를 설치하고 MOCK/P 정차 상태에서 일정·둘러보기 양쪽의 함께 보기 탭 제거 및 나머지 주차·맛집·체험 탭 표시를 확인했다. 테스트 전후 방탈출 저장 JSON과 여행 프로필/저장 장소 SharedPreferences 원문이 같았다. 인증 값은 출력하거나 커밋하지 않았다.
- 로컬 증거: `C:/my-car-buddy/.gradle/car-escape-remove-sight-20261114/device-result.json`, `without-sight-schedule.png`, `without-sight-explore.png`, `managed-result.json`.


## 2026-11-14 · 창문·와이퍼 차량 퍼즐의 실제 SDK 연결 감사

- 판정: **차량 조작 SDK 입력 미연결**. 현재 flow5의 차량 문제는 화홍문 c2_gates(와이퍼 3회 → 비상등 → 와이퍼 3회)와 호수 c5_window(창문 내리기)다. 문·깜빡이·전조등 문제는 구형 flow3에 있었으며 현재 flow4/5의 물길·봉화는 앱 내 Sequence 입력이다.
- 코드 근거: `ui/CaseLocks.kt`의 CarSequenceLock은 ‘차량 신호 연동 전 · 화면 버튼으로 입력’으로 표시하고 화면 버튼의 onChange로만 답안을 만든다. `platform/sdk/PleosVehicleAssist.kt`는 Brake/EvBattery만 수집하며 창문·와이퍼·비상등 수집이 없다. `PleosSdkStatus`에도 해당 필드/이벤트가 없고 `ui/GameViewModel.kt`의 updateLiveInputs는 LIVE VehicleState에 parked/batteryPercent만 채운다. changeStoryCar와 StoryPuzzleDraft.car로 실제 SDK 조작을 연결하는 경로가 없다.
- 정답 판정 테스트: 관리형 gradle-run의 질문 “현재 스토리의 창문·와이퍼 문제 정답 판정 테스트가 통과하는가?”에 `:app:testDebugUnitTest --tests com.team.carescape.game.SiyeokFlow4Test --tests com.team.carescape.game.SiyeokFlow5Test` exit 0, 17개 통과. 답안 배열을 직접 넣는 순수 엔진 테스트이며 실제 콘솔 → SDK → 퍼즐 입력 → 제출의 성공 증거가 아니다. 기존 StoryIntegrationTest도 vm.changeStoryCar로 정답 배열을 주입한다.
- 에뮬레이터 관찰: Pleos Connect v2.1.0 / API 34 / emulator-5554 / 사용자 10. 기존 MOCK 상태에서 로비로 이동하여 LIVE 연결 상태 화면을 열었다. P_SPEED_CAR_UX_OBSERVED 및 OPTIONAL_READ_ONLY_SIGNALS의 연결 준비를 확인했다. 위치에는 UPDATED_LOCATION_ACCURACY_UNIT_UNVERIFIED가 표시된다. CAR_LIGHTS/CAR_DOORS/CAR_WINDOWS/CAR_SAFETY는 현재 설치 앱에서 granted=true였다. 따라서 이번 핵심 누락은 해당 신호를 수집하고 퍼즐 초안에 전달하는 앱 코드이며, 권한 선언만으로 구현됐다고 보지 않는다.
- 콘솔은 GET과 페이지 JS를 읽어 창문·와이퍼·비상등 제어 경로를 확인했다. 브라우저 세션에 따라 DDS 준비 상태가 바뀌는 것도 관측했다. 이번 감사에서는 차량 제어 PUT을 보내거나 LIVE 관광/위치/안전 게이트를 우회하지 않았다. 실제 SDK 차량 조작으로 두 퍼즐이 풀리는 종단 테스트는 구현 부재로 진행하지 못했다. 화면 버튼 판정 성공을 실제 SDK 성공으로 바꾸어 설명하지 않는다.
- 데이터 보존: 기존 여행 저장을 백업한 뒤 LIVE 상태를 읽었고, 원래 MOCK 모드로 복귀했다. 테스트 전후 저장 JSON이 같았다. 원래 완료 여행은 그대로 보존했다. 기능 코드 변경·APK 재설치·원격 푸시는 하지 않았다.
- 필요한 후속 구현: 지원·권한을 확인한 SDK 수신 → 최초 표본/중복/오래된 값과 실제 조작 변화 구분 → 활성 문제 입력 누적 → 사용자의 제출 버튼에서 정답 판정. P·정지·UX·관광/위치 조건은 유지해야 한다.
- 로컬 증거: `C:/my-car-buddy/.gradle/car-escape-sdk-puzzle-audit-20261114/live-sdk-status.json`, `live-sdk-status.png`, `managed-result.json`, `story-before.json`(원시 백업은 로컬 전용).


## 2026-11-14 · 유효 도착과 15분 경과 후 위치 노후화 재차단 완화

- 사용자 후속 정책 반영: 같은 앱 이용 중 현재 여행·챕터·모드에서 유효한 최신 도착 위치를 확인하고 관광 시작 후 15분을 충족하면, 위치 표본이 오래됐다는 이유만으로 다시 잠그지 않는다. 확인은 15분 이전에도 확보하므로 완료 순간 새 GPS 콜백을 강제하지 않는다. 이는 ‘도착 확인 후 관광 대기시간 충족’이며 그 장소에서 연속 15분 체류했다는 위치 증명이 아니다. 이 기록이 이전 명세의 매번 최신 위치 요구보다 우선한다.
- 구현: TourLocationConfirmation/TourVisitConfirmation을 ViewModel 소유 메모리 상태로 둔다. 세션·챕터·모드·원래 관광 시작 시각·목표점/반경에 묶는다. 복귀·조사·힌트·정답 제출의 저장 직전 검사도 동일한 공통 unlockTourGate를 사용한다. 관광 시작 시각이나 SDK 수신 타임스탬프를 새 값으로 꾸미지 않으며 원시 위치를 영속 저장하지 않는다. 기존 저장 스키마·완료 단서·보관 여행은 변경하지 않는다.
- 무효화: 확인된 차량 출발이나 새 반경 이탈, 유효하지 않은 위치/일반 SDK 오류, 백그라운드·모드 변경·앱 재실행에서는 위치를 다시 확인한다. 출발 직전의 아직 신선한 구표본으로 곧바로 재인증하지 않도록 수신 경계와 표본을 구분한다. 실제 출발 시각은 같은 READY CarSafetyReading에서 가져오고 미수신 초기 상태를 주행으로 만들지 않는다. P·정지·CarUx·시간 검증은 매번 유지한다.
- 실제 만료 경로: 어댑터의 15초 만료값을 TourPolicy.SDK_LOCATION_EXPIRY_MILLIS로 공유한다(기존 수치 유지). 일반 30초 노후화와 정확한 LOCATION_STALE 만료 오류를 구분해 처리하고, LOCATION_SDK_ERROR/NULL/INVALID까지 무시하지 않는다. SDK 상태창의 관광 완료 안내도 반영했다. 새 챕터 최초 인증에는 최신 위치·좌표·정확도 검사를 유지한다.
- 빌드 확인: 관리형 gradle-run 질문 “15분 이후 위치 노후화 완화가 빌드·단위 테스트·Lint를 통과하는가?”에 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` exit 0. JVM 단위 테스트 142개 통과, Lint 오류 0개. 기존 permission 보호 수준 관련 deprecated 경고는 남는다.
- 에뮬레이터 시연 확인: Pleos Connect v2.1.0 / API 34, emulator-5554 / 사용자 10에서 ADB instrumentation으로 StoryIntegrationTest 4개 통과. 신규 케이스는 MOCK 관광 시간 명시 우회 뒤 실제 SystemClock으로 30.25초 넘게 기다려 위치가 노후화된 상태에서 화면 정답 제출·저장 성공을 확인한다. P→D→P 뒤 새 위치 없이는 막히고 새 위치 입력으로 복구되는 것도 확인했다. 기존 5챕터 전체 진행·힌트/오답·중복 지급 방지·재생성/백그라운드 차단도 회귀 확인했다. 실제 LIVE 15분 관광이나 연속 위치 체류 검증으로 표시하지 않는다.
- 데이터 보존: 테스트 전 원본 여행·여행 도우미 설정을 백업했고 fixture 정리 뒤 원본 JSON 및 여행 설정 보존을 확인했다. 원래 완료 여행을 새 시연 여행으로 바꾸지 않았다. 앱을 다시 열고 명시적 MOCK P 상태로 돌려두었다.
- 남은 제한: 현재 LIVE SDK의 accuracyMeters는 미터 단위 근거가 확인되지 않아 null이다. 최초 위치 인증 보류는 그대로이며, 이번 변경만으로 실제 LIVE 방문 인증이 성공한 것은 아니다. 창문·와이퍼 SDK 퍼즐 입력 연결은 앞 감사에서 확인한 대로 별도 미구현 상태다.
- 로컬 증거: `C:/my-car-buddy/.gradle/car-escape-tour-stale-20261114/validation-summary.json`, `instrumentation-result.json`, `managed-result.json`, `updated-app-lobby.png`. 원시 사용자 저장 백업과 계측 로그는 로컬에만 보관한다.

## 2026-11-14 · 차량 문제 실제 SDK·콘솔 입력 / release/v0.4.0

**구현 완료 / Debug·단위·Lint 확인 / Pleos 에뮬레이터 확인.** Windows·Android Studio JBR·Pleos Connect v2.1.0/API 34·emulator-5554·Android 사용자 10에서 실행했다. Ubuntu·실차 하드웨어·전체 LIVE 위치 인증은 미확인이다.

- Vehicle 2.3.2의 Wiper/Window capability·초기 현재값·리스너를 실제 연결했다. `pleos.car.permission.CAR_WINDOWS`가 granted=true임을 확인했다. 와이퍼 OFF→ON과 ROW_1_LEFT 창문 0→양수를 각각 1회로 센다. 켜진 상태 반복·Low→High·창문 추가 이동은 입력을 늘리지 않는다. 앱은 차량 설정을 제어하지 않는다.
- 최초 신호는 기준선이다. foreground·문제·초기화·안전·신호 오류·기록부·화면 전환 경계에서 큐/세대를 검증하며 제출 직전에도 입력 세대와 초기화 여부를 재검사한다. SDK 모드는 화면 버튼으로 답안을 만들 수 없다.
- 첫 비상등 대안 검증에서 Android 표준 `CAR_EXTERIOR_LIGHTS` 권한 granted=false를 확인했다. 당시 SDK 테스트는 1개 통과·비상등 1개 건너뜀으로 구분했다. 이후 사용자 승인으로 **flow5만 WIPER×3→WINDOW→WIPER×3**로 바꿨다. 최신 정답·힌트·solution·오답 피드백·판문 색 표시를 함께 수정했고 사용하지 않는 AAOS 대안/특권 권한 선언은 제거했다. flow3·4의 기존 정답과 기존 해결 기록은 유지한다.
- 디버그 로비에 명시적 `차량 답안: 실제 SDK` 선택을 추가했다. 관광·시간·저장은 MOCK, P·실제 정지·CarUx·문제 입력은 SDK다. 자동 전환·영구 저장·LIVE 위치 검증 우회는 없다. 실제 안전 신호가 없으면 모의 P 입력으로 열 수 없다.

| 검증 | 결과 |
| --- | --- |
| 관리형 Gradle 질문 | “실제 차량 입력 연결과 순서 판정이 빌드·단위 테스트·Lint를 통과하는가?” → 통과. `gradle_with_jbr.py`/gradle-run 관리 래퍼, `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`, 최종 79.304초 exit 0 |
| JVM | 164개, 실패·오류·건너뜀 0. 새 입력 매퍼 20개와 창문 정답/구형 정답·함정 보존 2개 포함 |
| SDK 콘솔 | `PleosVehicleConsoleTest` 2개 통과·건너뜀 0. 실제 와이퍼 3회·창문 내림, 7개 순서 수집과 명시적 메모리 엔진 제출 정답 |
| 앱 스토리·저장 | `StoryIntegrationTest` 5개 통과·건너뜀 0. 기존 4개 회귀 + 실제 콘솔→실제 SDK 안전/조작→VM→화면 제출→DataStore의 c2_gates 해결 저장 |
| 입력 UI | `CarSequenceInputTest` 3개 통과·건너뜀 0. SDK 대체 버튼 숨김·미확인 제출 차단·제출 전 무판정·MOCK 보존 |
| Lint | 오류 0·경고 61. 권한 protectionLevel deprecation, 기존 의존성·Compose·리소스 등 경고는 잔존. 초기 새 테스트의 잘못된 assertDoesNotExist import는 제거 후 재검증 |
| 사용자 데이터·콘솔 | 원본 여행 JSON 의미 동일, 여행 안내 설정 바이트 동일, 초기 와이퍼 Off·앞왼쪽 창문 0·비상등 false 복원. 초기 원본·로컬 원시 로그는 Git 밖에 보관 |

실제 입력의 샘플 로그는 `REAL_SDK_SEVEN_GATE_SUBMIT_CORRECT` 및 `HYBRID_MOCK_TOUR_REAL_P_SDK_WWW_WINDOW_WWW_UI_SUBMIT_DATASTORE_PASS; LIVE_LOCATION_NOT_VERIFIED`다. 독립 SDK 검증의 관광/foreground는 메모리 fixture이며, 앱 통합은 실제 MainActivity foreground와 MOCK 관광을 사용했다. LIVE FusedLocation confidence의 미터 단위 정확도는 여전히 확인되지 않았다. 이 결과를 실제 방문 인증·실차 성공으로 표시하지 않는다.

재현과 데이터 경계: [차량 문제 SDK 안내](vehicle-puzzle-sdk.md). 로컬 검증·백업 경로는 `C:\my-car-buddy\.gradle\car-escape-vehicle-sdk-20261114`이며 커밋·푸시하지 않는다. 이번 코드는 기존 release/v0.4.0의 로컬 변경으로 두며 원격 푸시는 실행하지 않았다.


## 2026-11-14 · 화홍문 수문을 좌우 깜빡이로 변경

- 구현 완료: 현재 flow5 `c2_gates`는 **오른쪽 깜빡이=수문 닫기, 왼쪽 깜빡이=수문 열기**다. 정답은 오른쪽 3번 → 왼쪽 1번 → 오른쪽 3번이며 총 7칸 후 제출한다. 안내·힌트·해설·일곱 칸의 닫힘/열림 표시·일반 MOCK 버튼·오답 피드백을 함께 변경했다. 전부 닫으면 기존 각성도 +2 함정을 적용한다. 단계 ID·기존 해결 기록과 호수 창문 문제, flow3·4 과거 정답은 보존한다.
- SDK 구현: Vehicle 2.3.2 `TurnSignal.checkTurnSignalStateCapabilities`, `getCurrentTurnSignalState`, `registerTurnSignalState`, 기존 `pleos.car.permission.CAR_LIGHTS` 읽기 권한을 사용한다. 단일 OFF/LEFT/RIGHT 상태를 같은 수신 시각의 좌우 값으로 변환한다. 초기값·재연결 첫값은 기준선이며 같은 활성 상태 반복은 입력하지 않는다. 같은 방향은 Off 후 다시 켜며 직접 좌우 전환은 새 방향 1회다. 오류·최신성·생명주기·P/정지/CarUx 제한과 명시적 제출·저장 조건을 유지한다. 앱이 방향지시등을 제어하는 기능은 추가하지 않았다.
- 빌드 확인: 관리형 `gradle-run` 질문 “깜빡이 수문 변경이 빌드·단위 테스트·Lint를 통과하는가?”에 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` 최종 exit 0(64.799초). JVM 테스트 **171개**, 실패·오류·건너뜀 0개. Lint 오류 0개, 경고 61개. 최종 안내 문구 수정 전 실행도 성공했으며 동일 최종 코드로 APK·기기 테스트를 진행했다.
- 에뮬레이터 시연 확인: Windows·Android Studio JBR·Pleos Connect v2.1.0/API 34, `emulator-5554`, Android 사용자 10. 실제 콘솔 `/vehicle/turn-signal?mode=Off|BlinkLeft|BlinkRight` → SDK → 순서 판정 검증. 첫 오른쪽 입력을 3.5초 이상 유지하고 동일 활성값을 재전송해도 추가 칸이 생기지 않았다. 독립 SDK 2개·스토리 통합 5개·UI 4개, 총 **11개 기기 테스트 통과**, 건너뜀 0개. 창문·와이퍼 기존 입력 회귀도 통과했다.
- 화면·저장 확인: MainActivity에서 MOCK 관광 + 실제 P/정지/CarUx + 실제 깜빡이 SDK 입력으로 `RRRLRRR` 초안을 만들었다. 화면 콜백으로 답안 위조는 차단됐고 신호 입력만으로 저장되지 않았다. 실제 UI의 제출 버튼을 누른 후 DataStore에 `c2_gates` 해결이 저장됐다. 관측 표식: `HYBRID_MOCK_TOUR_REAL_P_SDK_RRRLRRR_UI_SUBMIT_DATASTORE_PASS`. 테스트는 격리된 MOCK 여행에서 진행하고 기존 MOCK/LIVE·archive·schema를 복원했다.
- 데이터·콘솔 확인: 테스트 전후 사용자 여행 JSON·여행 설정이 같았고 방향지시등 Off·와이퍼 Off·앞왼쪽 창문 0·비상등 false 원래 상태를 확인했다. 테스트 heartbeat를 정리했다. 원시 사용자 기록·로그는 `C:\my-car-buddy\.gradle\car-escape-turn-signal-20261114` 로컬 백업에만 두며 커밋·푸시하지 않는다.
- 확인 불가: 실제 차량 하드웨어와 Ubuntu 실행은 미확인이다. 관광은 MOCK 시연이며 LIVE 위치 정확도 계약은 여전히 미확정이다. 이번 콘솔 SDK·UI 제출 성공을 실제 방문 인증 전체 성공으로 해석하지 않는다. Android Studio에서 `C:\car_escape`의 app을 Run해 확인하며, Ubuntu 명령과 콘솔 조작 순서는 [차량 문제 SDK 안내](vehicle-puzzle-sdk.md)를 따른다.


## 2026-11-14 · 만석거 높이표와 방향 자물쇠

- 구현 완료: 도착 3의 flow5 `c3_channel`을 사용자 제공 5×5 숫자 표와 기존 방향 자물쇠로 교체했다. 중앙 25에서 상하좌우의 더 작은 수 중 최솟값을 골라 이동하며, 막히면 종료한다. 정확한 경로는 25→20→17→15→13→11→9→7→6→5→4→3, 방향은 **↑ ↑ → → ↓ ↓ ↓ ← ↓ ← ←**다. 3의 이웃 10·23·4는 모두 더 크므로 1까지 이동하지 않는다. 대각선·표 밖 이동은 경로에 포함하지 않는다.
- 화면·내용: 왼쪽에 숫자 표와 네 규칙, 오른쪽에 기존 중앙 원 스와이프 자물쇠를 배치했다. 시작 칸만 금색과 START로 표시한다. 경로를 자동으로 보여 주지 않으며 입력된 방향·초기화·제출을 유지한다. 농서·수문 살펴보기·힌트·해설·오답·완료 문구도 맞췄다. 표는 `WaterHeightGrid`에서 경로와 정답을 계산하고, UI는 숫자와 시작점만 표시한다. 기존 flow3·4와 해결 단계 ID·획득 기록은 보존한다.
- 빌드 확인: 관리형 `gradle-run` 질문 “물길 표와 방향 자물쇠가 빌드·단위 테스트·Lint를 통과하는가?”에 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` exit 0. JVM **180개**, 실패·오류·건너뜀 0개. Lint 오류 0개, 경고 61개. 최소 하강·대각선 제외·지역 최솟값 종료·표/답 일치·구형 흐름 보존 회귀를 포함한다.
- 에뮬레이터 시연 확인: Windows·Android Studio JBR·Pleos Connect v2.1.0/API 34·emulator-5554/사용자 10. 새 물길 통합 1개, 표 UI 1개, 다섯 장소 완주 회귀 1개, 총 **3개 기기 테스트 통과**, 건너뜀 0개. 일반 MOCK 관광/P 상태로 검증했으며 이 퍼즐은 차량 SDK 입력을 사용하지 않는다. 정차·위치 조건은 기존 앱 계약을 따른다.
- 제출·저장: 농서 조사→수문 접근→실제 중앙 원 스와이프→초기화→같은 길이 오답→11방향 정답→UI 제출→`c3_channel` 저장→갈대 사이 일기 해금·획득→Activity 재생성 후 보존을 확인했다. 입력·초기화만으로 기록이 바뀌지 않았고, 오답·정답은 제출 후에만 반영됐다. 관측 표식은 `MOCK_WATER_GRID_11_REAL_SWIPES_UI_SUBMIT_DATASTORE_RECREATE_PASS; LIVE_SDK_NOT_USED`다.
- 시각 확인: `WaterHeightGridUiTest`가 25개 셀의 표시·배치 순서·중앙 시작 표시를 검사했다. 격리된 MOCK 화면의 실제 캡처 `C:\my-car-buddy\.gradle\car-escape-water-grid-20261114\water-grid-before-submit.png`를 저장해 표·규칙·자물쇠의 배치를 직접 확인했다. 스크린샷 기준 이미지 자동 비교를 실행했다는 뜻은 아니다.
- 데이터 경계: 테스트 후 사용자 MOCK/LIVE·archive·schema와 여행 설정을 복원·대조했다. 차량 콘솔 상태도 변경되지 않았다. 원시 저장·로그·캡처는 위 로컬 비공개 경로에만 보관한다. 실제 차량·실제 관광 인증·Ubuntu 실행은 이번 확인 범위가 아니다. 커밋·푸시는 실행하지 않았다.
- 재현: Android Studio에서 `C:\car_escape` → app Run, 일반 MOCK의 도착 3 → 둑 위 종이 → 둑 끝 수문. Ubuntu에서는 `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` 후 테스트 APK를 설치하고 `adb -s emulator-5554 shell am instrument --user 10 -w -r -e class com.team.carescape.ui.StoryIntegrationTest#reservoirHeightGridCentralSwipesSubmitSaveAndResumeCompletedChannel,com.team.carescape.ui.WaterHeightGridUiTest com.team.carescape.test/androidx.test.runner.AndroidJUnitRunner`로 재현할 수 있다(기기·사용자 번호는 환경에 맞게 변경). 위 Ubuntu 명령은 안내이며 이 Windows 환경에서 Ubuntu 실행했다고 표시하지 않는다.


## release/v0.4.0 원격 병합 검증

- 사용자 푸시 요청에 따라 로컬 SDK 입력·관광 위치 노후화 예외·화홍문 깜빡이 수문·만석거 높이표 방향 자물쇠를 `5c615e0`에 커밋하고, 원격의 `990a207`(1795년 호수 장면), `00c8efc`(주행 챕터), `cbcfd73`(장면 BGM)을 병합했다. 강제 푸시나 원격 이력 삭제를 사용하지 않는다.
- 충돌 해결: 로컬 `TourPolicy`·회귀 테스트의 확인된 방문과 신선도 예외 기준을 유지한다. 원격의 관광 시작 뒤 위치 검사를 모두 생략하는 변경은 채택하지 않았다. 1795년 호수 장면과 실제 창문 입력 설명을 결합하고, debug HTTP 설정은 127.0.0.1/localhost만 허용하는 `vehicle_console_test_network.xml`로 통합했다.
- 연결 보완: 일반 MOCK의 출발하기만 자동 시연 주행을 요청한다. 시연 관광+실제 SDK 입력에서는 새 콘솔 기어·속도 자동 변경 요청을 하지 않는다. 서비스의 허용 기본값은 false이며 매 요청에 명시적으로 전달·소모한다. 이미 시작한 시연 주행을 모드 변경 시 취소하는 새 기능을 구현했다는 의미는 아니다.
- 검증 중 발견한 원격 코드 오류 수정: NaviHelper **3.0.5** 원본의 `requestNavigationScreen`에 명시된 일반 권한 `pleos.car.permission.NAVI_CUSTOM_MAP`을 선언하고 호출 전에 확인한다. `startForeground(id, notification, type)`는 API 29 이상에서만 호출하고 API 28에는 두 인자 호출을 사용한다. 권한 실패를 숨기거나 Lint baseline으로 오류를 제외하지 않았다.
- 빌드 확인: 관리형 `gradle-run` 질문 “원격 변경과 통합한 release/v0.4.0의 빌드·단위 테스트·Lint가 통과하는가?”에 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` 최종 exit 0(70.339초). JVM **196개**, 실패·오류·건너뜀 0개. Lint 오류 0개, 경고 **66개**. 앞선 두 실행에서 확인한 권한 누락·API 호환성 오류는 수정 후 같은 검증을 통과했다.
- 에뮬레이터 시연 확인: Pleos Connect v2.1.0/API 34, emulator-5554/사용자 10에서 병합본의 `StoryIntegrationTest` **6개 통과**, 건너뜀 0개. 일반 MOCK의 다섯 장소 진행, 오답·힌트·관광 시작 시각, 출발·백그라운드 제한, 만석거 11방향 실제 스와이프·제출·일기 해금·재실행 보존, MOCK 관광+실제 SDK P/깜빡이 RRRLRRR의 UI 제출·저장을 확인했다.
- 보존·한계: 테스트 전후 사용자 여행 JSON·여행 설정·콘솔 상태를 대조하고 주행 서비스의 로컬 설정도 초기 상태로 복원했다. 인증값·원시 저장·백업·기기 로그는 커밋하지 않는다. 이번 병합 기기 테스트는 자동 시연 주행 버튼이나 실제 내비 화면 전환·실차 오디오·API 28 기기를 직접 실행한 검증이 아니다. 기존 LIVE 위치 정확도 미확정 상태는 유지한다. 원격 주행 기능의 이전 관측은 [주행 챕터](drive-chapter.md)의 별도 이력이다.
- 재현: Android Studio에서 car_escape의 app을 Pleos 기기에 Run한다. Ubuntu에서는 `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`와 테스트 APK 설치 후 `adb -s emulator-5554 shell am instrument --user 10 -w -r -e vehicleConsoleControls true -e class com.team.carescape.ui.StoryIntegrationTest com.team.carescape.test/androidx.test.runner.AndroidJUnitRunner`를 사용한다. 테스트는 MOCK 여행을 격리·복원한다. Ubuntu 자체 실행은 확인하지 않았다.


## 2026-11-15 · 사건 선택 카드 효과

- 구현 완료: 플레이 가능한 사건을 누르면 카드와 핀이 함께 최대 2% 확대·4dp 상승했다가 돌아오고, 금색 테두리가 짧게 밝아지며 붉은 ‘선택 완료’ 도장이 찍힌다. 약 220ms의 단발 효과이며 이미 선택된 카드를 다시 눌러도 재생된다. 선택 콜백은 즉시 실행되고 기존 ‘이 사건을 맡겠습니다’의 화면 이동은 유지한다.
- 상태·안전: 카드의 composition scope가 효과 수명을 소유하고, 재클릭 시 이전 Animatable 작업을 취소·재시작한다. 화면을 떠나거나 안전 제한으로 카드가 제거되면 효과도 취소된다. 기존 P·정지·foreground 조건을 유지하며, 선택 불가 카드는 disabled/선택 상태 semantics를 제공한다. Android 애니메이션 배율 0은 정적인 선택 표시로 끝난다.
- 빌드 확인: 관리형 `gradle-run` 질문 “사건 선택 효과와 UI 테스트가 빌드되는가?”에 `:app:assembleDebug :app:assembleDebugAndroidTest` exit 0. “모션 끄기 검증을 분리한 UI 테스트가 빌드되는가?”에도 `:app:assembleDebugAndroidTest` exit 0. 검증 종료 후 wrapper 소유 임시 로그만 정리했다.
- 에뮬레이터 확인: Pleos/API 34, emulator-5554/사용자 10에서 `ThemeCardFeedbackTest` 3개 통과, 건너뜀 0. 실제 터치의 즉시 콜백·진행 중 재클릭·최종 도장 유지와 선택 불가 카드의 무반응을 확인했다. MotionDurationScale=0f 검증은 기본 눌림 ripple과 구분해 선택 의미 동작을 직접 호출하고 종료 상태를 비교한다. 고정 시계로 효과 전/중/재시작/후와 모션0 화면을 PNG 7개로 기록하고 실제 파일을 확인했다. 저장된 골든 이미지와의 비교 테스트는 아니다.
- 화면·기록: 실제 사건 고르기 화면을 열어 선택 도장과 카드 배치를 확인했고, 앱을 재설치한 뒤에도 기존 여행 JSON은 보존됐다. 로컬 증거는 `C:\my-car-buddy\.gradle\car-escape-case-selection-20261115\selected-before.png`, `selected-during.png`, `selected-restarted.png`, `selected-after.png`, `zero-motion-before.png`, `zero-motion-after.png`, `zero-motion-after-repeat.png`, `case-board.png`에 있다. 원시 기록·백업은 커밋하지 않는다.
- 재현: Android Studio에서 `ThemeCardFeedbackTest`를 Pleos 기기에 Run한다. Ubuntu는 APK 설치 후 `adb -s emulator-5554 shell am instrument --user 10 -w -r -e class com.team.carescape.ui.ThemeCardFeedbackTest com.team.carescape.test/androidx.test.runner.AndroidJUnitRunner`로 실행한다. Ubuntu 자체 실행과 신규 실차·SDK 검증을 한 것은 아니다.


## 2026-11-15 · 로비 자물쇠 체험 버튼 제거

- 로비의 ‘자물쇠 체험’ 버튼을 제거했다. 남은 ‘여행 도우미’는 같은 줄 가운데에 놓인다. 기존 사건 선택 효과를 보존했다.
- 관리형 `gradle-run` 질문 “로비 버튼 삭제 후 빌드되는가?”에 `:app:assembleDebug :app:assembleDebugAndroidTest` exit 0. 검증 종료 후 wrapper 소유 로그만 정리했다.
- Pleos/API 34의 기존 `LockWorkshopLifecycleTest` 1개 통과, 건너뜀 0. 로비 버튼의 부재를 확인하고 테스트 내부에서 기존 VM 진입 함수를 사용해 정차·화면 생명주기·명시 제출 제한을 계속 검증한다. 앱 재설치 후 실제 로비에서 버튼 부재와 여행 도우미 배치를 확인했으며 사용자 여행 기록은 보존했다. 화면 증거는 로컬 `C:\my-car-buddy\.gradle\car-escape-remove-workshop-20261115\lobby.png`다.


## 2026-11-15 공모전용 장소 카드 전환·전반 점검

- 사용자 승인: 직접 준비한 장소 카드 + Pleos 길 안내로 전환하고 앱 전반의 공모전 규정 관련 구현을 점검. 이미지·시안은 팀 AI 생성 자료라는 사용자 답변을 기록. AI 생성 자체 금지/별도 표시 의무는 공개 공모전 규정에서 확인하지 못했으며 생성 서비스 약관은 미확인.
- 구현 완료: 네이버 지도 SDK·검색·TourAPI 클라이언트와 외부 인증값 주입 제거. 공식 자료 기반 주차 5·음식 3개를 앱에 포함하고 방문 순서 카드로 전환. 입구 좌표는 null이며 관광지 대표점 안내를 별도 이름/좌표/버튼으로 표시. 기존 검색 plan은 새 curated_plan_v1에 복사하지 않고 기존 프로필·방탈출 기록은 유지.
- 검토 후 보완: 기존 저장 카드 및 일정 배정에도 최종 장소 공개 조건을 적용하고 원본은 삭제하지 않음. 도착 시 자동 Activity 실행·전체화면 알림 제거, 알림 클릭 복귀 및 런처 복귀로 변경. SDK 상태의 권한 확인 버튼에서 Android 13+ 알림 권한도 요청하며 미허용 자체를 게임 허용 조건으로 쓰지 않음. 서비스 입력 로그는 원시 좌표 대신 입력 타입만 기록. 미사용 문/비공개 경고등 권한 제거, 임시 음성·BGM 파일은 Debug로 제한, 알림에 MOCK 표시.
- 빌드 확인: gradle-run workflow `1add1111a3ff7b282382760da8c09c51`. 관리형 `run`으로 `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug` 실행.
  - 질문 “공모전용 추천 카드와 안전 보완 후 전체 단위 테스트, Debug·Release APK, 기기 테스트 APK 및 Lint가 통과하는가?”: exit 0, 153.739초. 첫 코드 검증 성공 후 독립 리뷰에서 발견한 저장 카드 비공개 처리와 알림 권한을 추가 보완했다.
  - 질문 “저장 장소 비공개 처리와 알림 권한 보완까지 포함한 최종 코드의 단위 테스트·Debug/Release·기기 테스트 APK·Lint가 통과하는가?”: exit 0, 92.650초. 최종 단위 테스트 **190개 성공, 실패/오류/건너뜀 0**. Lint **오류 0, 경고 66, Hint 3**. 구형 타깃·의존성 버전·SDK native 16KB 정렬·미사용 리소스 등 경고가 남아 있으며 무경고 빌드로 표현하지 않는다.
- 산출물 확인: Debug/Release의 DEX+Manifest에서 Naver Maps 클래스/검색·TourAPI endpoint/키 필드 표식 없음, 생성 BuildConfig에 해당 필드 없음. Release에 demo_routes.json 없음(Debug만 포함). 폰트 OFL 6종과 Apache 2.0 원문(META-INF/AL2.0)이 APK에 포함됨. SDK AAR가 병합한 INTERNET 권한은 남으며 이것을 앱 자체 외부 검색 호출로 해석하지 않는다. 전체 과거 Git 이력의 비밀값 부재를 검증한 것은 아니다.
- 에뮬레이터 확인: Windows·Android Studio JBR·Pleos Connect API 34 emulator-5554, user 10, 2560×1440. 최종 Debug/테스트 APK 업데이트 설치 후 `ContestTravelUiTest` **3개 성공(14.56초)**: 공개 장소·비공개 최종 장소 화면, 실제 터치 선택 콜백, UUID 격리 저장소에서 프로필 유지·이전 API 결과 차단·출처/관광지 안내 좌표 저장 복원. 테스트 전후 사용자 trip-progress.json 바이트 동일.
- 확인 불가/범위 밖: 실제 장소 입구 좌표·영업 상태·주차 빈자리·실제 경로 완료·실차 주행 안전·Ubuntu 실행은 이번에 검증하지 않았다. 앱 재실행 시 현재 에뮬레이터의 주행/내비 화면이 나타났으며 이를 새 추천 장소까지 안내 성공으로 기록하지 않는다. 기본 Release 산출물은 `app-release-unsigned.apk`이고 서명·원격 업로드·최종 제출은 하지 않았다.
- 근거/남은 항목: [공모전 점검](contest-compliance-review.md), [장소 출처](curated-places.md), [여행 도우미 사용법](travel-guide.md). 로컬 증거·변경 전 백업은 `C:/my-car-buddy/.gradle/car-escape-contest-20261115/`에 있으며 원시 로그·위치·개인 데이터는 업로드하지 않는다.


## 2026-11-15 — 4장 봉화: 현재 켜진 독서등 개수

- 요청/구현: current flow5 `c4_beacon`을 기존 FIRE 누적 탭에서 앞·뒤 좌우 독서등 4개의 **현재 ON 개수**로 변경. 정답 4, 단계 ID·완료/단서/재료 기록 유지. flow3·4는 기존 문제 보존. 일반 MOCK은 네 카드 시연 입력, LIVE/hybrid는 실제 SDK read-only 입력이다.
- 환경: Windows, Android Studio JBR, Gradle 9.1.0, Vehicle SDK 2.3.2, Pleos `emulator-5554` / API 34 / Android user 10. Ubuntu와 실제 하드웨어 실행은 미확인이다.
- 실제 API: `Light.checkReadingLightStateCapabilities`, `getCurrentReadingLightState`, `registerReadingLightState`; `SeatValue.ROW_1_LEFT/RIGHT`, `ROW_2_LEFT/RIGHT`; `LightState.OFF/ON`; 기존 `pleos.car.permission.CAR_LIGHTS`. 공식 sources JAR/AAR로 서명을 확인했다. Cabin 전역 상태·AUTO switch·조작 횟수를 합산하지 않는다.
- 입력 보호: 네 좌석 모두 읽기 지원·ON/OFF 상태·9초 최신성 확인, 3초 polling, 요청 timeout·늦은 응답·listener revision 처리. 미확인은 false/0으로 변환하지 않는다. 제출 직전과 저장 큐 실행 때 현재 개수·연결 세대·P/정지/CarUx/foreground/관광 조건을 확인한다. 실제 차량 setter 호출 없음.
- 관리형 Gradle workflow `7d195f5b2340ce77ea363e3fb2dd1131`: `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug` 성공. **단위 193개, 실패/오류/skip 0. Lint 오류 0, Warning 66, Hint 3.** Release는 unsigned APK이다.
- 기기 검증 테스트를 추가한 뒤 `:app:assembleDebugAndroidTest`에서 진단 lambda 문자열 끝의 불필요한 쉼표를 한 번 수정했고, 같은 작업 재실행 성공. 제품 코드 실패로 설명하지 않는다.
- 기기 실행: app/test APK를 기존 앱 위에 설치. `InteriorLightCountLockTest` 3개 + `InteriorLightSdkTest` 1개 + `StoryIntegrationTest#museumBeaconUsesCurrentMockCountAndPreservesCompletionAfterRecreation` 1개, **5개 전부 성공 (42.171초)**.
- 실제 SDK 관측: Console `/vehicle/reading-lights?area=...&value=...` 입력 후 **실제 READING_LIGHTS_STATE**에서 0 → 3 → 4 → 3 확인. 마지막 3개는 다른 좌석을 꺼 네 위치를 독립적으로 확인. HTTP switch 응답을 SDK 성공으로 대신 쓰지 않았다. 테스트 시작 P/속도 0 및 SDK P/정지/UX를 확인했다.
- UI/저장: 일반 MOCK에서 3개 조작만으로 저장되지 않음 → 명시 제출 오답 → 4개로 변경만으로 저장되지 않음 → 명시 제출 정답 → 중복 제출 재지급 없음 → Activity 재생성 후 완료 유지. SDK 표시 카드는 터치로 바뀌지 않으며, 미확인 상태/disabled 이전 콜백 제출 차단 확인.
- 경계: 실제 SDK 수신 테스트와 MOCK 관광/저장 통합 테스트를 구분한다. 이 실행은 전체 LIVE 관광 인증·실차/모든 차종 지원 성공을 뜻하지 않는다.
- 정리: 기존 MOCK/LIVE 여행과 archive/schema 동일 확인, 콘솔 네 독서등과 P/속도 원상 복원 확인. 로컬 `.gradle` 아래 원본 백업·기기 테스트 결과는 커밋하지 않는다. Git commit/push는 이번 요청에 포함되지 않아 수행하지 않았다.

재현: Android Studio에서 `C:\car_escape`를 열고 `app` Run → 안전한 P 정차 → 시연 관광+실제 차량 입력 → 4장 봉화함(부싯돌 먼저) → 콘솔 Reading Lights 앞·뒤 좌우 ON → 앱 4개 확인 → 제출. 일반 MOCK에서는 카드 네 개로 동일 흐름을 확인한다. 시연 바로가기는 현재 MOCK 여행을 교체하므로 원래 기록을 보존할 때는 위 격리 테스트를 쓴다.


## 2026-11-15 — release/v0.4.0 공유를 위한 최신 스토리 통합

- 사용자 푸시 요청에 따라 로컬 독서등 봉화·공모전용 장소 카드/길 안내·사건 선택 효과/로비 정리 변경을 `e58afa3`에 보존했다.
- 원격 `6b9794b`까지의 스토리 9커밋을 병합했다. `c4_index` 정답 `丙 상자 · 10 (p10)`과 일지·노인 구술·힌트·해설·장면을 보존하고, flow5 봉화는 현재 켜진 독서등 4개 제출로 유지했다. 문서 한 곳의 충돌도 두 설명을 모두 보존해 해결했다.
- 통합본 관리형 Gradle: `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug` 성공. 단위 193개 성공, 오류/실패 0, Lint 오류 0(Warning 66, Hint 3). Release unsigned 상태 유지.
- 바뀐 상자 정답에서 봉화로 이어지는 기기 회귀 `StoryIntegrationTest#museumBeaconUsesCurrentMockCountAndPreservesCompletionAfterRecreation` 1개 성공. 기존 MOCK/LIVE 저장 기록과 콘솔 원래 상태를 보존했다. 독서등 실제 SDK 수신과 독립 UI 검증은 바로 위 5개 성공 결과를 재사용했으며 다시 실행했다고 표시하지 않는다.
- staging 검사에서 실제 인증/환경/서명키/빌드 산출물 경로와 토큰 패턴 없음. local.properties·nearby.properies.env·.gradle·app/build는 제외했다. 강제 푸시나 원격 기록 삭제는 사용하지 않는다. 실제 원격 반영 commit은 Git 상태와 공유 결과를 따른다.

## 2026-11-15 · 프롤로그 녹음 음성 적용

- 내용: 팀이 만든 프롤로그 녹음 17개(`res/raw/voice_prologue_01~17.mp3`)를 넣고, 장면 대사가 나올 때 같은 번호의 녹음이 있으면 재생하고 없으면 기기 음성(TTS)으로 읽게 했다(`VoiceCatalog`, `VoicePlayer`, `Narrator`). 녹음이 나오는 동안 배경음악을 줄인다. 음성 끄기 스위치·정차 조건·화면 이동 때 멈추는 규칙은 기존 안내와 같다. 대본 번호는 `VoiceCatalog`가 정하고 대본 내보내기(`VoiceScriptTest`)와 같은 번호를 쓴다.
- 확인: 단위 테스트 통과(프롤로그 17줄이 prologue_01~17로 연결). 기기 테스트 `VoicePlaybackTest` 통과(에뮬레이터에서 17개 파일 모두 재생 시작).
- 확인하지 못한 것: 실제 소리를 귀로 들어 보지는 못했다(에뮬레이터 화면이 멈춰 프롤로그를 직접 눌러 보지 못함). 녹음 파일의 이용 조건은 팀이 확인한다.
- 소리 크기 조정(같은 날): 녹음 파일 자체는 정상 크기였다(최대 약 −3 dBFS, 평균 −16~−22 dBFS, 에뮬레이터에서 디코딩해 측정). 재생 쪽을 고쳤다 — 녹음을 기기 음성 안내(TTS)와 같은 안내 음성 채널(`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`)로 내보내고, `LoudnessEnhancer`로 약 9 dB 키우고(내장 리미터), 대사가 나오는 동안 배경음악을 0.15→0.06으로 더 줄였다. 실제로 들리는 크기는 팀이 확인한다.

## 2026-11-15 — 다섯 봉화 복원과 기본 불씨 1개

- 사용자 후속 요청 반영: 봉화는 최대 5개, 부싯돌로 켠 첫 봉화 1개는 고정. 앞·뒤 좌우 독서등의 현재 ON 수 0~4개를 더해 봉화 1~5개로 표시·판정한다. 정답은 기존 봉화 4개이므로 실제 독서등 3개가 필요하며, 네 독서등 모두 ON은 봉화 5개로 유효한 오답이다. 기본 불씨는 게임 표현이고 차량 조명을 앱이 자동 제어하지 않는다.
- 기존 `LockVisual(CarVisual.Beacon)` 그림을 다섯 칸으로 재사용했다. 별도 조명 카드·버튼 제거. 일반 MOCK은 2~5번 봉화 자체를 눌러 각각 켜고 끄며, 첫 봉화 클릭은 무효다. 실제 SDK 모드는 읽기 전용이며 미확인 표시와 제출 차단을 유지한다. 모든 판정·저장은 명시적 제출에만 일어난다.
- `BeaconLightMapping`이 기본 불씨 합산을 담당한다. SDK snapshot·VM draft·제출 입력·최신성/연결 세대 확인은 원래 독서등 raw 0~4 기준을 유지한다. 정답 도우미는 `requiredLights`, 오답 피드백은 봉화 수를 사용한다. 완료 단계 ID·아이템·flow3/4의 기존 퍼즐은 보존한다.
- 관리형 Gradle workflow `15bca77d70602022a9b4b628f00b2116`: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 성공. 단위 194개, 실패/오류/skip 0. Lint 오류 0(Warning 66, Hint 3). 이번 변경의 Release APK 빌드는 실행하지 않았다.
- Pleos emulator-5554 / API 34 / Android 사용자 10, Debug app/test APK 설치. `InteriorLightCountLockTest` 기존 3개 및 `StoryIntegrationTest#museumBeaconUsesCurrentMockCountAndPreservesCompletionAfterRecreation` 1개, 총 **4개 성공**. 고정 불씨 터치 무효·raw4 봉화5 오답·하나 꺼 raw3 봉화4 정답·제출 전 미저장·중복지급 방지·Activity 재생성 완료 보존 확인. 기존 여행·archive·schema 내용은 테스트 전후 동일하며 테스트용 여행을 남기지 않았다.
- 실제 캡처 파일을 테스트가 `cache/beacon-five/beacon-1.png`, `beacon-5.png`, `beacon-4.png`에 생성했고, 호스트 `C:/my-car-buddy/.gradle/car-escape-beacon-five-20261115/`의 같은 파일명으로 꺼내 확인했다. 단순 테스트 성공만으로 캡처를 추정하지 않고 PNG 파일 존재·유효성과 봉화 1/5/4 표시를 별도로 확인했다. 자동 픽셀 기준 비교를 수행한 것은 아니다.
- 실제 SDK 독서등 0→3→4→3 수신은 앞선 검증 결과를 재사용했다. 이번 실행은 변경된 수식·그림·MOCK 조작·저장 경로를 검증했으며 SDK 수신 테스트나 실차/전체 LIVE 관광을 새로 검증했다고 설명하지 않는다.
- 구현 및 Debug 빌드·기기 검증 완료. 변경은 현재 로컬 `release/v0.4.0`에 있으며 이번 요청에는 commit/push를 수행하지 않았다.


## 2026-11-15 — 봉화 불 아이콘 복원

- 사용자 요청으로 `e58afa3`의 `InteriorLightCountLock`에서 사용하던 `🔥` / `Esc.noto(24)` 불꽃을 복원했다. 주황 원 대신 이전 불꽃을 표시하고 기존 화구 받침을 유지한다. 기본 봉화 1개+독서등 4개, 정답 4개, 시연 직접 터치, SDK 읽기 전용, 미확인 차단, 제출/저장 로직은 그대로다.
- 관리형 Gradle workflow `dd5ad9cb0ec8eddeded3118297eed76e`, 질문 “이전 불꽃 아이콘 복원 후 Debug APK 빌드가 통과하는가?”: `:app:assembleDebug` 성공. 아이콘 표시만 변경하여 이 단계에서 단위·기기 자동 테스트를 새로 실행하지 않았다.
- Pleos emulator-5554 / Android 사용자 10에 Debug APK를 재설치하고 설치 전후 여행 저장 내용이 같음을 확인했다. 기존 4장 수사를 재개하고 시연 P·현재 장소 위치를 갱신하여 봉화 화면의 이전 불꽃 모양을 직접 확인했다. 실제 SDK 입력을 새로 검증한 것은 아니다.
- 실제 화면: `C:/my-car-buddy/.gradle/car-escape-beacon-five-20261115/flame-restored.png`. 현재 로컬 변경이며 이번 요청에서 commit/push는 하지 않았다.


## 2026-11-15 — 봉화는 좌석과 무관하게 왼쪽부터 채움

- 사용자 후속 요청: 봉화와 독서등 좌석의 고정 대응을 제거했다. 원래 SDK의 앞·뒤 좌우 `ReadingLightState` 원시값은 그대로 읽되, 표시만 기본 불씨 1개+ON 개수로 왼쪽부터 연속 채운다. 뒤 오른쪽만 ON이어도 1·2번 봉화가 켜지고, 조합이 달라도 개수가 같으면 같은 모습이다. 자리 이름은 표시하지 않는다. 이전 🔥 불꽃은 유지한다.
- MOCK에서는 꺼진 봉화 클릭으로 조명 수 +1, 켜진 봉화 클릭으로 -1(고정 첫 봉화 제외)이며 항상 왼쪽부터 채운다. 기존 raw Boolean 목록과 onToggle 콜백을 사용하고, 클릭 시 최신 상태에서 꺼진 항목/켜진 항목을 선택한다. 실제 SDK는 여전히 읽기 전용이다. 미수신/오래됨/불완전한 신호는 고정 불씨 외 네 칸을 미확인으로 표시하고 제출을 막는다. 자동 제출·정답·저장 방식은 바꾸지 않았다.
- 관리형 Gradle workflow `caef9862011b9f67c27fc44106e5b8df`: “봉화 왼쪽 채움 변경 후 앱과 UI 테스트 APK가 빌드되는가?”에 대해 `:app:assembleDebug :app:assembleDebugAndroidTest` 성공.
- Pleos emulator-5554 / Android 사용자 10에 app/test APK 설치. 기존 `InteriorLightCountLockTest` 3개를 보강하여 전부 통과: MOCK 끝 봉화 반복 클릭과 켜진 봉화 감소, SDK의 떨어진 좌석 조합별 동일 개수 표시, base 고정, SDK 터치 불가, 불완전 상태 차단, 보관된 콜백의 최신 상태·안전 가드 확인. SDK 테스트는 제어된 상태를 주입한 UI 계약 검증이며 실제 콘솔 신호 수신을 새로 실행한 것은 아니다. 설치·테스트 전후 여행 저장 내용은 동일했다. 이번에는 단위 전체/Lint/Release를 재실행하지 않았다.
- 자동 UI 검증 후 앱을 다시 전면으로 가져와 안전 상태 확인 화면까지 확인했으나, 봉화 화면 재개 전 에뮬레이터 프로세스가 종료되고 ADB 연결이 끊겼다. 따라서 이번 왼쪽 채움의 수동 봉화 화면 캡처는 확보하지 못했다. 위 3개 UI 테스트는 봉화 상태·개수·콜백을 semantics로 검증한 결과이며, 별도 스크린샷 기록이나 픽셀 기준 비교 성공을 뜻하지 않는다. 사용자의 여행을 초기화하거나 봉화 정답을 제출하지 않았다.
- 현재 로컬 `release/v0.4.0` 변경이며 이 요청에서 commit/push는 수행하지 않았다.


## 2026-11-15 — 봉화 수정 공유와 원격 프롤로그 음성 병합

- 사용자 푸시 요청으로 기본 불씨 1개+독서등 수, 봉화 5개, 이전 🔥 아이콘, 왼쪽부터 연속 채움과 MOCK ±1 조작을 `33bf4a2`에 커밋했다. 새 `BeaconLightMapping.kt`와 관련 테스트·안내 문서를 포함했다.
- 원격 `a7400f0`의 프롤로그 음성 17개·VoiceCatalog·VoicePlayer·음성/BGM 연결을 보존해 병합했다. GameViewModel은 음성 변경과 봉화 안내를 함께 유지하며, 검증 문서의 끝부분 충돌은 양쪽 기록을 모두 남겼다. 앞선 미커밋/미푸시 문구는 당시 작업 이력이다.
- 관리형 Gradle workflow `3f8464e88262e495116c1a1c6b173727`, 질문 “원격 음성 변경과 봉화 수정 통합본의 빌드·단위 테스트·Lint가 통과하는가?”: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 성공. 단위 195개, 오류·실패·skip 0. Lint 오류 0(Warning 83, Hint 3). 이번 통합본의 Release 빌드는 하지 않았다.
- 기존 봉화 도메인/저장 검증과 마지막 왼쪽 채움 UI 3개 성공 증거는 위 항목을 참조한다. 이번 병합 후에는 기기 테스트·실제 SDK 수신·음성 청취를 새로 실행하지 않았다.
- 커밋 대상에서 로컬 환경·인증·키·빌드/로그 산출물을 제외했다. 실제 원격 공유 여부와 최종 커밋은 push 결과 및 원격 ref 확인으로 구분한다.


## 2026-11-15 — 여행 도우미 고정 카드 확장

- 사용자 요청에 따라 API 연결 없이 앱 내 카드 8개를 40개로 확장했다(32개 추가). 주차·맛집·카페·체험·숙소·야경/저녁을 5개 관광지 권역에 배정했다. 공식 출처·주소·자료 확인 한계는 [장소 자료](curated-places.md)에 기록했다.
- 기존 8개 ID와 저장 키를 보존했다. 여러 권역에서 보이는 후보는 같은 ID로 저장 중복을 막고, 차량 이동·프로그램 이용 조건을 카드에 표시한다. 좌표는 계속 null이고 길 안내는 기존 관광지 대표점이다. 외부 지도·검색 API·인증·네트워크 의존성·게임 진행 상태는 변경하지 않았다.
- 관리형 Gradle workflow `00c42e32fb8a2a52069eee9a701159b3`: 질문 “장소 확장 후 여행 테스트와 APK 빌드가 통과하는가?”에 대해 `:app:testDebugUnitTest --tests com.team.carescape.travel.*` 및 `:app:assembleDebug` 성공. 여행 관련 단위 14개, 실패·오류·skip 0. 분류별 카드 존재·출처·좌표/안내 경계·기존 ID·공유 후보 저장 중복·일정/스포일러 저장 경계를 확인했다. Android Studio에서 `app` Run 후 여행 도우미를 열면 새 데이터를 확인할 수 있다. 카페는 관심사 선택, 숙소는 1박 이상 일정에서 표시된다. Ubuntu·실제 기기 화면·SDK 입력·실시간 장소 운영은 이번에 새로 검증하지 않았다.


## 2026-11-15 — 보이는 IDE 콘솔과 LIVE 앱의 실제 SDK 점검

- 사용자 요청: 시연·혼합 모드 없이 보이는 Pleos 에뮬레이터 앱과 IDE의 Virtual Console을 조작한다. 관광 15분 대기는 생략하도록 승인됐으나, 최초 도착 인증에서 차단돼 관광 타이머와 그 생략 단계는 실행하지 않았다.
- 환경: Windows, Pleos Connect v2.1.0/API 34, emulator-5554/Android 사용자 10, 화면 2560×1440. 녹화용 headless 인스턴스를 기존 데이터 유지로 정상 종료하고 보이는 에뮬레이터 창을 열었다. Ethernet 10.0.2.15 및 IDE 브라우저 실제 세션의 DDS ONLINE 확인. 외부 지도·검색 API는 연결하지 않았다.
- 최신 Debug APK를 `install --no-streaming -r -t --user 10`으로 설치했다. 이 점검에서는 새 Gradle 빌드·단위/기기 instrumentation 테스트를 실행하지 않고, 직전 여행 데이터 확장의 빌드 APK를 사용했다. 앱 화면에서 `실제 SDK`를 선택하고 정상 권한 요청을 허용했다. Fused 위치·P/정지/CarUx·차량 보조·NaviHelper·TTS/STT의 준비 상태를 실제 UI에서 확인했다. 음성 준비를 청취·인식 성공으로 설명하지 않는다.
- IDE 콘솔의 보이는 버튼·검색·좌석 선택·슬라이더/키보드로 차량 값을 변경했다. 차량 입력 HTTP PUT이나 가짜 브라우저 heartbeat는 사용하지 않았다. HTTP GET은 관찰·백업용이고 SDK 성공 판정은 앱 프로세스의 SDK 수신 로그 및 안전 UI로 대조했다. SDK 수신 신호를 앱의 시연 입력으로 대체하지 않았다.
- 확인: 콘솔 D/속도 0 → 앱 안전 차단, P/속도 0 → 로비 복구. 창문 콘솔 77%→SDK WINDOW_POS 8, 100%→10, 닫힘→0 관측. SDK 단위를 퍼센트로 가정한 초기 프로브 기대값 75/100은 틀려 실패했고, 현행 capability/원시값 경계를 읽은 뒤 10·0 수신을 확인했다. 이는 앱 소스 오류가 아니며, 모든 차량의 위치 단위가 0~10이라는 의미도 아니다.
- 확인: 와이퍼 Off→Low→Off에 SDK WINDSHIELD_WIPERS_STATE 1→2→1. 방향지시등 Blink right→Off→Blink left→Off에서 앱 SDK 값은 1→0→2→0. 독서등 앞·뒤 좌우 네 영역의 실제 READING_LIGHTS_STATE로 켜짐 0→3→4→3→0을 확인했다. 두 번째 3개는 다른 좌석 조합이다. 원래 창문·와이퍼·방향지시등·독서등·P/정지 상태를 복원했다.
- 제한/발견: LIVE 사건을 정상 UI로 시작하고 화성행궁 길 안내를 요청했으며 내비 앱 호출을 관찰했다. GPS는 콘솔에 입력 항목이 없어 `adb emu geo fix`로 전달했다. Fused 실제 갱신은 READY이나 accuracyMeters가 null인 현행 경계 때문에 `위치 정확도 근거를 확인하지 못해 인증을 보류합니다.`가 표시됐다. confidence는 공식 v1.1.3 문서·sources에 미터 정확도 계약이 없어 변환하지 않았다. 길 안내 완료·정확한 좌표의 SDK 표본 대조·실제 LIVE 도착/관광·전체 자물쇠 제출·봉화 정답 저장은 미검증이다.
- 정리: 테스트 전 여행·설정·콘솔·위치를 로컬 백업했고 기존 모든 여행과 archive/schema 내용의 동일성을 확인했다. 새 LIVE 테스트 여행 1건은 첫 이동 단계로 남겨 사용자가 차단 화면을 확인할 수 있게 했다. GPS는 원래 에뮬레이터 좌표로 복원했다. SDK/위치 정책·게임 정답·프로덕션 코드는 이번에 변경하지 않았다. 증거와 검증 요약은 Git 제외된 `C:/my-car-buddy/.gradle/car-escape-live-sdk-20261115/`에 보관한다. 원시 위치·SDK 로그·설정 백업은 커밋하지 않는다.


## 2026-11-15 — 위치·관광 대기를 제외한 LIVE SDK 전체 풀이

- 사용자 후속 승인에 따라 위치 인증과 관광 15분 대기만 제외하고 실제 SDK로 문제를 푸는 격리 debug 세션을 추가했다. UUID Intent로 명시 실행할 때만 LIVE로 고정하며 화면에 `LIVE SDK 테스트 / 위치·15분 제외 · 테스트 기록`을 표시한다. 가짜 위치·안전·차량 callback이나 과거 관광 시각을 만들지 않았다. 원래 `TourPolicy`·Release 위치 인증은 유지된다. [실행 방법](live-sdk-console-test.md).
- 환경: Windows, `release/v0.4.0` / 기준 HEAD `baeff9c`, Pleos Connect v2.1.0/API 34, `emulator-5554`/Android 사용자 10. 보이는 네이티브 에뮬레이터와 VS Code의 Pleos Virtual Console(`127.0.0.1:8088`)에서 DDS 연결을 유지했다. 실제 앱 터치·다이얼 탭·방향 스와이프와 IDE 콘솔 버튼·좌석 선택·슬라이더 키보드 조작으로 진행했다. HTTP GET과 앱 SDK 수신 로그는 관찰용으로만 사용했고, HTTP 제어 요청·가짜 heartbeat·SDK 답안 주입·기기 진행 파일 편집은 하지 않았다. 외부 지도/검색 API를 연결하지 않았다.
- 저장 격리: `files/sdk-live-tests/<UUID>/trip-progress.json`만 사용한다. 일반 여행용 DriveService/DemoArrival 자동 시작과 출발 버튼을 검증 세션에서 제외하고 실행 전 앱을 force-stop했다. 테스트 전 원본 여행 파일을 백업했으며 종료·재설치·재개 후에도 `files/datastore/trip-progress.json`이 바이트 단위로 동일했다. 테스트 기록의 일반 사진 보고서 내보내기도 막고 실제 버튼 비활성을 확인했다.
- 실제 UI 완주: 사건 고르기 → 시역 사건 수락 → 프롤로그 → 각 장소 길 안내 요청 → 도착 장면 → 관광 시작·귀환 → 탐색·자물쇠 입력·명시 제출 → 단서/소지품 획득 → 다음 장소 → 진 엔딩 `연이의 이름`. 5개 장소의 `solvedSteps`가 각각 3·4·3·7·6개(합계 23), 중복 없이 기대 집합과 일치한다. `mode=LIVE`, `flowVersion=5`, `chapterIndex=4`, `phase=ENDED`, 종료 시각·3개 장 보상·4개 단서가 저장됐다. 관광 시작 기록 5개 모두 실제 시계를 사용하고 `debugTimeBypass=false`다. 이것을 실제 관광지 방문 인증으로 해석하지 않는다.
- 방향지시등 `c2_gates`: 문제를 연 뒤 IDE Blink right→Off를 3회, Blink left→Off를 1회, Blink right→Off를 3회 조작했다. 앱에 7회가 정확히 누적되고 제출 전에는 미해결임을 확인한 다음 제출하여 단서 획득/정답 저장을 확인했다. 입력 도중 별도로 기어 D/속도 0을 실제 콘솔로 넣자 안전 차단 화면이 표시됐다. P 복귀 시 기존 입력은 초기화되고 제출이 비활성화되어, 새 7회 입력 후 제출했다.
- 실내조명 `c4_beacon`: 네 READING_LIGHTS_STATE 영역 1·4·16·64의 실제 0/1 수신과 봉화를 대조했다. 실제 독서등 0→1→3→4개에서 기본 불씨를 합친 봉화 1→2→4→5개가 표시됐다. 조명 4개(봉화 5개)를 명시 제출하면 `오답 1회`와 `봉화를 너무 많이 올렸다`가 표시되며 해결되지 않는다. 앞 왼쪽을 꺼서 다른 좌석 조합의 조명 3개를 만들었을 때도 왼쪽부터 봉화 4개가 채워졌다. 이 상태를 제출해 `c4_beacon` 저장과 재료 획득을 확인했다. 의도한 오답 1회는 격리 기록에만 남았고 각성도·힌트 사용은 0이었다.
- 운전석 창문 `c5_window`: 문제 진입 시 WINDOW_POS 영역 16 값 0, 빈 답안, 제출 비활성을 확인했다. 콘솔 Front left 슬라이더를 실제 키보드 End·Tab으로 100%에 놓자 SDK 원시값 10이 수신되고 앱의 `창문` 입력 1회와 제출 활성화가 표시됐다. 직접 제출해 정답 저장을 확인했다. 콘솔 %와 SDK 원시값을 같은 단위로 가정하지 않았다.
- 나머지 UI 문제도 직접 입력했다. 화성행궁 한자·0213, 화홍문 시간 다이얼·열쇠·홍예 수 7, 만석거 현판·높이 표의 11방향 스와이프·1799, 박물관 문서/순서/장소/상자/부싯돌/백신 제조, 마지막 호수·4172·백신 사용·진상 2문제를 정상 제출했다. 소지품이 많은 화면은 오른쪽 목록을 실제 스크롤해 사용 버튼을 눌렀다. 현행 시역 flow5에는 와이퍼 답안 단계가 없으므로 이번 완주를 와이퍼 문제 정답 제출 성공으로 표시하지 않는다. 앞선 별도 신호 수신 결과와 구분한다.
- 최종 APK를 데이터 보존 재설치하고 같은 UUID로 실행한 뒤 정상 `수사 재개`로 진 엔딩과 보고서를 다시 열었다. 재개 후 테스트 여행 파일도 재설치 전 완료 스냅샷과 바이트 단위로 일치한다. 보고서 사진 저장 버튼은 비활성이며 저장을 실행하지 않았다. 에뮬레이터는 복구된 진 엔딩 화면으로 남겨 두었다. 창문 닫힘·독서등 네 개 OFF·방향지시등 OFF·기어 P·속도 0 및 원래 와이퍼 상태를 보이는 콘솔에서 복원/확인했다.
- 빌드/단위 검증: 관리형 gradle-run workflow `df5d056c7b34a6f9d3e9aa926d583a82`, 질문 “LIVE 차량 테스트의 격리·안전 정책과 Debug/Release 빌드가 통과하는가?”에 대해 `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 성공. 단위 201개, 실패·오류·skip 0(새 순수 검증 세션 정책 4개 포함). 이후 보고서 사진 제한 변경은 workflow `71b6d7bc3fe750f069b05f969ba80b23`, 질문 “검증 세션의 보고서 내보내기 차단이 Debug/Release에서 빌드되는가?”에 대해 `:app:assembleDebug :app:assembleRelease` 성공, 그 최종 APK의 재개·보고서 버튼을 기기 UI에서 확인했다. 두 workflow 모두 finish하여 래퍼 소유 임시 빌드 로그만 정리했다. 전체 Lint·Android instrumentation·Ubuntu·실차 검증은 이번에 실행하지 않았다.
- 조작 도구에서 발견한 제한: 최초 방향 스와이프가 인식 임계보다 짧아 입력이 없었으므로 거리를 늘려 다시 입력했다(오답 제출로 집계되지 않음). 사용자가 모니터/창 배율을 변경하는 동안 처음 조명 클릭이 반영되지 않아 실제 값 0을 확인하고, 물리 픽셀 좌표 및 Windows UIAutomation의 콘솔 검색/Slider 포커스로 보정했다. 콘솔 값·SDK·화면이 일치한 뒤에만 통과로 판정했다. 재개 중 시스템 Bluetooth 팝업은 화면 바깥 앱 영역을 눌러 닫은 후 계속했다. 이 일시적 조작 실패를 SDK 연동 실패나 성공으로 바꾸어 기록하지 않았다.
- 확인 범위: **구현 완료 / Debug·Release 빌드 확인 / 보이는 Pleos 에뮬레이터의 LIVE SDK UI 풀이·제출·저장·재개 확인.** GPS 정확도 계약, 실제 관광지 좌표/반경·15분 체류 인증, 내비 경로 안내 완료, 별도 CarUx 정책 조작, 가청 음성 품질, 실차 동작은 미검증/제외다. Release는 검증 세션을 활성화할 수 없도록 소스 분리했고 빌드는 확인했으며 Release APK 기기 실행은 하지 않았다.
- 상세 증거/요약은 Git 제외 로컬 `C:/my-car-buddy/.gradle/car-escape-live-session-20261115/`의 `verification-summary.json`, `final-test-trip.json`, 차량 입력 UI XML·PNG와 SDK 값 스냅샷에 보관한다. 원시 위치·SDK 로그·설정/진행 백업은 커밋하지 않는다. 기존 여행 도우미 데이터 확장 변경은 보존했다. 이번 요청에서는 commit/push를 수행하지 않았다.
