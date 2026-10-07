package app.prajakeeyam.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ApiException(val code: Int, message: String) : Exception(message)

/** Thin OkHttp client for the Prajakeeyam backend. All calls are suspend + IO-dispatched. */
class ApiClient(baseUrl: String, private val client: OkHttpClient, private val prefs: Prefs) {
    private val base = baseUrl.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------------ core
    private fun url(path: String, params: Map<String, String?> = emptyMap()): String {
        val b = (base + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private suspend fun request(method: String, url: String, body: JSONObject? = null, auth: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            if (auth) prefs.token?.let { builder.header("Authorization", "Bearer $it") }
            val requestBody = body?.toString()?.toRequestBody(jsonType)
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete(requestBody)
                else -> builder.method(method, requestBody ?: "{}".toRequestBody(jsonType))
            }
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}"
                    if (resp.code == 401 && auth) prefs.clearSession()
                    throw ApiException(resp.code, detail)
                }
                text
            }
        }

    private suspend fun getJson(path: String, params: Map<String, String?> = emptyMap()) = JSONObject(request("GET", url(path, params)))
    private suspend fun postJson(path: String, body: JSONObject? = null) = JSONObject(request("POST", url(path), body))
    private suspend fun patchJson(path: String, body: JSONObject) = JSONObject(request("PATCH", url(path), body))

    // ------------------------------------------------------------------ misc
    /** Fire-and-forget ping that wakes a sleeping Render instance. */
    suspend fun warmUp() {
        runCatching { request("GET", url("/health"), auth = false) }
    }

    suspend fun locationsVersion(): String = getJson("/locations/version").getString("version")

    suspend fun locationsBundle(): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url("/locations/bundle")).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, "bundle HTTP ${resp.code}")
            resp.body.bytes() // OkHttp transparently un-gzips
        }
    }

    // ------------------------------------------------------------------ auth / me
    suspend fun googleLogin(idToken: String): AuthResult = parseAuth(postJson("/auth/google", JSONObject().put("id_token", idToken)))

    suspend fun devLogin(sub: String, name: String): AuthResult =
        parseAuth(postJson("/auth/dev", JSONObject().put("sub", sub).put("name", name)))

    private fun parseAuth(o: JSONObject) = AuthResult(o.getString("access_token"), User.fromJson(o.getJSONObject("user")))

    suspend fun me(): User = User.fromJson(getJson("/me"))

    suspend fun updateMe(homeVillageId: Int?): User =
        User.fromJson(patchJson("/me", JSONObject().put("home_village_id", homeVillageId ?: JSONObject.NULL)))

    suspend fun deleteMe() {
        request("DELETE", url("/me"))
    }

    // ------------------------------------------------------------------ problems
    suspend fun problems(
        villageId: Int? = null, mandalId: Int? = null, constituencyId: Int? = null,
        sort: String = "new", status: String? = null, cursor: String? = null, limit: Int = 20,
    ): Page<Problem> {
        val o = getJson(
            "/problems",
            mapOf(
                "village_id" to villageId?.toString(), "mandal_id" to mandalId?.toString(),
                "constituency_id" to constituencyId?.toString(), "sort" to sort, "status" to status,
                "cursor" to cursor, "limit" to limit.toString(),
            ),
        )
        return Page(o.getJSONArray("items").mapObjects(Problem::fromJson), o.optStringOrNull("next_cursor"))
    }

    suspend fun problem(id: Int): Problem = Problem.fromJson(getJson("/problems/$id"))

    suspend fun createProblem(
        mandalId: Int, villageId: Int?, category: String, title: String, description: String, photoUrl: String?,
    ): Problem = Problem.fromJson(
        postJson(
            "/problems",
            JSONObject().put("mandal_id", mandalId).put("village_id", villageId ?: JSONObject.NULL)
                .put("category", category).put("title", title).put("description", description)
                .put("photo_url", photoUrl ?: JSONObject.NULL),
        ),
    )

    suspend fun updateProblem(id: Int, status: String? = null, hidden: Boolean? = null): Problem {
        val body = JSONObject()
        status?.let { body.put("status", it) }
        hidden?.let { body.put("is_hidden", it) }
        return Problem.fromJson(patchJson("/problems/$id", body))
    }

    suspend fun toggleUpvote(id: Int): Pair<Boolean, Int> {
        val o = postJson("/problems/$id/upvote")
        return o.getBoolean("upvoted") to o.getInt("upvote_count")
    }

    suspend fun comments(problemId: Int, cursor: String? = null): Page<Comment> {
        val o = getJson("/problems/$problemId/comments", mapOf("cursor" to cursor, "limit" to "50"))
        return Page(o.getJSONArray("items").mapObjects(Comment::fromJson), o.optStringOrNull("next_cursor"))
    }

    suspend fun addComment(problemId: Int, body: String): Comment =
        Comment.fromJson(postJson("/problems/$problemId/comments", JSONObject().put("body", body)))

    suspend fun reportProblem(id: Int, reason: String, note: String? = null) {
        postJson("/problems/$id/report", JSONObject().put("reason", reason).put("note", note ?: JSONObject.NULL))
    }

    suspend fun reportComment(id: Int, reason: String) {
        postJson("/comments/$id/report", JSONObject().put("reason", reason))
    }

    suspend fun blockUser(userId: Int) {
        postJson("/users/$userId/block")
    }
}

/** True for connectivity problems (as opposed to server-side rejections). */
fun Throwable.isNetworkError(): Boolean = this is IOException
