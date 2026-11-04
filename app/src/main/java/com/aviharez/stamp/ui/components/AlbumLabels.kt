package com.aviharez.stamp.ui.components

import androidx.annotation.StringRes
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor

@get:StringRes
val AlbumCategory.label: Int
    get() = when (this) {
        AlbumCategory.Trips -> R.string.album_category_trips
        AlbumCategory.Everyday -> R.string.album_category_everyday
        AlbumCategory.Pets -> R.string.album_category_pets
        AlbumCategory.Other -> R.string.album_category_other
    }

@get:StringRes
val AlbumCoverColor.label: Int
    get() = when (this) {
        AlbumCoverColor.Ink -> R.string.album_cover_ink
        AlbumCoverColor.Kraft -> R.string.album_cover_kraft
        AlbumCoverColor.Sage -> R.string.album_cover_sage
        AlbumCoverColor.Red -> R.string.album_cover_red
        AlbumCoverColor.Cobalt -> R.string.album_cover_cobalt
    }
