package com.honglu.typing.input

/**
 * Input mode state machine.
 */
enum class InputMode {
    ENGLISH,
    PINYIN,            // Typing in pinyin letters
    PINYIN_SELECTING   // Showing character candidate list
}
