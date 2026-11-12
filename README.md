# car_escape · RoadEscape

차량이 정차한 상황을 개발 패널로 모의 입력하고, 세 경유지의 자물쇠를 풀어 단서를 모으는 네이티브 Android 기본 프로젝트입니다. 앱 표시명은 **차량 방탈출 데모**입니다. 게임 데이터와 진행은 기기 안에만 두며 로그인이나 서버를 사용하지 않습니다.

이름·패키지·정답은 개발용 기본값입니다. 실제 위치나 차량 상태를 읽지 않으며, Pleos Virtual Console과 앱의 도착·주차 버튼도 연결되어 있지 않습니다. debug 화면에는 `개발용 시뮬레이션`을 표시합니다. 실제 이동 중 사용을 위한 완성품이나 실차 지원 검증 결과가 아닙니다.

구현 요청의 기준은 [개발 가이드라인](docs/PLEOS_CODEX_DEVELOPMENT_GUIDELINES.md)입니다. 빌드·테스트·에뮬레이터의 **실제 실행 결과**는 [검증 기록](docs/verification.md)에서 구분합니다.

화면은 [Escape Road 화면 디자인 PDF](<docs/Escape Road 화면 디자인.pdf>)의 버건디 배경·금색 테두리·양피지 카드·명조 서체를 바탕으로 구성했습니다. **2026-11-13에는 배경·종이 질감, 로고와 장식 그림을 PDF에서 직접 잘라낸 PNG로 교체했습니다.** PNG는 PDF 각 쪽의 합성 JPEG에서 영역을 잘라내고 일부 배경을 투명 처리한 자료이며, 원본의 독립된 디자인 레이어나 글꼴을 추출한 것은 아닙니다. 동적 문구·진행도·정답 입력·버튼·경로 도식·테두리는 네이티브 Compose로 유지합니다. [27쪽 시안과 실제 화면의 대응](docs/ui-design.md), [PNG 추출 명세](docs/pdf-assets.json), [포함 글꼴과 라이선스](docs/third-party-notices.md)를 확인할 수 있습니다. 실제 차량·GPS 연동은 포함하지 않습니다.

**2026-11-13 PDF PNG 교체 후 debug·release 빌드, JVM 15개와 기기 UI 6개가 통과했습니다.** 이번 실행은 실패·오류·건너뜀 없이 총 21개이며, 기존 저장 8개·VM 4개는 재실행하지 않았습니다. Lint는 오류 0개·경고 21개입니다. 14개 PNG의 원본·추출 명세·파일 해시와 재추출 결과도 대조했습니다. 이번 변경의 확인 범위와 APK 해시는 [최신 검증 기록](docs/verification.md)을 확인합니다.

Pleos에서 로비·사건 선택·경로·자물쇠 3종·힌트·DRIVING 가림과 기념카드 미리보기를 확인했습니다. density 320에서는 로비 상단의 이미지 비율만 확인했으며 전체 플레이는 미검증입니다. 검증 후 화면 설정을 원래대로 돌리고 최신 debug 설치를 유지했습니다. 작업 직전 보관한 원본 파일 3개는 SHA-256 일치로 복원했고, 임시 진행·추가 설정은 정리했습니다.

2026-11-12 UI 개편 당시에는 JVM 15개·기기 18개, 총 33개 테스트가 실패·오류·건너뜀 없이 통과했습니다. 이후 메뉴 활성화·대화상자 글자색만 수정하고 debug·release 빌드, JVM 15개, Lint를 다시 확인했습니다. 마지막 두 수정 이후 기기 18개 전체는 재실행하지 않았으며 당시 Lint는 오류 0개·경고 18개였습니다. 이 결과는 이번 PNG 교체 이후의 전체 재검증 결과와 구분합니다. 좁은 화면은 일부 배치만 확인했고, 현재 Pleos 파일 선택기의 자동 취소로 기념카드 PNG의 실제 파일 저장은 확인하지 못했습니다.

