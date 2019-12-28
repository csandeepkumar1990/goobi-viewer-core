/**
 * This file is part of the Goobi viewer - a content presentation and management application for digitized objects.
 *
 * Visit these websites for more information.
 *          - http://www.intranda.com
 *          - http://digiverso.com
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.goobi.viewer.model.cms.tilegrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.CollectionUtils;

import io.goobi.viewer.model.cms.CMSMediaItem;
import io.goobi.viewer.model.cms.tilegrid.ImageGalleryTile.Priority;

/**
 * <p>TileGridBuilder class.</p>
 *
 */
public class TileGridBuilder {

    private int gridSize = 9;
    private int reserveForHighPriority = 9;
    private String language = "en";
    private Set<String> tags = new HashSet<>();

    private final HttpServletRequest request;
    
    /**
     * <p>Constructor for TileGridBuilder.</p>
     *
     * @param servletRequest a {@link javax.servlet.http.HttpServletRequest} object.
     */
    public TileGridBuilder(HttpServletRequest servletRequest) {
        this.request = servletRequest;
    }

    /**
     * <p>size.</p>
     *
     * @param size a int.
     * @return a {@link io.goobi.viewer.model.cms.tilegrid.TileGridBuilder} object.
     */
    public TileGridBuilder size(int size) {
        this.gridSize = size;
        return this;
    }

    /**
     * <p>reserveForHighPriority.</p>
     *
     * @param reserve a int.
     * @return a {@link io.goobi.viewer.model.cms.tilegrid.TileGridBuilder} object.
     */
    public TileGridBuilder reserveForHighPriority(int reserve) {
        this.reserveForHighPriority = reserve;
        return this;
    }

    /**
     * <p>tags.</p>
     *
     * @param tags a {@link java.util.Collection} object.
     * @return a {@link io.goobi.viewer.model.cms.tilegrid.TileGridBuilder} object.
     */
    public TileGridBuilder tags(Collection<String> tags) {
        this.tags.addAll(tags);
        return this;
    }

    /**
     * <p>tags.</p>
     *
     * @param tags a {@link java.lang.String} object.
     * @return a {@link io.goobi.viewer.model.cms.tilegrid.TileGridBuilder} object.
     */
    public TileGridBuilder tags(String... tags) {
        for (String tag : tags) {
            this.tags.add(tag);
        }
        return this;
    }

    /**
     * <p>build.</p>
     *
     * @param items a {@link java.util.List} object.
     * @return a {@link io.goobi.viewer.model.cms.tilegrid.TileGrid} object.
     */
    public TileGrid build(List<ImageGalleryTile> items) {
        if (!tags.isEmpty()) {
            items = filter(items, tags);
        }
        
        items = items.stream()
        .filter(item -> tags.isEmpty() || countTags(item, tags) > 0)
        .sorted(new SemiRandomOrderComparator<ImageGalleryTile>(tile -> tile.getDisplayOrder()))
        .collect(Collectors.toList());
         
        List<ImageGalleryTile> priorityItems = filter(items, Priority.IMPORTANT);
        priorityItems = priorityItems.subList(0, Math.min(gridSize, Math.min(priorityItems.size(), reserveForHighPriority)));
        
        List<ImageGalleryTile> defaultItems = filter(items, Priority.DEFAULT);
        defaultItems = defaultItems.subList(0, Math.min(defaultItems.size(), gridSize - priorityItems.size()));

        
        items = new ArrayList<>();
        items.addAll(priorityItems);
        items.addAll(defaultItems);
        
//        Collections.sort(items, new Comparator<ImageGalleryTile>() {
//            @Override
//            public int compare(ImageGalleryTile item1, ImageGalleryTile item2) {
//                return Integer.compare(item1.getDisplayOrder(), item2.getDisplayOrder());
//            }
//        });
        
        return new TileGrid(items, tags, language, request);
    }

    private List<ImageGalleryTile> filter(List<ImageGalleryTile> items, Set<String> tags) {
        List<ImageGalleryTile> filtered = new ArrayList<>();
        for (ImageGalleryTile item : items) {
            if (countTags(item, tags) > 0) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private List<ImageGalleryTile> filter(List<ImageGalleryTile> items, Priority priority) {
        List<ImageGalleryTile> filtered = new ArrayList<>();
        for (ImageGalleryTile item : items) {
            if (item.getPriority().equals(priority)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public static class PriorityComparator implements Comparator<CMSMediaItem> {

        @Override
        public int compare(CMSMediaItem item1, CMSMediaItem item2) {
            return Integer.compare(item1.getPriority().ordinal(), item2.getPriority().ordinal());
        }

    }

    public static class ContainedTagComparator implements Comparator<CMSMediaItem> {

        private Set<String> tags;

        public ContainedTagComparator(Set<String> tags) {
            this.tags = tags;
        }

        @Override
        public int compare(CMSMediaItem item1, CMSMediaItem item2) {
            int item1TagCount = countTags(item1, this.tags);
            int item2TagCount = countTags(item2, this.tags);
            return Integer.compare(item2TagCount, item1TagCount);
        }

    }

    /**
     * Returns the number of tags that are both in the collection 'tags' and in the tag-list of the GalleryTile item
     *
     * @param item a {@link io.goobi.viewer.model.cms.tilegrid.ImageGalleryTile} object.
     * @param tags a {@link java.util.Collection} object.
     * @return a int.
     */
    protected static int countTags(ImageGalleryTile item, Collection<String> tags) {
        return CollectionUtils.intersection(item.getCategories().stream().map(c -> c.getName()).collect(Collectors.toList()), tags).size();
    }

    /**
     * <p>language.</p>
     *
     * @param language a {@link java.lang.String} object.
     * @return a {@link io.goobi.viewer.model.cms.tilegrid.TileGridBuilder} object.
     */
    public TileGridBuilder language(String language) {
        this.language = language;
        return this;
    }
}
