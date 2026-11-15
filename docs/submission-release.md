# 제출용 Release 준비

팀 **D2E**, 앱 **EscapeRoad**, 제출 APK 파일명은 **`D2E_EscapeRoad_release.apk`**다. 이 문서는 제출용 README와 서명·기기 검증·배포를 준비하기 위한 작업 기준이다.

**현재 상태: REVIEW 구현 / Debug·서명 Release·기기 테스트 APK 빌드 확인 / 단위 215개·신규 UI 2개 통과 / 서명 유효·debuggable=false.** 최종 서명 Release를 새 Pleos 에뮬레이터에 설치·실행하고 실제 SDK의 P·정지·UX 허용 상태에서 관광 위치 시연을 선택해 화성행궁 반경 밖 입력 차단, 수동 관광 시작, 실제 15분 카운트와 조기 복귀 차단을 확인했습니다. 앱 강제종료·재실행 후 수사 재개로 같은 관광 단계와 남은 시간(약 15분에서 약 13분)이 이어졌습니다. 실제 15분 경과 후 복귀·문제 해제, 차량 퍼즐 전체 입력·제출, 전체 스토리 완주, 실차 주행·실제 GPS 도착 인증·실제 관광지 방문은 이번 기기 검증에서 확인하지 않았습니다. 시연 좌표를 UI로 입력했으며 시간 우회·앱 상태 파일 주입·가짜 SDK 안전 신호는 사용하지 않았습니다. unsigned APK나 기존 Debug 테스트 결과를 최종 서명 APK 완주로 표시하지 않는다.

## 제출물 구성

