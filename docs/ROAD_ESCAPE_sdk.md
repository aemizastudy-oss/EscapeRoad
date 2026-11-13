# ROAD ESCAPE — 차량 여행형 방탈출 앱 기획서

> **최종 수정:** 2026-11-12 (관광지 중심·전 경유지 반경 500m 임시 적용)  
> **대상:** 현대자동차그룹 차량용 앱 공모전 · Pleos Connect  
> **상태 표시:** **확정** = 제품 방향·규칙 / **초기값** = 현장 테스트 후 조정 가능 / **검증 필요** = SDK·차량 환경에서 확인 전

## 1. 핵심 콘셉트 · 확정

**차량으로 지정된 여행 경로를 따라가고, 각 경유지에서 관광한 뒤 차로 돌아와 짧은 방탈출을 진행하는 여행형 미스터리 게임.**

- 게임은 **관광지 반경 내 도착 → 관광 → 차로 복귀 → 퍼즐 해금 → 다음 경유지** 순서로 진행한다.
- 경유지의 역사·특징을 관찰해 다음 문제의 단서를 얻도록 유도한다.
- **주행 중 게임 화면·조작은 허용하지 않는다.** Pleos의 공식 주행 제한 상태에서 게임 이용이 가능할 때만 플레이한다.
- 별도 장소 방문 증빙(휴대전화 사진·카메라 등)은 사용하지 않는다.

## 2. 여행 코스 · 확정

**수원 행궁동에서 시작해 광교 방향으로 이동하는 5개 경유지 코스**를 채택한다.

| 순서 | 경유지 | 비고 |
|---|---|---|
| CH.1 | **화성행궁** | 출발 챕터 |
| CH.2 | **화홍문** | 장소 관찰형 챕터 |
| CH.3 | **만석공원(만석거)** | 드라이브 연계 |
| CH.4 | **수원광교박물관** | 기록 수집 |
| CH.5 | **광교호수공원** | 최종 챕터 |

- **관광지 5곳과 방문 순서는 확정.** 챕터별 세부 문제와 줄거리는 이번 문서에서 다루지 않는다.
- **도착 인증 중심점은 각 관광지의 위도·경도**다. 해당 지점으로부터 **반경 500m를 모든 경유지에 동일 적용(임시값)**한다.
- 특정 주차장을 지정하거나 주차장 좌표를 인증 기준으로 사용하지 않는다. 사용자는 관광지 반경 내에 합법적으로 주차할 수 있다.
- 관광지의 **정확한 대표 좌표는 아직 입력 전**이다. 넓은 공원은 실제 방문할 구역을 기준으로 대표 지점을 선정한다.
- 관광지로 가는 길 안내와 주차 위치 선정은 구분한다. 합법적인 주차 가능 여부는 이용자가 확인해야 한다.

## 3. 핵심 사용자 흐름 · 확정

1. 앱에서 확정된 수원 여행 코스 시작.
2. `NaviHelper`로 **해당 챕터의 관광지** 길 안내.
3. `FusedLocation`으로 차량의 **지정 반경 내 도착** 확인.
4. 주차하고 **「관광 시작」** 버튼 누름 → **현재 시각 및 챕터 ID를 즉시 영구 저장**.
5. 사용자는 자유롭게 관광한다. 시동 OFF 등으로 앱이 종료돼도 **실시간 타이머를 실행할 필요 없음**.
6. 관광 후 차량으로 복귀해 앱을 다시 연다(시동을 껐던 경우 재시동 후 실행).
7. **현재 시각 − 저장된 관광 시작 시각 ≥ 15분**인지 확인한다.
8. **최신 차량 위치 재확인 + Pleos상 게임 이용 가능한 주차 상태**까지 충족하면 퍼즐 해금.
9. 퍼즐 완료 및 단서 저장 → 다음 경유지 안내. 다섯 번째 챕터 완료 시 여행 종료.

> **중요:** 시동 OFF/ON 자체는 타이머나 해금 조건이 아니다. 차량 전원이 꺼진 동안 앱이 계속 동작하는 게 아니라, **앱 재실행 시 시간 차이를 계산**한다. 앱이 자동 실행되는 것도 가정하지 않는다.

## 4. 인증·관광 규칙

| 항목 | 설정 | 상태 |
|---|---|---|
| 도착 인증 기준점 | **각 관광지 대표 좌표** | 확정 |
| 모든 경유지 인증 반경 | **500m** | **임시값(추후 조정)** |
| 모든 경유지 최소 관광 시간 | **15분** | **확정** |
| 시간 시작 기준 | 「관광 시작」 버튼 누른 순간 | **확정** |
| 퍼즐 이용 조건 | 위치 인증 + 15분 경과 + 게임 허용 주차 상태 + 이전 챕터 완료 | **확정** |
| 거리 숫자 표시 | 사용자 화면에 표시하지 않음 | **확정** |

