# 주행 중 안내 (drive 패키지)

주행 중에 차량 데이터로 사건 BGM과 각 장의 "이동 중" 장면 음성을 재생하는 기능이다. 기어 D에서는 Pleos가 앱 화면을 막고 주행 화면을 띄우므로, 앱이 화면에 없어도 동작하는 **포그라운드 서비스**로 만들었다. 게임 진행(도착 확인·퍼즐·저장)은 바꾸지 않고, 저장된 여행 기록을 **읽기만** 한다.

## 동작

| 상황 | 동작 |
| --- | --- |
| "다음 목적지" 화면의 **출발하기** | 그 장소를 내비 목적지로 설정하고 출발 준비. 이미 달린 구간이면 처음부터 다시 준비(시연 반복용) |
| 여행이 `EN_ROUTE`(이전 장소를 마쳐 다음 장소가 열림) + 기어 D | 내비 화면을 앞으로, BGM 시작, 목적지 안내 멘트. 출발하기를 안 눌렀어도 D면 시작 |
| 이동 중 | 남은 거리가 900m 이하가 되면 "곧 도착합니다" 멘트 |
| 상단 오른쪽 **음성 켜짐/꺼짐** | 꺼짐: 지금 나오는 음성을 바로 멈추고, 꺼져 있는 동안 나올 음성은 버린다(나중에 몰아서 나오지 않음). 켜짐: 그 뒤에 나올 음성부터 나온다. 앱의 장면 내레이션과 주행 멘트 모두 적용, BGM은 그대로. 설정은 앱을 다시 켜도 유지 |
| 가는 길에 P | BGM 일시정지, D에서 다시 재생 |
| 장소 반경 안 위치 2회 연속 | BGM·음성 정지(남은 대사는 버림). 도착 확인은 지금처럼 앱의 게임이 한다 |
| 도착 후 기어 P | 앱을 앞으로 다시 띄움(전체 화면 알림으로 연다. 서비스가 직접 여는 것은 Android가 막음) |
| 내비 음성·차량/시스템 음성 | 그동안 BGM·대사를 멈추고, 끝나면 0.8초 뒤 이어서 재생(TTS 대사는 처음부터 다시) |
| 장소를 다 풀기 전에 D | 아무 일도 없음. 게임이 다음 `EN_ROUTE`로 넘어가야 출발 안내가 시작된다 |
| 시동 OFF/ON, 앱 재시작 | 진행 중인 여행이 있으면 서비스 재시작. 이미 나온 대사·끝난 구간은 다시 재생하지 않음 |

- **남은 거리**: 내비가 주는 도로 거리가 최근(15초 이내)이고 직선거리와 비교해 그럴듯하면(0.9배 이상, 3배+3km 이하) 그 값을, 아니면 직선거리를 쓴다. 정해진 경로가 아니라 실시간 값이라 우회·재탐색·정체와 상관없이 같은 규칙으로 동작한다.
- **오디오 겹침 방지**: 오디오 포커스를 잃으면(덕킹 대신 일시정지 요청) 멈추고, 포커스와 상관없이 다른 앱이 내비 안내·음성 비서·통화·벨소리·알람·알림·접근성, 그리고 차량 시스템 용도(비상·안전·차량 상태·안내 방송)로 재생 중이면 멈춘다. 실차의 경고음·안내 방송도 같은 규칙으로 피한다.
- **주행 멘트**: 사건 줄거리가 아니라 지금 향하는 장소의 짧은 정보를, 차가 요원(운전자)에게 말을 거는 말투로 전한다. 장소마다 두 줄(출발할 때 장소 정보, 900m 전 "곧 도착합니다"). 문구는 `app/src/main/assets/drive_lines.json`에서 고친다. 음성 파일 이름은 `voice_drive_ch<n>_info`, `voice_drive_ch<n>_near`.

## 파일

