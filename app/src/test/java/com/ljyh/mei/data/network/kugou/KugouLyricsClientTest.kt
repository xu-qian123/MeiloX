package com.ljyh.mei.data.network.kugou

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class KugouLyricsClientTest {

    /** Mirrors the private key in the client (XOR mask after the 4-byte header). */
    private val krcKey = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
    )

    @Test
    fun parsesSongSearchResults() {
        val body = """
            {"data":{"info":[
              {"hash":"HASH1","songname":"Song A","singername":"Artist A","duration":245,"album_name":"AlbumA","album_audio_id":12345},
              {"hash":"","songname":"skip","singername":"x","duration":1}
            ]}}
        """.trimIndent()
        val results = parseKugouSearchResults(body)
        assertEquals(1, results.size)
        assertEquals("HASH1", results[0].hash)
        assertEquals("Song A", results[0].title)
        assertEquals("Artist A", results[0].artist)
        assertEquals(245_000L, results[0].durationMs)
    }

    @Test
    fun parsesLyricCandidates() {
        val body = """{"candidates":[{"id":"1","accesskey":"K","duration":245000,"score":80}]}"""
        val candidates = parseKugouLyricCandidates(body)
        assertEquals(1, candidates.size)
        assertEquals("1", candidates[0].id)
        assertEquals("K", candidates[0].accessKey)
        assertEquals(80, candidates[0].score)
    }

    @Test
    fun convertsKrcLinesToEditableYrc() {
        val krc = "[1000,3000]<0,500,0>你<500,500,0>好"
        assertEquals(
            "[1000,3000](1000,500,0)你(1500,500,0)好",
            convertKugouKrcToEditableYrc(krc),
        )
    }

    @Test
    fun decodesKrcPayloadRoundTripWithTranslation() {
        val languageJson = JSONObject()
            .put(
                "content",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("type", 1)
                        .put(
                            "lyricContent",
                            org.json.JSONArray().put(
                                org.json.JSONArray().put("译文一"),
                            ).put(
                                org.json.JSONArray().put("译文二"),
                            ),
                        ),
                ),
            )
            .toString()
        val languageTag = "[language:${Base64.getEncoder().encodeToString(languageJson.toByteArray())}]"
        val krc = buildString {
            appendLine("[1000,3000]<0,500,0>你<500,500,0>好")
            appendLine("[4000,2000]<0,300,0>世<300,300,0>界")
            append(languageTag)
        }
        val body = """{"status":200,"content":"${encryptKrc(krc)}"}"""

        val payload = decodeKugouKrcDownloadPayload(body)
        assertNotNull(payload)
        assertEquals(
            "[1000,3000](1000,500,0)你(1500,500,0)好\n[4000,2000](4000,300,0)世(4300,300,0)界",
            payload!!.lyrics,
        )
        assertEquals("[00:01.00]译文一\n[00:04.00]译文二", payload.translatedLyrics)
    }

    @Test
    fun rejectsBrokenPayloads() {
        assertNull(decodeKugouKrcDownloadPayload("""{"status":404}"""))
        assertNull(decodeKugouKrcDownloadPayload("""{"status":200,"content":""}"""))
        assertNull(decryptKugouKrcPayload(byteArrayOf(1, 2, 3)))
    }

    /** 4-byte header + XOR mask + zlib, exactly like the Kugou download payload. */
    private fun encryptKrc(krc: String): String {
        val deflated = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out).use { it.write(krc.toByteArray()) }
        }.toByteArray()
        val masked = ByteArray(deflated.size) { index ->
            (deflated[index].toInt() xor krcKey[index % krcKey.size].toInt()).toByte()
        }
        return Base64.getEncoder().encodeToString(byteArrayOf(0x6b, 0x72, 0x63, 0x31) + masked)
    }
}
