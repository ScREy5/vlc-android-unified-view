/*******************************************************************************
 *  VideoAudioMetadataRepository.kt
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

package org.videolan.vlc.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.VideoAudioMetadataDao
import org.videolan.vlc.mediadb.models.VideoAudioMetadata

/**
 * Repository for managing video audio metadata cache.
 * This allows videos to be displayed with their audio metadata (artist, album, etc.)
 * in the audio tab.
 */
class VideoAudioMetadataRepository(private val dao: VideoAudioMetadataDao) {

    /**
     * Get metadata for a specific media by its ID
     */
    suspend fun getByMediaId(mediaId: Long): VideoAudioMetadata? {
        return dao.getByMediaId(mediaId)
    }

    /**
     * Get metadata for a specific media by its MRL
     */
    suspend fun getByMrl(mrl: String): VideoAudioMetadata? {
        return dao.getByMrl(mrl)
    }

    /**
     * Get all cached metadata
     */
    suspend fun getAll(): List<VideoAudioMetadata> {
        return dao.getAll()
    }

    /**
     * Get all cached metadata as a Flow for reactive updates
     */
    fun getAllAsFlow(): Flow<List<VideoAudioMetadata>> {
        return dao.getAllAsFlow()
    }

    /**
     * Get metadata for multiple media IDs
     */
    suspend fun getByMediaIds(mediaIds: List<Long>): List<VideoAudioMetadata> {
        return dao.getByMediaIds(mediaIds)
    }

    /**
     * Get all media IDs that have cached metadata
     */
    suspend fun getAllMediaIds(): List<Long> {
        return dao.getAllMediaIds()
    }

    /**
     * Save or update metadata
     */
    suspend fun save(metadata: VideoAudioMetadata) {
        dao.insert(metadata)
    }

    /**
     * Save or update multiple metadata entries
     */
    suspend fun saveAll(metadata: List<VideoAudioMetadata>) {
        dao.insertAll(metadata)
    }

    /**
     * Delete metadata for a specific media
     */
    suspend fun deleteByMediaId(mediaId: Long) {
        dao.deleteByMediaId(mediaId)
    }

    /**
     * Delete metadata by MRL
     */
    suspend fun deleteByMrl(mrl: String) {
        dao.deleteByMrl(mrl)
    }

    /**
     * Delete all cached metadata
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }

    /**
     * Get count of cached entries
     */
    suspend fun count(): Int {
        return dao.count()
    }

    /**
     * Check if metadata exists and is up to date
     */
    suspend fun getIfUpToDate(mediaId: Long, lastModified: Long): VideoAudioMetadata? {
        return dao.getIfUpToDate(mediaId, lastModified)
    }

    /**
     * Get distinct artists from video metadata
     */
    suspend fun getDistinctArtists(): List<String> {
        return dao.getDistinctArtists()
    }

    /**
     * Get distinct albums from video metadata
     */
    suspend fun getDistinctAlbums(): List<String> {
        return dao.getDistinctAlbums()
    }

    /**
     * Get distinct genres from video metadata
     */
    suspend fun getDistinctGenres(): List<String> {
        return dao.getDistinctGenres()
    }

    /**
     * Get metadata by artist
     */
    suspend fun getByArtist(artist: String): List<VideoAudioMetadata> {
        return dao.getByArtist(artist)
    }

    /**
     * Get metadata by album
     */
    suspend fun getByAlbum(album: String): List<VideoAudioMetadata> {
        return dao.getByAlbum(album)
    }

    /**
     * Get metadata by genre
     */
    suspend fun getByGenre(genre: String): List<VideoAudioMetadata> {
        return dao.getByGenre(genre)
    }

    companion object : SingletonHolder<VideoAudioMetadataRepository, Context>({ 
        VideoAudioMetadataRepository(MediaDatabase.getInstance(it).videoAudioMetadataDao()) 
    })
}
