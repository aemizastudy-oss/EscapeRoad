# car_escape · RoadEscape

차량으로 관광지를 이동하고, 관광 후 차로 돌아와 짧은 방탈출을 진행하는 네이티브 Android 프로젝트입니다. **기존 3경유지 Mock 테스트를 보존하면서 수원 관광 코스 5챕터, 15분 관광 기록, Pleos SDK 연결 경계를 추가했습니다.** 앱 표시명은 `차량 방탈출 데모`입니다.

현재 관광 문제는 연동 확인용 임시 콘텐츠입니다. 실제 관광·실차 지원·현재 위치 인증에 성공한 완성품으로 설명하지 않습니다. 로그인·앱 서버·외부 AI·결제는 추가하지 않았습니다.

기준 문서는 최신 [관광·SDK 기획서](docs/ROAD_ESCAPE_sdk.md)이며, [관광 흐름과 데이터 경계](docs/tour-flow.md), [SDK API·권한·제약](docs/pleos-sdk-integration.md)에 구현 범위를 기록했습니다. 기존 [기본 프로젝트 가이드](docs/PLEOS_CODEX_DEVELOPMENT_GUIDELINES.md)는 초기 3경유지 프로토타입의 기준입니다.

## 현재 검증 상태

2026-11-13 문서 작성 시점의 구분입니다. 실제 실행한 명령·기기 결과·APK 해시는 [검증 기록](docs/verification.md)을 우선합니다.

| 항목 | 상태 |
| --- | --- |
| 관광 정책·별도 저장·5챕터·SDK 어댑터·UI 연결 | 구현 완료 |
| 최신 Debug·Release APK / JVM / Lint | 빌드 성공 / 37개 통과 / 오류 0·경고 26 |
| 최신 통합본의 기기 회귀 | 26개 통과. 기존 3경유지와 MOCK 5챕터 순차 진행·재생성·범위 밖 차단 포함 |
| 실제 SDK 초기화·수명주기 시험 | 최신 1회 통과(18.673초). 새 위치 콜백·P/정지/UX·브레이크·배터리 관측, 6개 연결 상태 READY |
| 속도 권한 허용 후 SDK 재시험 | 8088 Virtual Console DDS ONLINE에서 확인. TTS/STT는 ON_DEVICE 초기화만 검증 |
| LIVE 도착 인증·길 안내 완료·가청 TTS·STT·실차 | 미확인. 연결 준비와 실제 기능 완료를 구분 |
| 기존 기기 저장 데이터 | 테스트 전 파일 3개 복원·SHA-256 일치 확인 |

**LIVE 위치 인증은 현재 정확도 근거 부족으로 보류합니다.** FusedLocation의 `confidence`를 미터 오차로 바꿀 공식 근거를 확인하지 못해 `accuracyMeters=null`을 전달합니다. SDK가 초기화되거나 좌표 콜백이 도착했다는 이유만으로 관광 인증을 성공 처리하지 않습니다.

Pleos 차량 시뮬레이션 콘솔은 `adb -s emulator-5554 forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`에서 사용합니다. 앱을 웹으로 실행하는 주소가 아니며, 최신 재시험에서는 콘솔 브리지 준비·Park·브라우저 활성 상태를 확인했습니다.

## 두 가지 시작 경로

| 경로 | 내용 |
| --- | --- |
| 로비의 **수원 관광 코스 · 5챕터** | 화성행궁 → 화홍문 → 만석공원·만석거 → 수원광교박물관 → 광교호수공원 |
| debug의 기존 **사건 고르기** 흐름 | 가상 경유지 3개와 숫자·순서·상태 조합 자물쇠의 기본 테스트 |

새 관광 코스는 setup에서 **실제 여행 시작(LIVE)** 또는 debug의 **시연 데이터로 시작(MOCK)**을 선택합니다. 모드와 저장 기록을 섞지 않으며, SDK 미지원·권한 거절을 Mock 성공으로 자동 대체하지 않습니다. release에는 모의 입력과 개발 패널이 없고, 과거 MOCK 원장을 LIVE 기록으로 바꾸지 않습니다.

