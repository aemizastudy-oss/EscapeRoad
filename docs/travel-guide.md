# 공모전용 여행 도우미

2026-11-15 사용자 요청으로 **직접 준비한 장소 카드 + Pleos 길 안내**로 전환했다. 네이버 지도·지역 검색과 TourAPI 호출, 지도 SDK 의존성, 로컬 인증값의 BuildConfig/Manifest 주입은 Debug·Release 모두 제거했다. 과거 연동 시도는 verification-log의 이력이다.

## 사용법

1. 안전한 P·정지·CarUx 허용 상태에서 로비의 여행 도우미를 연다.
2. 여행 날짜·기간·관심사를 선택한다. 왼쪽 방문 순서에서 관광지를 선택한다.
3. 주차·맛집 카드에서 주소, 출처, 안내 대상 설명을 확인하고 `+ 담기` 또는 `+ 일정에 담기`를 누른다.
4. `주변 관광지로 안내`는 기존 Pleos NaviHelper를 통해 해당 관광지 대표점까지 안내를 요청한다. **식당·주차장 입구 안내가 아니다.** MOCK에서는 실제 길 안내를 요청하지 않는다.

현재 고정 카드 40개(주차 9·맛집 10·카페 6·체험 8·숙소 3·야경/저녁 4)를 등록했다. 5개 관광지 모두 각 분류에 후보가 있으며, 권역을 공유하는 장소는 같은 ID로 저장한다. 카페는 여행 정보에서 카페 관심사를 선택하면 표시되고, 숙소는 1박 이상에서 야경 대신 표시된다. 먼 후보는 차량 이동을 안내한다. 별점·실시간 영업·예약·요금·주차 빈자리는 제공하지 않는다. 사진·외부 지도는 쓰지 않는다.

## 데이터와 저장

- 고정 목록: `travel/CuratedPlaces.kt`. 공식 출처와 확인 한계는 [장소 자료](curated-places.md).
- 기존 여행 프로필은 유지한다. 새 장소 저장/일정 배정은 `travel_guide` SharedPreferences의 `curated_plan_v1`을 사용한다.
- 이전 네이버/TourAPI 검색으로 저장한 `plan`은 읽거나 새 목록으로 재사용하지 않는다. 사용자 원본을 임의 삭제하지 않으며, 과거 자료를 공모전 데이터로 간주하지 않는다.
- 방탈출의 `trip-progress.json`과 챕터·힌트·단서 기록은 변경하지 않는다.
- 기존 `nearby.properies.env`, `nearby.properties` 등 로컬 인증 파일은 그대로 남지만 현재 빌드에서는 읽지 않는다. 저장소 제외 규칙도 유지한다.
- 장소의 `lat/lng`는 입구 좌표 미확인으로 null이다. `navigationName/Lat/Lng`는 기존 관광지 대표점이며 별도로 저장한다. 직선거리나 도보 시간을 실제 경로처럼 계산하지 않는다.

## 실행·검증

Android Studio: Gradle Sync → Pleos 에뮬레이터 선택 → `app` Run → 안전 정차에서 여행 도우미.

Ubuntu 재현 명령:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

Ubuntu 실행 여부와 실제 기기 검증 결과는 [검증 로그](verification-log.md)에 구분한다. Release APK는 서명 설정이 없으면 unsigned이며 그대로 제출 완료로 표시하지 않는다.


## 고정 자료 추가 방법

`CuratedPlaces.kt`의 `entries`에 ID·연결 관광지·분류·이름·주소·공식 출처 URL·안내 문구를 추가한다. 여러 관광지에서 보여 줄 후보는 `otherCheckpoints`에 연결할 ID를 더한다. 기존 저장을 위해 이미 배포한 카드 ID는 바꾸지 않는다. `note`에는 이동·이용 조건을 적고, 확인되지 않은 입구 좌표·영업시간·요금·평점은 만들지 않는다. 자료 수정 후 Gradle 테스트와 APK 재빌드, Android Studio Run이 필요하며 앱에서 실시간 내려받지 않는다.
