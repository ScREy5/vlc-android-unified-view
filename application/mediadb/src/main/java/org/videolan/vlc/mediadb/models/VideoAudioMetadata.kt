/*******************************************************************************
 *  VideoAudioMetadata.kt
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

package org.videolan.vlc.mediadb.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to store audio metadata extracted from video files.
 * This allows videos to be displayed and filtered in the audio tab
 * using their embedded audio metadata (artist, album, genre, etc.)
 */
@Entity(tableName = "video_audio_metadata_table")
data class VideoAudioMetadata(
    @PrimaryKey
    val mediaId: Long,
    val mrl: String,
    @ColumnInfo(defaultValue = "")
    val artist: String = "",
    @ColumnInfo(defaultValue = "")
    val album: String = "",
    @ColumnInfo(defaultValue = "")
    val albumArtist: String = "",
    @ColumnInfo(defaultValue = "")
    val genre: String = "",
    @ColumnInfo(defaultValue = "0")
    val trackNumber: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val discNumber: Int = 0,
    @ColumnInfo(defaultValue = "")
    val artworkUrl: String = "",
    @ColumnInfo(defaultValue = "0")
    val releaseYear: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val lastModified: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val parsedTimestamp: Long = System.currentTimeMillis()
)