### 관광 코스를 Mock으로 시연하기

1. 로비에서 **수원 관광 코스 · 5챕터 → 시연 데이터로 시작**을 선택합니다. 기존 진행이 있으면 새 게임 확인을 거칩니다.
2. 개발 패널에서 **PARKED**를 확인하고 현재 챕터의 **도착 위치 시연**을 누릅니다.
3. **관광 시작**을 누르고 시작 기록의 **저장 완료**를 확인합니다. 다시 눌러도 최초 시작 시각은 바뀌지 않습니다.
4. **15분 경과 시연**을 누릅니다. 이 버튼은 MOCK의 debug 우회 표시이며 실제 15분의 경과나 실제 방문을 검증한 결과가 아닙니다.
5. 현재 경유지의 **퍼즐 열기**로 진행해 화면 입력·힌트·정답 보기·건너뛰기를 사용합니다.
6. 다음 챕터에서도 위치 시연·관광 시작 저장·15분 경과 시연을 새로 진행합니다. 다섯 장소를 처리하면 실제 해결·건너뜀·힌트·정답 공개 수를 확인합니다.

앱이 백그라운드에 다녀오면 모의 주차와 위치도 무효화됩니다. 저장된 관광 시작 기록은 남지만 PARKED와 도착 위치를 다시 확인해야 합니다. MOCK의 위치·시간 시연을 실제 SDK 관측 로그로 사용하지 않습니다.

| 챕터 | 임시 검증 정답 |
| --- | --- |
| 화성행궁 | 숫자 `0427` — 선행 `0` 포함 |
| 화홍문 | 위 → 오른쪽 → 아래 → 왼쪽 |
| 만석공원·만석거 | 가상 채널 `3`, 레벨 `2` |
| 수원광교박물관 | 숫자 `1500` |
| 광교호수공원 | 왼쪽 → 위 → 오른쪽 → 아래 |

관광지의 실제 역사·전시물·관찰 문제로 확정된 정답이 아닙니다. 문제와 이야기는 별도 제작 전이며, 가상 장치 입력은 차량 설정을 바꾸지 않습니다. 기존 3경유지 테스트 정답은 `0427`, 위→오른쪽→아래→왼쪽, 채널 3·레벨 2로 보존했습니다.

### 실제 SDK 모드

**실제 여행 시작** 후 **권한 확인**과 **SDK 연결 상태**에서 기능별 상태를 확인합니다. 실제 데이터 모드에는 모의 주차·도착·15분 버튼이 없습니다.

- 관광 시작과 퍼즐 이용에는 foreground, P, 실제 정지, CarUx 게임 허용이 모두 필요합니다. P만으로 허용하지 않습니다.
- 모든 관광지 반경은 **500m 임시값**, 최소 관광 시간은 **15분 고정**입니다. 복귀 시 현재 위치를 다시 확인하며 과거 위치나 NaviHelper 도착 이벤트만으로 열지 않습니다.
- `TourPolicy`의 위치 최대 나이 30초·정확도 상한 100m는 앱의 보수적 초기값입니다. SDK 어댑터의 더 엄격한 유효 시간 검사는 별도로 적용하며, 어느 값도 Pleos 공식 갱신 주기·정확도 보장이 아닙니다.
- 공개 관광지 대표점은 코드에 등록했습니다. 만석공원·광교호수공원의 방문 구역·최종 인증 중심은 미확정이며 **다섯 구간 모두 합법적 주차 접근성과 현장 검증이 필요합니다.** 지정 주차장은 없고 사용자 화면에 거리 숫자를 표시하지 않습니다.
- 최신 위치라도 미터 단위 정확도를 확인할 수 없어 현재 LIVE 인증은 보류합니다. 허위 정확도·방문·관광 완료를 만들어 통과시키지 않습니다.
- ON_DEVICE TTS/STT는 안전·관광 게이트를 통과한 현재 문제에서만 사용합니다. 음성은 입력 후보이며 사용자가 확인해 **이 답안으로 제출**을 눌러야 합니다. 모호한 말·부호·소수점·미지원 상태 조합 입력은 추정하지 않습니다.

