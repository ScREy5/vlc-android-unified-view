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
import org.videolan.tools.Settings
import org.videolan.vlc.gui.helpers.MediaComparators
import org.videolan.vlc.mediadb.models.VideoAudioMetadata
import org.videolan.vlc.repository.VideoAudioMetadataRepository
import org.videolan.vlc.util.VideoMetadataExtractor
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

    private var cachedCombined: List<MediaWrapper>? = null
    private var cachedFilter: String? = null
    private var cachedSort: Int = sort
    private var cachedDesc: Boolean = desc
    private var cachedOnlyFavorites: Boolean = onlyFavorites

    override fun getAll(): Array<MediaWrapper> = getCombinedMedia().toTypedArray()

    override fun getPage(loadSize: Int, startposition: Int) : Array<MediaWrapper> {
        val list = if (parent != null) getScopedPage(loadSize, startposition).toTypedArray() else getCombinedMedia(startposition + loadSize)
            .drop(startposition)
            .take(loadSize)
            .toTypedArray()
        model.viewModelScope.launch { completeHeaders(list, startposition) }
        return list
    }

    private fun getScopedPage(loadSize: Int, startposition: Int): List<MediaWrapper> {
        val list = if (model.filterQuery == null) when(parent) {
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
        return list.toList()
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

        // Load cached metadata for videos
        if (video.isNotEmpty()) {
            loadVideoMetadataCache(video)
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
            metadataRepository.getByMediaIds(videoIds).associateBy { it.mediaId }
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
            model.viewModelScope.launch(Dispatchers.IO) {
                VideoMetadataExtractor.extractAndCacheMetadata(context, videosWithoutMetadata, metadataRepository)
                // Reload cache after extraction
                val videoIds = videos.map { it.id }
                videoMetadataCache = metadataRepository.getByMediaIds(videoIds).associateBy { it.mediaId }
                // Invalidate combined cache to trigger re-sort with new metadata
                cachedCombined = null
            }
        }
    }

    /**
     * Get cached metadata for a media item (returns null for audio or uncached videos)
     */
    private fun getVideoMetadata(media: MediaWrapper): VideoAudioMetadata? {
        return if (media.type == MediaWrapper.TYPE_VIDEO) videoMetadataCache[media.id] else null
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

    override fun getTotalCount() = if (parent != null) {
        if (model.filterQuery == null) when (parent) {
            is Album -> parent.realTracksCount
            is Playlist -> parent.getRealTracksCount(Settings.includeMissing, onlyFavorites)
            is Artist,
            is Genre -> parent.tracksCount
            else -> medialibrary.audioCount
        } else when(parent) {
            is Artist -> parent.searchTracksCount(model.filterQuery)
            is Album -> parent.searchTracksCount(model.filterQuery)
            is Genre -> parent.searchTracksCount(model.filterQuery)
            is Playlist -> parent.searchTracksCount(model.filterQuery)
            else -> medialibrary.getAudioCount(model.filterQuery)
        }
    } else (if (model.filterQuery == null) medialibrary.audioCount + medialibrary.videoCount else medialibrary.getAudioCount(model.filterQuery) + medialibrary.getVideoCount(model.filterQuery))
}