- **15분이 지났어도 차량이 다른 곳으로 이동했다면 해당 경유지 퍼즐을 열지 않는다.** 복귀 후 위치를 다시 확인한다.
- **500m는 임시로 동일 적용**하는 수치이며 Pleos 공식 권장값이 아니다. 주변 도로·다른 장소가 포함될 수 있어 추후 관광지별 조정이 필요하다.
- 지정 주차장은 없다. 반경 내 합법적인 주차가 불가능하거나 주차장이 만차인 경우에는 다른 합법적 주차 공간을 찾되, 위치 인증과 안전한 주차 조건을 동일하게 적용한다.
- **실제 관광 여부는 SDK로 직접 확인할 수 없다.** 시간 제한과 장소 관찰형 단서로 방문을 유도하지만, 차 안에서 기다리는 행위를 완전히 차단하지는 못한다.
- `getLastKnownLocation()`은 과거 좌표일 수 있으므로 **단독 인증에 사용하지 않는다**. 갱신 위치·타임스탬프·오류를 확인해야 한다.
- Pleos FusedLocation 공식 문서에서 **고정된 위치 갱신 주기를 확인하지 못했다.** 따라서 '15초 연속 같은 위치' 인증은 적용하지 않는다.

## 5. 사용 기술 · Pleos Background만 사용

| SDK | 용도 |
|---|---|
| **NaviHelper** | 관광지 목적지·경유지 길 안내 |
| **FusedLocation** | 차량 위치 수신, 관광지 반경 진입 및 위치 재인증 |
| **Vehicle** | 지원 차량에 한해 차량 상태 기반 퍼즐 보조 |
| **TextToSpeech** | 게임 스토리·힌트 음성 출력 |
| **SpeechToText** | 음성 정답 입력(선택 기능) |

- 외부 지도·위치 API, Firebase, 외부 AI·클라우드 서비스 등은 **사용하지 않는다**.
- 주행 중 게임 제한은 **Pleos 공식 주행 제한 가이드**를 따른다. 단순히 P단이라고 가정하는 것과 앱에서 게임 이용을 허용받는 것은 구별한다.
- SDK별 권한, 차량 지원 여부, 차량별 실제 동작은 **검증 필요**.
- 아래 Kotlin 코드는 **앱 내부 데이터·수학 연산 예시**로, Pleos에 없는 위치 API를 만들어낸 코드가 아니다.

## 6. 개발용 Kotlin 코드 — 경유지 좌표·반경 등록 및 도착 판정

### 6.1 경유지 정보와 등록/수정

```kotlin
// WaypointConfig.kt — Kotlin 기본 기능만 사용

data class WaypointConfig(
    val id: String,
    val name: String,
    val latitude: Double,          // 관광지 대표 지점 위도
    val longitude: Double,         // 관광지 대표 지점 경도
    val radiusMeters: Double = 500.0, // 전 경유지 공통 임시 반경
    val minTourMinutes: Int = 15
) {
    init {
        require(id.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(radiusMeters.isFinite() && radiusMeters > 0.0)
        require(minTourMinutes == 15)  // 확정 규칙: 모든 경유지 15분
    }
}

object WaypointCatalog {
    private val items = linkedMapOf<String, WaypointConfig>()

    fun register(config: WaypointConfig) {
        items[config.id] = config
    }

    fun get(id: String): WaypointConfig? = items[id]

    fun all(): List<WaypointConfig> = items.values.toList()

    fun changeRadius(id: String, newRadiusMeters: Double): Boolean {
        val old = items[id] ?: return false
        items[id] = old.copy(radiusMeters = newRadiusMeters)
        return true
    }
}

// 실제 관광지 좌표를 확보해 앱 초기화 시 등록 (예: 화홍문).
// 다른 네 경유지도 같은 방식으로 등록한다.
fun registerHwahongmun(attractionLat: Double, attractionLon: Double) {
    WaypointCatalog.register(
        WaypointConfig(
            id = "hwahongmun",
            name = "화홍문",
            latitude = attractionLat,
            longitude = attractionLon,
            radiusMeters = 500.0
        )
    )
}

// 추후 현장 검증 후 반경을 조정할 경우에만 사용:
// WaypointCatalog.changeRadius("hwahongmun", 400.0)
```

- 나머지 4개 경유지도 **관광지 대표 좌표를 확보한 뒤** 같은 방식으로 등록한다. 최초 반경은 모두 **500m**이다.
- **등록 좌표·반경은 배포할 앱의 초기 설정**으로 관리한다. `WaypointCatalog`는 **실행 중 메모리 저장 예시**이므로 실행 중 변경한 반경은 재실행하면 자동 보존되지 않는다.
- 플레이어에게 좌표·반경 수정 권한을 제공하지 않는다.

### 6.2 위치 간 거리 계산 및 도착 판정