좌표의 공식 출처·페이지·현재 값과 인증 사유는 [관광 흐름 문서](docs/tour-flow.md)에 정리했습니다. 실제 SDK API·지원·권한·수신·출력 상태는 [SDK 연동 문서](docs/pleos-sdk-integration.md)와 구분해 확인합니다.

## 저장과 데이터 보존

| 파일 | 역할 |
| --- | --- |
| `files/datastore/game-session.json` | 기존 단일 게임 진행. 방문·오답·힌트·정답 공개·완료·단서를 원자 스냅샷으로 저장 |
| `files/datastore/tour-state.json` | `sessionId + checkpointId + mode`별 최초 관광 시작과 MOCK debug 표시를 별도 Typed DataStore로 저장 |
| `roadescape-ui` SharedPreferences | 경유지별 수첩·세션별 평가. 게임 진행 원장과 무관 |
| `roadescape-lobby-layout` SharedPreferences | 로비 요소의 화면 프로필별 이동량. 게임 진행과 별개 |

관광 시작은 저장 성공 후에만 화면에 공개하며 중복 클릭으로 시각을 초기화하지 않습니다. 앱 종료 사이에는 반복 타이머 없이 재실행 때 시간 차이를 계산합니다. 같은 부팅의 wall/단조 시각을 대조하고, 역행·불합리한 시계 변화를 보류합니다. 외부 시간 서버가 없는 로컬 방식이므로 **고의적인 시각 조작을 완전히 막지는 못합니다.**

새 게임의 새 세션이 과거 관광 기록을 재사용하거나 자동 삭제하지 않습니다. 기존 게임 JSON에 없던 `sourceMode`는 schema `1`의 `MOCK` 기본값으로 읽으며 LIVE로 소급 분류하지 않습니다. 위치·주차·UX는 저장된 값으로 인증하지 않고 재확인합니다. 원시 위치와 음성 인식 내용은 관광 기록에 영구 저장하지 않습니다.

손상 JSON·미지원 버전·읽기/쓰기 실패는 원본을 자동으로 빈 기록으로 덮어쓰지 않습니다. **다시 시도**를 제공하고 저장 문제 중 진행을 막습니다. 사용자가 기존 게임의 **새 게임 → 확인**을 선택하면 `game-session.json`은 내부 `.bak`로 보존합니다. 관광 저장 오류 때문에 이 게임 원장을 비우지 않습니다. 앱 제거·앱 데이터 삭제는 내부 진행과 백업도 삭제합니다.

수첩과 평가는 **메모 저장 / 평가 저장**을 눌러야 영구 저장합니다. 저장하지 않은 편집은 화면 이동·주차 중단에서 사라질 수 있습니다. 완료 후 해설·기념카드를 열 수 있으며, PNG 저장은 시스템 파일 선택기를 사용합니다. 기존 Pleos AVD에서 파일 선택기가 자동 취소된 이력이 있어 실제 파일 저장 성공은 별도 확인 항목입니다.

## 로비 배치 편집

debug 로비에서 안전 정차를 확인한 뒤 오른쪽 위 **로비 배치 편집**을 누릅니다. 로고·문구·버튼 줄·이어하기 카드를 드래그하거나 1/10dp 화살표로 옮기고 **저장**합니다. **취소**와 **기본값**도 지원하며, 기기에 저장한 위치는 앱 재실행 후 유지합니다. 버튼 줄은 묶음으로 이동합니다.

팀에 배치를 공유하려면 **레이아웃 JSON 복사**로 내보낸 내용을 `app/src/main/assets/lobby_layout.json`에 반영하고 재빌드합니다. release에는 편집 조작부가 없고 저장 배치는 적용됩니다. 화면별 설정·안전 조건·공유 방법은 [로비 위치 편집](docs/lobby-layout-editor.md)에 정리했습니다.

## 프로젝트 구성

단일 `:app` 모듈, Kotlin·Compose·ViewModel/StateFlow·Navigation·Typed DataStore를 사용합니다.

