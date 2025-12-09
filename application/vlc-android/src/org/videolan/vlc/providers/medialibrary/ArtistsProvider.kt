/*****************************************************************************
 * ArtistsProvider.kt
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
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.Settings
import org.videolan.vlc.media.VideoArtist
import org.videolan.vlc.mediadb.models.VideoAudioMetadata
import org.videolan.vlc.repository.VideoAudioMetadataRepository
import org.videolan.vlc.viewmodels.SortableModel

class ArtistsProvider(context: Context, model: SortableModel, var showAll: Boolean) : MedialibraryProvider<Artist>(context, model) {
    override val isAudioPermDependant = true

    // Repository for video audio metadata cache
    private val metadataRepository by lazy { VideoAudioMetadataRepository.getInstance(context) }
    
    // Cache of combined artists (native + video)
    private var cachedCombinedArtists: List<Artist>? = null
    private var cachedFilter: String? = null
    private var cachedSort: Int = sort
    private var cachedDesc: Boolean = desc

    /**
     * Clear all caches to force reload on next query
     */
    fun clearCaches() {
        cachedCombinedArtists = null
    }

    override fun getAll(): Array<Artist> = getCombinedArtists().toTypedArray()

    override fun getPage(loadSize: Int, startposition: Int): Array<Artist> {
        val list = getCombinedArtists()
            .drop(startposition)
            .take(loadSize)
            .toTypedArray()
        model.viewModelScope.launch { completeHeaders(list, startposition) }
        return list
    }

    override fun getTotalCount(): Int = getCombinedArtists().size

    /**
     * Get combined list of native medialibrary artists and video-based artists
     */
    private fun getCombinedArtists(): List<Artist> {
        // Check cache validity
        if (cachedCombinedArtists != null && 
            cachedFilter == model.filterQuery && 
            cachedSort == sort && 
            cachedDesc == desc) {
            return cachedCombinedArtists!!
        }

        // Get native artists from medialibrary
        val nativeArtists = if (model.filterQuery == null) {
            medialibrary.getArtists(showAll, sort, desc, Settings.includeMissing, onlyFavorites)
        } else {
            medialibrary.searchArtist(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, Int.MAX_VALUE, 0)
        }.toList()

        // Get video artists from metadata
        val videoArtists = getVideoArtists()

        // Merge: add video artists that don't exist in native artists
        val nativeArtistNames = nativeArtists.map { it.title.lowercase() }.toSet()
        val uniqueVideoArtists = videoArtists.filter { videoArtist ->
            videoArtist.title.lowercase() !in nativeArtistNames
        }

        // Combine and sort
        val combined = (nativeArtists + uniqueVideoArtists)
            .sortedWith(getArtistComparator(sort, desc))

        // Update cache
        cachedCombinedArtists = combined
        cachedFilter = model.filterQuery
        cachedSort = sort
        cachedDesc = desc

        android.util.Log.d("ArtistsProvider", "Combined ${nativeArtists.size} native + ${uniqueVideoArtists.size} video artists = ${combined.size} total")
        
        return combined
    }

    /**
     * Get artists derived from video audio metadata
     */
    private fun getVideoArtists(): List<VideoArtist> {
        return runBlocking(Dispatchers.IO) {
            // Get all video metadata with artist info
            val allMetadata = metadataRepository.getAll()
            
            // Group by artist name (case-insensitive)
            val artistGroups = allMetadata
                .filter { it.artist.isNotBlank() }
                .groupBy { it.artist.lowercase() }
            
            // Get all videos for matching
            val allVideos = medialibrary.getVideos(Medialibrary.SORT_ALPHA, false, Settings.includeMissing, onlyFavorites)
            val videosById = allVideos.associateBy { it.id }
            
            // Create VideoArtist for each group
            val videoArtists = artistGroups.mapNotNull { (_, metadataList) ->
                val artistName = metadataList.first().artist
                
                // Filter by search query if present
                if (model.filterQuery != null && !artistName.lowercase().contains(model.filterQuery!!.lowercase())) {
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
                
                val artist = VideoArtist(artistName, matchingVideos.size, artworkMrl)
                artist.setVideos(matchingVideos)
                artist
            }
            
            android.util.Log.d("ArtistsProvider", "Created ${videoArtists.size} video artists from ${allMetadata.size} metadata entries")
            videoArtists
        }
    }

    /**
     * Get comparator for sorting artists
     */
    private fun getArtistComparator(sort: Int, desc: Boolean): Comparator<Artist> {
        val comparator: Comparator<Artist> = when (sort) {
            Medialibrary.SORT_ALPHA -> compareBy { it.title.lowercase() }
            else -> compareBy { it.title.lowercase() }
        }
        return if (desc) comparator.reversed() else comparator
    }
}