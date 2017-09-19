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
package de.intranda.digiverso.presentation.model.viewer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.intranda.digiverso.presentation.controller.DataManager;

public enum PageType {

    viewImage("image"),
    viewToc("toc"),
    viewThumbs("thumbs"),
    viewPreview("preview"),
    viewMetadata("metadata"),
    viewFulltext("fulltext"),
    viewOverview("overview"),
    viewFullscreen("fullscreen"),
    viewReadingMode("readingmode"),
    viewCalendar("calendar"),
    search("search"),
    advancedSearch("advancedsearch"),
    timelinesearch("timelinesearch"),
    calendarsearch("calendarsearch"),
    term("term"),
    browse("browse", PageTypeHandling.cms),
    expandCollection("expandCollection"),
    firstWorkInCollection("rest/redirect/toFirstWork"),
    sites("sites"),
    // TODO remove
    editContent("crowd/editContent"),
    editOcr("crowd/editOcr"),
    editHistory("crowd/editHistory"),
    index("index", PageTypeHandling.cms),
    other(""); //unknown page type name in Navigationhelper. Probably a cms-page

    /** Logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(PageType.class);

    private final String name;
    private final PageTypeHandling handling;

    private PageType(String name) {
        this.name = name;
        this.handling = PageTypeHandling.none;
    }

    private PageType(String name, PageTypeHandling handling) {
        this.name = name;
        this.handling = handling;
    }

    public PageTypeHandling getHandling() {
        return this.handling;
    }

    public boolean isHandledWithCms() {
        return this.handling.equals(PageTypeHandling.cms);
    }
    
    public boolean isCmsPage() {
        switch (this) {
            case editContent:
            case editHistory:
            case editOcr:
            case viewCalendar:
            case viewFullscreen:
            case viewFulltext:
            case viewImage:
            case viewMetadata:
            case viewOverview:
            case viewPreview:
            case viewReadingMode:
            case viewThumbs:
            case viewToc:
                return true;
            default:
                return false;
        }
    }

    /**
     * 
     * @return
     */
    public boolean isDocumentPage() {
        switch (this) {
            case editContent:
            case editHistory:
            case editOcr:
            case viewCalendar:
            case viewFullscreen:
            case viewFulltext:
            case viewImage:
            case viewMetadata:
            case viewOverview:
            case viewPreview:
            case viewReadingMode:
            case viewThumbs:
            case viewToc:
                return true;
            default:
                return false;
        }
    }

    public static List<PageType> getTypesHandledByCms() {
        Set<PageType> all = EnumSet.allOf(PageType.class);
        List<PageType> cmsPages = new ArrayList<>();
        for (PageType pageType : all) {
            if (pageType.isHandledWithCms()) {
                cmsPages.add(pageType);
            }
        }
        return cmsPages;
    }

    /**
     * 
     * @param name
     * @return
     * @should return correct type for raw names
     * @should return correct type for mapped names
     * @should return correct type for enum names
     */
    public static PageType getByName(String name) {
        if (name == null) {
            return null;
        }
        for (PageType p : PageType.values()) {
            if (p.getName().equalsIgnoreCase(name) || p.name.equalsIgnoreCase(name) || p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return PageType.other;
    }

    public String getName() {
        String configName = DataManager.getInstance().getConfiguration().getPageType(this);
        if (configName != null) {
            return configName;
        }

        return name;
    }

    public static enum PageTypeHandling {
        none,
        cms;
    }

    /**
     * 
     * @param docStructType
     * @return
     */
    public static PageType getPagetTypeForDocStructType(String docStructType) {
        // First choice: Use preferred target page type for this docstruct type, if configured
        String preferredPageTypeName = DataManager.getInstance().getConfiguration().getDocstructTargetPageType(docStructType);
        PageType preferredPageType = PageType.getByName(preferredPageTypeName);
        if (StringUtils.isNotEmpty(preferredPageTypeName) && preferredPageType == null) {
            logger.error("docstructTargetPageType configured for '{}' does not exist: {}", docStructType, preferredPageTypeName);
        }
        // Second choice: Use target page type configured as _DEFAULT, if available
        String defaultPageTypeName = DataManager.getInstance().getConfiguration().getDocstructTargetPageType("_DEFAULT");
        PageType defaultPageType = PageType.getByName(defaultPageTypeName);
        if (StringUtils.isNotEmpty(defaultPageTypeName) && defaultPageType == null) {
            logger.error("docstructTargetPageType configured for '_DEFAULT' does not exist: {}", docStructType, defaultPageTypeName);
        }

        if (preferredPageType != null) {
            logger.trace("Found preferred page type: {}", preferredPageType.getName());
            return preferredPageType;
        } else if (defaultPageType != null) {
            logger.trace("Found default page type: {}", defaultPageType.getName());
            return defaultPageType;
        }

        return null;
    }

    /**
     * 
     * @param docStructType
     * @param mimeType
     * @param anchorOrGroup
     * @param hasImages
     * @param preferOverviewPage Use the overview page type, if not a page resolver URL
     * @param pageResolverUrl If this page type is for a page resover url, ignore certain preferences
     * @return
     * @should return overview page type if preferOverviewPage true
     * @should return configured page type correctly
     * @should return metadata page type for application mime type
     * @should return toc page type for anchors
     * @should return image page type correctly
     * @should return medatata page type if nothing else matches
     */
    public static PageType determinePageType(String docStructType, String mimeType, boolean anchorOrGroup, boolean hasImages,
            boolean preferOverviewPage, boolean pageResolverUrl) {
        // Prefer the overview page, if available (and not a page URL)
        if (preferOverviewPage && !pageResolverUrl) {
            return PageType.viewOverview;
        }
        // Determine preferred target for the docstruct
        PageType configuredPageType = PageType.getPagetTypeForDocStructType(docStructType);
        if (configuredPageType != null && !pageResolverUrl) {
            return configuredPageType;
        }
        if ("application".equals(mimeType)) {
            return PageType.viewMetadata;
        }
        if (anchorOrGroup) {
            return PageType.viewToc;
        }
        if (hasImages) {
            return PageType.viewImage;
        }

        return PageType.viewMetadata;
    }
}
