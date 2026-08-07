package com.visorcraft.blackpearl.input

import com.visorcraft.blackpearl.settings.Action
import com.visorcraft.blackpearl.settings.Settings

object KeyMap {
    fun resolve(keyCode: Int, settings: Settings): Action =
        settings.keyMap[keyCode] ?: Action.NONE
}
