# 기존 시역 UI와 Pleos SDK 연결

> 이 문서는 2026-11-13 SDK 연결 당시의 검증 기록입니다. v0.4에서는 SDK·안전 경계를 유지하면서 관광 후 자물쇠 제출과 schema 2 원본 보관·재개를 추가했습니다. 현재 흐름과 최종 검증은 [README](../README.md)와 [검증 로그](verification-log.md)의 v0.4 절을 따릅니다. 아래의 복귀 후 위치 재확인 설명은 당시 이력이며, 2026-11-15 사용자 후속 결정인 [관광 시작과 복귀 정책](tour-return-policy.md)이 우선합니다. 최초 도착·관광 시작의 위치 조건은 유지합니다.

작성일: 2026-11-13. **Debug·Release 빌드 / JVM 44개·기기 UI 3개 통과 / Lint 오류 0개**를 확인했다. 아래의 코드 연결 설명을 실제 차량·길 안내·발화·방문 인증 성공으로 해석하지 않는다. 실제 검증 범위와 이전 SIM 시연 기록은 [verification-log.md](verification-log.md)에 구분해 보존한다.

## 적용 기준과 보존 범위

- UI 기준은 `release/v0.1.0`의 `9a726a2fb284276aad7c06c44a7626e1d471a616`이다. `com.team.carescape`의 로비·테마 선택·브리핑·프롤로그·코스·시역 챕터·엔딩, 기존 이미지·글꼴·1280×720 비례 배치를 유지한다. 안전 대기·연결 상태와 관광 조건 안내를 해당 흐름에 연결한다.
- 현재 선택 가능한 콘텐츠는 **「시역(屍疫), 물을 건너지 못하는 자들」 스토리**다. 차량 퍼즐 테마는 기존 비활성 카드·테스트로 보존한다. 이번 연결에서 시역에 새 정답·퍼즐을 만들지 않았다.
- 복원한 요구 문서 [ROAD_ESCAPE_sdk.md](ROAD_ESCAPE_sdk.md)는 아래 Git 원본과 바이트가 같다. 문서 자체의 기획 상태·미확정 항목은 그대로 보존했다.

| 원본 식별 | 값 |
| --- | --- |
| 읽은 ref | `main` → `61709b2b9153eaa1178b368f60472cec449d7aab` |
| 경로 | `docs/ROAD_ESCAPE_sdk.md` |
| 해당 문서 마지막 변경 commit | `be08283cc83bd58beb3392d25606a532085f1d59` |
| Git blob SHA-1 | `69cee624fdb34108162f0f1a4287530a48f866a3` |
| 파일 SHA-256 | `1c79ba9473186c829d374f2f3bdad40ce1aa529660eae73cc6f7d89106c46660` |

요구 문서의 “대표 좌표 입력 전”은 작성 당시 상태다. 현재 `TourCatalog`에는 아래 공개 자료를 바탕으로 5개 대표점을 초기 입력했다. 공원 방문 구역·최종 인증 중심과 주차 접근성은 현장 검증 전이며, 전 지점 **500m는 임시 앱 설정**, **15분은 공통 관광 규칙**이다.