2026-11-12 검증에 사용한 release 복사본은 로컬 설치 검증용 debug 서명으로 실행해 개발 패널 부재와 UNKNOWN 차단을 확인했습니다. 당시 debug를 재설치하고 원래 저장 데이터를 해시 일치 상태로 복원했습니다. Ubuntu 실행은 미실행이며 배포용 서명·원격 업로드는 진행하지 않았습니다.

## 프로젝트 구성

단일 `:app` 모듈에 Compose 화면, 순수 Kotlin 게임 규칙, JSON 콘텐츠, Typed DataStore 저장을 구성했습니다.

| 경로 | 역할 |
| --- | --- |
| `app/src/main/java/com/example/roadescape/model/` | 콘텐츠·세션·퍼즐 입력·단서 모델 |
| `app/src/main/java/com/example/roadescape/game/` | 자물쇠 판정, 진행 전이, 게이트와 데이터 검증 |
| `app/src/main/java/com/example/roadescape/data/` | assets JSON 로딩, 단일 진행 스냅샷 저장 |
| `app/src/main/java/com/example/roadescape/platform/` | 앱 소유 도착 이벤트·주차 상태 인터페이스 |
| `app/src/main/java/com/example/roadescape/ui/` | ViewModel, Navigation, 화면과 입력 컴포넌트 |
| `app/src/debug/` | 가짜 제공자와 개발 패널 |
| `app/src/release/` | 미연동 제공자와 개발 패널을 표시하지 않는 구성 |
| `app/src/main/assets/demo_package.json` | 테스트 경유지·퍼즐·단서 3개 |
| `app/src/main/res/drawable-nodpi/pdf_*.png` | 사용자 PDF에서 잘라낸 배경·질감·로고·장식 PNG |
| `tools/extract_pdf_assets.py` | PDF PNG 자산 추출 스크립트 |
| `docs/pdf-assets.json` | 자산의 출처와 추출 영역 명세 |
| `app/src/test/`, `app/src/androidTest/` | 게임·저장·화면 검증 코드 |

UI 명령은 ViewModel의 한 이벤트 큐에서 처리합니다. 정답 판정·경유지 완료·단서 지급은 하나의 저장 스냅샷으로 갱신하고, 저장 성공 후 화면 상태를 바꿉니다. 세션 ID와 퍼즐 ID가 지난 화면의 요청은 거절합니다.

## 고정한 도구와 라이브러리

라이브러리 버전은 [Version Catalog](gradle/libs.versions.toml)에서 관리합니다. Gradle Wrapper 버전·배포본 체크섬은 [wrapper 설정](gradle/wrapper/gradle-wrapper.properties)에 있습니다.

| 항목 | 선택값 |
| --- | --- |
| applicationId / namespace | `com.example.roadescape` |
| 앱 버전 | `0.1.0` / versionCode `1` |
| Gradle / Android Gradle Plugin | `9.1.0` / `9.0.1` |
| Kotlin / Compose·Serialization 플러그인 | `2.2.21` |
| Compose BOM / Material 3 | `2025.08.01` / BOM 관리 |
| Activity Compose | `1.10.1` |
| Lifecycle / Navigation Compose | `2.9.3` / `2.9.3` |
| Typed DataStore | `1.1.7` |
| kotlinx.serialization / Coroutines | `1.9.0` / `1.10.2` |
| minSdk / compileSdk / targetSdk | `28` / `36` / `34` |
| 소스·바이트코드 대상 | Java / Kotlin JVM `17` |
| 이번 로컬 실행에 사용한 JVM | Android Studio JBR `25.0.4` |

AGP 9의 내장 Kotlin 구성을 사용하므로 `org.jetbrains.kotlin.android` 플러그인을 별도로 적용하지 않습니다. `compileSdk 36`으로 빌드하되 실행할 Pleos AVD는 API 34입니다. 컴파일 SDK와 에뮬레이터 API가 같은 값이어야 하는 구성은 아닙니다.

