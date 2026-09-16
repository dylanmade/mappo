package com.mappo.data.model

enum class DeviceButton {
    BUTTON_A,
    BUTTON_B,
    BUTTON_X,
    BUTTON_Y,
    BUTTON_L1,
    BUTTON_R1,
    AXIS_L2,
    AXIS_R2,
    BUTTON_THUMBL,
    BUTTON_THUMBR,
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    BUTTON_START,
    BUTTON_SELECT;

    /** Defined in [OutputNames.gamepad]. */
    val displayName: String get() = OutputNames.gamepadName(name)
}
