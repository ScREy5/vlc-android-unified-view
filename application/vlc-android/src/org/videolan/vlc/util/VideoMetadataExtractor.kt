/*******************************************************************************
 *  VideoMetadataExtractor.kt
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

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.VLCInstance
import org.videolan.vlc.mediadb.models.VideoAudioMetadata
import org.videolan.vlc.repository.VideoAudioMetadataRepository

/**
 * Utility class for extracting audio metadata from video files using libVLC.
 * This allows videos to display artist, album, genre, etc. in the audio tab.
 */
object VideoMetadataExtractor {
    private const val TAG = "VideoMetadataExtractor"
    
    private val mediaFactory by lazy { 
        FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory 
    }

    /**
     * Extract audio metadata from a video MediaWrapper using libVLC parsing.
     * 
     * @param context Application context
     * @param mediaWrapper The video to extract metadata from
     * @return VideoAudioMetadata if parsing was successful, null otherwise
     */
    suspend fun extractMetadata(context: Context, mediaWrapper: MediaWrapper): VideoAudioMetadata? = withContext(Dispatchers.IO) {
        if (mediaWrapper.type != MediaWrapper.TYPE_VIDEO) {
            Log.d(TAG, "Not a video: ${mediaWrapper.uri}")
            return@withContext null
        }

        try {
            val libVLC = VLCInstance.getInstance(context)
            val media = mediaFactory.getFromUri(libVLC, mediaWrapper.uri)
            
            // Parse the media to extract metadata
            media.parse(IMedia.Parse.ParseLocal or IMedia.Parse.ParseNetwork)
            
            // Wait for parsing to complete (with timeout)
            var attempts = 0
            while (!media.isParsed && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            if (!media.isParsed) {
                Log.w(TAG, "Media parsing timed out for: ${mediaWrapper.uri}")
                media.release()
                return@withContext null
            }
            
            // Extract metadata from the parsed media
            val artist = media.getMeta(IMedia.Meta.Artist) ?: ""
            val album = media.getMeta(IMedia.Meta.Album) ?: ""
            val albumArtist = media.getMeta(IMedia.Meta.AlbumArtist) ?: ""
            val genre = media.getMeta(IMedia.Meta.Genre) ?: ""
            val artworkUrl = media.getMeta(IMedia.Meta.ArtworkURL) ?: ""
            val trackNumberStr = media.getMeta(IMedia.Meta.TrackNumber)
            val discNumberStr = media.getMeta(IMedia.Meta.DiscNumber)
            val dateStr = media.getMeta(IMedia.Meta.Date)
            
            val trackNumber = trackNumberStr?.toIntOrNull() ?: 0
            val discNumber = discNumberStr?.toIntOrNull() ?: 0
            val releaseYear = dateStr?.take(4)?.toIntOrNull() ?: 0
            
            media.release()
            
            // Only return metadata if at least one meaningful field is present
            if (artist.isEmpty() && album.isEmpty() && albumArtist.isEmpty() && genre.isEmpty()) {
                Log.d(TAG, "No audio metadata found for: ${mediaWrapper.uri}")
                return@withContext null
            }
            
            Log.d(TAG, "Extracted metadata for ${mediaWrapper.uri}: artist=$artist, album=$album")
            
            VideoAudioMetadata(
                mediaId = mediaWrapper.id,
                mrl = mediaWrapper.uri.toString(),
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                trackNumber = trackNumber,
                discNumber = discNumber,
                artworkUrl = artworkUrl,
                releaseYear = releaseYear,
                lastModified = mediaWrapper.lastModified,
                parsedTimestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from ${mediaWrapper.uri}", e)
            null
        }
    }

    /**
     * Extract and cache metadata for multiple videos.
     * Only extracts metadata for videos that don't have cached metadata
     * or whose files have been modified since the last extraction.
     * 
     * @param context Application context
     * @param videos List of video MediaWrappers to process
     * @param repository Repository to save the metadata
     * @return Number of videos successfully processed
     */
    suspend fun extractAndCacheMetadata(
        context: Context,
        videos: List<MediaWrapper>,
        repository: VideoAudioMetadataRepository
    ): Int = withContext(Dispatchers.IO) {
        var processed = 0
        val existingIds = repository.getAllMediaIds().toSet()
        
        for (video in videos) {
            // Skip if metadata already exists and is up to date
            val existing = repository.getIfUpToDate(video.id, video.lastModified)
            if (existing != null) {
                continue
            }
            
            val metadata = extractMetadata(context, video)
            if (metadata != null) {
                repository.save(metadata)
                processed++
            }
        }
        
        Log.d(TAG, "Processed $processed out of ${videos.size} videos")
        processed
    }

    /**
     * Apply cached metadata to a MediaWrapper.
     * This updates the MediaWrapper's metadata fields with cached values.
     * 
     * @param mediaWrapper The MediaWrapper to update
     * @param metadata The cached metadata to apply
     */
    fun applyMetadataToWrapper(mediaWrapper: MediaWrapper, metadata: VideoAudioMetadata) {
        // Update the MediaWrapper's metadata fields
        // Note: MediaWrapper fields are protected, so we need to use reflection or
        // create wrapper methods. For now, we'll store the metadata separately
        // and use it when displaying/sorting.
    }
}
