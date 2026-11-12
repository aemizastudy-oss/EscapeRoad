# Pleos SDK 플랫폼 연동 경계

기준일: 2026-11-13. [ROAD_ESCAPE_sdk.md](ROAD_ESCAPE_sdk.md)의 실제 연동 요청을 바탕으로 공식 문서와 로컬 Maven AAR·sources JAR·`javap`를 대조했습니다. 이 문서는 **어댑터 구현과 API 확인 근거**입니다. 이번 앱의 빌드·에뮬레이터·실차에서 각 기능이 성공했다는 뜻은 아니며, 실제 실행 결과는 [검증 기록](verification.md)과 구분합니다.

## 고정한 SDK와 확인 근거

| SDK | 버전 | 실제 호출 경계 |
| --- | --- | --- |
| FusedLocation | `1.1.3` | `isSupported`, `initialize`, 위치 갱신 리스너 등록·해제, `release` |
| NaviHelper | `3.0.5` | 연결 결과 초기화, 이벤트 리스너, `requestRoute(RouteInfo)`, `release` |
| Vehicle | `2.3.2` | Brake·EvBattery 도메인 획득, 읽기 capability 확인, 현재 브레이크·SOC 조회 |
| TextToSpeech | `3.3.1` | `ON_DEVICE`, 초기화·이벤트·한국어 locale·audio attributes, `speak`·`stop`·해제 |
| SpeechToText | `3.3.1` | `ON_DEVICE`, 초기화·결과 리스너·한국어 locale, 명시적 `request`·`stop`·해제 |

