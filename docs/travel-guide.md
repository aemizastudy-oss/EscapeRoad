# 여행 도우미 (부가 서비스)

방탈출과 별개로, 원하는 사람만 쓰는 여행 추천 화면이다. **로비 → 여행 도우미**로만 들어간다. 방탈출 진행 기록은 읽기만 하고(마지막 장소 공개 여부) 바꾸지 않는다. 정차(P) 중에만 보인다.

## 화면

여행 앱의 흔한 구성(지도 + 목록, 시간대 타임라인, 빈 칸 채우기와 담기, 아이콘 카테고리)을 차 화면에 맞춰 옮겼다.

1. **여행 정보 입력** (처음 한 번, 위쪽 요약 칩을 누르면 다시): 일정(당일치기 · 1박 2일 · 2박 3일), 출발 날짜(미정 가능), 누구와, 관심사, 식사. 기기 안 별도 파일(`SharedPreferences: travel_guide`)에만 저장한다.
2. **위쪽 막대**: 여행 요약 칩, 화면 전환(일정 · 둘러보기 · 저장 n), 로비.
3. **왼쪽 네이버 지도**: 공개된 경유지 번호와 그날 방문 순서, 고른 카테고리의 추천 장소 핀을 실제 지도에 표시한다. 「주변 확대 / 경로 전체 보기」로 범위를 바꾼다. 경유지 핀은 장소 선택, 추천 핀은 카드 선택으로 연결된다. 연결선은 방문 순서이며 실제 도로 경로나 길찾기 결과가 아니다. 지도 키가 없거나 인증·통신에 실패하면 안내와 재시도를 표시한다.
4. **일정**: 일차를 고르면 세로 타임라인. 10:00 시작, 관광 약 1시간, 식사 1시간, 경유지 사이 「차로 약 n분」(직선거리 어림). 점심·저녁·숙소는 빈 칸이며, 칸을 누르면 그 근처 맛집·숙소가 열리고 카드의 「+ 일정에 담기」로 채운다(「비우기」로 비움). 휴관 경고.
5. **카테고리 칩**: 색 점과 한 글자 표시가 붙은 주차 · 맛집 · (카페) · 체험 · 숙소(당일치기는 야경·저녁). 관심사에 맞는 칩을 앞으로.
6. **카드**: 이름, 종류, 거리(걸어서 몇 분), 「+ 담기」. 누르면 주소·전화·출처·「길 안내」(정차 중에만, 시연 모드는 요청하지 않음).
7. **둘러보기**: 일정과 상관없이 경유지와 카테고리만 골라 본다.
8. **저장**: 담은 곳 목록(어느 날 어느 칸에 담았는지), 길 안내, 빼기. 담은 정보도 기기 안에만 저장한다.

**개발용 예시 데이터:** debug 빌드에서 키가 없으면 「예시 데이터로 보기 (개발용)」로 배치를 확인할 수 있다. 장소 이름이 「예시 맛집 1」처럼 나오고 출처에 「시연용 예시 · 실제 장소 아님」이라고 표시한다. release 빌드에는 없다.

## 스포일러

다섯 번째 장소는 방탈출의 최종 추리로 밝혀지는 곳이라, 공개 전에는 `??? · 마지막 장소`로 가리고 검색하지 않는다. 그날 저녁 추천도 바로 앞 장소 근처로 돌린다. 방탈출 없이 여행만 하는 이용자는 **스포일러 주의 · 그래도 보기**로 열 수 있다(이 화면을 보는 동안만).

## 데이터 출처

| 탭 | 출처 |
| --- | --- |
| 주차 · 맛집 · 카페 · 야경 | 네이버 검색 API ‘지역’ — 검색어(예: `수원 화성행궁 주차장`), 한 번에 최대 5개 |
| 체험 | 네이버 지역 검색 + TourAPI 레포츠(28) |
| 숙소 | 네이버 지역 검색 + TourAPI 숙박(32) |

- 검색에는 **경유지 이름과 경유지 좌표만** 쓴다. 이용자 위치는 보내지 않는다.
- 결과는 화면을 보는 동안만 메모리에 둔다(파일로 저장하지 않음).
- 별점·리뷰는 다루지 않는다. 사진은 공공누리 유형이 섞여 있어 쓰지 않는다.
- 출처를 화면 아래와 카드마다 밝힌다(네이버 지역 검색, ⓒ한국관광공사).

## API 키 넣기

### 네이버 지도

현재 사용 중인 `C:\car_escape\nearby.properies.env` 파일을 그대로 읽는다. 다음 항목에 Maps 키 ID를 저장한 후 다시 빌드하고 Android Studio에서 Run한다. 파일명에 포함된 `properies`도 지원한다. 새 설정은 `nearby.properties.example`을 `nearby.properties`로 복사해 사용할 수 있다.

```properties
naver.map.keyId=발급받은_Maps_키_ID
```

