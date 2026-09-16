package com.mappo.data.model

/**
 * **The one place every output's user-facing name is defined** — the gamepad buttons, keyboard
 * keys, and mouse buttons Mappo can emit, and the short prefix naming each device. Edit a name
 * here and it changes everywhere: the command picker's lists, the remap views' assignment text
 * ([com.mappo.data.model.steam.BindingOutput.displayLabel]), and the overlay buttons
 * ([RemapTarget.displayLabel]).
 *
 * Each map is `code to name`. The CODE (left) is what's stored in the database and dispatched —
 * never edit it here; the NAME (right) is display only. Entry order is the order the command
 * picker lists them in. A code missing from its map displays as the raw code.
 *
 * Unified 2026-09-16 at Dylan's request. Before that the gamepad names lived on the
 * `DeviceButton` enum, the stick-direction names in `RemapInputOptions`, and keyboard + mouse
 * had no names at all — every surface printed the raw code, and the assignment text printed
 * even gamepad buttons raw ("GP: BUTTON_A") while the picker said "A".
 *
 * Physical INPUT names (what the controller's own buttons are called on the remap screen) are a
 * separate vocabulary and not here — see `InputSource.displayName()` in SteamEnums.kt.
 */
object OutputNames {

    /** Device prefixes, as in "KB: ENTER". */
    const val GAMEPAD_PREFIX = "GP"
    const val KEYBOARD_PREFIX = "KB"
    const val MOUSE_PREFIX = "MS"

    /** Virtual gamepad outputs: buttons (codes = [DeviceButton] names), then stick directions
     *  (analog, Shizuku-only). */
    val gamepad: Map<String, String> = linkedMapOf(
        "BUTTON_A" to "A",
        "BUTTON_B" to "B",
        "BUTTON_X" to "X",
        "BUTTON_Y" to "Y",
        "BUTTON_L1" to "LB",
        "BUTTON_R1" to "RB",
        "AXIS_L2" to "LT",
        "AXIS_R2" to "RT",
        "BUTTON_THUMBL" to "L3",
        "BUTTON_THUMBR" to "R3",
        "DPAD_UP" to "D-Pad Up",
        "DPAD_DOWN" to "D-Pad Down",
        "DPAD_LEFT" to "D-Pad Left",
        "DPAD_RIGHT" to "D-Pad Right",
        "BUTTON_START" to "Start / Menu",
        "BUTTON_SELECT" to "Select / View",

        "LSTICK_UP" to "Left Stick Up",
        "LSTICK_DOWN" to "Left Stick Down",
        "LSTICK_LEFT" to "Left Stick Left",
        "LSTICK_RIGHT" to "Left Stick Right",
        "RSTICK_UP" to "Right Stick Up",
        "RSTICK_DOWN" to "Right Stick Down",
        "RSTICK_LEFT" to "Right Stick Left",
        "RSTICK_RIGHT" to "Right Stick Right",
    )

    /** Keyboard keys, grouped by physical keyboard row. */
    val keyboard: Map<String, String> = linkedMapOf(
        "ESCAPE" to "ESCAPE",
        "F1" to "F1",
        "F2" to "F2",
        "F3" to "F3",
        "F4" to "F4",
        "F5" to "F5",
        "F6" to "F6",
        "F7" to "F7",
        "F8" to "F8",
        "F9" to "F9",
        "F10" to "F10",
        "F11" to "F11",
        "F12" to "F12",

        "GRAVE" to "GRAVE",
        "1" to "1",
        "2" to "2",
        "3" to "3",
        "4" to "4",
        "5" to "5",
        "6" to "6",
        "7" to "7",
        "8" to "8",
        "9" to "9",
        "0" to "0",
        "MINUS" to "MINUS",
        "EQUALS" to "EQUALS",
        "BACKSPACE" to "BACKSPACE",

        "TAB" to "TAB",
        "Q" to "Q",
        "W" to "W",
        "E" to "E",
        "R" to "R",
        "T" to "T",
        "Y" to "Y",
        "U" to "U",
        "I" to "I",
        "O" to "O",
        "P" to "P",
        "LEFT_BRACKET" to "LEFT_BRACKET",
        "RIGHT_BRACKET" to "RIGHT_BRACKET",
        "BACKSLASH" to "BACKSLASH",

        "CAPS_LOCK" to "CAPS_LOCK",
        "A" to "A",
        "S" to "S",
        "D" to "D",
        "F" to "F",
        "G" to "G",
        "H" to "H",
        "J" to "J",
        "K" to "K",
        "L" to "L",
        "SEMICOLON" to "SEMICOLON",
        "APOSTROPHE" to "APOSTROPHE",
        "ENTER" to "ENTER",

        "SHIFT_LEFT" to "SHIFT_LEFT",
        "Z" to "Z",
        "X" to "X",
        "C" to "C",
        "V" to "V",
        "B" to "B",
        "N" to "N",
        "M" to "M",
        "COMMA" to "COMMA",
        "PERIOD" to "PERIOD",
        "SLASH" to "SLASH",
        "SHIFT_RIGHT" to "SHIFT_RIGHT",

        "CTRL_LEFT" to "CTRL_LEFT",
        "META_LEFT" to "META_LEFT",
        "ALT_LEFT" to "ALT_LEFT",
        "SPACE" to "SPACE",
        "ALT_RIGHT" to "ALT_RIGHT",
        "MENU" to "MENU",
        "CTRL_RIGHT" to "CTRL_RIGHT",

        "SYSRQ" to "SYSRQ",
        "SCROLL_LOCK" to "SCROLL_LOCK",
        "BREAK" to "BREAK",
        "INSERT" to "INSERT",
        "MOVE_HOME" to "MOVE_HOME",
        "PAGE_UP" to "PAGE_UP",

        "FORWARD_DEL" to "FORWARD_DEL",
        "MOVE_END" to "MOVE_END",
        "PAGE_DOWN" to "PAGE_DOWN",
        "DPAD_UP" to "DPAD_UP",
        "DPAD_DOWN" to "DPAD_DOWN",
        "DPAD_LEFT" to "DPAD_LEFT",
        "DPAD_RIGHT" to "DPAD_RIGHT",
    )

    /** Mouse buttons and wheel directions. */
    val mouse: Map<String, String> = linkedMapOf(
        "MOUSE_LEFT" to "MOUSE_LEFT",
        "MOUSE_MIDDLE" to "MOUSE_MIDDLE",
        "MOUSE_RIGHT" to "MOUSE_RIGHT",
        "SCROLL_UP" to "SCROLL_UP",
        "SCROLL_DOWN" to "SCROLL_DOWN",
        "MOUSE_BACK" to "MOUSE_BACK",
        "MOUSE_FORWARD" to "MOUSE_FORWARD",
    )

    fun gamepadName(code: String): String = gamepad[code] ?: code
    fun keyboardName(code: String): String = keyboard[code] ?: code
    fun mouseName(code: String): String = mouse[code] ?: code
}
