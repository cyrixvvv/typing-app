package com.honglu.typing.data

import android.content.Context
import org.json.JSONObject

/**
 * Content repository: manages built-in typing content.
 * Loads texts from JSON assets (contents_en.json, contents_cn.json).
 */
object ContentRepository {

    /**
     * Get a random English text from the built-in collection.
     */
    fun getRandomEnglishText(context: Context): String {
        return getRandomText(context, "contents_en.json")
    }

    /**
     * Get a random Chinese text from the built-in collection.
     */
    fun getRandomChineseText(context: Context): String {
        return getRandomText(context, "contents_cn.json")
    }

    /**
     * Get text by ID and language.
     */
    fun getTextById(context: Context, lang: String, id: Int): String {
        return getByIdFromFile(context, lang, id)
    }

    /**
     * List all available content titles.
     */
    fun listAvailableContent(context: Context): List<ContentItem> {
        return listOf(
            ContentItem("en_1", "English", "The Quick Brown Fox"),
            ContentItem("en_2", "English", "To be or not to be"),
            ContentItem("cn_1", "Chinese", "春晓 - 孟浩然"),
            ContentItem("cn_2", "Chinese", "静夜思 - 李白")
        )
    }

    private fun getRandomText(context: Context, assetPath: String): String {
        try {
            val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val items = root.getJSONArray("items")

            // Pick a random item
            val idx = (0 until items.length()).random()
            val item = items.getJSONObject(idx)
            return item.getString("text")
        } catch (e: Exception) {
            // Fallback: return a default text
            return "The quick brown fox jumps over the lazy dog."
        }
    }

    private fun getByIdFromFile(context: Context, lang: String, id: Int): String {
        try {
            val json = context.assets.open("contents_${if (lang == "cn") "cn" else "en"}.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val items = root.getJSONArray("items")

            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                if (obj.getInt("id") == id) {
                    return obj.getString("text")
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        return "Loading..."
    }
}

data class ContentItem(
    val id: String,
    val lang: String,
    val title: String
)