| 항목 | 제출 위치·방법 |
| --- | --- |
| 소스 | 기존 [EscapeRoad 저장소](https://github.com/aemizastudy-oss/EscapeRoad), `release/v0.4.0` |
| APK | 파일 용량을 고려해 [GitHub Release](https://github.com/aemizastudy-oss/EscapeRoad/releases/tag/v0.4.0-submission)의 asset으로 제공 |
| 저장소 내 안내 | `apk/README.md`에서 확정한 GitHub Release 페이지와 APK 파일을 안내 |
| README | 앱 소개, 설치·실행 방법, 테스트 환경·설정, Pleos 버전·화면 규격, APK 링크 |
| 접수 페이지 | 저장소 주소와 APK가 첨부된 최종 Release 페이지 주소 |
| 접수 메일 | 저장소 주소와 최종 소스 커밋 번호 |

APK는 같은 이름의 `D2E_EscapeRoad_release.apk`로 게시하고, 최종 APK SHA-256·서명 인증서 SHA-256·소스 커밋 번호를 Release 본문에 함께 기록한다. 링크·지문·해시는 최종 산출물을 검사한 뒤 확정하며, 현재 초안에는 임의 값을 넣지 않는다.

## REVIEW 모드의 확정 경계

REVIEW는 심사자가 에뮬레이터에서 위치 조건과 관광 대기를 재현하기 위한 별도 모드다. LIVE·MOCK의 여행과 기록을 구분한다.

| 입력·조건 | REVIEW 처리 |
| --- | --- |
| 위치 입력 | 사용자가 명시적으로 시연 위치를 입력한다. 실제 차량 GPS 방문 증명이 아니다. |
| 최초 도착·관광 시작 | 입력한 위도·경도와 실제 목적지 좌표로 거리를 계산하고 기본 500m 반경을 검사한다. 반경 밖 입력은 차단한다. |
| 관광 시작 | 사용자가 버튼을 눌러 정상 시계의 실제 시작 시각을 저장한다. 자동 시작하지 않는다. |
| 관광 시간 | 실제 15분 경과를 유지한다. 대기 생략·시각 조작·과거 시작 기록 주입을 사용하지 않는다. |
| P·정지·CarUx | 실제 차량 SDK와 앱 foreground를 확인한다. 값이 없거나 오래됐거나 제한 상태이면 허용하지 않는다. |
| 차량 퍼즐 | 방향지시등·창문·좌석별 독서등 등 실제 SDK 신호를 읽고 명시적인 제출로 판정한다. |
| 출발 | REVIEW에서 자동 시연 주행을 시작하지 않는다. |
| 기록 | LIVE·MOCK와 분리하고, 실제 관광 방문 인증 기록으로 합치지 않는다. |
| 복귀·문제 열기·제출 | 유효한 현재 모드·챕터의 시작 기록·실제 15분·시계·현재 안전 조건을 확인한다. 복귀 위치는 다시 요구하지 않는다. |

기존 Debug의 격리 LIVE 검증 세션은 위치·15분을 제외한 SDK 확인용이다. REVIEW에는 해당 제외 조건을 적용하지 않는다. 기존 MOCK의 자동 출발·시간 생략도 REVIEW 기능이 아니다.

일반 LIVE의 `accuracyMeters=null` 제한은 별도로 남는다. REVIEW의 시연 위치는 Pleos FusedLocation의 미터 정확도 계약을 검증하거나 실차 GPS 문제를 해결한 결과가 아니다. 일반 관광 복귀 정책은 [tour-return-policy.md](tour-return-policy.md)를 따른다.

## 실행 환경과 순서

기존 검증 환경은 Windows 11, Pleos Connect v2.1.0, Android Automotive API 34, 가로 2560×1440·160dpi, `emulator-5554`·Android 사용자 10이다. 신규 제출 APK에서 동일 환경으로 실행 여부를 다시 기록한다. Ubuntu와 실제 차량 실행은 미검증이다.

1. 최신 제출용 APK를 준비하고 설치 대상 기기·사용자를 확인한다. 기존 서명이 다른 앱의 여행 기록을 임의로 삭제하지 않는다.
2. 서명된 APK를 설치하고 `com.team.carescape/.MainActivity`를 실행한다.
3. Pleos Virtual Console에서 P·속도 0을 설정한다. 앱에서 SDK 권한을 요청하고 실제 P·정지·CarUx 허용이 수신되는지 확인한다.
4. 로비의 실행 모드 선택에서 **실행 모드 → 관광 위치 시연**을 고른 뒤 **사건 고르기 → 시역 → 여행 시작**을 진행한다.
5. 현재 목적지의 시연 위치를 입력한다. 최초 위치 검사를 통과하면 **관광 시작**을 누른다. 반경 밖 입력으로 시작이 차단되는지도 확인할 수 있다.
6. **실제 15분**을 기다린다. 시작 전이나 15분 전에는 복귀할 수 없어야 한다.
7. P·정지·CarUx·foreground 상태에서 **차로 돌아왔어요**를 눌러 문제를 연다. 콘솔로 SDK 차량 신호를 입력하고 앱의 **제출**로 판정·단서·저장 결과를 확인한다.
8. 앱 재실행 후 REVIEW의 현재 진행이 복구되고 LIVE·MOCK 기록이 섞이지 않는지 확인한다. 다음 장소의 관광 시작은 새 위치 조건을 확인한다.

설치 예시(기기·사용자 번호는 환경에 맞게 조정):

```powershell
adb -s emulator-5554 install --user 10 -r D2E_EscapeRoad_release.apk
adb -s emulator-5554 shell am start --user 10 -n com.team.carescape/.MainActivity
```

Debug와 신규 Release는 같은 앱 ID여도 서명이 달라 업데이트 설치가 거부될 수 있다. 원본 데이터 보존과 별도 검증 환경 사용 여부를 먼저 확인한다. 이 문서의 명령은 앱 제거·데이터 삭제를 수행하지 않는다.

## 로컬 Release 서명과 재현 빌드

새 release 키는 저장소 밖의 사용자 `.android/D2E/` 폴더에 보관한다. 저장소에는 실제 키와 인증정보를 넣지 않고 `keystore.properties.example`만 공유한다. 저장소 루트의 실제 `keystore.properties`는 Git 제외 대상이다.

Java Properties의 Windows 경로는 `/`를 사용한다. 아래는 형식 예시이며 실제 비밀번호를 문서나 채팅에 기록하지 않는다.

```properties
storeFile=C:/Users/사용자/.android/D2E/EscapeRoad-release.jks
storePassword=REPLACE_LOCALLY
keyAlias=escaperoad
keyPassword=REPLACE_LOCALLY
```

빌드 설정은 로컬 `keystore.properties`가 있을 때 `submissionRelease` 서명 설정을 release에 연결한다. 파일이 존재하지만 필수 값이 비어 있으면 오류로 중단한다. 파일이 없는 소스 체크아웃은 unsigned APK가 만들어질 수 있으므로, 실행 APK가 필요한 다른 개발자는 자신의 로컬 키와 설정을 준비한다.

Android SDK Platform 36, `android.car` 컴파일 라이브러리, Pleos Maven 의존성 접근을 준비한다. `local.properties`의 SDK 경로도 개인 환경에서만 관리한다.

Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

Ubuntu 재현 안내(이 프로젝트의 Ubuntu 실행은 미검증):

```bash
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

위 명령은 개발자 재현용이다. Codex가 직접 Gradle을 실행하는 경우 세션에 설치된 `gradle-run` 스킬의 관리형 래퍼를 따른다.

## 최종 APK 검사와 게시

1. 서명된 release 산출물을 확인한다. `app-release-unsigned.apk`는 제출용 실행 파일로 사용하지 않는다. 최종 파일명을 `D2E_EscapeRoad_release.apk`로 맞춘다.
2. Android SDK build-tools의 `apksigner`로 서명 유효성과 인증서 SHA-256을 확인한다. release의 `debuggable=false`도 확인한다.

   ```text
   apksigner verify --verbose --print-certs D2E_EscapeRoad_release.apk
   ```

3. 최종 파일의 SHA-256을 계산하고, 이후 파일을 변경하거나 다시 서명했다면 새 해시를 계산한다.

   ```powershell
   Get-FileHash -Algorithm SHA256 -LiteralPath D2E_EscapeRoad_release.apk
   ```

4. 해당 파일로 설치·앱 실행·권한 요청·P/정지/UX 제한·REVIEW 위치 반경 검사·실제 15분·제출·저장·재개를 확인한다. 실행하지 않은 항목은 미검증으로 적는다. 실제 15분을 기다리지 않은 정책 테스트를 실제 대기 성공으로 쓰지 않는다.
5. README와 `apk/README.md`에 실제 결과와 Release 링크를 반영하고, 제출 소스를 커밋·푸시한다. 최종 커밋 번호는 아래 명령으로 확인한다.

   ```bash
   git rev-parse HEAD
   ```

6. 최종 커밋을 가리키는 GitHub Release에 같은 APK를 첨부하고, 본문에 소스 커밋·APK 해시·서명 인증서 지문·실행 환경·검증 및 한계를 기록한다. 최종 소스 커밋을 README 자신의 해시로 반복 수정하지 않는다.
7. 공개한 Release의 APK를 다시 내려받아 로컬 최종 APK와 SHA-256이 같은지 확인한 뒤, 저장소 주소·Release 페이지·최종 커밋 번호를 제출 정보로 사용한다.

최종 파일은 D2E_EscapeRoad_release.apk이며, APK SHA-256은 `0e5f93dc6eb564a5687ecc586607920d92ded88edd3258237267cccba23cbf8b`, 서명 인증서 SHA-256은 `303cea1b55209e766b3e78d2a294dfe7bf1aa91ee399080670b437c722873065`이다. 내려받기 주소는 [apk/README.md](../apk/README.md)에 있다. 접수 페이지·메일 발송은 별도다.

## 검증 기록에 남길 항목

날짜·소스 커밋·APK 버전·APK/인증서 해시, OS·SDK·에뮬레이터·화면 규격, LIVE/REVIEW/MOCK 구분, 위치 입력 출처, 실제 대기 여부, P·정지·UX 차단 결과, 차량 SDK 관측·제출·저장·재개 결과, 제외 항목과 실패 사유를 [verification-log.md](verification-log.md)에 기록한다. 사용자 원본 여행 파일·원시 위치/차량 로그·서명키·비밀번호는 커밋하지 않는다.

## 저장소 공개 기한

제출 안내에 따라 Private 저장소는 **2026년 11월 16일까지 Public으로 전환하고 11월 23일까지 공개 상태를 유지**해야 한다. 이번 작업의 저장소 공개 여부와 실제 접수 완료 여부는 별도로 확인한다.
