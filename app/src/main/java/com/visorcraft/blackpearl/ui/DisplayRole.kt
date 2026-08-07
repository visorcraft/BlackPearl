package com.visorcraft.blackpearl.ui

import com.visorcraft.blackpearl.state.DeckState

enum class DisplayRole { PRIMARY, COMPANION;

    companion object {
        fun roleFor(displayId: Int, state: DeckState): DisplayRole =
            if (displayId == state.primaryDisplayId) PRIMARY else COMPANION
    }
}
