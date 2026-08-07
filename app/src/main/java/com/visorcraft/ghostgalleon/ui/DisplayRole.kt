package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.state.DeckState

enum class DisplayRole { PRIMARY, COMPANION;

    companion object {
        fun roleFor(displayId: Int, state: DeckState): DisplayRole =
            if (displayId == state.primaryDisplayId) PRIMARY else COMPANION
    }
}