## Android Studio에서 열기

1. **File → Open**에서 `car_escape` 프로젝트 루트를 선택합니다. Windows에서 이번 작업 경로는 `C:\car_escape`입니다.
2. **Settings → Build, Execution, Deployment → Build Tools → Gradle**에서 Gradle JDK를 확인하고 Sync를 실행합니다. 이번 로컬 검증은 Android Studio의 JBR 25.0.4를 사용했습니다.
3. **Tools → SDK Manager**에서 Android SDK Platform 36과 Build Tools 36.0.0을 확인합니다. 개인 SDK 경로는 로컬의 `local.properties`에만 둡니다.
4. **Tools → Device Manager**에서 설치된 Pleos Connect AVD를 실행합니다. 기존 AVD를 초기화할 필요는 없습니다.
5. **Build Variants**에서 `app`의 `debug`를 선택하고 실행 대상으로 Pleos AVD를 지정한 뒤 **Run → Run 'app'**을 실행합니다.

초기 Gradle 배포본·의존성 다운로드에는 네트워크가 필요합니다. 앱 실행 중 게임은 네트워크나 API 키를 필요로 하지 않습니다.

## Windows PowerShell에서 빌드·설치

프로젝트 루트에서 실행합니다. Java가 PATH에 없으면 먼저 실제 설치 경로에 맞춰 JDK를 설정합니다.

```powershell
cd C:\car_escape
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

AVD가 실행 중이면 아래처럼 기기를 확인하고 debug APK를 설치합니다. `emulator-5554`는 이번 환경에서 관측한 값이므로 `adb devices` 결과가 다르면 바꿉니다.

```powershell
$demoAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $demoAdb devices
& $demoAdb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
& $demoAdb -s emulator-5554 shell am start -n com.example.roadescape/.MainActivity
```

연결된 테스트 대상이 확인된 환경에서 기기 테스트와 Gradle 설치 task도 실행할 수 있습니다. 여러 기기가 연결돼 있으면 다른 기기에 테스트가 실행되지 않도록 대상을 먼저 확인합니다.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:installDebug
```

## Ubuntu에서 빌드·설치

Android Studio에서 같은 프로젝트 루트를 열고 SDK 경로와 Gradle JDK를 설정합니다. 터미널에서는 사용 중인 JDK의 `JAVA_HOME`과 Android SDK의 `platform-tools` PATH가 설정되어 있어야 합니다.

```bash
cd /path/to/car_escape
chmod +x gradlew
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
adb devices
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.example.roadescape/.MainActivity
# 실행 대상 AVD를 확인한 뒤
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
```

빌드 성공 시 debug APK 경로는 `app/build/outputs/apk/debug/app-debug.apk`입니다. `assembleRelease`의 서명 없는 산출물은 `app/build/outputs/apk/release/app-release-unsigned.apk`이며 배포 서명은 이번 범위에 포함하지 않습니다. 실제 생성 여부와 확인한 보고서 경로는 검증 기록을 확인합니다.

## 개발 패널로 완주하기

