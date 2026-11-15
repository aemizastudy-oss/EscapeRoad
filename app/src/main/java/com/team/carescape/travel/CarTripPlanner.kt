package com.team.carescape.travel

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 차에서 읽은 에너지 값. 못 읽은 값은 null이며 0이나 만충으로 추정하지 않는다. */
data class CarEnergy(val batteryPercent: Int? = null, val fuelPercent: Int? = null, val rangeKm: Int? = null) {
    val hasAny get() = batteryPercent != null || fuelPercent != null || rangeKm != null
}

/** 장소와 장소 사이 한 구간. 거리·시간은 직선 거리로 만든 추정치이며 실제 경로가 아니다. */
data class TripLeg(val from: GuideStop, val to: GuideStop, val straightKm: Double, val estimateKm: Int, val estimateMinutes: Int)

enum class EnergyVerdict { ENOUGH, TIGHT, CHARGE, LOW, RANGE_UNKNOWN, NO_DATA }

data class CarTripPlan(
    val legs: List<TripLeg>,
    val remainingKm: Int,
    val driveMinutes: Int,
    val tourMinutes: Int,
    val verdict: EnergyVerdict,
    val message: String,
    val restHint: String?,
)

/**
 * 내 차 맞춤 이동 계획. 남은 코스 길이를 차의 주행 가능 거리·잔량과 견준다.
 * 실제 도로 경로·교통은 반영하지 않으므로 화면에 '추정'으로 표시하고, 판단이 어려우면 결론을 내지 않는다.
 */
object CarTripPlanner {
    /** 도심 도로는 직선보다 길다. 직선 × 1.4를 추정 도로 거리로 쓴다. */
    const val ROAD_FACTOR = 1.4
    /** 수원 도심 평균 속도 가정(km/h). */
    const val CITY_KMH = 25.0
    /** 남은 코스 외에 남겨 둘 여유 거리(km). 귀갓길·우회 대비. */
    const val RESERVE_KM = 30
    /** 장소마다 관광 최소 시간(분). 게임 규칙과 같다. */
    const val TOUR_MINUTES = 15
    const val LOW_BATTERY = 20
    const val LOW_FUEL = 15

    fun legs(stops: List<GuideStop>, fromIndex: Int): List<TripLeg> =
        stops.drop(fromIndex.coerceIn(0, stops.size)).zipWithNext { a, b ->
            val straight = haversineKm(a.lat, a.lng, b.lat, b.lng)
            val km = (straight * ROAD_FACTOR).roundToInt().coerceAtLeast(1)
            TripLeg(a, b, straight, km, (km / CITY_KMH * 60).roundToInt().coerceAtLeast(3))
        }

    /** [fromIndex]는 지금 있는(또는 다음에 갈) 장소 번호다. 그 장소부터 끝까지의 구간을 계산한다. */
    fun plan(stops: List<GuideStop>, fromIndex: Int, energy: CarEnergy): CarTripPlan {
        val legs = legs(stops, fromIndex)
        val remaining = legs.sumOf { it.estimateKm }
        val drive = legs.sumOf { it.estimateMinutes }
        val tours = (stops.size - fromIndex.coerceIn(0, stops.size)) * TOUR_MINUTES
        val need = remaining + RESERVE_KM
        val (verdict, message) = when {
            energy.rangeKm != null && energy.rangeKm >= need ->
                EnergyVerdict.ENOUGH to if (remaining == 0) "주행 가능 ${energy.rangeKm}km. 남은 이동이 없어요."
                else "주행 가능 ${energy.rangeKm}km · 남은 코스 약 ${remaining}km. 여유 ${RESERVE_KM}km를 남기고도 충분해요."
            energy.rangeKm != null && energy.rangeKm >= remaining ->
                EnergyVerdict.TIGHT to "주행 가능 ${energy.rangeKm}km · 남은 코스 약 ${remaining}km. 코스는 마칠 수 있지만 빠듯해요. 중간에 충전·주유를 권해요."
            energy.rangeKm != null ->
                EnergyVerdict.CHARGE to "주행 가능 ${energy.rangeKm}km · 남은 코스 약 ${remaining}km. 다음 장소로 가기 전에 충전·주유해 주세요."
            (energy.batteryPercent ?: 101) < LOW_BATTERY || (energy.fuelPercent ?: 101) < LOW_FUEL ->
                EnergyVerdict.LOW to "${levelText(energy)}로 낮아요. 주행 가능 거리는 받지 못했어요. 출발 전 충전·주유를 권해요."
            energy.batteryPercent != null || energy.fuelPercent != null ->
                EnergyVerdict.RANGE_UNKNOWN to "${levelText(energy)}예요. 주행 가능 거리는 받지 못해 코스 완주 여부는 판단하지 않아요."
            else -> EnergyVerdict.NO_DATA to "차량 에너지 정보를 받지 못했어요. 계기판의 주행 가능 거리를 확인해 주세요."
        }
        val rest = if (drive + tours >= 120) "운전과 관광을 합쳐 2시간이 넘어요. 장소 사이에 한 번은 쉬어 가세요." else null
        return CarTripPlan(legs, remaining, drive, tours, verdict, message, rest)
    }

    private fun levelText(e: CarEnergy) = listOfNotNull(e.batteryPercent?.let { "배터리 $it%" }, e.fuelPercent?.let { "연료 $it%" }).joinToString(" · ")

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(a))
    }
}
