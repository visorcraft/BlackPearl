package com.visorcraft.blackpearl.ui.deck

import com.visorcraft.blackpearl.settings.Action

class CarouselNavigation(private val itemCount: Int) {

    fun move(index: Int, action: Action): Int {
        val target = when (action) {
            Action.NAV_LEFT, Action.PAGE_PREV -> index - 1
            Action.NAV_RIGHT, Action.PAGE_NEXT -> index + 1
            else -> index
        }
        return target.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    }
}