```kotlin
// WaypointDistance.kt — 외부 API 없이 순수 수학 계산
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2).pow(2.0)
    return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, a)))
}

fun isInsideWaypoint(vehicleLat: Double, vehicleLon: Double, target: WaypointConfig): Boolean {
    if (!vehicleLat.isFinite() || !vehicleLon.isFinite()) return false
    if (vehicleLat !in -90.0..90.0 || vehicleLon !in -180.0..180.0) return false
    return distanceMeters(vehicleLat, vehicleLon, target.latitude, target.longitude) <= target.radiusMeters
}
```

- 차량 좌표는 공식 Pleos `FusedLocation`의 **위치 갱신 콜백**에서 받아 전달한다.
- `FusedLocation`의 `addProximityAlert()`를 사용해 지정 영역 진입을 감지하는 방식도 가능하다. 단, **실제 SDK 함수 인자·권한·수명주기는 공식 문서 기준으로 연동**한다.
- 거리 함수가 `true`인 것은 **위치 조건만 통과**했다는 뜻이며, 관광 15분·안전한 주차 조건은 별도로 확인한다.

## 7. 개발용 Kotlin 코드 — 관광 시작 후 15분 판정

```kotlin
// TourTime.kt — 영구 저장/불러오기는 Pleos 앱 환경에서 별도로 연동

data class TourStartRecord(
    val waypointId: String,
    val startedAtEpochMillis: Long
)

fun canUnlockByTime(record: TourStartRecord, nowEpochMillis: Long): Boolean {
    if (record.startedAtEpochMillis <= 0 || nowEpochMillis < record.startedAtEpochMillis) return false
    return nowEpochMillis - record.startedAtEpochMillis >= 15L * 60L * 1_000L
}

// 관광 시작 버튼을 누른 시각을 저장한다고 가정한 값의 생성 예시:
fun createTourStartRecord(waypointId: String): TourStartRecord =
    TourStartRecord(waypointId, System.currentTimeMillis())
```

**실제 앱 구현에서 반드시 해야 할 일**

1. 도착 인증 후 「관광 시작」 클릭 시 `TourStartRecord`와 현재 챕터·단서 상태를 **비휘발성 저장소에 기록**하고 저장 완료를 확인한다.
2. 시동 OFF/앱 종료 뒤 재실행 시 저장된 기록을 **복원**한다. 앱이 꺼져 있는 동안 타이머를 반복 실행할 필요는 없다.
3. `canUnlockByTime(...)` + 최신 위치 인증 + Pleos 게임 허용 주차 상태 + 선행 챕터 완료를 모두 통과하면 퍼즐을 연다.
4. 퍼즐 완료 시 다음 챕터 진행 상태도 저장한다.

> **검증 필요:** Pleos 환경에서 사용할 실제 영구 저장 구현, 전원 OFF/ON 후 데이터 보존, 시스템 시각 변경·조작 시 처리. `System.currentTimeMillis()`는 **앱 자체 시간 계산**이며 차량 시동 여부를 감지하는 API가 아니다. 외부 시간 서버를 사용하지 않는 조건에서 임의 시각 조작을 완벽히 막을 수는 없다.

## 8. 예외 처리 및 검증 체크리스트

| 발생 상황 | 처리 원칙 |
|---|---|
| 위치 데이터 없음·오래됨 | 도착 인증 보류, 다시 위치 확인 |
| 주변 주차장 만차 | 해당 관광지 반경 500m 내 다른 합법적 주차 공간 확인. 범위 밖에서는 인증하지 않음 |
| 관광지 주변 도로에서 범위 진입 | 게임 허용 주차 상태 확인. 지나가기만 해도 위치 조건이 충족될 수 있으므로 관광 시작·15분 조건과 함께 확인 |
| 관광 시작 기록 저장 실패 | 관광 시작 완료로 처리하지 않음 |
| 시동 OFF 후 앱 종료 | 재실행 시 관광 시작 기록·챕터 복원 |
| 15분 미경과 | 퍼즐 잠금 유지 |
| 15분 경과했지만 위치가 다름 | 해당 경유지 위치 재인증 요구 |
| 주행 제한 상태로 전환 | 즉시 퍼즐 중지 및 진행 상황 저장 |
| 차량 시계 변경 | 비정상 경과 시간 판정 보류; 완전한 위변조 방지는 미보장 |

**출시 전 필수 확인:** 5곳의 실제 관광지 대표 좌표 및 반경 500m 내 합법적 주차 접근성, 차량별 위치 정확도, 주행 제한, 재시동 후 데이터 복원, `NaviHelper` 길 안내 동작.

## 공식 참고 문서

- [현대자동차그룹 앱 공모전](https://appdevchallenge.auto-hmg.io/)
- [FusedLocation](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [NaviHelper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [Vehicle](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [TextToSpeech](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)
- [SpeechToText](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)
- [Pleos 주행 제한 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [Pleos 앱 구조 및 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)

**요약:** 확정 5개 관광지 이동 → **관광지 중심 500m 이내** 차량 위치 인증 → 「관광 시작」 시각 저장 → 관광(필요 시 시동 OFF) → 복귀 후 **15분 경과 + 위치 재인증 + 안전한 주차** 확인 → 방탈출 진행.
