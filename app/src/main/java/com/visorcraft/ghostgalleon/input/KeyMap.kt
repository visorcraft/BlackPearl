package com.visorcraft.ghostgalleon.input

import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.Settings

object KeyMap {
    fun resolve(keyCode: Int, settings: Settings): Action =
        settings.keyMap[keyCode] ?: Action.NONE
}
