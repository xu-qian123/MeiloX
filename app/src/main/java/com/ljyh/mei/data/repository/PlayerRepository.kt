package com.ljyh.mei.data.repository

import com.ljyh.mei.data.model.Lyric
import com.ljyh.mei.data.model.Tracks
import com.ljyh.mei.data.model.AmllLyricSearchResult
import com.ljyh.mei.data.model.api.GetIntelligence
import com.ljyh.mei.data.model.api.GetLyric
import com.ljyh.mei.data.model.api.GetLyricV1
import com.ljyh.mei.data.model.api.GetSongDetails
import com.ljyh.mei.data.model.api.Intelligence
import com.ljyh.mei.data.model.qq.u.GetLyricData
import com.ljyh.mei.data.model.qq.u.GetSearchData
import com.ljyh.mei.data.model.qq.u.LyricResult
import com.ljyh.mei.data.model.qq.u.SearchResult
import com.ljyh.mei.data.model.weapi.Like
import com.ljyh.mei.data.model.weapi.Radio
import com.ljyh.mei.data.network.QQMusicUApiService
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.network.api.ApiService
import com.ljyh.mei.data.network.api.WeApiService
import com.ljyh.mei.data.network.safeApiCall
import android.util.Base64
import com.ljyh.mei.data.model.api.CheckSongLike
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class PlayerRepository(
    private val qqMusicUApiService: QQMusicUApiService,
    private val apiService: ApiService,
    private val weApiService: WeApiService
) {

    suspend fun searchNew(keyword: String): Resource<SearchResult> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                qqMusicUApiService.search(
                    GetSearchData(
                        comm = GetSearchData.Comm(),
                        req = GetSearchData.Req(param = GetSearchData.Req.Param(query = keyword))
                    )
                )
            }
        }
    }

    private fun b64encode(str: String): String {
        return Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    suspend fun getLyricNew(
        title: String,
        album: String,
        artist: String,
        duration: Long,
        id: Long
    ): Resource<LyricResult> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                qqMusicUApiService.getLyric(
                    GetLyricData(
                        comm = GetLyricData.Comm(),
                        getPlayLyricInfo = GetLyricData.GetPlayLyricInfo(
                            param = GetLyricData.GetPlayLyricInfo.GetLyric(
                                singerName = b64encode(artist),
                                songName = b64encode(title),
                                albumName = b64encode(album),
                                interval = duration,
                                songID = id
                            )
                        )
                    )
                )
            }
        }
    }

    suspend fun getLyricLrc(
        title: String,
        album: String,
        artist: String,
        duration: Long,
        id: Long
    ): Resource<LyricResult> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                qqMusicUApiService.getLyric(
                    GetLyricData(
                        comm = GetLyricData.Comm(),
                        getPlayLyricInfo = GetLyricData.GetPlayLyricInfo(
                            param = GetLyricData.GetPlayLyricInfo.GetLyric(
                                singerName = b64encode(artist),
                                songName = b64encode(title),
                                albumName = b64encode(album),
                                interval = duration,
                                songID = id,
                                qrc = 0,
                                qrcT = 0
                            )
                        )
                    )
                )
            }
        }
    }

    suspend fun getLyric(id: String): Resource<Lyric> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                apiService.getLyric(
                    GetLyric(
                        id = id
                    )
                )
            }
        }
    }


    suspend fun getLyricV1(id: String): Resource<Lyric> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                apiService.getLyricV1(
                    GetLyricV1(
                        id = id
                    )
                )
            }
        }
    }


    suspend fun like(id: String, like: Boolean) {
        apiService.like(
            Like(
                trackId = id,
                like = like
            )
        )
    }

    private val amllClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getAMLLyric(id: String): Resource<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://amlldb.bikonoo.com/ncm-lyrics/$id.ttml"
                val request = Request.Builder().url(url).build()

                val result = amllClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val lyricContent = response.body?.string()
                        if (!lyricContent.isNullOrEmpty() && lyricContent != "歌词不存在") {
                            Resource.Success(lyricContent)
                        } else {
                            Resource.Error("歌词不存在")
                        }
                    } else {
                        if (response.code == 404) {
                            Resource.Error("歌词不存在")
                        } else {
                            Resource.Error("请求失败，错误码: ${response.code}")
                        }
                    }
                }
                result
            } catch (e: IOException) {
                Resource.Error("网络异常，请检查你的网络连接")
            }
        }
    }

    /**
     * 关键词搜索 AMLL TTML DB（多源匹配用）。
     */
    suspend fun searchAMLLLyrics(query: String): List<AmllLyricSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("query", query)
                    .put("type", "title")
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://amlldb.bikonoo.com/api/search-lyrics")
                    .post(body)
                    .build()
                amllClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    parseAmllSearchResults(response.body?.string().orEmpty())
                }
            } catch (e: Exception) {
                Timber.tag("PlayerRepository").w(e, "AMLL search failed")
                emptyList()
            }
        }
    }

    /**
     * 按文件名取 AMLL TTML 内容（多源匹配用）。
     */
    suspend fun getAMLLLyricRaw(file: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val safeFile = file.trim()
                    .takeIf { it.endsWith(".ttml") && '/' !in it }
                    ?: return@withContext null
                val request = Request.Builder()
                    .url("https://amlldb.bikonoo.com/raw-lyrics/$safeFile")
                    .build()
                amllClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.string()?.takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseAmllSearchResults(body: String): List<AmllLyricSearchResult> {
        val array = JSONArray(body)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val file = item.optString("file").takeIf { it.endsWith(".ttml") } ?: continue
                add(
                    AmllLyricSearchResult(
                        file = file,
                        title = item.optString("title"),
                        titles = item.optStringArray("titles"),
                        artist = item.optString("artist"),
                        artists = item.optStringArray("artists"),
                        albums = item.optStringArray("albums"),
                    )
                )
            }
        }
    }

    private fun JSONObject.optStringArray(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    suspend fun getRadio(): Resource<Radio>{
        return withContext(Dispatchers.IO){
            safeApiCall {
                weApiService.getRadio()
            }
        }
    }


    suspend fun getIntelligenceList(id: String, playlistId: String, startSongId:String): Resource<Intelligence>{
        return withContext(Dispatchers.IO){
            safeApiCall {
                apiService.getIntelligenceList(
                    GetIntelligence(
                        songId = id,
                        playlistId = playlistId,
                        startMusicId = startSongId
                    )
                )
            }
        }
    }

    suspend fun getSongDetail(id: String): Resource<Tracks>{
        return withContext(Dispatchers.IO){
            safeApiCall {
                apiService.getSongDetail(
                    GetSongDetails(id)
                )
            }
        }
    }

    suspend fun checkSongLike(id: Long): Resource<Boolean>{
        return withContext(Dispatchers.IO){
            safeApiCall {
                val result = apiService.checkSongLike(CheckSongLike("[${id}]"))
                Timber.tag("Player Repo").d(result.toString())
                result.ids.contains(id)
            }
        }
    }

}
