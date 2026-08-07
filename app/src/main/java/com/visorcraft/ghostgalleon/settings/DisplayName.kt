package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.library.AppEntry

// The ONE place per-app custom names apply: any AppEntry a deck, picker,
// hero, or dock renders comes from AppLibrary, whose accessors rename the
// entry's label with settings.customNames[packageName] when present.
fun AppEntry.displayName(settings: Settings): AppEntry =
    settings.customNames[packageName]?.let { copy(label = it) } ?: this