1. 로비의 **사건 고르기 → 이 사건을 맡겠습니다 → 수사 시작**으로 이동합니다. 수칙을 확인하고 구성을 선택한 뒤 프롤로그의 **새 테스트 게임 시작**을 누릅니다. 기존 진행이 있으면 초기화를 확인합니다. 이어갈 때는 로비의 **수사 재개**를 선택합니다.
2. 개발 패널의 **현재 경유지 도착 처리**로 현재 경유지의 방문 기록을 저장합니다. 아직 열리지 않은 경유지를 먼저 처리할 수 없습니다.
3. **PARKED**를 누릅니다. 처음 실행하거나 앱이 백그라운드에 다녀오면 주차 상태는 **UNKNOWN**이며 다시 확인해야 합니다.
4. 현재 경유지의 **퍼즐 열기**를 선택합니다. 화면 키패드·방향 버튼·가상 장치 설정 버튼만으로 입력할 수 있습니다.
5. 정답을 입력하고 **자물쇠 당기기**로 제출합니다. 숫자·순서 입력은 **마지막 입력 삭제**, 모든 자물쇠는 **처음부터**를 지원합니다. 자리 수가 맞지 않는 등 형식 오류는 오답 횟수를 올리지 않습니다.
6. **힌트 요청**에서 두 단계의 쪽지를 차례로 **열어보기** 할 수 있습니다. 이미 공개한 힌트를 다시 읽어도 횟수는 늘지 않습니다. **정답 보기 → 확인**은 공개 이력만 저장하며, 직접 제출하거나 **건너뛰기 → 확인**을 눌러야 경유지가 완료됩니다.
7. 상단 **소지품 가방**에서 획득 단서를 열면 내용과 출처, 해결 또는 건너뛰기로 획득한 경위를 볼 수 있습니다.
8. **다음 경유지 보기**로 돌아가 새 현재 경유지의 도착을 다시 처리합니다. 세 경유지를 처리하면 해결·건너뜀·힌트·정답 공개 내역이 완료 화면에 표시됩니다.

| 경유지 | 자물쇠 | 개발 정답 | 보상 |
| --- | --- | --- | --- |
| 테스트 경유지 1 | 숫자형 | `0427` — 첫 `0` 포함 | 테스트 단서 A |
| 테스트 경유지 2 | 순서형 | 위 → 오른쪽 → 아래 → 왼쪽 | 테스트 단서 B |
| 테스트 경유지 3 | 상태 조합형 | 채널 `3`, 레벨 `2` | 테스트 단서 C |

건너뛴 경유지도 단서를 한 번 받습니다. 테스트 단서는 다음 퍼즐의 필수 정답으로 쓰이지 않습니다. 가상 장치 값은 실제 차량 설정을 변경하지 않습니다.

퍼즐 도중 **DRIVING** 또는 **UNKNOWN**을 누르면 퍼즐·힌트·단서와 수첩·해설 등의 내용을 가리고 게임 변경 명령을 막습니다. 개발 패널은 계속 표시되므로 **PARKED**로 되돌린 뒤 저장된 진행에서 재개할 수 있습니다. 도착 버튼은 개발용 방문 이벤트이며 실시간 위치 확인이 아닙니다.

## 새 화면 사용법

- **더보기**에서 탐정 수첩, 저장된 수사, 사건 기록부, 자물쇠 도감, 저장 확인, 탐정 수칙을 엽니다. 상단 **‹**는 이전 화면, **로비**는 시작 화면으로 돌아갑니다.
- 수첩은 경유지별 텍스트를 **메모 저장**으로 보존합니다. 평가는 완료 화면에서 **테마 평가 → 평가 저장**으로 보존합니다. 둘 다 이 기기에만 저장되며 게임 진행과 별개입니다. 저장 전 편집은 주차 중단·화면 이동에서 사라질 수 있습니다.
- 전체 완료 후 **사건 해설**과 **기념 카드 만들기**를 엽니다. **이미지로 저장**의 PNG 출력 코드와 시스템 파일 선택기 호출을 구현했습니다. 현재 Pleos AVD에서는 파일 선택기 스텁이 자동 취소되어 실제 PNG 파일 저장은 미확인입니다. 파일 선택기로 나가면 주차 확인이 무효화되므로 복귀 후 개발 패널에서 PARKED를 다시 확인합니다.
- 실제 패키지는 하나이며 경유지·힌트 수는 기존과 같습니다. 구성 선택은 화면 체험용이며 힌트 정책을 바꾸지 않습니다. 알파벳·색·열쇠 자물쇠는 도감에서 준비 중으로 표시합니다. 실제 내비게이션·카메라·음성 내레이션은 연결하지 않았습니다.

