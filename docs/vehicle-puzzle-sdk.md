# 차량 조작 문제와 실제 SDK 입력

현재 `release/v0.4.0`의 flow5 문제에 읽기 전용 Vehicle SDK 입력을 연결했다. 방향지시등·와이퍼·창문 설정을 앱이 바꾸지 않으며, 사용자가 차량 또는 Pleos 콘솔에서 조작한 값을 읽는다. 들어온 신호는 답안에만 쌓이고 **제출**을 눌러야 판정·저장한다.

| 현재 문제 | 입력 |
| --- | --- |
| 화홍문 `c2_gates` | 오른쪽 깜빡이 3번 → 왼쪽 깜빡이 1번 → 오른쪽 깜빡이 3번 |
| 호수 `c5_window` | 닫힌 운전석 창문을 내리기 1번 → 제출 |

2026-11-14 후속 사용자 결정으로 flow5 화홍문 입력을 좌우 깜빡이로 변경했다. **오른쪽=수문 닫기, 왼쪽=수문 열기**이며 가운데 넷째 칸만 연다. 앞선 와이퍼·창문 대체안은 이 결정으로 대체한다. 단계 ID와 기존 해결 기록은 유지한다. flow3·4의 예전 비상등 문제는 당시 내용으로 보존하며, 해당 비상등 입력의 실제 SDK 연결은 미지원으로 표시한다.

## 신호와 입력 기준

- Vehicle **2.3.2** `TurnSignal.checkTurnSignalStateCapabilities` / `getCurrentTurnSignalState` / `registerTurnSignalState`: `OFF / LEFT / RIGHT`를 읽는다. 같은 방향을 켜 둔 상태의 반복 통지는 추가 입력이 아니다. 반복하려면 `OFF`로 돌아간 뒤 다시 켠다. 왼쪽에서 오른쪽처럼 방향 자체가 바뀌어도 새 방향 1회로 읽는다. 초기 현재값·재연결 첫값은 기준선으로만 쓴다.
- 방향지시등은 `pleos.car.permission.CAR_LIGHTS` 읽기 권한을 사용한다. 한 SDK 값에서 비활성 방향을 먼저 갱신한 뒤 활성 방향을 전달한다. 공개 enum에 양쪽 비상등 상태는 없으므로 비상등 문제를 구현한 것으로 설명하지 않는다.

