package com.aviharez.stamp.domain.model

/**
 * Colour treatment of a stamp's picture (the paper border stays white). Applied when the stamp
 * PNG is rendered, from the original photo, so it can always be changed back.
 *
 * @param free part of the free tier; the others are Pro (CLAUDE.md §4 "all edges/filters").
 */
enum class StampFilter(val free: Boolean) {
    Original(free = true),
    Postcard(free = true),
    Engraved(free = true),
    Sepia(free = false),
    Vivid(free = false),
    Faded(free = false),
    RedInk(free = false),
    BlueInk(free = false),
    GreenInk(free = false),
    ;

    companion object {
        fun fromName(name: String?): StampFilter = entries.firstOrNull { it.name == name } ?: Original
    }
}
