package com.ljyh.mei.data.model.weapi

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRequestPreferencesTest {
    @Test fun requestBuilderUsesProvidedIdentityWithoutApplicationOrDataStore() {
        val request = buildGetHomePageResourceShow("true", "existing-device-id")
        assertEquals("true", request.refresh)
        assertEquals("existing-device-id", JsonParser.parseString(request.adExtJson).asJsonObject["android_id"].asString)
    }

    @Test fun storedEmptyIdIsPreservedRatherThanRegenerated() {
        val request = buildGetHomePageResourceShow("false", "")
        assertEquals("", JsonParser.parseString(request.adExtJson).asJsonObject["android_id"].asString)
    }

    @Test fun callerCanStillOverrideAdPayloadThroughDsl() {
        val request = buildGetHomePageResourceShow("false", "existing") {
            adExtJson = "{\"android_id\":\"override\"}"
        }
        assertEquals("override", JsonParser.parseString(request.adExtJson).asJsonObject["android_id"].asString)
        assertEquals("override", buildAdExtJson("existing") { androidId = "override" }.androidId)
    }
}
