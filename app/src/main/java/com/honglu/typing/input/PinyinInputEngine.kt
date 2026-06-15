package com.honglu.typing.input

import com.honglu.typing.R
import org.json.JSONObject

/**
 * Lightweight pinyin input engine.
 * Uses a built-in JSON dictionary of common Chinese characters/words indexed by pinyin.
 * Supports full-pinyin (全拼) input: user types pinyin letters, engine returns candidate characters.
 */
class PinyinInputEngine {

    // Dictionary: pinyin -> list of (character, frequency)
    private val dictionary = mutableMapOf<String, List<Pair<String, Int>>>()

    /**
     * Load dictionary from a JSON asset file.
     * Expected format: { "ni": [["你", 1000], ["尼", 300]], ... }
     */
    fun loadDictionary(context: android.content.Context, assetPath: String) {
        val json = assetToString(context, assetPath)
        val root = JSONObject(json)

        root.keys().forEach { pinyin ->
            val arr = root.getJSONArray(pinyin)
            val pairs = mutableListOf<Pair<String, Int>>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONArray(i)
                val char = item.getString(0)
                val freq = item.getInt(1)
                pairs.add(char to freq)
            }
            dictionary[pinyin] = pairs.sortedByDescending { it.second }
        }
    }

    /**
     * Get character suggestions for a pinyin string.
     * Returns sorted list of unique characters.
     */
    fun getSuggestions(pinyin: String): List<String> {
        val key = pinyin.lowercase()
        return dictionary[key]?.map { it.first }?.distinct() ?: emptyList()
    }

    /**
     * Check if the pinyin has any suggestions.
     */
    fun hasSuggestions(pinyin: String): Boolean {
        return getSuggestions(pinyin).isNotEmpty()
    }

    /**
     * Reset pinyin accumulator.
     */
    fun reset() {
        // Dictionary stays loaded
    }

    /**
     * Map a keyboard character to its pinyin equivalent.
     * For QWERTY keyboard: a->a, b->b, ... z->z
     * Only lowercase letters are pinyin keys.
     */
    fun isPinyinKey(char: Char): Boolean {
        return char in 'a'..'z'
    }

    /**
     * Convert a Unicode Chinese character to its pinyin (lookup in reverse dictionary).
     * This is a fallback; normally pinyin is typed directly from keyboard.
     */
    fun charToPinyin(char: Char): String {
        // Reverse lookup (expensive, only for debugging)
        for ((pinyin, entries) in dictionary) {
            if (entries.any { it.first.contains(char) }) {
                return pinyin
            }
        }
        return ""
    }

    private fun assetToString(context: android.content.Context, path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
