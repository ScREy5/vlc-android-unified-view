/*****************************************************************************
 * VideoArtist.java
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

import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.Album;

import java.util.ArrayList;
import java.util.List;

/**
 * A virtual Artist that represents videos grouped by artist name from their audio metadata.
 * This allows videos with embedded audio metadata (artist, album, etc.) to appear in the
 * Artists tab of the audio browser.
 * 
 * Uses negative IDs to distinguish from real medialibrary artists.
 */
public class VideoArtist extends Artist {

    private List<MediaWrapper> videos = new ArrayList<>();

    public VideoArtist(String name, int videoCount, String artworkMrl) {
        // Use negative ID based on name hash to avoid collisions with real artists
        super(-Math.abs(name.hashCode()), name, "", artworkMrl, "", 0, 0, videoCount, false);
    }

    public VideoArtist(Parcel in) {
        super(in);
    }

    /**
     * Set the videos belonging to this artist
     */
    public void setVideos(List<MediaWrapper> videos) {
        this.videos = videos != null ? videos : new ArrayList<>();
    }

    /**
     * Get the videos belonging to this artist
     */
    public List<MediaWrapper> getVideos() {
        return videos;
    }

    @Override
    public Album[] getAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        // Videos don't have real albums in the medialibrary sense
        return new Album[0];
    }

    @Override
    public Album[] getPagedAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return new Album[0];
    }

    @Override
    public Album[] searchAlbums(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return new Album[0];
    }

    @Override
    public int searchAlbumsCount(String query) {
        return 0;
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
    public int getAlbumsCount() {
        return 0;
    }

    @Override
    public int getTracksCount() {
        return videos.size();
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        // Virtual artists don't support favorites
        return false;
    }

    /**
     * Check if this is a video-based virtual artist
     */
    public boolean isVideoArtist() {
        return true;
    }

    public static final Creator<VideoArtist> CREATOR = new Creator<VideoArtist>() {
        @Override
        public VideoArtist createFromParcel(Parcel in) {
            return new VideoArtist(in);
        }

        @Override
        public VideoArtist[] newArray(int size) {
            return new VideoArtist[size];
        }
    };
}