- [수원시 관광 안내 PDF](https://www.suwon.go.kr/webcontent/ckeditor/2019/1/28/22c61a9a-2220-4abc-a683-ea4bd025f901.pdf)
- [공공데이터포털 전국박물관미술관정보표준데이터](https://www.data.go.kr/data/15017323/standard.do)

대표점 값은 기존 `main`의 등록값을 재사용했다. 이번에는 공공데이터포털의 데이터 항목을 재확인했으며, 수원시 PDF 재조회는 시간 초과였다. 이를 새 현장 좌표 검증이나 합법적 주차 확인으로 표시하지 않는다.

목적지는 관광지 대표점이다. 지정 주차장으로 인증하지 않으며 주차 가능 여부를 보장하지 않는다. 이용자가 합법적인 주차 위치를 확인한다. 사용자 화면에는 차량 좌표·현재 거리 숫자를 표시하지 않는다.

## SDK 연결 범위

기존 `main`의 SDK 어댑터 7개 파일을 `com.team.carescape.platform.sdk`로 이식했다. 공개 API·안전 조건은 유지하고 패키지·리플렉션 경로를 변경했다. 외부 지도·위치 서비스, 클라우드 음성, 서버는 추가하지 않았다.

| 의존성 | 버전 | 연결한 코드와 한계 |
| --- | --- | --- |
| `ai.pleos.playground:NaviHelper` | `3.0.5` | 초기화·리스너·대표점 경로 요청. 요청 수락과 실제 경로 시작/도착을 구분한다. |
| `ai.pleos.playground:FusedLocation` | `1.1.3` | 최신 갱신 콜백·좌표·원본 시각·수신 elapsed 시각·오류 확인. `getLastKnownLocation()`으로 인증하지 않는다. |
| `ai.pleos.playground:Vehicle` | `2.3.2` | 지원·읽기 capability 확인 후 **배터리 잔량·브레이크 상태만 읽기**. P·정지·CarUx의 대체 증거로 쓰지 않는다. |
| `ai.pleos.playground:TextToSpeech` | `3.3.1` | Pleos `ON_DEVICE` 한국어 초기화·발화·중지·리스너 정리. 실제 발화는 이번 기기 검증 대기다. |
| `ai.pleos.playground:SpeechToText` | `3.3.1` | `ON_DEVICE` 초기화·요청·최종 결과·중지 어댑터. **현재 스토리에는 음성 정답 UI나 녹음 시작 버튼이 없다.** |

위 기본 연결 설명 이후 방향지시등·와이퍼·운전석 창문 읽기를 실제 SDK 퍼즐 입력에 연결했다. 구체적인 콘솔 관측은 vehicle-puzzle-sdk.md와 verification-log.md를 따른다. 임의의 차량 제어 쓰기나 미지원 문·경고등 입력을 구현했다고 주장하지 않는다.

현재 `Narrator`는 Pleos runtime에 위임한다. Android 기본 TTS로 자동 전환하지 않는다. 정차용 스토리 내레이션은 안전한 정차에서만 요청한다. 이후 추가된 별도 DriveService는 주행 중 짧은 목적지 안내·BGM을 담당한다. 그 동작과 검증 한계는 drive-chapter.md를 따른다. 실차 안전이나 시장 입점 승인을 받은 것으로 설명하지 않는다.

## 안전·수명주기·권한

`MainActivity`의 foreground 시작/중단에 맞춰 runtime을 시작·중단하고, ViewModel 종료 때 닫는다. background 전환 시 모의 안전/위치 입력과 대기 음성을 무효화하고 구독·발화·인식을 중단한다. 재진입하면 새 안전 상태와 위치를 확인한다.

연결 상태 창은 LIVE의 신선한 안전 표본이 주행·UX 제한을 나타내면 즉시 숨긴다. 초기 권한이나 서비스 미확인 때의 설정 경로를 게임 허용으로 취급하지 않는다. SDK AAR에서 병합한 `INTERNET` 권한은 존재하지만 앱 자체의 외부 서버·클라우드 API 호출을 추가한 것은 아니다.

LIVE 게임·길 안내 요청·음성은 **foreground + 신선한 실제 P + 실제 정지 + 공식 CarUx 허용**을 모두 요구한다. 공개 AAOS `android.car`로 안전 신호를 읽으며 클래스·서비스·권한·신호가 없거나 오래되면 허용하지 않는다. `uses-library android.car`는 선택형이며, 설치 라이브러리 목록만으로 실제 런타임 클래스의 존재를 단정하지 않는다. 게임 허용을 우회하는 `distractionOptimized=true` 선언은 없다.

오른쪽 위 **SDK 상태 → 권한 확인**은 플랫폼이 `dangerous`로 정의한 미허용 SDK 권한을 Android 권한 창으로 요청한다. 권한 선언·요청만으로 서비스 지원을 보장하지 않으며 거절/미지원/무응답은 별도 상태로 남는다. 시스템 변경이나 권한 우회는 필요 절차로 안내하지 않는다.

runtime이 확인하는 권한은 다음과 같다.

```text
pleos.car.permission.FUSED_LOCATION
pleos.car.permission.NAVI_ROUTE
pleos.car.permission.NAVI_ROUTE_SEARCH
pleos.car.permission.CAR_DRIVE
pleos.car.permission.CAR_ENERGY
pleos.car.permission.TTS_SERVICE
pleos.car.permission.STT_SERVICE
android.car.permission.CAR_SPEED
android.car.permission.CAR_POWERTRAIN
```

## LIVE 인증 보류와 MOCK

`LocationData.confidence`에 대해 **미터 단위 정확도로 해석할 근거를 확인하지 못했다**. 이를 거리 오차로 변환하지 않고, `confidenceRaw`와 `accuracyMeters=null`을 구분한다. 따라서 위치 콜백을 받아 SDK 상태가 READY여도 현재 LIVE 관광 인증은 `LOCATION_ACCURACY_UNKNOWN`으로 보류한다. 새 정확도 계약을 확인하기 전 임의 기본값이나 MOCK 위치로 통과시키지 않는다.

DEBUG는 명시적으로 MOCK/LIVE를 선택하고, release는 LIVE만 사용한다. MOCK은 개발 패널에서 **P 정차**와 **현재 장소 위치 시연**을 직접 입력해야 한다. LIVE가 실패해도 자동으로 MOCK으로 바뀌지 않는다. 모드별 저장 기록을 분리하며 모의 방문·시간 생략은 실제 방문 증거가 아니다.

MOCK 위치도 최신성 조건을 갖는다. 표본 만료·background·재실행 후에는 P와 현재 장소 위치를 다시 입력한다. “15분 경과 시연”은 MOCK에서만 관광 시작 기록의 명시적 우회 플래그를 저장한다. 실제 시작 시각을 과거로 바꾸거나 LIVE 조건을 해제하지 않는다.

## 관광 시작·저장·재개

1. 현재 챕터의 새 위치·안전 상태를 확인하고 **관광 시작**을 누른다.
2. 시작 시각·boot ID·session/mode/챕터와 EXPLORING 진행 상태를 하나의 `trip-progress.json` DataStore 갱신으로 저장한다. **성공을 확인한 뒤** 화면 상태를 진행시킨다.
3. 앱을 닫거나 전원이 꺼져도 반복 타이머를 유지하지 않는다. 재실행 시 저장된 시작 기록과 현재 시각으로 15분을 계산한다.
4. 최소 15분, 현재 챕터의 새 위치, 안전한 정차, 선행 챕터 완료를 확인한 뒤 단서·다음 챕터로 진행한다. 마지막 장소에도 같은 관광 시간 규칙을 적용한다.

같은 시작 기록을 중복 클릭으로 다시 쓰지 않고, MOCK/LIVE의 session과 기록을 섞지 않는다. 역행·의심스러운 시각 변경은 보류하며 외부 시간 서버 없는 환경에서 완전한 시각 위변조 방지를 주장하지 않는다. 원시 위치·안전 관측·음성 원문은 이 저장 파일에 기록하지 않는다. 손상·새 schema·잘못된 데이터는 자동 초기화하지 않고 원본을 보존한다. 저장 실패 시 성공 화면으로 진행하지 않고 다시 읽기 경로를 제공한다.

## 이번 후보의 검증 상태

| 항목 | 현재 상태 |
| --- | --- |
| 기존 UI 연결·SDK 어댑터·관광 저장 코드 | 구현 및 MOCK 대표 흐름 확인 |
| SDK 7파일 패키지 이식 | 원본 역치환 일치 7/7 정적 확인 |
| debug/release 빌드·JVM 테스트·Lint | 최종 변경안 41초, 실제 `C:\car_escape` 재검증 1분 27초 성공. JVM 44개 실패·오류·건너뜀 0. Lint 오류 0·경고 8·힌트 3 |
| MOCK 관광·재개·안전 차단 기기 검증 | `TourLifecycleTest` 3개, 63.049초 통과. 1챕터의 실제 UI·Activity 재생성·주행/foreground 차단·모드별 기록 분리 확인 |
| 5개 챕터 전체 UI 시연 | 미실행. 스토리 엔진의 전체 5개 진행은 JVM으로 구분 검증 |
| 실제 길 안내 완료·가청 발화·음성 인식 | 미실행 |
| 실제 방문·재시동 후 복원·Ubuntu 실행 | 미확인 |

`READY`는 해당 SDK의 준비 또는 관측 상태다. 실제 경로 실행, 소리가 들리는 발화, 음성 인식, 관광 완료·합법적 주차·방문 성공은 각각 별도로 검증해야 한다.
