/*****************************************************************************
 * TracksProvider.kt
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
import org.videolan.medialibrary.interfaces.media.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.VideoAlbum
import org.videolan.medialibrary.media.VideoArtist
import org.videolan.tools.Settings
import org.videolan.vlc.gui.helpers.MediaComparators
import org.videolan.vlc.mediadb.models.VideoAudioMetadata
import org.videolan.vlc.repository.VideoAudioMetadataRepository
import org.videolan.vlc.util.VideoMetadataExtractor
import org.videolan.vlc.util.buildAudioDescription
import org.videolan.vlc.util.getEffectiveAlbum
import org.videolan.vlc.util.getEffectiveArtist
import org.videolan.vlc.viewmodels.SortableModel
import java.util.Comparator

class TracksProvider(val parent : MediaLibraryItem?, context: Context, model: SortableModel) : MedialibraryProvider<MediaWrapper>(context, model) {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = parent !is Playlist
    override fun canSortByAlbum() = parent !== null && parent !is Album && parent !is Playlist
    override fun canSortByLastModified() = parent !is Playlist
    override fun canSortByReleaseDate() = parent !is Playlist
    override fun canSortByInsertionDate() = true
    override fun canSortByName() = parent !is Playlist
    override fun canSortByFileNameName() = parent !is Playlist
    override fun canSortByTrackId() = parent is Album
    override val isAudioPermDependant = true

    // Repository for video audio metadata cache
    private val metadataRepository by lazy { VideoAudioMetadataRepository.getInstance(context) }
    
    // Cache of video audio metadata indexed by media ID
    private var videoMetadataCache: Map<Long, VideoAudioMetadata> = emptyMap()

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_DEFAULT)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", parent is Artist)
        onlyFavorites = Settings.getInstance(context).getBoolean("${sortKey}_only_favs", false)
        if (sort == Medialibrary.SORT_DEFAULT) sort = when (parent) {
            is Artist -> Medialibrary.SORT_ALBUM
            is Album -> Medialibrary.TrackId
            else -> Medialibrary.SORT_ALPHA
        }
    }

    /**
     * Clear all caches to force reload on next query
     */
    fun clearCaches() {
        cachedCombined = null
        cachedScopedVideos = null
        cachedScopedParentId = null
        videoMetadataCache = emptyMap()
    }

    private var cachedCombined: List<MediaWrapper>? = null
    private var cachedFilter: String? = null
    private var cachedSort: Int = sort
    private var cachedDesc: Boolean = desc
    private var cachedOnlyFavorites: Boolean = onlyFavorites
    
    // Cache of videos matching the current parent (artist/album)
    private var cachedScopedVideos: List<MediaWrapper>? = null
    private var cachedScopedParentId: Long? = null

    override fun getAll(): Array<MediaWrapper> {
        return if (parent != null) getAllScoped().toTypedArray() else getCombinedMedia().toTypedArray()
    }

    /**
     * Get all tracks for a scoped (parent) query, including matching videos for Artist/Album
     */
    private fun getAllScoped(): List<MediaWrapper> {
        // For VideoArtist and VideoAlbum (or artists/albums with negative IDs after unparceling),
        // we need to load videos by matching metadata
        val isVideoOnlyArtist = parent is VideoArtist || (parent is Artist && parent.id < 0)
        val isVideoOnlyAlbum = parent is VideoAlbum || (parent is Album && parent.id < 0)
        
        if (isVideoOnlyArtist || isVideoOnlyAlbum) {
            // Get stored videos if this is a VideoArtist/VideoAlbum instance
            val storedVideos = when (parent) {
                is VideoArtist -> parent.getVideos()
                is VideoAlbum -> parent.getVideos()
                else -> emptyList()
            }
            
            // If we have stored videos, use them; otherwise load by name matching
            val videos = if (storedVideos.isNotEmpty()) {
                storedVideos
            } else {
                // Parent was parceled and lost its videos, reload by name matching
                getVideosMatchingParent()
            }
            
            // Apply metadata descriptions to videos
            if (videos.isNotEmpty()) {
                runBlocking(Dispatchers.IO) {
                    val metadata = metadataRepository.getByMediaIds(videos.map { it.id })
                    videoMetadataCache = metadata.associateBy { it.mediaId }
                    applyMetadataToVideoDescriptions(videos)
                }
            }
            return videos.sortedWith(if (desc) getComparator(sort).reversed() else getComparator(sort))
        }
        
        val nativeTracks = when(parent) {
            is Artist -> parent.getTracks(sort, desc, Settings.includeMissing, onlyFavorites)
            is Album -> parent.getTracks(sort, desc, Settings.includeMissing, onlyFavorites)
            is Genre -> parent.getTracks(sort, desc, Settings.includeMissing, onlyFavorites)
            is Playlist -> parent.getTracks(Settings.includeMissing, onlyFavorites)
            else -> medialibrary.getAudio(sort, desc, Settings.includeMissing, onlyFavorites)
        }.toMutableList()
        
        // For native Artist and Album views, include videos with matching metadata
        if (parent is Artist || parent is Album) {
            val matchingVideos = getVideosMatchingParent()
            if (matchingVideos.isNotEmpty()) {
                return (nativeTracks + matchingVideos)
                    .sortedWith(if (desc) getComparator(sort).reversed() else getComparator(sort))
            }
        }
        
        return nativeTracks.toList()
    }

    override fun getPage(loadSize: Int, startposition: Int) : Array<MediaWrapper> {
        val list = if (parent != null) getScopedPage(loadSize, startposition).toTypedArray() else getCombinedMedia(startposition + loadSize)
            .drop(startposition)
            .take(loadSize)
            .toTypedArray()
        model.viewModelScope.launch { completeHeaders(list, startposition) }
        return list
    }

    private fun getScopedPage(loadSize: Int, startposition: Int): List<MediaWrapper> {
        // For VideoArtist and VideoAlbum (or artists/albums with negative IDs after unparceling)
        val isVideoOnlyArtist = parent is VideoArtist || (parent is Artist && parent.id < 0)
        val isVideoOnlyAlbum = parent is VideoAlbum || (parent is Album && parent.id < 0)
        
        if (isVideoOnlyArtist || isVideoOnlyAlbum) {
            // Get stored videos if this is a VideoArtist/VideoAlbum instance
            val storedVideos = when (parent) {
                is VideoArtist -> parent.getVideos()
                is VideoAlbum -> parent.getVideos()
                else -> emptyList()
            }
            
            // If we have stored videos, use them; otherwise load by name matching
            val videos = if (storedVideos.isNotEmpty()) {
                storedVideos
            } else {
                // Parent was parceled and lost its videos, reload by name matching
                getVideosMatchingParent()
            }
            
            // Apply metadata descriptions to videos
            if (videos.isNotEmpty() && videoMetadataCache.isEmpty()) {
                runBlocking(Dispatchers.IO) {
                    val metadata = metadataRepository.getByMediaIds(videos.map { it.id })
                    videoMetadataCache = metadata.associateBy { it.mediaId }
                    applyMetadataToVideoDescriptions(videos)
                }
            }
            
            // Filter by search query if present
            val filteredVideos = if (model.filterQuery != null) {
                val query = model.filterQuery!!.lowercase()
                videos.filter { it.title.lowercase().contains(query) }
            } else videos
            
            // Sort and paginate
            return filteredVideos
                .sortedWith(if (desc) getComparator(sort).reversed() else getComparator(sort))
                .drop(startposition)
                .take(loadSize)
        }
        
        val nativeTracksArray = if (model.filterQuery == null) when(parent) {
            is Artist -> parent.getPagedTracks(sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
            is Album -> parent.getPagedTracks(sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
            is Genre -> parent.getPagedTracks(sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
            is Playlist -> parent.getPagedTracks(loadSize, startposition, Settings.includeMissing, onlyFavorites)
            else -> medialibrary.getPagedAudio(sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
        } else when(parent) {
            is Artist -> parent.searchTracks(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
            is Album -> parent.searchTracks(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
            is Genre -> parent.searchTracks(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
            is Playlist -> parent.searchTracks(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
            else -> medialibrary.searchAudio(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
        }
        
        val nativeTracks = nativeTracksArray.toMutableList()

        // For native Artist and Album views (positive IDs), include videos with matching metadata
        if ((parent is Artist && parent.id > 0) || (parent is Album && parent.id > 0)) {
            val matchingVideos = getVideosMatchingParent()
            if (matchingVideos.isNotEmpty()) {
                // Filter by search query if present
                val filteredVideos = if (model.filterQuery != null) {
                    val query = model.filterQuery!!.lowercase()
                    matchingVideos.filter { it.title.lowercase().contains(query) }
                } else matchingVideos
                
                // Combine native tracks with matching videos
                val combined = (nativeTracks + filteredVideos)
                    .sortedWith(if (desc) getComparator(sort).reversed() else getComparator(sort))
                
                // Return the requested page
                return combined.drop(startposition).take(loadSize)
            }
        }
        
        return nativeTracks
    }

    /**
     * Get videos that have metadata matching the current parent (artist or album)
     */
    private fun getVideosMatchingParent(): List<MediaWrapper> {
        if (parent !is Artist && parent !is Album) return emptyList()
        
        // Check if we have cached results for this parent
        if (cachedScopedVideos != null && cachedScopedParentId == parent.id) {
            return cachedScopedVideos!!
        }

        val parentName = parent.title.lowercase()
        val videos = runBlocking(Dispatchers.IO) {
            // Get all videos from medialibrary
            val allVideos = medialibrary.getVideos(Medialibrary.SORT_ALPHA, false, Settings.includeMissing, onlyFavorites)
            
            // Get all cached metadata
            val allMetadata = metadataRepository.getAll().associateBy { it.mediaId }
            
            // Filter videos that have matching artist/album metadata
            val matching = allVideos.filter { video ->
                val metadata = allMetadata[video.id] ?: return@filter false
                when (parent) {
                    is Artist -> metadata.artist?.lowercase() == parentName || metadata.albumArtist?.lowercase() == parentName
                    is Album -> metadata.album?.lowercase() == parentName
                    else -> false
                }
            }.toList()
            
            // Update cache and apply descriptions
            if (matching.isNotEmpty()) {
                videoMetadataCache = allMetadata
                applyMetadataToVideoDescriptions(matching)
            }
            
            android.util.Log.d("TracksProvider", "Found ${matching.size} videos matching ${parent::class.simpleName} '${parent.title}'")
            matching
        }
        
        cachedScopedVideos = videos
        cachedScopedParentId = parent.id
        return videos
    }

    private fun getCombinedMedia(limit: Int? = null): List<MediaWrapper> {
        if (cachedCombined != null && cachedFilter == model.filterQuery && cachedSort == sort && cachedDesc == desc && cachedOnlyFavorites == onlyFavorites) {
            if (limit == null) return cachedCombined!!
            if (cachedCombined!!.size >= limit) return cachedCombined!!.take(limit)
        }

        val needsCache = limit == null
        val effectiveLimit = limit ?: Int.MAX_VALUE

        val audioCount = if (model.filterQuery == null) medialibrary.audioCount else medialibrary.getAudioCount(model.filterQuery)
        val videoCount = if (model.filterQuery == null) medialibrary.videoCount else medialibrary.getVideoCount(model.filterQuery)

        val audioLimit = minOf(audioCount, effectiveLimit)
        val videoLimit = minOf(videoCount, effectiveLimit)

        val audio = if (audioLimit > 0) {
            if (model.filterQuery == null) medialibrary.getPagedAudio(sort, desc, Settings.includeMissing, onlyFavorites, audioLimit, 0).asList()
            else medialibrary.searchAudio(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, audioLimit, 0).asList()
        } else emptyList()

        val video = if (videoLimit > 0) {
            if (model.filterQuery == null) medialibrary.getPagedVideos(sort, desc, Settings.includeMissing, onlyFavorites, videoLimit, 0).asList()
            else medialibrary.searchVideo(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, videoLimit, 0).asList()
        } else emptyList()

        // Load cached metadata for videos and apply to descriptions
        if (video.isNotEmpty()) {
            loadVideoMetadataCache(video)
            // Apply cached metadata to video descriptions for display
            applyMetadataToVideoDescriptions(video)
            // Trigger background extraction for videos without cached metadata
            triggerMetadataExtraction(video)
        }

        val comparator = getComparator(sort)
        val combined = (audio + video)
            .sortedWith(if (desc) comparator.reversed() else comparator)
            .let { if (limit == null) it else it.take(limit) }

        if (needsCache) {
            cachedCombined = combined
            cachedFilter = model.filterQuery
            cachedSort = sort
            cachedDesc = desc
            cachedOnlyFavorites = onlyFavorites
        }
        return combined
    }

    /**
     * Load cached video audio metadata for the given videos
     */
    private fun loadVideoMetadataCache(videos: List<MediaWrapper>) {
        val videoIds = videos.map { it.id }
        videoMetadataCache = runBlocking(Dispatchers.IO) {
            val metadata = metadataRepository.getByMediaIds(videoIds).associateBy { it.mediaId }
            android.util.Log.d("TracksProvider", "Loaded ${metadata.size} cached metadata entries for ${videos.size} videos")
            metadata
        }
    }

    /**
     * Trigger background extraction for videos that don't have cached metadata
     */
    private fun triggerMetadataExtraction(videos: List<MediaWrapper>) {
        val videosWithoutMetadata = videos.filter { video ->
            val cached = videoMetadataCache[video.id]
            cached == null || cached.lastModified < video.lastModified
        }
        
        if (videosWithoutMetadata.isNotEmpty()) {
            android.util.Log.d("TracksProvider", "Triggering metadata extraction for ${videosWithoutMetadata.size} videos")
            model.viewModelScope.launch(Dispatchers.IO) {
                val extracted = VideoMetadataExtractor.extractAndCacheMetadata(context, videosWithoutMetadata, metadataRepository)
                android.util.Log.d("TracksProvider", "Extracted metadata for $extracted videos")
                if (extracted > 0) {
                    // Reload cache after extraction
                    val videoIds = videos.map { it.id }
                    videoMetadataCache = metadataRepository.getByMediaIds(videoIds).associateBy { it.mediaId }
                    // Invalidate combined cache and trigger refresh
                    cachedCombined = null
                    // Trigger UI refresh on the main thread
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        refresh()
                    }
                }
            }
        }
    }

    /**
     * Get cached metadata for a media item (returns null for audio or uncached videos)
     */
    private fun getVideoMetadata(media: MediaWrapper): VideoAudioMetadata? {
        return if (media.type == MediaWrapper.TYPE_VIDEO) videoMetadataCache[media.id] else null
    }

    /**
     * Apply cached audio metadata to video descriptions for display in the audio tab.
     * This shows artist · album format like audio files instead of duration.
     */
    private fun applyMetadataToVideoDescriptions(videos: List<MediaWrapper>) {
        var appliedCount = 0
        for (video in videos) {
            val metadata = videoMetadataCache[video.id]
            if (metadata != null) {
                val description = video.buildAudioDescription(metadata)
                if (description.isNotEmpty()) {
                    video.description = description
                    appliedCount++
                }
            }
        }
        android.util.Log.d("TracksProvider", "Applied metadata descriptions to $appliedCount of ${videos.size} videos")
    }

    private fun getComparator(sort: Int): Comparator<MediaWrapper> = when (sort) {
        Medialibrary.SORT_DURATION -> compareBy { it.length }
        Medialibrary.SORT_INSERTIONDATE -> compareBy { it.insertionDate }
        Medialibrary.SORT_LASTMODIFICATIONDATE -> compareBy { it.lastModified }
        Medialibrary.SORT_RELEASEDATE -> compareBy { it.releaseYear }
        Medialibrary.SORT_FILESIZE -> compareBy { it.length }
        Medialibrary.SORT_ARTIST -> compareBy { it.getEffectiveArtist(getVideoMetadata(it)) }
        Medialibrary.SORT_ALBUM -> compareBy { it.getEffectiveAlbum(getVideoMetadata(it)) }
        Medialibrary.SORT_FILENAME -> compareBy { it.fileName }
        Medialibrary.TrackNumber, Medialibrary.TrackId -> MediaComparators.BY_TRACK_NUMBER
        else -> Comparator { first, second -> MediaComparators.ANDROID_AUTO.compare(first, second) }
    }

    override fun getTotalCount(): Int {
        if (parent == null) {
            return if (model.filterQuery == null) medialibrary.audioCount + medialibrary.videoCount 
                   else medialibrary.getAudioCount(model.filterQuery) + medialibrary.getVideoCount(model.filterQuery)
        }
        
        // For VideoArtist and VideoAlbum (or artists/albums with negative IDs after unparceling)
        val isVideoOnlyArtist = parent is VideoArtist || (parent is Artist && parent.id < 0)
        val isVideoOnlyAlbum = parent is VideoAlbum || (parent is Album && parent.id < 0)
        
        if (isVideoOnlyArtist || isVideoOnlyAlbum) {
            // Get stored videos if this is a VideoArtist/VideoAlbum instance
            val storedVideos = when (parent) {
                is VideoArtist -> parent.getVideos()
                is VideoAlbum -> parent.getVideos()
                else -> emptyList()
            }
            
            // If we have stored videos, use them; otherwise load by name matching
            val videos = if (storedVideos.isNotEmpty()) {
                storedVideos
            } else {
                getVideosMatchingParent()
            }
            
            return if (model.filterQuery != null) {
                val query = model.filterQuery!!.lowercase()
                videos.count { it.title.lowercase().contains(query) }
            } else videos.size
        }
        
        val nativeCount = if (model.filterQuery == null) when (parent) {
            is Album -> parent.realTracksCount
            is Playlist -> parent.getRealTracksCount(Settings.includeMissing, onlyFavorites)
            is Artist, is Genre -> parent.tracksCount
            else -> medialibrary.audioCount
        } else when(parent) {
            is Artist -> parent.searchTracksCount(model.filterQuery)
            is Album -> parent.searchTracksCount(model.filterQuery)
            is Genre -> parent.searchTracksCount(model.filterQuery)
            is Playlist -> parent.searchTracksCount(model.filterQuery)
            else -> medialibrary.getAudioCount(model.filterQuery)
        }
        
        // Add matching videos count for native Artist and Album views (positive IDs)
        if ((parent is Artist && parent.id > 0) || (parent is Album && parent.id > 0)) {
            val matchingVideosCount = getVideosMatchingParent().let { videos ->
                if (model.filterQuery != null) {
                    val query = model.filterQuery!!.lowercase()
                    videos.count { it.title.lowercase().contains(query) }
                } else videos.size
            }
            return nativeCount + matchingVideosCount
        }
        
        return nativeCount
    }
}