| 경로 | 책임 |
| --- | --- |
| `model/` | 콘텐츠·게임·관광 기록·입력 타입 |
| `game/GameController.kt`, `LockEvaluator.kt` | 순수 게임 진행과 자물쇠 판정 |
| `game/TourPolicy.kt`, `TourCatalog.kt` | 15분·현재 위치·안전 정책, 관광지 대표점 |
| `game/VoiceAnswerParser.kt` | 음성 텍스트를 제출 전 입력 후보로 변환 |
| `data/` | 정적 콘텐츠·게임/관광 개별 원장·별도 로비 배치 설정 |
| `platform/sdk/` | 실제 Pleos 위치·길 안내·차량 보조·음성, AAOS 안전 상태 |
| `src/debug/`, `src/release/` | 모의 제공자·개발 패널의 빌드별 분리 |
| `ui/` | 화면·이벤트 직렬 처리·안전 가림·SDK 상태 |
| `assets/demo_package.json`, `assets/suwon_tour_package.json` | 기존 3개와 신규 5개 검증 콘텐츠 |
| `assets/lobby_layout.json` | 로비 기본 이동량과 팀 공유용 JSON |
| `app/src/main/res/drawable-nodpi/pdf_*.png` | PDF에서 잘라낸 배경·질감·로고·장식 PNG 14개 |
| `tools/extract_pdf_assets.py`, `docs/pdf-assets.json` | PNG 재추출 스크립트와 원본 좌표·SHA 명세 |
| `src/test/`, `src/androidTest/` | 게임·관광·저장·음성·UI 검증 코드 |

표의 Kotlin 경로는 `app/src/main/java/com/example/roadescape/` 기준입니다. [화면 대응과 보조 저장 경계](docs/ui-design.md), [글꼴·라이선스](docs/third-party-notices.md)도 참고합니다. PDF 이미지 전체를 앱 화면으로 붙이지 않고 동적 문구·입력·버튼은 네이티브 UI로 유지합니다.

## 빌드 환경

버전은 [Version Catalog](gradle/libs.versions.toml)와 [Gradle Wrapper](gradle/wrapper/gradle-wrapper.properties)에 고정했습니다.

| 항목 | 값 |
| --- | --- |
| applicationId / namespace | `com.example.roadescape` |
| 앱 버전 | `0.1.0` / versionCode `1` |
| Gradle / AGP | `9.1.0` / `9.0.1` |
| Kotlin / Compose·Serialization 플러그인 | `2.2.21` |
| Compose BOM | `2025.08.01` |
| Activity / Lifecycle / Navigation | `1.10.1` / `2.9.3` / `2.9.3` |
| DataStore / Serialization / Coroutines | `1.1.7` / `1.9.0` / `1.10.2` |
| Pleos Vehicle / FusedLocation / NaviHelper | `2.3.2` / `1.1.3` / `3.0.5` |
| Pleos TTS / STT | 각각 `3.3.1` |
| minSdk / compileSdk / targetSdk | `28` / `36` / `34` |
| 소스·바이트코드 대상 | Java / Kotlin JVM `17` |
| 기존 로컬 검증 JVM / Pleos AVD | Android Studio JBR `25.0.4` / API `34` |

AGP 9의 내장 Kotlin을 사용하며 `org.jetbrains.kotlin.android`를 중복 적용하지 않습니다. compile SDK와 실행 AVD API가 같은 값일 필요는 없습니다. 초기 의존성 다운로드에는 네트워크가 필요합니다. Mock 게임은 앱 서버 없이 동작하며, LIVE SDK 서비스의 접속·차량 지원 조건은 별도 검증합니다.

TTS/STT SDK AAR의 CCG validation용 `INTERNET` 선언이 병합 Manifest에 들어옵니다. 이전 프로토타입의 **INTERNET 권한 없음** 기록을 최신 SDK 앱에 적용하지 않습니다. 앱이 별도 서버·계정·외부 AI를 추가했다는 의미는 아니며 실제 권한과 ON_DEVICE 사용 경계는 [SDK 문서](docs/pleos-sdk-integration.md)에 있습니다.

### Android Studio

