package com.honglu.typing.util

/**
 * Device utility functions.
 */
object DeviceUtils {

    /**
     * Check if the event comes from a physical keyboard (OTG).
     */
    fun isPhysicalKeyboard(event: android.view.KeyEvent): Boolean {
        val device = event.device ?: return false
        val source = device.sources
        return (source and android.view.InputDevice.SOURCE_KEYBOARD) != 0 &&
               !device.isVirtual
    }

    /**
     * Check if the event comes from a TV remote (D-pad).
     */
    fun isRemoteControl(event: android.view.KeyEvent): Boolean {
        val device = event.device ?: return false
        // D-pad events from leanback often have no device or are marked as gamepad
        return (device.sources and android.view.InputDevice.SOURCE_DPAD) != 0
    }

    /**
     * Map Android key code to a character.
     * Handles both uppercase and lowercase letters and common punctuation.
     */
    fun keyCodeToChar(keyCode: Int, shift: Boolean): Char? {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_A -> if (shift) 'A' else 'a'
            android.view.KeyEvent.KEYCODE_B -> if (shift) 'B' else 'b'
            android.view.KeyEvent.KEYCODE_C -> if (shift) 'C' else 'c'
            android.view.KeyEvent.KEYCODE_D -> if (shift) 'D' else 'd'
            android.view.KeyEvent.KEYCODE_E -> if (shift) 'E' else 'e'
            android.view.KeyEvent.KEYCODE_F -> if (shift) 'F' else 'f'
            android.view.KeyEvent.KEYCODE_G -> if (shift) 'G' else 'g'
            android.view.KeyEvent.KEYCODE_H -> if (shift) 'H' else 'h'
            android.view.KeyEvent.KEYCODE_I -> if (shift) 'I' else 'i'
            android.view.KeyEvent.KEYCODE_J -> if (shift) 'J' else 'j'
            android.view.KeyEvent.KEYCODE_K -> if (shift) 'K' else 'k'
            android.view.KeyEvent.KEYCODE_L -> if (shift) 'L' else 'l'
            android.view.KeyEvent.KEYCODE_M -> if (shift) 'M' else 'm'
            android.view.KeyEvent.KEYCODE_N -> if (shift) 'N' else 'n'
            android.view.KeyEvent.KEYCODE_O -> if (shift) 'O' else 'o'
            android.view.KeyEvent.KEYCODE_P -> if (shift) 'P' else 'p'
            android.view.KeyEvent.KEYCODE_Q -> if (shift) 'Q' else 'q'
            android.view.KeyEvent.KEYCODE_R -> if (shift) 'R' else 'r'
            android.view.KeyEvent.KEYCODE_S -> if (shift) 'S' else 's'
            android.view.KeyEvent.KEYCODE_T -> if (shift) 'T' else 't'
            android.view.KeyEvent.KEYCODE_U -> if (shift) 'U' else 'u'
            android.view.KeyEvent.KEYCODE_V -> if (shift) 'V' else 'v'
            android.view.KeyEvent.KEYCODE_W -> if (shift) 'W' else 'w'
            android.view.KeyEvent.KEYCODE_X -> if (shift) 'X' else 'x'
            android.view.KeyEvent.KEYCODE_Y -> if (shift) 'Y' else 'y'
            android.view.KeyEvent.KEYCODE_Z -> if (shift) 'Z' else 'z'
            android.view.KeyEvent.KEYCODE_0 -> if (shift) ')' else '0'
            android.view.KeyEvent.KEYCODE_1 -> if (shift) '!' else '1'
            android.view.KeyEvent.KEYCODE_2 -> if (shift) '@' else '2'
            android.view.KeyEvent.KEYCODE_3 -> if (shift) '#' else '3'
            android.view.KeyEvent.KEYCODE_4 -> if (shift) '$' else '4'
            android.view.KeyEvent.KEYCODE_5 -> if (shift) '%' else '5'
            android.view.KeyEvent.KEYCODE_6 -> if (shift) '^' else '6'
            android.view.KeyEvent.KEYCODE_7 -> if (shift) '&' else '7'
            android.view.KeyEvent.KEYCODE_8 -> if (shift) '*' else '8'
            android.view.KeyEvent.KEYCODE_9 -> if (shift) '(' else '9'
            // Punctuation
            android.view.KeyEvent.KEYCODE_COMMA -> if (shift) '<' else ','
            android.view.KeyEvent.KEYCODE_PERIOD -> if (shift) '>' else '.'
            android.view.KeyEvent.KEYCODE_APOSTROPHE -> if (shift) '"' else '\''
            android.view.KeyEvent.KEYCODE_SEMICOLON -> if (shift) ':' else ';'
            android.view.KeyEvent.KEYCODE_SLASH -> if (shift) '?' else '/'
            android.view.KeyEvent.KEYCODE_MINUS -> if (shift) '_' else '-'
            android.view.KeyEvent.KEYCODE_EQUALS -> if (shift) '+' else '='
            android.view.KeyEvent.KEYCODE_GRAVE -> if (shift) '~' else '`'
            android.view.KeyEvent.KEYCODE_LEFT_BRACKET -> if (shift) '{' else '['
            android.view.KeyEvent.KEYCODE_RIGHT_BRACKET -> if (shift) '}' else ']'
            android.view.KeyEvent.KEYCODE_BACKSLASH -> if (shift) '|' else '\\'
            else -> null
        }
    }
}
