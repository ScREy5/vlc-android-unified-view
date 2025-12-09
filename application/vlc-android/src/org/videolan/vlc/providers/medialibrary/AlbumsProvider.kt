/*****************************************************************************
 * AlbumsProvider.kt
 *****************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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
 *****************************************************************************/

package org.videolan.vlc.providers.medialibrary

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.Settings
import org.videolan.vlc.media.VideoAlbum
import org.videolan.vlc.media.VideoArtist
import org.videolan.vlc.mediadb.models.VideoAudioMetadata
import org.videolan.vlc.repository.VideoAudioMetadataRepository
import org.videolan.vlc.viewmodels.SortableModel

class AlbumsProvider(val parent : MediaLibraryItem?, context: Context, model: SortableModel) : MedialibraryProvider<Album>(context, model) {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByReleaseDate() = true
    override fun canSortByArtist() = true
    override fun canSortByInsertionDate()= true
    override val isAudioPermDependant = true

    // Repository for video audio metadata cache
    private val metadataRepository by lazy { VideoAudioMetadataRepository.getInstance(context) }
    
    // Cache of combined albums (native + video)
    private var cachedCombinedAlbums: List<Album>? = null
    private var cachedFilter: String? = null
    private var cachedSort: Int = sort
    private var cachedDesc: Boolean = desc

    init {
        sort = Settings.getInstance(context).getInt(sortKey, if (parent is Artist) Medialibrary.SORT_RELEASEDATE else Medialibrary.SORT_DEFAULT)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
        onlyFavorites = Settings.getInstance(context).getBoolean("${sortKey}_only_favs", false)
    }

    /**
     * Clear all caches to force reload on next query
     */
    fun clearCaches() {
        cachedCombinedAlbums = null
    }

    override fun getAll(): Array<Album> = getCombinedAlbums().toTypedArray()

    override fun getPage(loadSize: Int, startposition: Int): Array<Album> {
        val list = getCombinedAlbums()
            .drop(startposition)
            .take(loadSize)
            .toTypedArray()
        model.viewModelScope.launch { completeHeaders(list, startposition) }
        return list
    }

    override fun getTotalCount(): Int = getCombinedAlbums().size

    /**
     * Get combined list of native medialibrary albums and video-based albums
     */
    private fun getCombinedAlbums(): List<Album> {
        // Check cache validity
        if (cachedCombinedAlbums != null && 
            cachedFilter == model.filterQuery && 
            cachedSort == sort && 
            cachedDesc == desc) {
            return cachedCombinedAlbums!!
        }

        // Get native albums from medialibrary
        val nativeAlbums = if (model.filterQuery == null) {
            when(parent) {
                is Artist -> parent.getPagedAlbums(sort, desc, Settings.includeMissing, onlyFavorites, Int.MAX_VALUE, 0)
                is Genre -> parent.getPagedAlbums(sort, desc, Settings.includeMissing, onlyFavorites, Int.MAX_VALUE, 0)
                else -> medialibrary.getPagedAlbums(sort, desc, Settings.includeMissing, onlyFavorites, Int.MAX_VALUE, 0)
            }
        } else {
            when(parent) {
                is Artist -> parent.searchAlbums(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, Int.MAX_VALUE, 0)
                is Genre -> parent.searchAlbums(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, Int.MAX_VALUE, 0)
                else -> medialibrary.searchAlbum(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, Int.MAX_VALUE, 0)
            }
        }.toList()

        // Get video albums from metadata (only for non-scoped views or VideoArtist parents)
        val videoAlbums = if (parent == null || parent is VideoArtist) {
            getVideoAlbums()
        } else {
            emptyList()
        }

        // Merge: add video albums that don't exist in native albums
        val nativeAlbumNames = nativeAlbums.map { it.title.lowercase() }.toSet()
        val uniqueVideoAlbums = videoAlbums.filter { videoAlbum ->
            videoAlbum.title.lowercase() !in nativeAlbumNames
        }

        // Combine and sort
        val combined = (nativeAlbums + uniqueVideoAlbums)
            .sortedWith(getAlbumComparator(sort, desc))

        // Update cache
        cachedCombinedAlbums = combined
        cachedFilter = model.filterQuery
        cachedSort = sort
        cachedDesc = desc

        android.util.Log.d("AlbumsProvider", "Combined ${nativeAlbums.size} native + ${uniqueVideoAlbums.size} video albums = ${combined.size} total")
        
        return combined
    }

    /**
     * Get albums derived from video audio metadata
     */
    private fun getVideoAlbums(): List<VideoAlbum> {
        return runBlocking(Dispatchers.IO) {
            // Get all video metadata with album info
            val allMetadata = metadataRepository.getAll()
            
            // Group by album name (case-insensitive)
            val albumGroups = allMetadata
                .filter { it.album.isNotBlank() }
                .groupBy { it.album.lowercase() }
            
            // Get all videos for matching
            val allVideos = medialibrary.getVideos(Medialibrary.SORT_ALPHA, false, Settings.includeMissing, onlyFavorites)
            val videosById = allVideos.associateBy { it.id }
            
            // Create VideoAlbum for each group
            val videoAlbums = albumGroups.mapNotNull { (_, metadataList) ->
                val albumName = metadataList.first().album
                val artistName = metadataList.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist
                    ?: metadataList.firstOrNull { it.artist.isNotBlank() }?.artist
                    ?: ""
                
                // Filter by search query if present
                if (model.filterQuery != null && !albumName.lowercase().contains(model.filterQuery!!.lowercase())) {
                    return@mapNotNull null
                }
                
                // Get matching videos
                val matchingVideos = metadataList.mapNotNull { metadata ->
                    videosById[metadata.mediaId]
                }
                
                if (matchingVideos.isEmpty()) {
                    return@mapNotNull null
                }
                
                // Get artwork from first video that has it
                val artworkMrl = matchingVideos.firstOrNull { it.artworkMrl != null }?.artworkMrl
                
                // Get release year (use the most common one)
                val releaseYear = metadataList.mapNotNull { if (it.releaseYear > 0) it.releaseYear else null }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }?.key ?: 0
                
                // Calculate total duration
                val totalDuration = matchingVideos.sumOf { it.length }
                
                val album = VideoAlbum(albumName, artistName, matchingVideos.size, releaseYear, artworkMrl, totalDuration)
                album.setVideos(matchingVideos)
                album
            }
            
            android.util.Log.d("AlbumsProvider", "Created ${videoAlbums.size} video albums from ${allMetadata.size} metadata entries")
            videoAlbums
        }
    }

    /**
     * Get comparator for sorting albums
     */
    private fun getAlbumComparator(sort: Int, desc: Boolean): Comparator<Album> {
        val comparator: Comparator<Album> = when (sort) {
            Medialibrary.SORT_ALPHA -> compareBy { it.title.lowercase() }
            Medialibrary.SORT_RELEASEDATE -> compareBy { it.releaseYear }
            Medialibrary.SORT_DURATION -> compareBy { it.duration }
            Medialibrary.SORT_ARTIST -> compareBy { it.description?.lowercase() ?: "" }
            else -> compareBy { it.title.lowercase() }
        }
        return if (desc) comparator.reversed() else comparator
    }
}