1. **File → Open**에서 `car_escape` 프로젝트 루트를 엽니다. 현재 Windows 경로는 `C:\car_escape`입니다.
2. **Settings → Build, Execution, Deployment → Build Tools → Gradle**에서 설치된 Gradle JDK를 선택하고 Sync합니다.
3. **Tools → SDK Manager**에서 Platform 36과 Build Tools 36.0.0을 확인합니다. 개인 SDK 경로는 커밋하지 않는 `local.properties`에 둡니다.
4. **Tools → Device Manager**에서 기존 Pleos AVD를 실행합니다. 기존 AVD를 초기화하지 않습니다.
5. `debug` Build Variant와 실행 기기를 선택하고 **Run → Run 'app'**을 누릅니다.

### Windows PowerShell

```powershell
cd C:\car_escape
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleRelease

$roadAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $roadAdb devices
& $roadAdb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
& $roadAdb -s emulator-5554 shell am start -n com.example.roadescape/.MainActivity
# 실행 대상을 확인한 뒤
.\gradlew.bat :app:connectedDebugAndroidTest
```

`emulator-5554`는 관측한 기기 이름의 예입니다. `adb devices`에 나온 실제 대상을 지정합니다. 여러 기기가 연결돼 있으면 원하지 않는 기기에서 테스트하지 않도록 먼저 확인합니다.

### Ubuntu

Android Studio에서 같은 프로젝트를 열고 SDK와 Gradle JDK를 지정합니다. 터미널의 `JAVA_HOME`과 Android SDK `platform-tools` PATH도 설치 환경에 맞춥니다.

```bash
cd /path/to/car_escape
chmod +x gradlew
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
adb devices
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.example.roadescape/.MainActivity
# 실행 대상을 확인한 뒤
./gradlew :app:connectedDebugAndroidTest
```

빌드 성공 시 debug APK는 `app/build/outputs/apk/debug/app-debug.apk`, 서명 없는 release는 `app/build/outputs/apk/release/app-release-unsigned.apk`입니다. 명령 예시가 해당 최신 APK의 생성·실행 성공을 의미하지는 않습니다. Ubuntu 실행과 배포 서명은 아직 확인하지 않았습니다.

## 이전 검증 이력

아래는 관광·SDK 확장 전의 결과이며 최신 통합본의 성공 근거로 합산하지 않습니다.

- **2026-11-13 PDF PNG 교체:** debug/release, JVM 15개와 기기 UI 6개 통과. 저장 8개·VM 4개는 당시 재실행하지 않았습니다. Lint 오류 0·경고 21, PNG 14개 재추출 SHA 일치를 확인했습니다.
- **2026-11-12 UI 개편:** JVM 15개·기기 18개, 총 33개 통과. 이후 메뉴·대화상자 보정에 debug/release/JVM/Lint를 재확인했으나 기기 전체를 다시 실행한 것은 아닙니다.
- 기존 Pleos 검증에서는 로비·자물쇠·힌트·DRIVING 가림을 확인하고 저장 데이터를 해시 일치 상태로 복원했습니다. 좁은 화면 전체 흐름, PNG 실제 파일 저장, SDK 위치·음성 성공을 확인한 결과는 아닙니다.

보고서·APK 해시·환경 제약과 현재 검증 상태는 [최신 검증 기록](docs/verification.md)을 확인합니다.

## Wrapper와 참고 자료

Wrapper 네 파일은 기존 로컬 `my-car-buddy`의 정상 Gradle 9.1.0 구성을 복사한 뒤 실행 확인한 이력입니다. 기존 저작권과 Apache License 2.0 고지를 유지했습니다. 배포본 SHA-256은 `a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806`이며 개인 경로·토큰·기기 로그·내부 백업은 공유 코드에서 제외합니다.

- [Gradle 9.1.0 원본](https://github.com/gradle/gradle/tree/v9.1.0), [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Pleos 공식 개발 문서](https://document.pleos.ai/docs/pleos-only/getting-started)
- [Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore)

원격 저장소 업로드·공개 배포·스토어 제출·실차 출시 검증은 별도 요청과 확인 범위입니다.
