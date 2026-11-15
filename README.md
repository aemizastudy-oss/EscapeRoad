# EscapeRoad

**차량으로 관광 명소를 하나의 이야기로 연결하는 스토리 방탈출 여행 앱입니다.** 팀 **D2E**가 Pleos Connect용으로 개발하며, 첫 사건 「시역(屍疫)」은 수원 5개 장소에서 관광 → 퍼즐 → 제출 → 단서 획득 → 저장·재개로 이어집니다.

앱 이름은 **EscapeRoad**입니다. 앱 ID는 `com.team.carescape`, 현재 버전은 `0.4.0` (`versionCode=4`)입니다.

## 제출용 Release APK

> **D2E 제출용 서명 Release APK**입니다. 시연 위치를 명시적으로 입력하는 REVIEW 모드를 제공하며, 실제 반경 검사·15분 대기·차량 SDK 안전 조건을 유지합니다. 검증한 범위와 제외 항목은 아래에 구분합니다. 접수 페이지·메일 제출은 팀이 진행합니다.

| 항목 | 내용 |
| --- | --- |
| 소스 저장소 | [aemizastudy-oss/EscapeRoad](https://github.com/aemizastudy-oss/EscapeRoad) |
| 작업 브랜치 | `release/v0.4.0` |
| 최종 제출 커밋 | 최종 커밋·푸시 후 `git rev-parse HEAD`로 확인해 접수 메일과 GitHub Release 본문에 기록 |
| APK 파일명 | `D2E_EscapeRoad_release.apk` |
| APK 내려받기 | [Release 페이지](https://github.com/aemizastudy-oss/EscapeRoad/releases/tag/v0.4.0-submission) · [APK 다운로드](https://github.com/aemizastudy-oss/EscapeRoad/releases/download/v0.4.0-submission/D2E_EscapeRoad_release.apk) |
| 저장소 내 APK 안내 | [apk/README.md](apk/README.md) |
| APK SHA-256 | `0e5f93dc6eb564a5687ecc586607920d92ded88edd3258237267cccba23cbf8b` |
| 서명 인증서 SHA-256 | `303cea1b55209e766b3e78d2a294dfe7bf1aa91ee399080670b437c722873065` |
| 제출 APK 설치·실행 | 최종 서명 Release를 새 Pleos 에뮬레이터에 설치·실행하고 실제 SDK의 P·정지·UX 허용 상태에서 관광 위치 시연을 선택해 화성행궁 반경 밖 입력 차단, 수동 관광 시작, 실제 15분 카운트와 조기 복귀 차단을 확인했습니다. 앱 강제종료·재실행 후 수사 재개로 같은 관광 단계와 남은 시간(약 15분에서 약 13분)이 이어졌습니다. |

APK는 119252036바이트(약 119.3 MB)로, **GitHub Releases의 첨부 파일**로 제공합니다. 제출 페이지에는 위 저장소와 최종 Release 페이지 주소를 입력하고, 접수 메일에는 저장소 주소와 최종 커밋 번호를 적습니다. 서명키·비밀번호·개인 SDK 경로는 저장소에 포함하지 않습니다.

## 실행 환경과 필요한 설정

| 구분 | 확인된 환경·설정 |
| --- | --- |
| 테스트 OS | Windows 11 |
| 차량 에뮬레이터 | Pleos Connect **v2.1.0** |
| Android 버전 | Android Automotive OS, **API 34** |
| 화면 규격 | **2560 × 1440**, **160 dpi**, 가로 화면 |
| 기존 검증 기기 식별자 | `emulator-5554`, Android 사용자 `10` |
| 개발 도구 | Android Studio, JBR 25, AGP 9.0.1, Kotlin 2.2.21, Gradle 9.1.0 |
| Android 빌드 설정 | compileSdk 36 / targetSdk 34 / minSdk 28, JVM 대상 17 |
| 외부 서비스 인증 | 현재 공모전용 실행 경로에는 네이버 Maps·지역 검색·TourAPI 인증키가 필요하지 않음 |
| 차량 입력 | Pleos Vehicle SDK의 기어·속도·방향지시등·창문·좌석별 독서등 등 실제 지원 신호 |
| 심사용 체험 입력 | REVIEW: 사용자가 입력한 시연 위치로 실제 좌표·반경을 검사하며, 실제 15분 대기 유지 |
| 조작 허용 | 앱 foreground, 최신 P·실제 정지·CarUx 이용 허용을 확인해야 함 |

일반 Android 스마트폰에서 Pleos 서비스와 차량 기능을 사용할 수 있다고 가정하지 않습니다. Ubuntu용 빌드 명령은 아래에 제공하지만, Ubuntu 실행과 실차 주행은 검증하지 않았습니다.

## APK 설치와 실행

아래 순서로 제출용 APK를 실행합니다. 기기에서 확인한 단계와 아직 확인하지 않은 범위는 검증 표를 참고하세요.

1. 위 표의 Release 페이지에서 `D2E_EscapeRoad_release.apk`를 내려받습니다. Pleos Connect v2.1.0 에뮬레이터를 실행하고 설치 대상과 Android 사용자를 확인합니다.
2. APK를 에뮬레이터에 설치합니다. 아래 명령의 기기 ID와 사용자 번호는 기존 검증 환경의 예시입니다.

   ```powershell
   adb -s emulator-5554 install --user 10 -r D2E_EscapeRoad_release.apk
   adb -s emulator-5554 shell am start --user 10 -n com.team.carescape/.MainActivity
   ```

3. Pleos Virtual Console에서 차량 상태를 **P·속도 0**으로 설정하고 실제 SDK 상태가 앱에 전달되는지 확인합니다. 앱의 **SDK 상태 → 권한 확인**에서 플랫폼이 허용하는 권한을 요청합니다. 콘솔 표시만으로 앱이 같은 상태를 수신했다고 판단하지 않습니다.
4. 안전 정차한 로비에서 오른쪽 위 **실행 모드 → 관광 위치 시연**을 선택하고 **사건 고르기 → 시역 → 여행 시작**으로 진행합니다. REVIEW는 LIVE·MOCK와 진행 기록을 분리합니다.
5. **시연 이동 화면 열기**를 누른 뒤 오른쪽 위 체험 설정을 엽니다. **목적지 위치 입력**으로 현재 장소의 시연 위치를 직접 입력하고 설정 창을 닫습니다. 입력한 위도·경도와 목적지의 **실제 거리·반경 조건**을 검사합니다. 반경 밖 위치를 입력하면 최초 도착·관광 시작이 차단돼야 합니다. 이 위치는 실제 차량 GPS 방문 인증이 아닙니다.
6. 목적지 반경과 P·정지·CarUx·foreground 조건을 충족하면 사용자가 **관광 시작**을 눌러 실제 시작 시각을 저장합니다. **실제로 15분을 기다립니다.** 시간 생략·자동 관광 시작·자동 출발은 제공하지 않습니다.
7. 관광 시작 후 15분이 지나면 안전 정차 상태에서 **차로 돌아왔어요**를 누릅니다. 방향지시등·창문·독서등 문제는 콘솔에서 실제 SDK 신호를 입력하고, 앱에서 **제출**합니다. 정답·단서·진행 기록을 함께 저장하며 재실행 후에도 이어갈 수 있습니다.

**REVIEW의 데이터 경계:** 시연 위치만 사용자가 입력하며, 위치 조건과 실제 15분 대기는 생략하지 않습니다. 차량 P·정지·CarUx·foreground와 차량 퍼즐 답안은 실제 SDK에서 받습니다. REVIEW에서의 진행을 실차 방문 인증이나 일반 LIVE 기록으로 합치지 않습니다. 화면에는 **위치 시연 / 실제 15분**이 표시됩니다.

기존 Debug 앱과 신규 Release APK는 서명이 다를 수 있어 같은 앱 ID로 업데이트 설치가 거부될 수 있습니다. 기존 여행 기록을 보존하고, 서명이 일치하는 설치본 또는 별도의 검증 환경에서 설치 여부를 확인합니다.

## 실행 모드와 관광 조건

| 모드 | 위치·차량 입력과 사용 목적 |
| --- | --- |
| LIVE | 실제 Pleos 위치·차량 SDK를 사용하는 일반 경로. 최초 위치 정확도 제한은 아래 참고 |
| REVIEW | 심사용 체험 경로. 명시적으로 입력한 시연 위치의 위도·경도·반경 검사와 실제 15분 대기를 유지하고, 안전 상태·차량 퍼즐 답안은 실제 SDK 사용 |
| MOCK | 기존 Debug 개발·회귀용 모의 입력. 기존 Debug 명시적 시간 생략·자동 시연 주행은 REVIEW 기능이 아님 |

REVIEW의 진행 기록은 LIVE·MOCK와 구분하며, 모드 전환은 안전 정차한 로비에서 진행합니다. 임의의 SDK 미수신을 P·정지로 간주하지 않습니다.

| 단계 | 적용 조건 |
| --- | --- |
| 최초 도착·관광 시작 | 목적지 반경 기본 500m, 최신 위치·정확도·P·정지·CarUx·foreground·정상 시계 |
| 관광 시간 | 사용자가 관광 시작을 누른 실제 시각부터 계산. 반경 밖 이동·GPS 단절만으로 멈추거나 초기화하지 않음 |
| 복귀·문제 입력·제출 | 현재 모드·챕터의 유효한 관광 시작 기록, 최소 15분, 정상 시계, P·정지·CarUx·foreground. 위치는 다시 요구하지 않음 |
| 다음 장소 관광 시작 | 새 장소의 위치와 안전 조건을 다시 확인 |

관광 시작 후 15분은 목적지 반경 안에 연속으로 머물렀다는 위치 증명이 아닙니다. 복귀는 사용자가 버튼으로 요청하며, 15분 경과나 탑승 신호만으로 다음 화면을 자동으로 열지 않습니다. 세부 계약은 [관광 시작과 복귀 정책](docs/tour-return-policy.md)을 따릅니다.

**현재 일반 LIVE의 최초 관광 시작 제한:** Pleos FusedLocation의 미터 정확도 계약을 확인하지 못해 어댑터가 `accuracyMeters=null`을 설정합니다. 최초 위치 정책은 정확도 미상을 허용하지 않으므로, 실제 위치 표본이 수신돼도 관광 시작이 보류됩니다. REVIEW의 명시적 시연 위치 입력은 이 SDK 계약을 확정하거나 일반 LIVE 위치 인증을 해결한 결과가 아닙니다.

SDK의 READY는 연결 준비 상태입니다. 실제 위치 인증, 차량 답안 수신, 길 안내 완료, 가청 음성 출력은 각각 확인해야 합니다. Pleos Connect v2.1.0에서는 온디바이스 TTS의 `ON_DEVICE_ENGINE_ERROR`를 관찰했으며, 음성·배경음의 실제 청취 품질과 실차 주행 결과를 빌드 성공으로 대신하지 않습니다.

## 검증 범위

**제출용 변경의 최신 결과**

| 항목 | 확인 결과 |
| --- | --- |
| 단위 테스트 | **215개 통과**, 실패·오류·skip 0. REVIEW 좌표·반경·실제 15분 경계·모드/안전/시계 조건 포함 |
| Debug·Release·기기 테스트 APK 빌드 | 통과 |
| 신규 UI 테스트 | Debug plain Compose 테스트 **2개 통과**. 선택 콜백·위치 입력·비활성화·안내 검증 |
| 최종 Release 서명 | APK Signature Scheme v2 유효, `debuggable=false`, 앱 표시명 `EscapeRoad` 확인 |
| 최종 Release 기기 확인 | 최종 서명 Release를 새 Pleos 에뮬레이터에 설치·실행하고 실제 SDK의 P·정지·UX 허용 상태에서 관광 위치 시연을 선택해 화성행궁 반경 밖 입력 차단, 수동 관광 시작, 실제 15분 카운트와 조기 복귀 차단을 확인했습니다. 앱 강제종료·재실행 후 수사 재개로 같은 관광 단계와 남은 시간(약 15분에서 약 13분)이 이어졌습니다. |
| 검증 제외 | 실제 15분 경과 후 복귀·문제 해제, 차량 퍼즐 전체 입력·제출, 전체 스토리 완주, 실차 주행·실제 GPS 도착 인증·실제 관광지 방문은 이번 기기 검증에서 확인하지 않았습니다. 시연 좌표를 UI로 입력했으며 시간 우회·앱 상태 파일 주입·가짜 SDK 안전 신호는 사용하지 않았습니다. |

아래는 **관광 복귀 정책 변경 시점의 이전 결과**입니다. 과거 Debug 검증을 최종 Release 완주로 표시하지 않습니다.

| 항목 | 기존 확인 결과 |
| --- | --- |
| Debug·Release·기기 테스트 APK 빌드 | 통과. 당시 Release 산출물은 unsigned |
| 단위 테스트 | 203개 통과, 실패·오류·skip 0 |
| 최근 기기 회귀 | MOCK 위치·안전 fixture를 이용한 2개 통과 |
| 실제 SDK 자물쇠 시연 | 이전 Debug 격리 LIVE 세션에서 5개 장소·23개 자물쇠와 저장·재개 확인. 위치·관광 대기 제외 조건을 사용한 결과 |

| 실제 LIVE 최초 도착·실차·Ubuntu | 미확인 또는 제외. 최초 위치 정확도 문제는 위 제한 참고 |

회차별 APK·SDK·기기 환경과 성공·제외 항목은 [검증 로그](docs/verification-log.md)에 기록합니다. 아래 상세 기록의 과거 테스트 수치와 현재 제출 APK 검증을 구분합니다.

## 소스 코드로 빌드하기

Android Studio에서 저장소 최상위 폴더를 열어 Gradle Sync를 진행합니다. Android SDK Platform 36과 `android.car` 컴파일 라이브러리, Pleos Maven 저장소 접근이 필요합니다. 개인 SDK 경로는 Git에서 제외하는 `local.properties`에 설정합니다.

개발·검증용 Debug 빌드:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

```bash
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. Android Studio에서 `app` 실행 구성과 Pleos 에뮬레이터를 선택해 Run합니다.

Release 서명은 로컬의 `keystore.properties`가 있을 때 적용합니다. [keystore.properties.example](keystore.properties.example)의 `storeFile`, `storePassword`, `keyAlias`, `keyPassword`를 본인 환경에 맞게 작성합니다. `storeFile`에는 저장소 밖의 release 키 경로를 지정합니다. 실제 키·비밀번호가 든 파일은 Git에서 제외하며, 키가 없는 다른 PC에서는 자신이 소유한 별도 키로 재현 빌드할 수 있습니다.

Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

Ubuntu:

```bash
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

로컬 서명 설정이 없으면 unsigned 산출물이 생성될 수 있으며, `app-release-unsigned.apk`는 실행 가능한 제출 APK로 사용하지 않습니다. 서명된 산출물은 `app/build/outputs/apk/release/app-release.apk`이며, 제출 파일명은 `D2E_EscapeRoad_release.apk`입니다. 파일 해시는 [SHA256SUMS.txt](apk/SHA256SUMS.txt), 서명·검증 절차는 [제출용 Release 안내](docs/submission-release.md)를 참고하세요.

제출할 소스의 최종 커밋 번호는 커밋·푸시 후 아래 명령으로 확인해 접수 메일과 GitHub Release 본문에 기록합니다.

```bash
git rev-parse HEAD
```

## 구현·개발용 시연 상세

아래는 기존 구현 및 Debug용 시연 안내입니다. **아래의 Release LIVE 전용·개발 패널 제외 설명은 REVIEW 추가 전의 이력이며, 실행 모드·서명·제출 방법은 위 현행 제출용 항목이 우선합니다.** 과거 통합 당시의 테스트 숫자는 해당 시점의 기록입니다. Debug 격리 LIVE 검증 세션의 위치·시간 예외는 REVIEW에 적용하지 않습니다.

<details>
<summary>기존 통합 스토리·자물쇠·Debug 시연·SDK 연결 상세 펼치기</summary>


Pleos Connect용 차량 여행 미스터리 앱입니다. v0.2의 SDK·안전 제한·저장·자물쇠를 기반으로 v0.3의 「시역(屍疫)」 스토리와 임시 연동 문제를 통합했습니다. 관광 → 자물쇠 → 제출 → 단서 → 저장·재개를 수원 5개 장소에 적용합니다. **Debug·Release 빌드, 단위 테스트 71개, 화성행궁 우선 기기 테스트 1개와 전체 코스·회귀 7개, Lint 오류 0개를 확인했습니다.** 최종 결과와 이전 검증 이력은 [검증 로그](docs/verification-log.md)에 구분해 기록합니다. 실제 관광 방문·길 안내 완료·가청 음성 성공과는 구분합니다.

앱 표시명은 `Escape Road`이고, 런처 아이콘은 금색 열쇠 그림(`res/mipmap-*/ic_launcher.png`, 원본 축소본 `docs/img/brand/app-icon-key-512.png`)입니다.

- 앱 ID: `com.team.carescape`
- 앱 버전: `0.4.0` (`versionCode=4`), 공유 브랜치: `release/v0.4.0`
- 도구 설정: AGP 9.0.1, Kotlin 2.2.21, Gradle 9.1.0, compileSdk 36 / targetSdk 34 / minSdk 28
- 현재 LIVE의 최초 도착·관광 시작 인증은 위치 정확도의 미터 단위 근거가 없어 보류합니다. 저장된 관광을 마친 뒤의 복귀·문제 열기에는 위치를 다시 요구하지 않습니다. SDK 준비 상태를 실제 방문·길 안내·발화 성공으로 표시하지 않습니다.
- 자세한 구현 범위·권한·제약은 [SDK 연결 안내](docs/sdk-ui-integration.md), 제품 요구는 [ROAD_ESCAPE_sdk.md](docs/ROAD_ESCAPE_sdk.md), 이전 시연 이력은 [검증 로그](docs/verification-log.md)를 참고하세요.


### 2026-11-15 공모전용 구성

여행 도우미를 **직접 준비한 주차장·식당 카드 + Pleos 길 안내**로 전환했다. 네이버 지도·지역 검색·TourAPI 호출과 인증값 주입을 제거했다. 입구 좌표 미확인 장소는 주소를 제공하고 `주변 관광지로 안내`로 관광지 대표점만 요청한다. 외부 API 검색으로 저장한 옛 목록은 새 목록으로 재사용하지 않는다. [사용법](docs/travel-guide.md), [장소 출처](docs/curated-places.md), [공모전 점검과 남은 확인](docs/contest-compliance-review.md)을 참고한다.

아래 이전 검증 숫자는 당시 이력이며 이번 변경의 결과는 [검증 로그](docs/verification-log.md) 최신 항목을 따른다. Release 서명·원격 업로드·최종 제출은 별도다.

### 통합 스토리 진행

안전한 정차가 확인되면 **사건 고르기 → 시역 → 여행 시작**으로 진행합니다. 각 장소의 최초 도착과 **관광 시작**에는 새 위치·목적지 500m 반경·P·정지·CarUx를 확인하고 시작 시각을 저장합니다. 사용자 후속 결정에 따라 **복귀·문제 열기·답안 제출에서는 위치·정확도·최신성·반경을 다시 검사하지 않습니다.** 저장된 현재 여행 모드·챕터의 시작 기록과 정상 시계 기준 최소 15분, P·정지·CarUx·앱 foreground를 확인합니다. 반경 밖이나 GPS가 끊긴 동안의 시간도 경과 시간에 포함되며 연속 15분 체류의 위치 증명은 아닙니다. **차로 돌아왔어요**는 사용자가 직접 누릅니다. 관광 조건만 채워서는 단서·재료를 지급하지 않고, 화면의 **제출**로 정답을 확인한 뒤 결과와 단서를 함께 저장합니다. [관광 시작과 복귀 정책](docs/tour-return-policy.md)이 이전 위치 재확인 안내보다 우선합니다.

새 여행은 **탐색형 퍼즐판(flow 5)**으로 시작합니다. 장소마다 오디오 증언 → 현장 관찰 → 차량 조작 → 앱 안 추리 단계를 차례로 풀고, 인물 일지를 모은 **사건 기록부**를 단서로 씁니다. 힌트 2·3단계와 함정은 시귀 각성도를 올리고, 각성도와 진상 추리 결과에 따라 진·일반·배드 엔딩으로 나뉩니다. 단계·정답·근거는 [시역 정식 퍼즐판](docs/story-puzzles.md)에 정리했습니다. 현재 차량 문제는 좌우 깜빡이·창문·좌석별 독서등 SDK 입력을 받으며 일반 MOCK에서는 화면 버튼을 사용합니다. [실제 SDK 연결·콘솔 검증](docs/vehicle-puzzle-sdk.md)을 참고하세요.

v0.4.0의 **연동 검증용 임시 문제**(화성행궁 `0427`, 화홍문 `↑ → ↓ ←`, 만석거 가상 장치 `3·2`, 박물관 `1500`, 호수공원 `← ↑ → ↓`)로 시작한 여행은 그대로 이어서 할 수 있습니다.

오답 횟수·열어 본 힌트·해결 기록은 여행과 함께 저장합니다. 재실행 뒤에도 현재 챕터의 관광 시작 기록과 경과 시간을 복구하고 새 P·정지·CarUx 상태를 확인합니다. 복귀에는 위치를 요구하지 않으며 다음 장소의 관광을 시작할 때 다시 위치를 확인합니다. 같은 정답을 다시 제출해도 단서·재료를 중복 지급하지 않습니다. 마지막 장소도 관광과 자물쇠를 마친 뒤 엔딩으로 갑니다.

**기존 v0.2 여행:** 완료한 장소·단서·재료는 유지합니다. 사용자 결정에 따라 현재 미완료 장소의 관광은 다시 시작합니다. 이전 시작 기록을 덮어쓰지 않고 `archivedTrips`에 원본을 보관한 뒤 새 통합 세션에서 이어갑니다. 완료한 여행은 완료 상태로 보존하며, 읽기만 할 때는 원본 파일을 쓰지 않고 다음 정상 저장에 schema 2로 기록합니다.

스토리 원문 출처는 [v0.3의 수원 코스 JSON](https://github.com/aemizastudy-oss/EscapeRoad/blob/b003204dd5810be00627cb491627eec26afd9e1a/app/src/main/assets/suwon_tour_package.json)입니다. 통합 본문은 `game/IntegratedSiyeok.kt`, 판정은 `game/StoryPuzzles.kt`, 진행은 `game/StoryEngine.kt`에서 관리합니다. 앱 ID와 SDK 연결은 v0.2를 유지하므로 별도 앱이 생성되지 않습니다.

### 시연 바로가기 (debug)

debug 빌드의 오른쪽 위 **시연 · SIM** 패널에서 MOCK과 P 정차를 고른 뒤 「시연 바로가기」로 원하는 지점(도착 1~5 · 장면 1~5 · 마지막 공개 · 진/일반/배드 엔딩)에 바로 갈 수 있다. 앞부분을 정답으로 자동 진행한 새 시연 여행으로 바뀐다. 관광 시작 기록이 있는 현재 챕터의 복귀·문제 열기에는 위치 표본을 다시 요구하지 않는다. 새 장소의 관광을 시작하려면 「현재 장소 위치 시연」과 정차 조건을 확인한다.

### 자물쇠 체험

로비의 자물쇠 체험 버튼은 제거했습니다. 내부 체험 화면과 회귀 테스트는 남아 있으며, 네 종류 입력 컴포넌트는 이야기의 문제에서 사용합니다. 이번 화면은 입력 연습이며 기존 시역의 정답·챕터·관광 기록을 바꾸지 않습니다. 화면의 **제출**을 누를 때만 현재 전체 답안을 읽어 표시하며 자동 정답 판정은 하지 않습니다.

| 종류 | 조작 |
| --- | --- |
| 색상 | 4개 다이얼을 위아래로 밀거나 눌러 변경. 각 칸은 빨강 → 노랑 → 초록 → 파랑으로 순환 |
| 방향 | 가운데 원에서 상하좌우로 밀었다가 놓으면 한 방향 추가. 아래에 순서대로 표시하며 초기화로 전체 삭제 |
| 영어 | 자물쇠를 눌러 키보드로 입력. A–Z만 대문자로 5칸에 표시. 키보드 완료는 제출하지 않음 |
| 숫자 | 4개 다이얼을 위아래로 밀거나 눌러 0–9 순환. 앞자리 0도 유지 |

방향 입력의 짧은 움직임·원 밖 시작·취소·멀티터치는 답으로 읽지 않습니다. 방향은 입력이 있을 때, 영어는 5글자를 채웠을 때 제출할 수 있습니다. 입력은 영구 저장하지 않으며 체험을 새로 열면 초기값으로 시작합니다. P·실제 정지·CarUx·foreground 조건이 풀리면 화면과 키보드를 닫고 변경·제출을 거절합니다.

영어 입력의 키보드 배열은 차량 키보드 설정에 따르므로 영문 입력 모드로 전환해야 할 수 있습니다. 앱은 입력된 ASCII 영문만 받습니다.

이미지 원본은 `docs/img/{color_lock,direction_lock,english_lock,num_lock}.png`이고, 앱은 동일 파일을 `app/src/main/res/drawable-nodpi/`에서 읽습니다. 자물쇠 외형은 원본 PNG이며 창의 값과 터치 영역만 Compose로 겹칩니다. 재사용 컴포넌트는 `ui/locks/DialLocks.kt`, `ui/locks/EnglishDialLock.kt`입니다. 각각 `value`, `onValueChange`, `onSubmit`, `modifier`, `enabled`, 선택적 `feedbackContent`를 받으며 화면 위치와 제출 후 동작은 호출자가 정합니다.

### 빌드와 실행

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

### DEBUG 시연과 재개

1. 오른쪽 위 개발 패널에서 **시연 MOCK**을 선택하고 **P 정차**를 명시적으로 입력합니다. 최초/복귀 상태를 자동 P로 만들지 않습니다.
2. 시역을 시작하고 **시연 이동 시작** 후 **도착한 것으로 하기 (시연)** 또는 개발 패널의 **현재 장소 위치 시연**을 누릅니다. 실제 위치·길 안내가 아닙니다.
3. 도착 화면에서 **관광 시작**을 눌러 저장을 완료합니다. 앱을 닫아도 시작 기록·챕터·획득 단서를 복구합니다.
4. 실제 15분을 기다리거나 DEBUG의 **15분 경과 시연**을 직접 누릅니다. 이 동작은 MOCK 기록에만 표시하며 LIVE에는 적용되지 않습니다.
5. 현재 챕터의 관광 시작 기록이 저장돼 있다면 앱 재실행·출발 뒤에도 **P 정차**를 다시 확인하고 **차로 돌아왔어요**를 누릅니다. 복귀에는 **현재 장소 위치 시연**을 다시 요구하지 않습니다. 자물쇠에서 답을 맞춰 **제출**해야 단서로 진행합니다. 다음 장소의 관광 시작에는 새 위치·안전 조건을 다시 확인하며 마지막 장소에도 15분·자물쇠 조건이 적용됩니다.

MOCK/LIVE 진행은 별도 저장합니다. LIVE 실패 시 자동으로 시연 입력을 섞지 않습니다. release에는 개발 패널·모의 P·시간 생략이 없습니다. 현재 거리 숫자나 지정 주차장을 안내하지 않으며 합법적인 주차 위치는 이용자가 확인합니다.

### 실제 SDK 확인

**SDK 상태 → 권한 확인**으로 플랫폼이 허용하는 런타임 권한을 요청하고 연결 상태를 확인합니다. 정차용 게임·내레이션은 foreground·최신 P·정지·CarUx 허용을 모두 요구합니다. 해당 runtime은 background에서 구독·음성을 멈추며, 아래의 별도 주행 안내 서비스는 예외로 분리돼 있습니다.

현재 음성은 Pleos ON_DEVICE입니다. 원격의 주행 챕터·배경음악을 병합했으며 이동 중에는 별도 서비스가 짧은 목적지 안내와 BGM을 담당합니다. 정차용 스토리·퍼즐 화면의 안전 제한은 유지합니다. [주행 챕터](docs/drive-chapter.md)의 적용 범위와 검증 이력을 참고하세요. Vehicle은 읽기 전용 battery/brake 보조 상태와 좌우 깜빡이·와이퍼·앞왼쪽 창문 입력을 연결했습니다. 디버그에서 관광은 MOCK, 차량 안전·답안은 실제 SDK로 선택해 콘솔 조작을 검증할 수 있습니다. 이 선택은 LIVE 관광 인증을 대체하지 않습니다. SpeechToText 어댑터는 있지만, 현재 시역에는 음성 정답·녹음 시작 UI가 없습니다. 공모전용 실행 경로에는 외부 장소 검색·지도·음성 서비스 클라이언트가 없습니다. SDK 내부 통신과 Debug 로컬 콘솔 통신은 별도입니다.


</details>
