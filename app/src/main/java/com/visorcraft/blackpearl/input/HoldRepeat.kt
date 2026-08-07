package com.visorcraft.blackpearl.input

// Hold-to-repeat timing for NAV actions, shared by the key and stick paths.
// Android does not auto-repeat gamepad buttons, so BaseDeckActivity owns
// repeats through NavRepeater using these values.
object HoldRepeat {
    const val INITIAL_DELAY_MS = 1000L
    const val REPEAT_INTERVAL_MS = 350L
}