| 경로 | 역할 |
| --- | --- |
| `drive/DriveLegEngine.kt` | 순수 Kotlin 규칙(단위 테스트 `DriveLegEngineTest`) |
| `drive/DriveCourse.kt`, `assets/drive_lines.json` | `TourCatalog` 좌표 + 주행 멘트로 구간 구성 |
| `drive/NarrationSwitch.kt` | 음성 켜짐/꺼짐 설정(앱 전체) |
| `drive/DemoArrival.kt` | 시연 주행이 도착·정차하면 기존 '둘러보는 중' 화면으로 넘김(시연 여행만) |
| `drive/DriveSignals.kt` | Pleos Vehicle(기어), FusedLocation(위치), NaviHelper(목적지·남은 거리) 연결 |
| `drive/DriveAudio.kt` | BGM 루프·오디오 포커스·대사 큐 |
| `drive/DriveService.kt` | 포그라운드 서비스(`mediaPlayback`), 여행 기록 감시, 알림에 상태 표시 |
| `drive/DriveBootReceiver.kt` | 부팅(시동) 후 진행 중인 여행이 있으면 재시작 |
| `res/raw/bgm_drive_siyeok.wav` | `tools/make_drive_bgm.py`로 합성한 임시 BGM(가사 없음, 외부 음원 미사용) |
| `tools/drive_sim.py` | 에뮬레이터 주행 시뮬레이터(우회·정체를 섞어 실제 주행처럼, 선택) |
| `debug/.../drive/DemoDrive.kt`, `debug/assets/demo_routes.json` | 시연 자동 주행(debug 빌드 전용) |
| `tools/make_demo_routes.py`, `tools/push_demo_voices.py` | 시연 경로 생성, 시연용 임시 음성 넣기 |

앱의 다른 코드는 `MainActivity.onCreate`의 두 줄(`DriveService.watch`, `DemoArrival.watch`), `EscapeApp.kt`의 음성 켜짐/꺼짐 버튼과 꺼짐일 때 대기 중인 내레이션을 버리는 부분, `Narrator.speak`의 꺼짐 확인, `StoryScreens.kt` "다음 목적지" 화면의 **출발하기** 버튼(`DepartButton`), `AndroidManifest.xml`의 서비스·리시버·권한 등록만 바꿨다. 앱의 `PleosSdkRuntime`은 화면에 있을 때만 동작하도록 설계돼 있어 그대로 두고, 서비스가 SDK에 따로 연결한다.

## 음성 파일

대사마다 아래 순서로 찾는다.

1. `app/src/main/res/raw/voice_<id>.(mp3|ogg)` — 녹음 파일을 앱에 넣을 때 (예: `voice_drive_ch1_info.mp3`)
2. 앱의 `files/voice/` 또는 `/sdcard/Android/data/com.team.carescape/files/voice/`의 `voice_<id>.(mp3|ogg|m4a|wav)` — 다시 빌드하지 않고 들어 볼 때(`tools/push_demo_voices.py`가 앞쪽에 넣는다)
3. Pleos TextToSpeech(ON_DEVICE)

BGM도 `files/bgm/bgm_drive.*`가 있으면 그 파일을 쓴다. Pleos Connect v2.1.0 에뮬레이터에서는 온디바이스 TTS가 `ON_DEVICE_ENGINE_ERROR`를 내므로, 에뮬레이터에서 대사를 들으려면 1이나 2의 파일이 필요하다(대사는 알림에 글자로 표시된다).

## 시연 자동 주행 (debug 빌드 · 시연 모드 전용)

시연(MOCK) 여행에서 **출발하기**를 누르면 에뮬레이터 차가 스스로 움직인다. 터미널 도구 없이 앱만으로 시연할 수 있다.

1. 출발하기 → 내비에 목적지 설정
2. 1.5초 뒤 기어 D (Pleos Virtual Console `127.0.0.1:8088`을 앱이 직접 호출 → Vehicle SDK·주행 화면·내비가 실제 차처럼 반응)
3. 수원역(1장) 또는 이전 장소에서 목적지까지 실제 도로 경로를 따라 1초마다 위치를 이동. 계기판 속도는 30~55km/h 사이로 변함
4. D부터 정차까지 약 **60초**(거리와 상관없이 시간 압축). 출발 멘트 → 내비 안내 음성이 나오면 잠시 멈춤 → "곧 도착합니다" 멘트 → 반경 500m 진입까지 보이도록 정했다. 주행 멘트는 구간당 두 줄만 쓴다
5. 반경에 들어가면 BGM·음성이 멈추고 차가 그 자리에서 정지 → 기어 P → 앱이 다시 앞으로 오고, 손으로 하던 **P 정차 → 도착한 것으로 하기(시연) → 관광 시작** 을 앱이 대신 해서 기존 **'<장소>을 둘러보는 중'** 화면이 바로 열린다(`drive/DemoArrival.kt`, 시연 여행에서만. 게임의 기존 단계를 그대로 거치며 15분 관광 시간도 이때부터 잰다)

