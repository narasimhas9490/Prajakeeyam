package app.prajakeeyam.data

import org.json.JSONArray
import org.json.JSONObject

// ------------------------------------------------------------------ locations
data class District(val id: Int, val name: String, val nameTe: String?)
data class Constituency(val id: Int, val districtId: Int, val name: String, val nameTe: String?, val reservation: String)
data class Mandal(val id: Int, val constituencyId: Int, val name: String, val nameTe: String?, val kind: String) {
    val isUrban: Boolean get() = kind == "urban"
}
data class Village(val id: Int, val mandalId: Int, val name: String, val nameTe: String?)

private val PHONETIC = listOf("sree" to "sri", "shree" to "sri", "aa" to "a", "ee" to "i", "oo" to "u", "th" to "t", "dh" to "d", "bh" to "b", "sh" to "s", "kh" to "k", "gh" to "g", "ph" to "p", "ch" to "c", "w" to "v", "y" to "i")
private val DROP_WORDS = Regex("\\b(village|mandal|town|rural|urban|revenue|gram|gramam|municipality|district|dist|h/o)\\b")
private val SUFFIXES = listOf(Regex("(palli|pally|pallee|palle)$") to "pale", Regex("(pett|pet|pettah|peta)$") to "peta", Regex("(pur|puram)$") to "puram", Regex("(kotta|kottah|kota)$") to "kota")

/** Loose key for comparing place names from different sources (geocoder vs LGD vs Wikipedia). */
fun placeKey(raw: String): String {
    var s = raw.lowercase(java.util.Locale.ROOT).replace(Regex("\\(.*?\\)"), " ")
    s = DROP_WORDS.replace(s, " ")
    s = s.replace(Regex("[^a-z0-9]+"), "")
    for ((a, b) in PHONETIC) s = s.replace(a, b)
    for ((re, rep) in SUFFIXES) s = re.replace(s, rep)
    return s.replace(Regex("(.)\\1"), "$1")
}

/** Pick the Telugu name when the UI language is Telugu and we have one. */
fun localName(name: String, nameTe: String?, lang: String?): String =
    if (lang == "te" && !nameTe.isNullOrBlank()) nameTe else name

// ------------------------------------------------------------------ API objects
data class User(
    val id: Int,
    val name: String,
    val email: String?,
    val pictureUrl: String?,
    val homeVillageId: Int?,
    val role: String,
) {
    val isAdmin: Boolean get() = role == "admin"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("email", email ?: JSONObject.NULL)
        .put("picture_url", pictureUrl ?: JSONObject.NULL)
        .put("home_village_id", homeVillageId ?: JSONObject.NULL).put("role", role)

    companion object {
        fun fromJson(o: JSONObject) = User(
            id = o.getInt("id"), name = o.getString("name"), email = o.optStringOrNull("email"),
            pictureUrl = o.optStringOrNull("picture_url"), homeVillageId = o.optIntOrNull("home_village_id"),
            role = o.optString("role", "user"),
        )
    }
}

data class Author(val id: Int, val name: String, val pictureUrl: String?) {
    companion object {
        fun fromJson(o: JSONObject) = Author(o.getInt("id"), o.getString("name"), o.optStringOrNull("picture_url"))
    }
}

data class Problem(
    val id: Int,
    val userId: Int,
    val author: Author,
    val villageId: Int?,
    val mandalId: Int,
    val constituencyId: Int,
    val category: String,
    val title: String,
    val description: String,
    val photoUrl: String?,
    val status: String,
    val upvoteCount: Int,
    val commentCount: Int,
    val createdAt: String,
    val myUpvote: Boolean,
    val isOwner: Boolean,
    val isHidden: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject) = Problem(
            id = o.getInt("id"), userId = o.getInt("user_id"), author = Author.fromJson(o.getJSONObject("author")),
            villageId = o.optIntOrNull("village_id"), mandalId = o.getInt("mandal_id"), constituencyId = o.getInt("constituency_id"),
            category = o.getString("category"), title = o.getString("title"), description = o.getString("description"),
            photoUrl = o.optStringOrNull("photo_url"), status = o.getString("status"),
            upvoteCount = o.optInt("upvote_count"), commentCount = o.optInt("comment_count"),
            createdAt = o.getString("created_at"), myUpvote = o.optBoolean("my_upvote"), isOwner = o.optBoolean("is_owner"),
            isHidden = o.optBoolean("is_hidden"),
        )
    }
}

data class Comment(val id: Int, val problemId: Int, val author: Author, val body: String, val createdAt: String, val isOwner: Boolean) {
    companion object {
        fun fromJson(o: JSONObject) = Comment(
            id = o.getInt("id"), problemId = o.getInt("problem_id"), author = Author.fromJson(o.getJSONObject("author")),
            body = o.getString("body"), createdAt = o.getString("created_at"), isOwner = o.optBoolean("is_owner"),
        )
    }
}

data class Page<T>(val items: List<T>, val nextCursor: String?)

data class AuthResult(val token: String, val user: User)

// ------------------------------------------------------------------ org.json helpers
fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = List(length()) { f(getJSONObject(it)) }
