/*****************************************************************************
 * VideoAlbum.java
 *****************************************************************************
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
 *****************************************************************************/

package org.videolan.vlc.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * A virtual Album that represents videos grouped by album name from their audio metadata.
 * This allows videos with embedded audio metadata (artist, album, etc.) to appear in the
 * Albums tab of the audio browser.
 * 
 * Uses negative IDs to distinguish from real medialibrary albums.
 */
public class VideoAlbum extends Album {

    private List<MediaWrapper> videos = new ArrayList<>();

    public VideoAlbum(String title, String artistName, int videoCount, int releaseYear, String artworkMrl, long totalDuration) {
        // Use negative ID based on title+artist hash to avoid collisions with real albums
        super(
            -((title + artistName).hashCode() & 0x7FFFFFFF),
            title,
            releaseYear,
            artworkMrl,
            artistName,
            0L, // albumArtistId - we don't have a real artist ID
            videoCount, // nbTracks
            videoCount, // nbPresentTracks
            totalDuration,
            false // isFavorite
        );
    }

    public VideoAlbum(Parcel in) {
        super(in);
    }

    /**
     * Set the videos belonging to this album
     */
    public void setVideos(List<MediaWrapper> videos) {
        this.videos = videos != null ? videos : new ArrayList<>();
    }

    /**
     * Get the videos belonging to this album
     */
    public List<MediaWrapper> getVideos() {
        return videos;
    }

    @Override
    public int getRealTracksCount() {
        return videos.size();
    }

    @Override
    public MediaWrapper[] getTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return videos.toArray(new MediaWrapper[0]);
    }

    @Override
    public MediaWrapper[] getPagedTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        if (offset >= videos.size()) {
            return new MediaWrapper[0];
        }
        int end = Math.min(offset + nbItems, videos.size());
        return videos.subList(offset, end).toArray(new MediaWrapper[0]);
    }

    @Override
    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        if (query == null || query.isEmpty()) {
            return getPagedTracks(sort, desc, includeMissing, onlyFavorites, nbItems, offset);
        }
        String lowerQuery = query.toLowerCase();
        List<MediaWrapper> result = new ArrayList<>();
        int count = 0;
        for (MediaWrapper video : videos) {
            if (video.getTitle().toLowerCase().contains(lowerQuery)) {
                if (count >= offset && result.size() < nbItems) {
                    result.add(video);
                }
                count++;
            }
        }
        return result.toArray(new MediaWrapper[0]);
    }

    @Override
    public int searchTracksCount(String query) {
        if (query == null || query.isEmpty()) {
            return videos.size();
        }
        String lowerQuery = query.toLowerCase();
        int count = 0;
        for (MediaWrapper video : videos) {
            if (video.getTitle().toLowerCase().contains(lowerQuery)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Artist retrieveAlbumArtist() {
        // Return null since we don't have a real artist ID
        return null;
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        // Virtual albums don't support favorites
        return false;
    }

    /**
     * Check if this is a video-based virtual album
     */
    public boolean isVideoAlbum() {
        return true;
    }

    public static final Creator<VideoAlbum> CREATOR = new Creator<VideoAlbum>() {
        @Override
        public VideoAlbum createFromParcel(Parcel in) {
            return new VideoAlbum(in);
        }

        @Override
        public VideoAlbum[] newArray(int size) {
            return new VideoAlbum[size];
        }
    };
}
