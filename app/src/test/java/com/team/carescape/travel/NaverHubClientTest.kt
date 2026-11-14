package com.team.carescape.travel

import java.net.URLDecoder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaverHubClientTest {
    private class RecordingHttp(private val body: String = """{"items":[]}""") : HttpGet {
        var calls = 0
        var url = ""
        var headers = emptyMap<String, String>()
        override suspend fun get(url: String, headers: Map<String, String>): String {
            calls++
            this.url = url
            this.headers = headers
            return body
        }
    }

    @Test fun hubCredentialsSelectHubEndpointAndNeverSendLegacyHeaders() = runTest {
        val http = RecordingHttp()
        val keys = TravelKeys("legacy-id", "legacy-secret", naverHubClientId = "hub-id", naverHubClientSecret = "hub-secret")
        NaverLocalClient(keys, http).search("화성행궁 주차장")
        assertEquals(1, http.calls)
        assertEquals("https://naverapihub.apigw.ntruss.com/search/v1/local", http.url.substringBefore('?'))
        val query = http.url.substringAfter('?').split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        assertEquals("화성행궁 주차장", query["query"])
        assertEquals("5", query["display"])
        assertEquals("json", query["format"])
        assertEquals(mapOf("X-NCP-APIGW-API-KEY-ID" to "hub-id", "X-NCP-APIGW-API-KEY" to "hub-secret"), http.headers)
        assertFalse(http.url.contains("hub-secret"))
    }

    @Test fun existingDevelopersCredentialsRemainUsableWithoutHubConfiguration() = runTest {
        val http = RecordingHttp()
        NaverLocalClient(TravelKeys("legacy-id", "legacy-secret"), http).search("화성행궁 식당")
        assertEquals("https://openapi.naver.com/v1/search/local.json", http.url.substringBefore('?'))
        assertEquals(mapOf("X-Naver-Client-Id" to "legacy-id", "X-Naver-Client-Secret" to "legacy-secret"), http.headers)
    }

    @Test fun incompleteHubPairReportsMissingFieldWithoutCallingEitherProvider() = runTest {
        val cases = listOf(
            TravelKeys("legacy-id", "legacy-secret", naverHubClientId = "hub-id") to "naver.search.clientSecret",
            TravelKeys("legacy-id", "legacy-secret", naverHubClientSecret = "hub-secret") to "naver.search.clientId",
        )
        cases.forEach { (keys, missing) ->
            val http = RecordingHttp()
            val client = NaverLocalClient(keys, http)
            val repo = TravelRepository(keys, client, TourApiClient(keys, http))
            val stop = GuideStop(0, "suwon_01", "화성행궁", "수원 화성행궁", 37.2818, 127.014)
            assertEquals(TravelResult.NotConfigured(listOf(missing)), repo.load(stop, TravelTab.PARKING, TripProfile()))
            assertTrue(runCatching { client.search("화성행궁 주차장") }.isFailure)
            assertEquals(0, http.calls)
        }
    }

    @Test fun hubResponsePopulatesTheExistingPlaceCardsAndMapCoordinates() = runTest {
        val http = RecordingHttp("""{"items":[{"title":"<b>행궁</b> 식당","category":"음식점>한식","roadAddress":"수원시 팔달구","mapx":"127.014","mapy":"37.2818"}]}""")
        val keys = TravelKeys(naverHubClientId = "hub-id", naverHubClientSecret = "hub-secret")
        val place = NaverLocalClient(keys, http).search("화성행궁 식당").single()
        assertEquals("행궁 식당", place.name)
        assertEquals(127.014, place.lng!!, 1e-6)
        assertEquals(37.2818, place.lat!!, 1e-6)
        assertEquals(NaverLocalClient.SOURCE, place.source)
    }
}
