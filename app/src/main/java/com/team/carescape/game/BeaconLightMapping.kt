package com.team.carescape.game

/** One fire from the flint plus the current ON state of four reading lamps. */
object BeaconLightMapping {
    const val BASE_LIT = 1
    const val LIGHT_COUNT = 4
    const val BEACON_COUNT = BASE_LIT + LIGHT_COUNT

    fun beaconCount(lightsOn: Int): Int {
        require(lightsOn in 0..LIGHT_COUNT)
        return BASE_LIT + lightsOn
    }
}