## 저장·복원과 오류 처리

동시에 유지하는 진행 세션은 하나입니다. 방문, 유효한 오답, 힌트 공개, 정답 공개, 해결, 건너뛰기와 단서 보상을 행동마다 저장합니다. 저장 중에는 추가 진행 입력을 막습니다.

- 저장 파일은 앱 내부 `files/datastore/game-session.json`입니다. 앱 프로세스를 종료해도 마지막 **저장 성공한 진행**을 읽지만 주차 상태는 저장하지 않아 다시 `UNKNOWN`에서 시작합니다.
- 키패드의 입력 초안은 영구 저장하지 않습니다. Compose의 화면 상태 복원 범위에서 유지될 수 있으며, 화면 이동·주차 중단 화면·프로세스 재시작에서 초기화될 수 있습니다. 저장된 완료·오답·힌트 이력과는 구분합니다.
- 손상 JSON, 지원하지 않는 저장 버전, 콘텐츠 버전 불일치와 읽기·쓰기 실패를 표시합니다. 자동으로 빈 게임을 덮어쓰지 않으며 **다시 시도**를 제공합니다.
- 사용자가 **새 게임 → 확인**을 선택하면 기존 파일을 같은 내부 폴더의 타임스탬프 `.bak` 파일로 보존한 뒤 새 세션을 만듭니다. 앱 데이터 삭제·앱 제거는 진행과 이 내부 백업도 지웁니다.
- 정적 콘텐츠의 중복 ID, 참조 누락, 미지원 자물쇠 타입과 잘못된 정답·범위는 시작 시 거절합니다. 콘텐츠 버전 자동 마이그레이션은 구현하지 않았습니다.

## 현재 경계와 다음 연동 지점

release에는 개발 패널과 가짜 입력 제공자가 없습니다. 실제 외부 상태 제공자가 아직 없어서 주차는 `UNKNOWN`으로 유지되고 퍼즐 플레이는 허용되지 않습니다. release 빌드 가능 여부와 실차 출시 준비 상태는 별도로 판단합니다.

실제 GPS·지도·차량 SDK·차량 제어·서버·계정·콘텐츠 제작은 이번 구현 범위 밖입니다. 향후 확인된 SDK를 연결할 위치는 `platform/ArrivalProvider`와 `platform/ParkingStateProvider`, 빌드별 `createPlatformSources()`입니다. SDK의 실제 신호·권한·지원 여부를 확인한 뒤 이 경계의 구현을 교체해야 합니다.

## Wrapper 출처와 참고 자료

`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties`는 로컬의 기존 `my-car-buddy` 프로젝트에서 복사했습니다. 새 Wrapper를 임의로 흉내 낸 파일이 아니며 이번 환경에서 `--version`으로 Gradle 9.1.0 실행을 확인했습니다. 스크립트의 기존 저작권·Apache License 2.0 고지를 유지했습니다. Gradle 원본과 라이선스는 [Gradle 9.1.0 저장소](https://github.com/gradle/gradle/tree/v9.1.0)와 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)에서 확인할 수 있습니다.

배포본 URL은 `https://services.gradle.org/distributions/gradle-9.1.0-bin.zip`, 설정된 SHA-256은 `a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806`입니다. 개인 SDK 경로, 로그, 테스트 산출물과 내부 백업은 공유 소스에서 제외합니다.

- [Pleos 앱 개발 시작](https://document.pleos.ai/en/docs/pleos-only/getting-started)
- [Pleos 에뮬레이터 설치](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [AGP 9.0 공식 릴리스 안내](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Compose 프로젝트 설정](https://developer.android.com/develop/ui/compose/setup)
- [Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore)

이 작업은 로컬 프로젝트 작성·검증 범위입니다. 원격 저장소 업로드나 공개 배포는 포함하지 않습니다.
