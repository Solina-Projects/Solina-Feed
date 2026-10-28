package com.solina.feed

import android.content.Context
import android.text.Html
import android.util.Xml
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

data class Task(val text: String, val done: Boolean)

data class Weather(val city: String, val temp: Int, val description: String, val min: Int, val max: Int)

data class NewsItem(val title: String, val link: String, val source: String)

/** Settings and user data (city, feeds, tasks, note) stored in SharedPreferences. */
object FeedStore {

    const val DEFAULT_FEEDS =
        "https://feeds.bbci.co.uk/persian/rss.xml\n" +
            "https://news.google.com/rss?hl=fa&gl=IR&ceid=IR:fa"

    private fun sp(c: Context) = c.getSharedPreferences("solina_feed", Context.MODE_PRIVATE)

    fun city(c: Context): String = sp(c).getString("city", "تهران") ?: "تهران"

    fun setCity(c: Context, value: String) {
        sp(c).edit().putString("city", value.trim()).apply()
    }

    fun feedsText(c: Context): String = sp(c).getString("feeds", DEFAULT_FEEDS) ?: DEFAULT_FEEDS

    fun setFeedsText(c: Context, value: String) {
        sp(c).edit().putString("feeds", value).apply()
    }

    fun feeds(c: Context): List<String> =
        feedsText(c).lines().map { it.trim() }.filter { it.startsWith("https://") }

    fun note(c: Context): String = sp(c).getString("note", "") ?: ""

    fun setNote(c: Context, value: String) {
        sp(c).edit().putString("note", value).apply()
    }

    fun tasks(c: Context): MutableList<Task> {
        val out = mutableListOf<Task>()
        try {
            val arr = JSONArray(sp(c).getString("tasks", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Task(o.getString("t"), o.optBoolean("d", false)))
            }
        } catch (e: Exception) {
            // Corrupt data: start with an empty list.
        }
        return out
    }

    fun setTasks(c: Context, tasks: List<Task>) {
        val arr = JSONArray()
        for (t in tasks) {
            arr.put(JSONObject().put("t", t.text).put("d", t.done))
        }
        sp(c).edit().putString("tasks", arr.toString()).apply()
    }

    internal fun cachedGeo(c: Context, city: String): String? = sp(c).getString("geo_$city", null)

    internal fun saveGeo(c: Context, city: String, value: String) {
        sp(c).edit().putString("geo_$city", value).apply()
    }
}

/** Network access: weather (Open-Meteo, no API key) and RSS/Atom news. */
object FeedNet {

    private const val MAX_BYTES = 2 * 1024 * 1024

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "SolinaFeed/0.1")
            conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, application/json, */*")
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw IllegalStateException("Response too large")
                }
            }
            return out.toString("UTF-8")
        } finally {
            conn.disconnect()
        }
    }

    fun weather(c: Context, city: String): Weather {
        var geo = FeedStore.cachedGeo(c, city)
        if (geo == null) {
            val json = JSONObject(
                get(
                    "https://geocoding-api.open-meteo.com/v1/search?name=" +
                        URLEncoder.encode(city, "UTF-8") + "&count=1&language=fa&format=json",
                ),
            )
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) throw IllegalStateException("City not found")
            val r = results.getJSONObject(0)
            geo = "${r.getDouble("latitude")}|${r.getDouble("longitude")}|${r.optString("name", city)}"
            FeedStore.saveGeo(c, city, geo)
        }
        val parts = geo.split("|")
        val json = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=${parts[0]}&longitude=${parts[1]}" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto",
            ),
        )
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        return Weather(
            city = parts[2],
            temp = Math.round(current.getDouble("temperature_2m")).toInt(),
            description = describe(current.getInt("weather_code")),
            min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
        )
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "آسمان صاف"
        1 -> "عمدتاً صاف"
        2 -> "نیمه‌ابری"
        3 -> "ابری"
        45, 48 -> "مه"
        51, 53, 55, 56, 57 -> "نم‌نم باران"
        61, 63, 65, 66, 67 -> "باران"
        71, 73, 75, 77 -> "برف"
        80, 81, 82 -> "رگبار"
        85, 86 -> "بارش برف"
        95, 96, 99 -> "رعد و برق"
        else -> "نامشخص"
    }

    /** Loads the given feeds and interleaves their items so every source shows up near the top. */
    fun news(feeds: List<String>, perFeed: Int = 8): List<NewsItem> {
        val lists = feeds.map { url ->
            try {
                parseFeed(get(url)).take(perFeed)
            } catch (e: Exception) {
                emptyList()
            }
        }
        val out = mutableListOf<NewsItem>()
        var i = 0
        while (lists.any { i < it.size }) {
            for (l in lists) if (i < l.size) out.add(l[i])
            i++
        }
        return out
    }

    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT).toString().trim()

    private fun parseFeed(xml: String): List<NewsItem> {
        val out = mutableListOf<NewsItem>()
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        var channelTitle = ""
        var inItem = false
        var title = ""
        var link = ""
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = p.name.lowercase()
                if (name == "item" || name == "entry") {
                    inItem = true
                    title = ""
                    link = ""
                } else if (name == "title") {
                    val t = clean(p.nextText())
                    if (inItem) title = t else if (channelTitle.isEmpty()) channelTitle = t
                } else if (name == "link" && inItem) {
                    val href = p.getAttributeValue(null, "href")
                    val text = p.nextText().trim()
                    if (link.isEmpty()) link = if (!href.isNullOrEmpty()) href else text
                }
            } else if (event == XmlPullParser.END_TAG) {
                val name = p.name.lowercase()
                if ((name == "item" || name == "entry") && inItem) {
                    inItem = false
                    if (title.isNotEmpty() && link.startsWith("http")) {
                        out.add(NewsItem(title, link, channelTitle))
                    }
                }
            }
            event = p.next()
        }
        return out
    }
}
