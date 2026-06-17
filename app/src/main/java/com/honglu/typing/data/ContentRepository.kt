package com.honglu.typing.data

import android.content.Context
import org.json.JSONObject

/**
 * Content repository: manages built-in typing content.
 * Loads texts from JSON assets (contents_en.json, contents_cn.json).
 */
object ContentRepository {

    private fun loadItems(context: Context, assetPath: String): List<ContentItem> {
        return try {
            val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr = root.getJSONArray("items")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ContentItem(
                    id = obj.getInt("id"),
                    title = obj.getString("title"),
                    text = obj.getString("text")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get a random English text from the built-in collection.
     */
    fun getRandomEnglishText(context: Context): String {
        val items = loadItems(context, "contents_en.json")
        if (items.isEmpty()) return "The quick brown fox jumps over the lazy dog."
        return items.random().text
    }

    /**
     * Get a random Chinese text from the built-in collection.
     */
    fun getRandomChineseText(context: Context): String {
        val items = loadItems(context, "contents_cn.json")
        if (items.isEmpty()) return "床前明月光。"
        return items.random().text
    }

    /**
     * Get text by composite id like "en_1", "cn_5".
     */
    fun getTextById(context: Context, contentId: String): String {
        val parts = contentId.split("_")
        if (parts.size != 2) return "Loading..."
        val assetFile = when (parts[0]) {
            "en" -> "contents_en.json"
            "cn" -> "contents_cn.json"
            else -> return "Loading..."
        }
        val id = parts[1].toIntOrNull() ?: return "Loading..."
        val items = loadItems(context, assetFile)
        return items.find { it.id == id }?.text ?: "Loading..."
    }

    /**
     * List all available content titles grouped by language.
     */
    fun listAvailableContent(context: Context): List<ContentItem> {
        val enItems = loadItems(context, "contents_en.json").map {
            it.copy(lang = "English")
        }
        val cnItems = loadItems(context, "contents_cn.json").map {
            it.copy(lang = "Chinese")
        }
        return enItems + cnItems
    }
}

/**
 * Content item data class.
 * id is the numeric id from the JSON asset.
 * For the UI, composite id string is derived as "{lang_prefix}_{id}".
 */
data class ContentItem(
    val id: Int,
    val title: String,
    val text: String = "",
    val lang: String = ""
) {
    /** Composite id used in Intents, e.g. "en_1", "cn_5" */
    val compositeId: String get() = "${if (lang == "Chinese") "cn" else "en"}_$id"
}