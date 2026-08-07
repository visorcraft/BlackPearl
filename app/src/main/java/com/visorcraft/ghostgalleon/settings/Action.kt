package com.visorcraft.ghostgalleon.settings

enum class Action {
    NAV_UP, NAV_DOWN, NAV_LEFT, NAV_RIGHT,
    CONFIRM, BACK, SWAP_SCREENS, TOGGLE_MODE,
    OPEN_SETTINGS, PAGE_PREV, PAGE_NEXT, NONE
}

// User-facing labels for the settings/remap UI: raw enum names must never
// surface on screen.
fun Action.label(): String = when (this) {
    Action.NAV_UP -> "Up"
    Action.NAV_DOWN -> "Down"
    Action.NAV_LEFT -> "Left"
    Action.NAV_RIGHT -> "Right"
    Action.CONFIRM -> "Confirm / Launch"
    Action.BACK -> "Back"
    Action.SWAP_SCREENS -> "Swap screens"
    Action.TOGGLE_MODE -> "Toggle mode"
    Action.OPEN_SETTINGS -> "Open settings"
    Action.PAGE_PREV -> "Page left (L1)"
    Action.PAGE_NEXT -> "Page right (R1)"
    Action.NONE -> "None"
}