- 경로: `app/src/debug/assets/demo_routes.json` (OSRM/OpenStreetMap 도로 경로, `python3 tools/make_demo_routes.py`로 다시 만듦)
- 코드: `app/src/debug/java/.../drive/DemoDrive.kt`. release 빌드에는 아무 동작도 하지 않는 같은 이름의 빈 구현이 들어가며, 실제 SDK(LIVE) 여행에서는 쓰지 않는다.
- 위치는 앱이 에뮬레이터 GPS를 바꿀 수 없어서, 시연 주행 중에는 경로 위치를 주행 안내에 직접 넣고 에뮬레이터의 고정 GPS는 무시한다. 그래서 내비 지도 위 차량 표시는 움직이지 않는다(아래 참고).
- debug 빌드에만 `127.0.0.1` 평문 HTTP 허용(`src/debug/res/xml/demo_network.xml`)과 INTERNET 권한을 추가했다.

### 시연용 임시 음성

에뮬레이터 TTS가 동작하지 않으므로, 시연에서 대사를 들으려면 한 번 실행한다(macOS).

```bash
python3 tools/push_demo_voices.py
```

`assets/drive_lines.json`의 주행 멘트 10줄을 macOS 음성(Yuna)으로 만들어 debug 앱의 `files/voice/`에 넣는다(`run-as` 사용, 저장소에는 아무것도 추가하지 않음). 녹음본을 `res/raw/voice_<id>.mp3`로 넣으면 그 파일이 우선이다.

## 에뮬레이터 시뮬레이션 (터미널 도구, 선택)

```bash
python3 tools/drive_sim.py --fast 15 --detour 0.3
```

- 기어·속도는 Pleos Virtual Console(`adb forward tcp:8088 tcp:8088`)로, 위치는 `adb emu geo fix`로 넣는다. 구간마다 현재 위치에서 OSRM(OpenStreetMap)으로 경로를 새로 찾고, 일부는 우회하며 속도와 신호 대기를 바꾼다.
- 각 구간 전에 기어 P로 기다리다가 앱에서 **출발하기**를 누르면 D로 출발한다. 도착하면 P로 세우고(앱이 다시 열림), 다음 장의 출발하기를 기다린다. `--manual`이면 Enter로 출발한다.
- 실시간 지도: http://127.0.0.1:8765
- 서비스 상태: `adb shell dumpsys activity service com.team.carescape/.drive.DriveService`
- 에뮬레이터의 Pleos 내비는 출발하기로 요청한 경로·지도·턴바이턴 안내를 보여 주지만, 지도 위 차량 표시는 에뮬레이터 GPS를 따르지 않고 고정 위치(수정구 시흥동)에 머문다. 내비가 받는 NMEA 문장의 체크섬이 에뮬레이터에서 잘못 만들어지기 때문이며(`GpsLocationSource: NMEA checksum mismatch`) 앱에서 고칠 수 없다. NaviHelper SDK에는 모의 주행 요청 API도 없다. 이때 내비의 남은 거리(약 21km)는 실제와 맞지 않으므로 위 규칙에 따라 직선거리를 쓴다. 실차에서는 내비와 차량 위치가 같다.

## 검증 (2026-11-14, Pleos Connect v2.1.0 에뮬레이터)

- 단위 테스트 통과(기존 102 + `DriveLegEngineTest`), release 컴파일 성공.
- 1장 구간: 시연(MOCK) 여행 시작 후 Virtual Console로 기어 D → BGM 재생·내비 목적지 화성행궁 설정·`ch1_travel_01`, 거리 기준 `ch1_travel_02`, 반경 진입 시 BGM 정지·구간 종료. 앱 재시작 후 같은 구간을 다시 재생하지 않음.
- 출발하기 흐름(같은 날): 출발하기 → 내비에 화성행궁 경로 → D에서 내비 화면이 앞으로·BGM·`ch1_travel_01` → 내비 안내 음성(“80m 좌회전”) 동안 우리 오디오 멈춤 후 재개 → 거리 기준 `ch1_travel_02` → 반경 진입 시 BGM·음성 정지 → P에서 앱이 다시 앞으로. 단위 테스트 111개 통과.
- 시연 자동 주행(같은 날): 출발하기 → D·내비 화면·BGM·`drive_ch1_info` → 약 50초 `drive_ch1_near` → 반경 진입·BGM·음성 정지 → P(D부터 약 62초) → 앱 다시 앞으로, 기존 '화성행궁을 둘러보는 중' 화면(15분 관광 시작).
- 음성 꺼짐으로 같은 주행: 두 멘트 모두 건너뜀(BGM은 재생), 도착 후 켜짐으로 바꿔도 건너뛴 멘트가 늦게 나오지 않음.
- 미검증: 2장 이후 출발(앱에서 1장 탐색 장면을 모두 풀어야 함; 규칙은 단위 테스트로 확인), 실제 음성 출력(에뮬레이터 TTS 오류), 실차의 차량 자체 음성과의 겹침(규칙만 구현), 실차.
