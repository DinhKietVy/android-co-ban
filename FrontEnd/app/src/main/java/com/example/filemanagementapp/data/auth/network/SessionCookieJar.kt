package com.example.filemanagementapp.data.auth.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

object SessionCookieJar : CookieJar {
    private const val PREFS_NAME = "auth_cookies"
    private const val KEY_COOKIES = "cookies_json"

    private lateinit var sharedPreferences: SharedPreferences
    private val cookies = LinkedHashMap<String, Cookie>()

    fun initialize(context: Context) {
        if (::sharedPreferences.isInitialized) {
            return
        }

        sharedPreferences = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        restoreCookies()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        ensureInitialized()

        var hasChanges = false
        cookies.forEach { cookie ->
            if (cookie.expiresAt < System.currentTimeMillis()) {
                hasChanges = removeCookie(cookie) || hasChanges
            } else {
                this.cookies[cookie.storageKey()] = cookie
                hasChanges = true
            }
        }

        if (hasChanges) {
            persistCookies()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        ensureInitialized()

        val now = System.currentTimeMillis()
        var hasExpiredCookies = false
        val validCookies = buildList {
            cookies.values.forEach { cookie ->
                if (cookie.expiresAt < now) {
                    hasExpiredCookies = true
                    return@forEach
                }

                if (cookie.matches(url)) {
                    add(cookie)
                }
            }
        }

        if (hasExpiredCookies) {
            removeExpiredCookies(now)
            persistCookies()
        }

        return validCookies
    }

    fun clear() {
        ensureInitialized()
        cookies.clear()
        sharedPreferences.edit().remove(KEY_COOKIES).apply()
    }

    private fun ensureInitialized() {
        check(::sharedPreferences.isInitialized) {
            "SessionCookieJar must be initialized from Application.onCreate()"
        }
    }

    private fun restoreCookies() {
        val rawCookies = sharedPreferences.getString(KEY_COOKIES, null).orEmpty()
        if (rawCookies.isBlank()) {
            return
        }

        runCatching {
            val jsonArray = JSONArray(rawCookies)
            for (index in 0 until jsonArray.length()) {
                val cookieJson = jsonArray.getJSONObject(index)
                decodeCookie(cookieJson)?.let { cookie ->
                    if (cookie.expiresAt >= System.currentTimeMillis()) {
                        cookies[cookie.storageKey()] = cookie
                    }
                }
            }
        }.onFailure {
            cookies.clear()
            sharedPreferences.edit().remove(KEY_COOKIES).apply()
        }
    }

    private fun persistCookies() {
        val jsonArray = JSONArray()
        cookies.values.forEach { cookie ->
            jsonArray.put(
                JSONObject().apply {
                    put("name", cookie.name)
                    put("value", cookie.value)
                    put("expiresAt", cookie.expiresAt)
                    put("domain", cookie.domain)
                    put("path", cookie.path)
                    put("secure", cookie.secure)
                    put("httpOnly", cookie.httpOnly)
                    put("hostOnly", cookie.hostOnly)
                    put("persistent", cookie.persistent)
                }
            )
        }

        sharedPreferences.edit()
            .putString(KEY_COOKIES, jsonArray.toString())
            .apply()
    }

    private fun removeExpiredCookies(now: Long) {
        val iterator = cookies.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.expiresAt < now) {
                iterator.remove()
            }
        }
    }

    private fun removeCookie(cookie: Cookie): Boolean {
        return cookies.remove(cookie.storageKey()) != null
    }

    private fun decodeCookie(cookieJson: JSONObject): Cookie? {
        return runCatching {
            val builder = Cookie.Builder()
                .name(cookieJson.getString("name"))
                .value(cookieJson.getString("value"))
                .path(cookieJson.getString("path"))

            val domain = cookieJson.getString("domain")
            if (cookieJson.optBoolean("hostOnly", false)) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }

            if (cookieJson.optBoolean("persistent", false)) {
                builder.expiresAt(cookieJson.getLong("expiresAt"))
            }
            if (cookieJson.optBoolean("secure", false)) {
                builder.secure()
            }
            if (cookieJson.optBoolean("httpOnly", false)) {
                builder.httpOnly()
            }

            builder.build()
        }.getOrNull()
    }

    private fun Cookie.storageKey(): String = "$domain|$path|$name"
}