- Vehicle **2.3.2** `Wiper.getCurrentWiperState` / `registerWiperState`, 앞유리 영역: `OFF → ON`을 한 번으로 센다. Low→High·반복 ON은 추가 입력이 아니다. 실제 블레이드 왕복 횟수나 OneShot 횟수를 읽었다는 의미가 아니다.
- `Window.getCurrentWindowPosition` / `registerWindowPosition`, `ROW_1_LEFT`: 완전히 닫힘(0)→부분 열림 또는 열림을 한 번으로 센다. 현재 에뮬레이터의 앞왼쪽 운전석 창문이다. 75→100처럼 계속 열리는 값은 추가 입력하지 않는다. 다시 입력하려면 먼저 완전히 닫는다.
- capability의 읽기 권한·영역·최댓값을 먼저 확인한다. 창문 값의 단위를 항상 %라고 가정하지 않는다. [Android WINDOW_POS 정의](https://developer.android.com/reference/android/car/VehiclePropertyIds#WINDOW_POS)는 0=닫힘, max=열림이다.
- 와이퍼·창문 읽기 권한은 기존 `pleos.car.permission.CAR_WINDOWS`를 사용한다. 와이퍼·창문 제어 권한은 추가하지 않았다.
- SDK 2.3.2 공개 API에 비상등 입력이 없다. 표준 AAOS 대안은 [HAZARD_LIGHTS_STATE](https://developer.android.com/reference/android/car/VehiclePropertyIds#HAZARD_LIGHTS_STATE)와 [특권 권한](https://developer.android.com/reference/android/car/Car#PERMISSION_EXTERIOR_LIGHTS)이 필요했고, 이 앱에서 `granted=false`를 확인했다. 사용자 결정에 따라 최신 문제를 처음에는 창문으로, 이어서 위 좌우 깜빡이로 바꾸고 사용하지 않는 대체 어댑터·특권 권한 선언은 제거했다.

첫 현재값은 기준선이며 답안이 아니다. 연결 복구·문제 전환·처음부터·안전 상태 해제·백그라운드·기록부 전환 때 기준선을 다시 잡는다. 최신 표본은 원래 수신 시각으로 9초만 인정하며 3초마다 현재값을 재확인한다. 늦은 응답·중복 표본·끊긴 동안의 입력은 답안으로 재생하지 않는다. 제출 대기 중 연결 세대나 초기화가 바뀌어도 예전 정답을 저장하지 않는다.

SDK 콜백은 메인 스레드로 모은 뒤 용량 128의 Channel로 **ViewModel 한 곳**에 순서대로 전달한다. 별도 재생은 없고, 수신자가 잠깐 없으면 제한된 큐에 보관되지만 새 문제의 순번 경계 이전 표본은 버린다. 큐가 가득 차면 연결 세대·기준선·초안을 무효화한다. 현재 상태 조회는 최신 표본 맵과 StateFlow로 구분한다.

## 앱에서 콘솔 조작 재현하기

1. Android Studio에서 `C:\car_escape`를 열고 `app`을 Pleos 에뮬레이터에 Run한다. 콘솔 `http://127.0.0.1:8088/`을 열어 **Park·속도 0**을 유지한다.
2. 로비 → **시연 · SIM** → **시연 MOCK**에서 **차량 답안: 화면 시연**을 눌러 **차량 답안: 실제 SDK**로 바꾼다. 로비에서만 바꿀 수 있고 재실행 시 자동 활성화되지 않는다.
3. **시연 관광 + 실제 차량 입력** 표시를 확인한다. 이 모드는 관광·시간·저장은 MOCK이며 **P·실제 정지·CarUx·문제 조작은 실제 SDK**다. 실제 안전 신호가 없으면 모의 P 버튼으로 진행할 수 없다.
4. 기존 여행을 이어가거나 시연 바로가기를 사용한다. **시연 바로가기는 현재 MOCK 여행을 새 시연 여행으로 교체한다.** 원래 여행을 보존하려면 기존 여행을 이어가거나 테스트 APK의 격리·복원 테스트를 사용한다.
5. 화홍문 장면: 첫째 홍예 문제 `7`을 제출한 뒤 판문 도르래를 연다. 방향지시등을 Off→BlinkRight→Off 3번, Off→BlinkLeft→Off 1번, Off→BlinkRight→Off 3번 조작한다. 앱에서 닫힘·열림 일곱 칸을 확인하고 **제출**한다. 깜빡이의 실제 점멸 횟수를 세는 문제가 아니다.
6. 호수 창문 문제는 창문을 완전히 닫은 뒤 다시 내리고 **제출**한다. 센서 입력만으로 완료되지 않는다.

일반 MOCK은 종전처럼 화면 버튼을 쓴다. LIVE는 SDK 입력만 받는다. LIVE의 최초 도착 인증은 위치 정확도의 미터 단위 계약 미확정으로 여전히 보류 중이다. 관광 시연과 실제 SDK 조작을 함께 검증한 결과를 **전체 LIVE 방문 인증 성공**으로 해석하면 안 된다.

## 검증 경계와 실행

Windows·Android Studio JBR·Pleos Connect v2.1.0/API 34·`emulator-5554`/Android 사용자 10에서 확인한다. 실제 하드웨어·Ubuntu 실행은 미확인이다. 테스트 결과와 수치는 [검증 로그](verification-log.md)의 2026-11-14 차량 문제 항목을 따른다.

- `PleosVehicleConsoleTest`: 독립 실제 SDK, 콘솔 방향지시등·와이퍼·창문, 메모리상의 스토리 판정. 이 테스트의 foreground는 테스트가 명시적으로 설정한다.
- `StoryIntegrationTest`: 실제 MainActivity에서 MOCK 관광·실제 SDK 안전/조작 → VM 초안 → UI 제출 → DataStore 해결 기록. 기존 MOCK/LIVE·archive·schema를 격리·복원한다.
- `CarSequenceInputTest`: SDK 모드의 대체 버튼 숨김, 제출 전 무판정, 신호 미확인 시 제출 차단, 일반 MOCK 입력 보존.

Ubuntu에서 재현할 명령(이 환경에서 Ubuntu 실행 자체는 하지 않음):

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5554 install -r -t --user 10 app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t --user 10 app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 forward tcp:8088 tcp:8088
adb -s emulator-5554 shell am instrument --user 10 -w -r \
  -e vehicleConsoleControls true \
  -e class com.team.carescape.platform.sdk.PleosVehicleConsoleTest,com.team.carescape.ui.StoryIntegrationTest,com.team.carescape.ui.CarSequenceInputTest \
  com.team.carescape.test/androidx.test.runner.AndroidJUnitRunner
```

기기 이름과 Android 사용자 번호는 각 환경에 맞게 확인한다. 콘솔 제어 테스트는 명시적 `vehicleConsoleControls=true`가 있어야 실행되며, 테스트가 사용한 방향지시등·와이퍼·창문 값과 자체 heartbeat 세션을 정리한다. 사용자 기록·원시 로그는 로컬 백업에만 보관한다.