Maven은 공식 Pleos 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`를 그룹 제한과 함께 추가했습니다. 기존 AGP·Kotlin·Compose·Gradle 버전은 바꾸지 않았습니다. 컴파일에는 `useLibrary("android.car")`로 AAOS 공개 API를 사용하고, Manifest의 공유 라이브러리 선언은 **선택 연결(`required=false`)**로 둡니다. 설치 시 이름으로 확인하는 공유 라이브러리와 실행 시 ClassLoader에서 찾는 공개 클래스의 가용성은 별도로 확인합니다.

로컬 AAR SHA-256:

| 파일 | SHA-256 |
| --- | --- |
| `Vehicle-2.3.2.aar` | `e11fdfe498cad3ecc0d5a2a6992b0c4a4d135195f1455018127bc069fd5ef937` |
| `FusedLocation-1.1.3.aar` | `20ff6dc2efbea53f32d1ce4712d517fc4abac3a9ce4b990f4aca52c35ebef081` |
| `NaviHelper-3.0.5.aar` | `15fadd11c7c611b1abdf7ebf9ce91f2a6ce2728e60b4e38b4343e11bb9c12e97` |
| `TextToSpeech-3.3.1.aar` | `bfbfb58376c69604afad3389878980461971683f0c51f619d03ab74b5e6a0375` |
| `SpeechToText-3.3.1.aar` | `35109ad13400a7fa103cca9fabbf9b785e63a6a98b17a0698a1aef51458d37bf` |

`javap`로 SDK 진입점, Fused `LocationData`, TTS/STT 리스너, Brake·EvBattery 함수 시그니처를 확인했습니다. 조회용 추출 JAR는 공유 소스가 아닌 `verification-artifacts/sdk-api/`에 둡니다. 기존 `my-car-buddy`의 구현·기록은 API 사용 단서로 읽었으며 그 프로젝트의 코드나 데이터를 수정하지 않습니다.

## 앱 연결 계약

구현은 `com.example.roadescape.platform.sdk`에 있습니다. 애플리케이션이 `PleosSdkRuntime(context)` 인스턴스 하나를 소유하고, 공개 함수는 메인 스레드에서 호출합니다.

- `locations: StateFlow<TourLocation?>`: LIVE 갱신 위치와 원본 시각·수신 단조 시각·오류 상태. 원시 좌표를 영구 저장하거나 로그에 쓰지 않습니다.
- `safety: StateFlow<TourSafety>`: foreground·P·실제 정지·CarUx 허용을 독립 Boolean으로 전달합니다. 하나라도 확인되지 않으면 해당 값은 false입니다.
- `status: StateFlow<PleosSdkStatus>`: SDK별 준비·미지원·권한·오류·오래됨, 경로 이벤트, 원시 confidence, 선택 Vehicle 보조 값, 음성 동작을 표시합니다. 초기화와 실제 값·가청 출력 성공은 구분합니다.
- `revision: Long`: 위치 갱신·만료, 안전 상태 변화와 lifecycle 무효화에서 증가합니다. 화면에서 누른 뒤 중단 상태가 끼어든 명령을 거절하는 데 사용할 수 있습니다.
- `start`, `setForeground`, `stop`: foreground에서만 구독하고 중지 후 다시 시작할 수 있습니다. `close`는 최종 해제입니다.
- `navigate(WaypointConfig): Boolean`: 안전한 상태에서 설정된 좌표로 요청합니다. true는 요청을 전송했다는 뜻이며 실제 경로 완료는 이벤트 상태로 따로 확인합니다.
- `speak(text)`, `listen(onResult)`, `stopAudio`: 안전 게이트를 매번 확인합니다. 음성 인식 결과는 입력 후보이며 게임 계층에서 현재 퍼즐·세션·관광 조건을 다시 확인해야 합니다.

debug의 MOCK/LIVE 선택, release LIVE 고정, 관광 시작·15분·경유지 정책과 게임 저장은 SDK 어댑터 밖에서 연결합니다. Mock 기록이나 가짜 주차 입력을 LIVE 증거로 만들지 않습니다.

## 위치와 관광 인증

도착 근거는 `registerFusedLocationCallback`의 새 `onChanged` 표본만 받습니다. 과거 조회인 `getLastKnownLocation`은 사용하지 않습니다. 좌표 유효 범위, 표본 시각, 수신 시각을 검사하고 이전·중복 표본으로 유효 시간을 늘리지 않습니다. 15초 갱신 유효 시간과 1초 이내 미래 시각 허용은 이 앱의 보수적 설정이며 Pleos의 보장된 갱신 주기가 아닙니다. 콜백이 끊기면 위치를 오래됨으로 바꿉니다. [공식 위치 접근 안내](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)

**정확도 단위는 미확정입니다.** 확인한 `LocationData`에는 `latitude`, `longitude`, `altitude`, `heading`, `confidence`, `timestampMillis`가 있고, `accuracyMeters`는 없습니다. 공식 모델 페이지와 sources JAR에서 confidence를 미터 단위 오차로 바꿀 근거를 확인하지 못했습니다. 따라서 원시 confidence는 진단에만 보존하고 `TourLocation.accuracyMeters=null`을 전달합니다. 정확도를 요구하는 관광 정책은 이 상태를 검증 보류로 처리하며 임의 값으로 인증하지 않습니다. [공식 모델 페이지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/entry)

관광지 대표 좌표가 null이면 길 안내를 요청하지 않습니다. NaviHelper의 도착 이벤트도 새 Fused 위치 인증·관광 시간·주차 조건을 대신하지 않습니다. [목적지 요청과 이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)

## 안전 상태와 선택 Vehicle 신호

공식 주행 제한 경계는 AAOS `CarUxRestrictionsManager`입니다. 이 앱은 `isRequiresDistractionOptimization == false`와 `activeRestrictions == 0`에 더해 P 기어와 속도 절댓값 0.1m/s 미만을 모두 요구합니다. 미지원·권한 거절·null·오래됨·연결 끊김은 허용으로 바꾸지 않습니다. 임의의 `distractionOptimized=true` 선언이나 주행 정책 우회를 추가하지 않았습니다. [Pleos 주행 제한 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)

현재 Pleos API 34 이미지에서는 최초 필수 연결 선언으로 설치할 때 `INSTALL_FAILED_MISSING_SHARED_LIBRARY: android.car`가 발생했고 해당 실행의 기기 테스트는 **0개**였습니다(`sdk-verification.log`). 정적 조회에서는 `pm` 목록에 `ai.pleos.playground.car`와 `car-frameworks-service`가 있었고, `/system/framework/android.car.jar` 경로와 조회한 vendor JAR에서 필요한 공개 타입을 찾지 못했습니다. **이 정적 조회만으로 실행 중 공개 AAOS 클래스가 없다고 결론 낼 수는 없습니다.** vendor 라이브러리를 임의 대체 API로 매핑하지 않았습니다.

runtime은 Android Car 타입이 없는 순수 인터페이스를 참조하고, `Class.forName`으로 실제 공개 타입을 찾은 경우에만 안전 수집기를 로딩합니다. 실제 누락·LinkageError가 발생하면 `carSafety=UNSUPPORTED / ANDROID_CAR_CLASSES_MISSING`와 P·정지·CarUx 허용 모두 false로 처리합니다. 다른 SDK 연결 성공만으로 주차 안전을 추정하지 않습니다. AVD 시스템·루트·정책 변경이나 라이브러리 강제 설치는 하지 않습니다.

선택 연결로 바꾼 뒤 **최초 실제 smoke 1개는 통과했고 공개 AAOS 클래스 탐색에도 성공했습니다.** 권한 허용 전 관측은 `carSafety=PERMISSION_DENIED / SecurityException`, `parked=true`, `carUxAllowed=true`, `stationary=false`였습니다. 해당 사용자 10에서 `CAR_SPEED`는 dangerous이며 미허용, `CAR_POWERTRAIN`은 normal이며 허용 상태였습니다. 따라서 당시 차단 사유는 공개 API 미지원이 아니라 **속도 권한 미허용으로 실제 정지를 확인하지 못한 상태**입니다. 일반 dangerous 권한 허용 후 재관측 결과는 [최신 검증 기록](verification.md)을 따릅니다.

속도는 원본 단조 timestamp 기준 6초 이내 표본만 사용하며, 같은 캐시 표본을 다시 읽어도 원본 만료 시각을 늘리지 않습니다. P 기어·CarUx는 변경 알림과 현재 값 조회로 확인합니다. 6초의 안전 값 유효 시간과 1초 조회 주기는 앱의 보수적 설정입니다.

Vehicle의 브레이크·EV SOC는 지원 여부와 읽기 권한 capability를 먼저 확인한 **읽기 전용 보조 값**입니다. 현재 게임 정답이나 안전한 주차를 이 두 값만으로 확정하지 않습니다. 이 구현은 실제 차량 상태를 설정하는 `set` API를 호출하지 않습니다. [Vehicle 도메인과 capability 안내](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)

## 음성 수명주기와 권한

TTS/STT는 `ON_DEVICE`만 생성하고 서버 등록·HYBRID·SERVER·토큰·외부 AI를 사용하지 않습니다. P·정지·CarUx 허용·foreground가 모두 맞을 때만 객체와 리스너를 만들며, 조건을 잃으면 즉시 stop·리스너 해제·release합니다. STT 차량 마이크는 사용자가 음성 입력을 요청한 뒤에만 `request()`로 시작합니다. [TTS 모드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [STT 시작·중지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop)

재생·인식은 음성 콘텐츠를 위한 일시적 오디오 포커스를 얻어야 합니다. 포커스를 잃으면 중단합니다. 음성은 요청별 SDK 객체 세대를 사용해 이전 콜백을 차단하고, STT는 최종 텍스트와 종료 이벤트를 모두 확인한 뒤 결과 후보를 전달합니다. TTS 30초·STT 15초 요청 timeout 뒤에는 성공으로 처리하지 않습니다. 인식 텍스트·원시 음성은 로그나 저장소에 남기지 않습니다. [TTS 이벤트·해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release)

Manifest 선언은 다음 실제 API 권한으로 한정했습니다.

| 기능 | 선언 |
| --- | --- |
| FusedLocation | `pleos.car.permission.FUSED_LOCATION` |
| 경로 조회·요청 | `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` |
| Vehicle 보조 읽기 | `pleos.car.permission.CAR_DRIVE`, `CAR_ENERGY` |
| 음성 | `pleos.car.permission.TTS_SERVICE`, `STT_SERVICE` |
| AAOS 속도·기어 | `android.car.permission.CAR_SPEED`, `CAR_POWERTRAIN` |

`missingRuntimePermissions(context)`는 실제 기기의 `PermissionInfo`에서 dangerous인 미허용 선언 권한만 반환합니다. 공식 Fused 권한은 dangerous입니다. 권한 요청 UI는 Activity에서 처리하고, 거절 시 어댑터는 계속 차단합니다. Fused를 Google 위치 API처럼 취급해 FINE/COARSE/BACKGROUND 위치 권한을 선제 추가하거나, 차량 서비스 STT에 Android 녹음 권한을 임의 요구하지 않았습니다. [Fused 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions), [Navi 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions), [STT 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions)

TTS/STT AAR Manifest는 CCG validation 용도의 `android.permission.INTERNET` 선언을 포함합니다. 따라서 SDK 추가 후 앱의 병합 Manifest를 기존의 ‘INTERNET 권한 없음’ 기록과 혼동하면 안 됩니다. 앱 어댑터는 ON_DEVICE 외 모드를 호출하지 않으며 별도 서버·계정·자격 증명을 추가하지 않았습니다.

## 아직 구분해야 하는 검증

공식 문서 열람, source/AAR·`javap` 함수 확인, 실제 호출 가능한 어댑터 구현을 완료했습니다. 최신 debug·release 빌드, JVM 테스트 37개와 Lint는 `sdk-build-final.log`에서 통과를 확인했습니다. 이 빌드 결과를 SDK별 실제 지원·위치 수신·길 안내·가청 음성 성공으로 확대하지 않습니다.

**SDK별 최종 관측 상태와 기기 테스트 결과는 [검증 기록](verification.md)의 최신 실행 항목을 기준으로 합니다.** 설치 실패, 일부 기기 테스트 통과·실패, AVD 무응답이나 호스트 실행 중단은 서로 구분해 기록하며 미완료 실행을 전체 통과로 표시하지 않습니다. 초기화 READY와 표본 수신, 경로 요청 전송과 경로 완료, 음성 요청 수락과 실제 인식·가청 출력은 같은 판정이 아닙니다.

`PleosSdkRuntimeTest`는 실제 Activity가 RESUMED인 상태에서 별도 runtime를 생성해 초기화와 background·재시작·close 경계를 관측합니다. 자동 권한 허용, 경로 요청, 발화와 마이크 인식은 수행하지 않습니다. 관측 상한은 15초이며 초기화 상태가 모두 CHECKING을 벗어나면 3초 이후 조기 종료할 수 있습니다. 따라서 NO_DATA나 위치 콜백 미관측은 해당 관측창의 결과이며 미지원 확정값이 아닙니다. 저장하는 smoke JSON·전용 로그는 상태 코드와 Boolean 위주로 제한하며 원시 좌표·음성·계정 정보를 포함하지 않습니다. 이 검사는 SDK 내부 리소스의 완전한 누수 부재나 실제 음성 재생·인식 성공까지 증명하지 않습니다.

권한 허용 전 첫 smoke에서는 NaviHelper의 서비스 연결 READY와 Vehicle의 브레이크·배터리 표본 존재를 관측했습니다. Fused는 약 3.5초 관측창에서 NO_DATA·새 위치 콜백 미관측이었고, TTS/STT는 정지 조건이 미확인되어 STOPPED를 유지했습니다. 이를 경로 안내·위치 인증·가청 음성·인식 성공이나 음성 SDK 미지원 판정으로 바꾸지 않습니다. 이후 권한 허용·재실행의 실제 상태 표는 [검증 기록](verification.md)에 둡니다.

현재는 공식 공개 자료의 5개 관광지 대표점을 초기 설정에 넣었습니다. 두 공원의 최종 방문 구역·인증 중심점과 전 구간의 주차 접근성은 현장 검증 전입니다. LIVE 인증은 미터 단위 위치 정확도 근거 확보 전까지 보류하고, 물리 정지·P·CarUx·권한·최신 표본도 독립적으로 모두 확인해야 합니다. 좌표를 비워 둔 설정도 별도로 차단합니다. SDK나 권한이 없는 환경에서는 오류·미지원·확인 대기를 표시하며 MOCK으로 자동 전환하지 않습니다.
