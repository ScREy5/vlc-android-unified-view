/*******************************************************************************
 *  VideoAudioMetadataDao.kt
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

package org.videolan.vlc.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.videolan.vlc.mediadb.models.VideoAudioMetadata

@Dao
interface VideoAudioMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: VideoAudioMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metadata: List<VideoAudioMetadata>)

    @Update
    suspend fun update(metadata: VideoAudioMetadata)

    @Query("SELECT * FROM video_audio_metadata_table WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): VideoAudioMetadata?

    @Query("SELECT * FROM video_audio_metadata_table WHERE mrl = :mrl")
    suspend fun getByMrl(mrl: String): VideoAudioMetadata?

    @Query("SELECT * FROM video_audio_metadata_table")
    suspend fun getAll(): List<VideoAudioMetadata>

    @Query("SELECT * FROM video_audio_metadata_table")
    fun getAllAsFlow(): Flow<List<VideoAudioMetadata>>

    @Query("SELECT * FROM video_audio_metadata_table WHERE mediaId IN (:mediaIds)")
    suspend fun getByMediaIds(mediaIds: List<Long>): List<VideoAudioMetadata>

    @Query("DELETE FROM video_audio_metadata_table WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM video_audio_metadata_table WHERE mrl = :mrl")
    suspend fun deleteByMrl(mrl: String)

    @Query("DELETE FROM video_audio_metadata_table")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM video_audio_metadata_table")
    suspend fun count(): Int

    @Query("SELECT mediaId FROM video_audio_metadata_table")
    suspend fun getAllMediaIds(): List<Long>

    // Get distinct artists from video metadata
    @Query("SELECT DISTINCT artist FROM video_audio_metadata_table WHERE artist != '' ORDER BY artist")
    suspend fun getDistinctArtists(): List<String>

    // Get distinct albums from video metadata
    @Query("SELECT DISTINCT album FROM video_audio_metadata_table WHERE album != '' ORDER BY album")
    suspend fun getDistinctAlbums(): List<String>

    // Get distinct genres from video metadata
    @Query("SELECT DISTINCT genre FROM video_audio_metadata_table WHERE genre != '' ORDER BY genre")
    suspend fun getDistinctGenres(): List<String>

    // Get metadata by artist
    @Query("SELECT * FROM video_audio_metadata_table WHERE artist = :artist")
    suspend fun getByArtist(artist: String): List<VideoAudioMetadata>

    // Get metadata by album
    @Query("SELECT * FROM video_audio_metadata_table WHERE album = :album")
    suspend fun getByAlbum(album: String): List<VideoAudioMetadata>

    // Get metadata by genre
    @Query("SELECT * FROM video_audio_metadata_table WHERE genre = :genre")
    suspend fun getByGenre(genre: String): List<VideoAudioMetadata>

    // Check if metadata exists and is up to date (based on last modified)
    @Query("SELECT * FROM video_audio_metadata_table WHERE mediaId = :mediaId AND lastModified >= :lastModified")
    suspend fun getIfUpToDate(mediaId: Long, lastModified: Long): VideoAudioMetadata?
}
