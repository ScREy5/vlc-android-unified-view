/*******************************************************************************
 *  MediaWithAudioMetadata.kt
 * ****************************************************************************
 * Copyright © 2024 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 ******************************************************************************/

package org.videolan.vlc.util

import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.mediadb.models.VideoAudioMetadata

/**
 * Extension properties and functions to work with video audio metadata.
 * This provides a way to access cached audio metadata for videos when
 * sorting and filtering in the audio tab.
 */

/**
 * Get the effective artist name for a media.
 * For audio, returns the MediaWrapper's artist.
 * For video with cached metadata, returns the cached artist.
 */
fun MediaWrapper.getEffectiveArtist(cachedMetadata: VideoAudioMetadata?): String {
    return when {
        type == MediaWrapper.TYPE_AUDIO -> artist?.title.orEmpty()
        cachedMetadata != null && cachedMetadata.artist.isNotEmpty() -> cachedMetadata.artist
        else -> ""
    }
}

/**
 * Get the effective album name for a media.
 * For audio, returns the MediaWrapper's album.
 * For video with cached metadata, returns the cached album.
 */
fun MediaWrapper.getEffectiveAlbum(cachedMetadata: VideoAudioMetadata?): String {
    return when {
        type == MediaWrapper.TYPE_AUDIO -> album?.title.orEmpty()
        cachedMetadata != null && cachedMetadata.album.isNotEmpty() -> cachedMetadata.album
        else -> ""
    }
}

/**
 * Get the effective genre for a media.
 * For audio, returns the MediaWrapper's genre.
 * For video with cached metadata, returns the cached genre.
 */
fun MediaWrapper.getEffectiveGenre(cachedMetadata: VideoAudioMetadata?): String {
    return when {
        type == MediaWrapper.TYPE_AUDIO -> genre.orEmpty()
        cachedMetadata != null && cachedMetadata.genre.isNotEmpty() -> cachedMetadata.genre
        else -> ""
    }
}

/**
 * Get the effective track number for a media.
 * For audio, returns the MediaWrapper's track number.
 * For video with cached metadata, returns the cached track number.
 */
fun MediaWrapper.getEffectiveTrackNumber(cachedMetadata: VideoAudioMetadata?): Int {
    return when {
        type == MediaWrapper.TYPE_AUDIO -> trackNumber
        cachedMetadata != null -> cachedMetadata.trackNumber
        else -> 0
    }
}

/**
 * Get the effective disc number for a media.
 * For audio, returns the MediaWrapper's disc number.
 * For video with cached metadata, returns the cached disc number.
 */
fun MediaWrapper.getEffectiveDiscNumber(cachedMetadata: VideoAudioMetadata?): Int {
    return when {
        type == MediaWrapper.TYPE_AUDIO -> discNumber
        cachedMetadata != null -> cachedMetadata.discNumber
        else -> 0
    }
}

/**
 * Get the effective album artist for a media.
 * For audio, returns the MediaWrapper's album artist.
 * For video with cached metadata, returns the cached album artist.
 */
fun MediaWrapper.getEffectiveAlbumArtist(cachedMetadata: VideoAudioMetadata?): String {
    return when {
        type == MediaWrapper.TYPE_AUDIO -> albumArtist.orEmpty()
        cachedMetadata != null && cachedMetadata.albumArtist.isNotEmpty() -> cachedMetadata.albumArtist
        else -> ""
    }
}

/**
 * Check if a video has any audio metadata (either from medialibrary or cached)
 */
fun MediaWrapper.hasAudioMetadata(cachedMetadata: VideoAudioMetadata?): Boolean {
    if (type == MediaWrapper.TYPE_AUDIO) return true
    if (cachedMetadata == null) return false
    return cachedMetadata.artist.isNotEmpty() || 
           cachedMetadata.album.isNotEmpty() || 
           cachedMetadata.genre.isNotEmpty()
}

/**
 * Build a description string for display (artist - album format)
 */
fun MediaWrapper.buildAudioDescription(cachedMetadata: VideoAudioMetadata?): String {
    val artist = getEffectiveArtist(cachedMetadata)
    val album = getEffectiveAlbum(cachedMetadata)
    
    return when {
        artist.isNotEmpty() && album.isNotEmpty() -> "$artist · $album"
        artist.isNotEmpty() -> artist
        album.isNotEmpty() -> album
        else -> ""
    }
}