설정 파일은 `local.properties` → `nearby.properies.env` → `nearby.properties.env` → `nearby.properties` 순서로 읽으며, 같은 항목은 뒤 파일의 값이 우선한다. `naver.maps.client.id`, `NAVER_MAP_KEY_ID`, `NAVER_MAPS_KEY_ID`, `NCP_KEY_ID`도 지원하지만 위 항목 하나만 쓰는 것을 권장한다. 모든 인증용 로컬 파일은 `.gitignore`로 제외한다. 실제 ID는 코드·문서·검증 로그에 기록하지 않는다.

네이버 클라우드 Maps Application에서 **Dynamic Map**을 선택하고 Android 패키지 **`com.team.carescape`**를 등록해야 한다. [공식 Android 시작 문서](https://navermaps.github.io/android-map-sdk/guide-ko/1.html)에 맞춰 SDK 3.24.1과 `com.naver.maps.map.NCP_KEY_ID`를 사용한다. Maps ID는 앱 인증용으로 APK에 포함되므로 패키지 제한을 적용한다. Client Secret은 지도 SDK에 필요하지 않다.

지도는 기기 현재 위치를 요청하지 않고 관광지 대표 좌표를 사용한다. 공개 전 마지막 장소는 SDK에 전달하지 않는다. 정차 조건이 풀리거나 앱이 백그라운드로 이동하면 기존 안전 화면으로 전환하고 MapView를 해제한다. 재진입하면 다시 생성한다.

### 장소 검색과 TourAPI (지도와 별도 인증)

Maps ID는 주차장·맛집 검색용 키가 아니다. NAVER API HUB의 지역 검색을 신청한 뒤, 같은 `C:\car_escape\nearby.properies.env` 파일에서 기존 `naver.map.keyId` 아래에 다음 두 줄을 추가하고 저장한다.

```properties
naver.search.clientId=발급받은_API_HUB_Client_ID
naver.search.clientSecret=발급받은_API_HUB_Client_Secret
```

Android Studio에서 **Build Variants → app: debug → Run app**으로 다시 빌드·설치한다. 그 후 시연 MOCK/P 정차 → 로비 → 여행 도우미 → 주차 또는 맛집을 선택한다. 두 값은 `NAVER API HUB → Application → 인증 정보`에서 발급한 값이며 Maps 인증이나 기존 Developers 인증과 섞지 않는다. 인증 값을 채팅·Git에 올리지 않는다.

현재는 로컬 시연용 Debug 앱에서 API HUB를 직접 호출한다. `https://naverapihub.apigw.ntruss.com/search/v1/local`과 `X-NCP-APIGW-API-KEY-ID`, `X-NCP-APIGW-API-KEY` 헤더를 사용한다. 응답은 기존 장소 카드와 지도 핀에 연결된다. HUB 두 항목 중 하나라도 설정하면 HUB를 선택하며, 한 항목이 비어 있을 때는 부족한 항목을 안내하고 다른 계정의 Developers API로 자동 전환하지 않는다. 키가 있다는 상태 문구는 설정 확인이며 실제 응답 성공을 뜻하지 않는다. [공식 지역 검색 문서](https://api.ncloud-docs.com/docs/naver-api-hub-search-local).

새 HUB 인증은 Debug APK에만 포함한다. Release 빌드에서는 새 HUB 두 필드를 비운다. 배포용 검색은 Secret을 보관하는 중계 서버로 연결하는 후속 작업이 필요하다. 기존 Developers/TourAPI 설정의 배포 처리까지 변경한 것은 아니다. `nearby.proxy.url`은 이 버전의 직접 호출 경로에서는 사용하지 않으며 빈 값으로 두어도 로컬 Debug HUB 검색에 영향이 없다.

기존 Developers 키가 있고 HUB 항목을 설정하지 않았다면 이전 호출 방식도 유지한다. Developers와 TourAPI 키는 아래 이름으로 `local.properties` 또는 같은 인증 파일에 저장할 수 있다.

```properties
NAVER_CLIENT_ID=발급받은_아이디
NAVER_CLIENT_SECRET=발급받은_시크릿
TOUR_API_KEY=공공데이터포털_일반_인증키(Decoding)
```

- `NAVER_CLIENT_ID/SECRET`는 기존 Developers 지역 검색용 인증이다. 신규 검색 신청은 NAVER API HUB로 이전됐으므로, 새 API HUB 키는 위의 `naver.search.*`에 넣는다. [공식 이전 공지](https://developers.naver.com/notice/article/32530)를 참고한다.
- TourAPI: data.go.kr → 「한국관광공사_국문 관광정보 서비스_GW」 활용 신청 → 일반 인증키(Decoding 값).

## 확인해야 할 것 (미확인)

- 네이버 검색 API의 상업적 이용·출처 표시 조건과 지역 검색 일일 호출 한도.
- TourAPI 상업적 이용 조건(출처 표시)과 항목별 공공누리 유형.
- 키를 넣은 뒤 실제 응답 형식(테스트는 문서 형식으로 만든 예시 응답으로만 했다).
- 앱 안에 Client Secret이 들어가므로, 실제 서비스 때는 중간 서버로 옮겨야 한다.
- 공공데이터 「전국주차장정보표준데이터」(요금·운영시간) 연결은 다음 단계.
