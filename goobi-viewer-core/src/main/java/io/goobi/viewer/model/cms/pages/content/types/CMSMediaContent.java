/*
 * This file is part of the Goobi viewer - a content presentation and management
 * application for digitized objects.
 *
 * Visit these websites for more information.
 *          - http://www.intranda.com
 *          - http://digiverso.com
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.goobi.viewer.model.cms.pages.content.types;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import io.goobi.viewer.api.rest.AbstractApiUrlManager;
import io.goobi.viewer.api.rest.v1.ApiUrls;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.IndexerTools;
import io.goobi.viewer.controller.StringTools;
import io.goobi.viewer.exceptions.CmsElementNotFoundException;
import io.goobi.viewer.exceptions.PresentationException;
import io.goobi.viewer.exceptions.ViewerConfigurationException;
import io.goobi.viewer.managedbeans.CmsMediaBean;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.model.cms.CategorizableTranslatedSelectable;
import io.goobi.viewer.model.cms.media.CMSMediaHolder;
import io.goobi.viewer.model.cms.media.CMSMediaItem;
import io.goobi.viewer.model.cms.media.CMSMediaItemMetadata;
import io.goobi.viewer.model.cms.pages.content.CMSContent;
import io.goobi.viewer.model.cms.pages.content.CMSContentItem;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "cms_content_media")
public class CMSMediaContent extends CMSContent implements CMSMediaHolder {

    private static final String BACKEND_COMPONENT_NAME = "media";

    
    @JoinColumn(name = "media_item_id")
    private CMSMediaItem mediaItem;

    public CMSMediaContent() {
        super();
    }

    public CMSMediaContent(CMSMediaContent orig) {
        super(orig);
        this.mediaItem = orig.mediaItem;
    }

    @Override
    public String getBackendComponentName() {
        return BACKEND_COMPONENT_NAME;
    }

    @Override
    public void setMediaItem(CMSMediaItem item) {
        this.mediaItem = item;
    }

    @Override
    public CMSMediaItem getMediaItem() {
        return mediaItem;
    }

    @Override
    public String getMediaFilter() {
        return CmsMediaBean.getImageFilter();
    }

    @Override
    public boolean hasMediaItem() {
        return this.mediaItem != null;
    }

    @Override
    public CategorizableTranslatedSelectable<CMSMediaItem> getMediaItemWrapper() {
        if (hasMediaItem()) {
            return new CategorizableTranslatedSelectable<>(mediaItem, true,
                    mediaItem.getFinishedLocales()
                            .stream()
                            .findFirst()
                            .orElse(BeanUtils.getLocale()),
                    Collections.emptyList());
        }
        return null;
    }

    public String getUrl() throws UnsupportedEncodingException, ViewerConfigurationException {
        return getUrl(null, null);
    }

    public String getUrl(String width, String height) throws ViewerConfigurationException, UnsupportedEncodingException {
        
        String contentString = "";
        String type = getMediaItem() != null ? getMediaItem().getContentType() : "";
        switch (type) {
            case CMSMediaItem.CONTENT_TYPE_XML:
                contentString = CmsMediaBean.getMediaFileAsString(getMediaItem());
                break;
            case CMSMediaItem.CONTENT_TYPE_PDF:
            case CMSMediaItem.CONTENT_TYPE_VIDEO:
            case CMSMediaItem.CONTENT_TYPE_AUDIO:
                boolean useContentApi = DataManager.getInstance().getConfiguration().isUseIIIFApiUrlForCmsMediaUrls();
                Optional<AbstractApiUrlManager> urls;
                if (useContentApi) {
                    urls = DataManager.getInstance().getRestApiManager().getContentApiManager();
                } else {
                    urls = DataManager.getInstance().getRestApiManager().getDataApiManager();
                }

                boolean legacyApi = !urls.isPresent();
                if (legacyApi) {
                    String baseUrl = useContentApi ? DataManager.getInstance().getRestApiManager().getContentApiUrl()
                            : DataManager.getInstance().getRestApiManager().getDataApiUrl();
                    URI uri = URI.create(baseUrl + "cms/media/get/"
                            + getMediaItem().getId() + ".pdf");
                    return uri.toString();
                }
                String filename = getMediaItem().getFileName();
                filename = URLEncoder.encode(filename, "utf-8");
                return urls.get().path(ApiUrls.CMS_MEDIA, ApiUrls.CMS_MEDIA_FILES_FILE).params(filename).build();

            default:
                // Images
                contentString = CmsMediaBean.getMediaUrl(getMediaItem(), width, height);
        }
        return contentString;
    }

    @Override
    public CMSContent copy() {
        CMSMediaContent copy = new CMSMediaContent(this);
        return copy;
    }


    @Override
    public List<File> exportHtmlFragment(String outputFolderPath, String namingScheme) throws IOException, ViewerConfigurationException {
        if (StringUtils.isEmpty(outputFolderPath)) {
            throw new IllegalArgumentException("hotfolderPath may not be null or emptys");
        }
        if (StringUtils.isEmpty(namingScheme)) {
            throw new IllegalArgumentException("namingScheme may not be null or empty");
        }
        if (this.mediaItem == null || !mediaItem.isHasExportableText()) {
            return Collections.emptyList();
        }

        List<File> ret = new ArrayList<>();
        Path cmsDataDir = Paths.get(outputFolderPath, namingScheme + IndexerTools.SUFFIX_CMS);
        if (!Files.isDirectory(cmsDataDir)) {
            Files.createDirectory(cmsDataDir);
        }

        // Export media item HTML content
        String html = CmsMediaBean.getMediaFileAsString(mediaItem);
        if (StringUtils.isNotEmpty(html)) {
            File file = new File(cmsDataDir.toFile(), this.getId() + "-" + this.mediaItem.getId() + ".html");
            FileUtils.writeStringToFile(file, html, StringTools.DEFAULT_ENCODING);
            ret.add(file);
        }

        return ret;
    }
    
    @Override
    public String handlePageLoad(boolean resetResults) throws PresentationException {
        return null;
    }
    
    /**
     * <p>
     * getMediaName.
     * </p>
     *
     * @param contentId a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public String getMediaName() {
        CMSMediaItemMetadata metadata = getMediaMetadata();
        return metadata == null ? "" : metadata.getName();
    }
    /**
     * <p>
     * getMediaDescription.
     * </p>
     *
     * @param contentId a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public String getMediaDescription() {
        CMSMediaItemMetadata metadata = getMediaMetadata();
        return metadata == null ? "" : metadata.getDescription();
    }

    
    /**
     * <p>
     * getMediaMetadata.
     * </p>
     *
     * @param itemId a {@link java.lang.String} object.
     * @return The media item metadata object of the current language associated with the contentItem with the given itemId. May return null if no
     *         such item exists
     */
    public CMSMediaItemMetadata getMediaMetadata() {

        if (getMediaItem() != null) {
            return getMediaItem().getCurrentLanguageMetadata();
        }
        return null;
    }

}
