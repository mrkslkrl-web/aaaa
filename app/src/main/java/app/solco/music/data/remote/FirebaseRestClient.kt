package app.solco.music.data.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class AuthSession(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMs: Long
)

class FirebaseAuthException(message: String) : Exception(message)

/**
 * Direct REST client for Firebase Auth and Cloud Firestore on project solco-d8ffb.
 * 100% compatible with existing Solco data stored under users/{uid}/{collection}/{docId}.
 */
class FirebaseRestClient(
    context: Context,
    private val http: OkHttpClient = defaultHttpClient()
) {
    companion object {
        const val PROJECT_ID = "solco-d8ffb"
        private const val API_KEY = "AIzaSyAMzkEsHeKPoNuguP34lE0G7QwyiEukOw4"
        private const val AUTH_BASE = "https://identitytoolkit.googleapis.com/v1"
        private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token?key=$API_KEY"
        private const val FIRESTORE_BASE =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"
        private const val PREF_NAME = "solco_firebase_session"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedSession: AuthSession? = loadSession()

    fun currentSession(): AuthSession? = cachedSession

    private fun loadSession(): AuthSession? {
        val uid = prefs.getString("uid", null) ?: return null
        val email = prefs.getString("email", null) ?: ""
        val idToken = prefs.getString("idToken", null) ?: return null
        val refreshToken = prefs.getString("refreshToken", null) ?: return null
        val exp = prefs.getLong("expiresAtMs", 0L)
        return AuthSession(uid, email, idToken, refreshToken, exp)
    }

    private fun saveSession(session: AuthSession) {
        cachedSession = session
        prefs.edit()
            .putString("uid", session.uid)
            .putString("email", session.email)
            .putString("idToken", session.idToken)
            .putString("refreshToken", session.refreshToken)
            .putLong("expiresAtMs", session.expiresAtMs)
            .apply()
    }

    fun signOut() {
        cachedSession = null
        prefs.edit().clear().apply()
    }

    suspend fun signIn(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            put("returnSecureToken", true)
        }.toString()
        val req = Request.Builder()
            .url("$AUTH_BASE/accounts:signInWithPassword?key=$API_KEY")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val res = http.newCall(req).execute()
        val text = res.body?.string().orEmpty()
        if (!res.isSuccessful) throw FirebaseAuthException(mapAuthError(text))
        val obj = json.parseToJsonElement(text).jsonObject
        val expSec = obj["expiresIn"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
        AuthSession(
            uid = obj["localId"]!!.jsonPrimitive.content,
            email = obj["email"]?.jsonPrimitive?.contentOrNull ?: email.trim(),
            idToken = obj["idToken"]!!.jsonPrimitive.content,
            refreshToken = obj["refreshToken"]!!.jsonPrimitive.content,
            expiresAtMs = System.currentTimeMillis() + expSec * 1000L
        ).also(::saveSession)
    }

    suspend fun signUp(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            put("returnSecureToken", true)
        }.toString()
        val req = Request.Builder()
            .url("$AUTH_BASE/accounts:signUp?key=$API_KEY")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val res = http.newCall(req).execute()
        val text = res.body?.string().orEmpty()
        if (!res.isSuccessful) throw FirebaseAuthException(mapAuthError(text))
        val obj = json.parseToJsonElement(text).jsonObject
        val expSec = obj["expiresIn"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
        AuthSession(
            uid = obj["localId"]!!.jsonPrimitive.content,
            email = obj["email"]?.jsonPrimitive?.contentOrNull ?: email.trim(),
            idToken = obj["idToken"]!!.jsonPrimitive.content,
            refreshToken = obj["refreshToken"]!!.jsonPrimitive.content,
            expiresAtMs = System.currentTimeMillis() + expSec * 1000L
        ).also(::saveSession)
    }

    suspend fun sendPasswordReset(email: String) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("requestType", "PASSWORD_RESET")
            put("email", email.trim())
        }.toString()
        val req = Request.Builder()
            .url("$AUTH_BASE/accounts:sendOobCode?key=$API_KEY")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val res = http.newCall(req).execute()
        val text = res.body?.string().orEmpty()
        if (!res.isSuccessful) throw FirebaseAuthException(mapAuthError(text))
    }

    suspend fun ensureFreshToken(): AuthSession = withContext(Dispatchers.IO) {
        val cur = cachedSession ?: throw FirebaseAuthException("Non sei collegato")
        if (cur.expiresAtMs > System.currentTimeMillis() + 60_000L) return@withContext cur
        val body = buildJsonObject {
            put("grant_type", "refresh_token")
            put("refresh_token", cur.refreshToken)
        }.toString()
        val req = Request.Builder()
            .url(TOKEN_URL)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val res = http.newCall(req).execute()
        val text = res.body?.string().orEmpty()
        if (!res.isSuccessful) throw FirebaseAuthException("Sessione scaduta: accedi di nuovo")
        val obj = json.parseToJsonElement(text).jsonObject
        val expSec = obj["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
        val updated = cur.copy(
            idToken = obj["id_token"]!!.jsonPrimitive.content,
            refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: cur.refreshToken,
            expiresAtMs = System.currentTimeMillis() + expSec * 1000L
        )
        saveSession(updated)
        updated
    }

    /**
     * Fetches all documents in users/{uid}/{collection} updated after [sinceMs] using Firestore runQuery,
     * falling back to listDocuments if sinceMs == 0 so documents without index/timestamp are never missed.
     */
    suspend fun fetchCollectionSince(
        collection: String,
        sinceMs: Long
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val session = ensureFreshToken()
        if (sinceMs <= 0L) {
            fetchAllDocumentsPaginated(session, collection)
        } else {
            fetchStructuredQuerySince(session, collection, sinceMs)
        }
    }

    private fun fetchAllDocumentsPaginated(
        session: AuthSession,
        collection: String
    ): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        var pageToken: String? = null
        while (true) {
            val url = buildString {
                append("$FIRESTORE_BASE/users/${session.uid}/$collection?pageSize=300")
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=")
                    append(URLEncoder.encode(pageToken, "UTF-8"))
                }
            }
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${session.idToken}")
                .get()
                .build()
            val res = http.newCall(req).execute()
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                if (res.code == 404) break
                throw IllegalStateException("Firestore ($collection): HTTP ${res.code}")
            }
            val root = json.parseToJsonElement(text).jsonObject
            val docs = root["documents"]?.jsonArray ?: break
            for (docEl in docs) {
                parseFirestoreDocument(docEl.jsonObject)?.let { results.add(it) }
            }
            pageToken = root["nextPageToken"]?.jsonPrimitive?.contentOrNull
            if (pageToken.isNullOrBlank()) break
        }
        return results
    }

    private fun fetchStructuredQuerySince(
        session: AuthSession,
        collection: String,
        sinceMs: Long
    ): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        var cursorMs = sinceMs
        while (true) {
            val tsIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(cursorMs))
            val queryBody = buildJsonObject {
                put("structuredQuery", buildJsonObject {
                    put("from", buildJsonArray {
                        add(buildJsonObject { put("collectionId", collection) })
                    })
                    put("where", buildJsonObject {
                        put("fieldFilter", buildJsonObject {
                            put("field", buildJsonObject { put("fieldPath", "u") })
                            put("op", "GREATER_THAN")
                            put("value", buildJsonObject { put("timestampValue", tsIso) })
                        })
                    })
                    put("orderBy", buildJsonArray {
                        add(buildJsonObject {
                            put("field", buildJsonObject { put("fieldPath", "u") })
                            put("direction", "ASCENDING")
                        })
                    })
                    put("limit", 500)
                })
            }.toString()

            val req = Request.Builder()
                .url("$FIRESTORE_BASE/users/${session.uid}:runQuery")
                .header("Authorization", "Bearer ${session.idToken}")
                .post(queryBody.toRequestBody(JSON_MEDIA))
                .build()
            val res = http.newCall(req).execute()
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("Firestore query ($collection): HTTP ${res.code}")
            }
            val arr = json.parseToJsonElement(text).jsonArray
            var batchCount = 0
            var maxU = cursorMs
            for (el in arr) {
                val docObj = el.jsonObject["document"]?.jsonObject ?: continue
                val map = parseFirestoreDocument(docObj) ?: continue
                results.add(map)
                batchCount++
                val u = (map["u"] as? Long) ?: 0L
                if (u > maxU) maxU = u
            }
            if (batchCount < 500 || maxU <= cursorMs) break
            cursorMs = maxU
        }
        return results
    }

    /**
     * Writes a document to users/{uid}/{collection}/{docId} with server timestamp on `u`.
     */
    suspend fun setDocument(
        collection: String,
        docId: String,
        fields: Map<String, Any?>
    ) = withContext(Dispatchers.IO) {
        val session = ensureFreshToken()
        val encodedId = URLEncoder.encode(docId, "UTF-8").replace("+", "%20")
        val docPath = "projects/$PROJECT_ID/databases/(default)/documents/users/${session.uid}/$collection/$encodedId"

        val fsFields = buildJsonObject {
            for ((k, v) in fields) {
                if (k == "u") continue
                put(k, toFirestoreValue(v))
            }
        }

        val commitBody = buildJsonObject {
            put("writes", buildJsonArray {
                add(buildJsonObject {
                    put("update", buildJsonObject {
                        put("name", docPath)
                        put("fields", fsFields)
                    })
                    put("updateTransforms", buildJsonArray {
                        add(buildJsonObject {
                            put("fieldPath", "u")
                            put("setToServerValue", "REQUEST_TIME")
                        })
                    })
                })
            })
        }.toString()

        val req = Request.Builder()
            .url("https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents:commit")
            .header("Authorization", "Bearer ${session.idToken}")
            .post(commitBody.toRequestBody(JSON_MEDIA))
            .build()
        val res = http.newCall(req).execute()
        val text = res.body?.string().orEmpty()
        if (!res.isSuccessful) {
            throw IllegalStateException("Firestore write ($collection/$docId): HTTP ${res.code} $text")
        }
    }

    suspend fun batchSetDocuments(
        collection: String,
        docs: List<Pair<String, Map<String, Any?>>>
    ) = withContext(Dispatchers.IO) {
        if (docs.isEmpty()) return@withContext
        val session = ensureFreshToken()
        for (chunk in docs.chunked(200)) {
            val commitBody = buildJsonObject {
                put("writes", buildJsonArray {
                    for ((docId, fields) in chunk) {
                        val encodedId = URLEncoder.encode(docId, "UTF-8").replace("+", "%20")
                        val docPath =
                            "projects/$PROJECT_ID/databases/(default)/documents/users/${session.uid}/$collection/$encodedId"
                        add(buildJsonObject {
                            put("update", buildJsonObject {
                                put("name", docPath)
                                put("fields", buildJsonObject {
                                    for ((k, v) in fields) {
                                        if (k == "u") continue
                                        put(k, toFirestoreValue(v))
                                    }
                                })
                            })
                            put("updateTransforms", buildJsonArray {
                                add(buildJsonObject {
                                    put("fieldPath", "u")
                                    put("setToServerValue", "REQUEST_TIME")
                                })
                            })
                        })
                    }
                })
            }.toString()

            val req = Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents:commit")
                .header("Authorization", "Bearer ${session.idToken}")
                .post(commitBody.toRequestBody(JSON_MEDIA))
                .build()
            val res = http.newCall(req).execute()
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("Firestore batch ($collection): HTTP ${res.code} $text")
            }
        }
    }

    private fun parseFirestoreDocument(docObj: JsonObject): Map<String, Any?>? {
        val name = docObj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val docId = name.substringAfterLast("/")
        val fieldsObj = docObj["fields"]?.jsonObject ?: return null
        val out = mutableMapOf<String, Any?>("_docId" to docId)
        for ((k, v) in fieldsObj) {
            out[k] = fromFirestoreValue(v.jsonObject)
        }
        return out
    }

    private fun fromFirestoreValue(obj: JsonObject): Any? {
        if (obj.containsKey("nullValue")) return null
        obj["stringValue"]?.jsonPrimitive?.contentOrNull?.let { return it }
        obj["integerValue"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { return it }
        obj["doubleValue"]?.jsonPrimitive?.doubleOrNull?.let { return it }
        obj["booleanValue"]?.jsonPrimitive?.booleanOrNull?.let { return it }
        obj["timestampValue"]?.jsonPrimitive?.contentOrNull?.let { iso ->
            return runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
        }
        obj["arrayValue"]?.jsonObject?.let { arr ->
            val values = arr["values"]?.jsonArray ?: return emptyList<Any?>()
            return values.map { fromFirestoreValue(it.jsonObject) }
        }
        obj["mapValue"]?.jsonObject?.let { map ->
            val fields = map["fields"]?.jsonObject ?: return emptyMap<String, Any?>()
            return fields.mapValues { (_, v) -> fromFirestoreValue(v.jsonObject) }
        }
        return null
    }

    private fun toFirestoreValue(v: Any?): JsonObject = when (v) {
        null -> buildJsonObject { put("nullValue", JsonNull) }
        is String -> buildJsonObject { put("stringValue", v) }
        is Boolean -> buildJsonObject { put("booleanValue", v) }
        is Int -> buildJsonObject { put("integerValue", v.toString()) }
        is Long -> buildJsonObject { put("integerValue", v.toString()) }
        is Double -> buildJsonObject { put("doubleValue", v) }
        is Float -> buildJsonObject { put("doubleValue", v.toDouble()) }
        is List<*> -> buildJsonObject {
            put("arrayValue", buildJsonObject {
                put("values", buildJsonArray {
                    for (item in v) add(toFirestoreValue(item))
                })
            })
        }
        is Map<*, *> -> buildJsonObject {
            put("mapValue", buildJsonObject {
                put("fields", buildJsonObject {
                    for ((mk, mv) in v) {
                        if (mk != null) put(mk.toString(), toFirestoreValue(mv))
                    }
                })
            })
        }
        is JsonElement -> toFirestoreValueFromJson(v)
        else -> buildJsonObject { put("stringValue", v.toString()) }
    }

    private fun toFirestoreValueFromJson(el: JsonElement): JsonObject = when (el) {
        is JsonNull -> buildJsonObject { put("nullValue", JsonNull) }
        is JsonPrimitive -> when {
            el.isString -> buildJsonObject { put("stringValue", el.content) }
            el.booleanOrNull != null -> buildJsonObject { put("booleanValue", el.booleanOrNull!!) }
            el.longOrNull != null -> buildJsonObject { put("integerValue", el.longOrNull!!.toString()) }
            el.doubleOrNull != null -> buildJsonObject { put("doubleValue", el.doubleOrNull!!) }
            else -> buildJsonObject { put("stringValue", el.content) }
        }
        is JsonArray -> buildJsonObject {
            put("arrayValue", buildJsonObject {
                put("values", buildJsonArray { el.forEach { add(toFirestoreValueFromJson(it)) } })
            })
        }
        is JsonObject -> buildJsonObject {
            put("mapValue", buildJsonObject {
                put("fields", buildJsonObject {
                    el.forEach { (k, v) -> put(k, toFirestoreValueFromJson(v)) }
                })
            })
        }
    }

    private fun mapAuthError(raw: String): String {
        val code = runCatching {
            json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: raw
        return when {
            code.contains("INVALID_LOGIN_CREDENTIALS") || code.contains("INVALID_PASSWORD") ->
                "Email o password sbagliate"
            code.contains("EMAIL_NOT_FOUND") -> "Nessun account con questa email"
            code.contains("EMAIL_EXISTS") -> "Esiste già un account con questa email: usa \"Entra\""
            code.contains("WEAK_PASSWORD") -> "Password troppo corta (almeno 6 caratteri)"
            code.contains("INVALID_EMAIL") -> "Email non valida"
            code.contains("TOO_MANY_ATTEMPTS") -> "Troppi tentativi, riprova tra poco"
            else -> "Errore autenticazione: $code"
        }
    }
}
