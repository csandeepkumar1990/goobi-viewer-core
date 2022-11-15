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
package io.goobi.viewer.dao.update;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.intranda.metadata.multilanguage.MultiLanguageMetadataValue;
import io.goobi.viewer.dao.IDAO;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.model.cms.media.CMSMediaHolder;
import io.goobi.viewer.model.cms.media.CMSMediaItem;
import io.goobi.viewer.model.cms.pages.CMSPage;
import io.goobi.viewer.model.cms.pages.CMSTemplateManager;
import io.goobi.viewer.model.cms.pages.content.CMSComponent;
import io.goobi.viewer.model.cms.pages.content.CMSContent;
import io.goobi.viewer.model.cms.pages.content.PersistentCMSComponent;
import io.goobi.viewer.model.cms.pages.content.TranslatableCMSContent;
import io.goobi.viewer.model.translations.IPolyglott;
import io.goobi.viewer.model.translations.TranslatedText;

public class CMSPageUpdate implements IModelUpdate {

    private static final Logger logger = LogManager.getLogger(CMSPageUpdate.class);
    CMSContentConverter contentConverter;
    CMSTemplateManager templateManager;

    @Override
    public boolean update(IDAO dao) throws DAOException, SQLException {

        if (!dao.tableExists("cms_content_items")) {
            return false;
        }

        List<Map<String, Object>> languageVersions = getTableData(dao, "cms_page_language_versions");
        List<Map<String, Object>> contentItems = getTableData(dao, "cms_content_items");

        if (languageVersions.isEmpty() || contentItems.isEmpty()) {
            return false;
        }

        List<Map<String, Object>> pages = getTableData(dao, "cms_pages");

        contentConverter = new CMSContentConverter(dao);
        templateManager = CMSTemplateManager.getInstance();

        /*Map page ids to a map of all owned languageVersions mapped to language*/
        Map<Long, Map<String, Map<String, Object>>> languageVersionMap = languageVersions.stream()
                .collect(Collectors.toMap(map -> (Long) map.get("owner_page_id"), map -> Map.of((String) map.get("language"), map),
                        (map1, map2) -> combineMaps(map1, map2)));

        /*Map language version ids to a list of all owned contentItems*/
        Map<Long, List<Map<String, Object>>> contentItemMap = contentItems.stream()
                .collect(Collectors.toMap(map -> (Long) map.get("owner_page_language_version_id"), List::of,
                        (map1, map2) -> ListUtils.union(map1, map2)));

        List<CMSPage> updatedPages = new ArrayList<>();
        for (Map<String, Object> pageValues : pages) {
            Long pageId = (Long) pageValues.get("cms_page_id");
            CMSPage page = dao.getCMSPage(pageId);
            try {
                Map<String, Map<String, Object>> pageLanguageVersions = languageVersionMap.get(pageId);
                Map<String, List<Map<String, Object>>> pageContentItemsMap = getContentItemsForPage(pageLanguageVersions, contentItemMap);

                TranslatedText title = getTranslatedText(pageLanguageVersions, "title");
                TranslatedText menuTitle = getTranslatedText(pageLanguageVersions, "menu_title");
                Boolean published = (Boolean) pageValues.get("published");
                Long topbarSliderId = getTopbarSliderId(contentItemMap, pageLanguageVersions);
                String legacyPageTemplateId = (String) pageValues.get("template_id");

                createPreviewComponent(contentItemMap, pageLanguageVersions, title, dao)
                        .ifPresent(page::addPersistentComponent);

                if (title.isEmpty()) {
                    title.setText(legacyPageTemplateId, IPolyglott.getDefaultLocale());
                }
                page.setTitle(title);
                page.setMenuTitle(menuTitle);
                page.setTopbarSliderId(topbarSliderId);
                page.setPublished(published);

                Map<String, CMSContent> contentMap = createContentObjects(pageContentItemsMap);

                CMSComponent componentTemplate = templateManager.getLegacyComponent(legacyPageTemplateId);
                if (componentTemplate != null) {
                    PersistentCMSComponent component = new PersistentCMSComponent(componentTemplate, contentMap.values());
                    page.addPersistentComponent(component);
                } else {
                    logger.warn("No legacy template found with id {}: Cannot update cmsPage {}", legacyPageTemplateId, page.getId());
                }
            } catch (Exception e) {
                logger.error("Error updating page {}", page.getId(), e);
                throw e;
            }
            updatedPages.add(page);
        }

        for (CMSPage cmsPage : updatedPages) {
            try {
                if (!dao.updateCMSPage(cmsPage)) {
                    throw new DAOException("Saving page failed");
                }
            } catch (Exception e) {
                logger.error("Error updating page {}", cmsPage.getId(), e);
                throw e;
            }
        }

        dao.executeUpdate("DROP TABLE cms_content_item_cms_categories;");
        dao.executeUpdate("DROP TABLE cms_content_items;");
        dao.executeUpdate("DROP TABLE cms_page_language_versions;");
        dao.executeUpdate("ALTER TABLE cms_pages DROP COLUMN template_id;");

        return true;
    }

    /**
     * 
     * @param contentItemMap
     * @param pageLanguageVersions
     * @param title
     * @param dao
     * @return
     * @throws DAOException
     */
    private Optional<PersistentCMSComponent> createPreviewComponent(Map<Long, List<Map<String, Object>>> contentItemMap,
            Map<String, Map<String, Object>> pageLanguageVersions, TranslatedText title, IDAO dao) throws DAOException {

        TranslatedText previewText = getText(contentItemMap, pageLanguageVersions, "preview01");
        TranslatedText dateText = getText(contentItemMap, pageLanguageVersions, "A0");
        CMSMediaItem previewImage = getImage(contentItemMap, pageLanguageVersions, "image01", dao);

        CMSComponent componentTemplate = templateManager.getComponent("preview").orElse(null);
        if (componentTemplate == null) {
            logger.error("Cannot create preview component: component template 'preview' not found");
        } else if (!previewText.isEmpty()) {
            PersistentCMSComponent component = new PersistentCMSComponent(componentTemplate);
            if (title != null && !title.isEmpty()) {
                component.getContentByItemId("title").ifPresent(c -> ((TranslatableCMSContent) c).setText(title));
            }
            if (!dateText.isEmpty()) {
                component.getContentByItemId("date").ifPresent(c -> ((TranslatableCMSContent) c).setText(dateText));
            }
            if (previewImage != null) {
                component.getContentByItemId("image").ifPresent(c -> ((CMSMediaHolder) c).setMediaItem(previewImage));
            }
            component.getContentByItemId("text").ifPresent(c -> ((TranslatableCMSContent) c).setText(previewText));
            return Optional.of(component);
        }
        return Optional.empty();
    }

    /**
     * 
     * @param contentItemMap
     * @param pageLanguageVersions
     * @param itemId
     * @param dao
     * @return
     * @throws DAOException
     */
    private static CMSMediaItem getImage(Map<Long, List<Map<String, Object>>> contentItemMap, Map<String, Map<String, Object>> pageLanguageVersions,
            String itemId, IDAO dao) throws DAOException {
        Map<String, Object> previewImageItem = getContentItemsOfItemId(pageLanguageVersions, contentItemMap, itemId).get("global");
        CMSMediaItem previewImage = null;
        if (previewImageItem != null) {
            Long previewImageId = (Long) previewImageItem.get("media_item_id");
            if (previewImageId != null) {
                previewImage = dao.getCMSMediaItem(previewImageId);
            }
        }
        return previewImage;
    }

    /**
     * 
     * @param contentItemMap
     * @param pageLanguageVersions
     * @param itemId
     * @return
     */
    private static TranslatedText getText(Map<Long, List<Map<String, Object>>> contentItemMap,
            Map<String, Map<String, Object>> pageLanguageVersions, String itemId) {
        Map<String, Map<String, Object>> previewTexts = getContentItemsOfItemId(pageLanguageVersions, contentItemMap, itemId);
        Map<String, String> previewValues = previewTexts.entrySet()
                .stream()
                .filter(e -> StringUtils.isNotBlank((String) e.getValue().get("html_fragment")))
                .collect(Collectors.toMap(Entry::getKey, e -> (String) e.getValue().get("html_fragment")));
        return new TranslatedText(new MultiLanguageMetadataValue(previewValues), IPolyglott.getDefaultLocale());
    }

    /**
     * 
     * @param contentItemsMap
     * @return
     */
    private Map<String, CMSContent> createContentObjects(Map<String, List<Map<String, Object>>> contentItemsMap) {
        Map<String, CMSContent> contentMap = new HashMap<>();
        for (Entry<String, List<Map<String, Object>>> legacyItemEntry : contentItemsMap.entrySet()) {
            String language = legacyItemEntry.getKey();
            for (Map<String, Object> legacyItem : legacyItemEntry.getValue()) {
                String type = (String) legacyItem.get("type");
                String legacyItemId = (String) legacyItem.get("item_id");
                try {
                    CMSContent content = createContent(legacyItem, type, Optional.ofNullable(contentMap.get(legacyItemId)), language);
                    if (content != null) {
                        content.setItemId(legacyItemId);
                        contentMap.put(legacyItemId, content);
                    }
                } catch (DAOException | IllegalArgumentException e) {
                    logger.error("Error creating content from legacy item of tye {} with item-id {}", type, legacyItemId);
                }
            }
        }
        return contentMap;
    }

    /**
     * 
     * @param legacyItem
     * @param type
     * @param existingContent
     * @param language
     * @return
     * @throws DAOException
     */
    private CMSContent createContent(Map<String, Object> legacyItem, String type, Optional<CMSContent> existingContent, String language)
            throws DAOException {
        switch (type) {
            case "TEXT":
                return contentConverter.createShortTextContent(language, legacyItem, existingContent);
            case "HTML":
                return contentConverter.createMediumTextContent(language, legacyItem, existingContent);
            case "SOLRQUERY":
                return contentConverter.createRecordListContent(legacyItem);
            case "PAGELIST":
                return contentConverter.createPageListContent(legacyItem);
            case "COLLECTION":
                return contentConverter.createCollectionContent(legacyItem);
            case "TILEGRID":
                return contentConverter.createImageListContent(legacyItem);
            case "RSS":
                return contentConverter.createRSSContent(legacyItem);
            case "SEARCH":
                return contentConverter.createSearchContent(legacyItem);
            case "GLOSSARY":
                return contentConverter.createGlossaryContent(legacyItem);
            case "MEDIA":
                return contentConverter.createMediaContent(legacyItem);
            case "GEOMAP":
                return contentConverter.createGeomapContent(legacyItem);
            case "SLIDER":
                return contentConverter.createSliderContent(legacyItem);
            case "METADATA":
                return contentConverter.createMetadataContent(legacyItem);
            default:
                return null;
        }
    }

    /**
     * 
     * @param pageLanguageVersions language versions of a page mapped by language string
     * @param contentItemMap List of contentItems belonging to a single language version, mapped by language version id
     * @return
     */
    private static Map<String, List<Map<String, Object>>> getContentItemsForPage(Map<String, Map<String, Object>> pageLanguageVersions,
            Map<Long, List<Map<String, Object>>> contentItemMap) {
        Map<String, List<Map<String, Object>>> map = new HashMap<>();
        for (Entry<String, Map<String, Object>> langEntry : pageLanguageVersions.entrySet()) {
            String langauge = langEntry.getKey();
            Long langVersionId = (Long) langEntry.getValue().get("cms_page_language_version_id");
            List<Map<String, Object>> contentItems = contentItemMap.get(langVersionId);
            if (contentItems != null) {
                contentItems = contentItems.stream().filter(i -> {
                    String itemId = (String) i.get("item_id");
                    return !"topbar_slider".equals(itemId) && !"preview01".equals(itemId);
                }).collect(Collectors.toList());
                map.put(langauge, contentItems);
            }
        }
        return map;
    }

    /**
     * 
     * @param contentItemMap
     * @param pageLanguageVersions
     * @return
     */
    private static Long getTopbarSliderId(Map<Long, List<Map<String, Object>>> contentItemMap,
            Map<String, Map<String, Object>> pageLanguageVersions) {
        Map<String, Map<String, Object>> topbarSliderItems = getContentItemsOfItemId(pageLanguageVersions, contentItemMap, "topbar_slider");
        if (!topbarSliderItems.isEmpty()) {
            Map<String, Object> sliderItem = topbarSliderItems.values().iterator().next();
            return (Long) sliderItem.get("slider_id");
        }
        return null;
    }

    /**
     * 
     * @param pageLanguageVersions
     * @param contentItemMap
     * @param itemId
     * @return
     */
    private static Map<String, Map<String, Object>> getContentItemsOfItemId(
            Map<String, Map<String, Object>> pageLanguageVersions,
            Map<Long, List<Map<String, Object>>> contentItemMap,
            String itemId) {

        Map<Long, String> languageVersionIdsAndLanguages = pageLanguageVersions.entrySet()
                .stream()
                .collect(Collectors.toMap(e -> (Long) e.getValue().get("cms_page_language_version_id"), Entry::getKey));
        Map<String, Map<String, Object>> pageContentItems = new HashMap<>();
        for (Entry<Long, String> entry : languageVersionIdsAndLanguages.entrySet()) {
            String language = entry.getValue();
            List<Map<String, Object>> items = contentItemMap.get(entry.getKey());
            if (items != null) {
                Map<String, Object> item = items.stream().filter(map -> Objects.equals(map.get("item_id"), itemId)).findAny().orElse(null);
                if (item != null) {
                    pageContentItems.put(language, item);
                }
            }
        }
        return pageContentItems;
    }

    /**
     * 
     * @param pageLanguageVersions
     * @param field
     * @return
     */
    private static TranslatedText getTranslatedText(Map<String, Map<String, Object>> pageLanguageVersions, String field) {
        Map<String, String> titleValues = pageLanguageVersions.entrySet()
                .stream()
                .filter(e -> !e.getKey().equals("global"))
                .collect(Collectors.toMap(Entry::getKey, e -> (String) e.getValue().get(field)));
        return new TranslatedText(new MultiLanguageMetadataValue(titleValues), IPolyglott.getDefaultLocale());
    }

    /**
     * 
     * @param <K>
     * @param <V>
     * @param map1
     * @param map2
     * @return
     */
    private static <K, V> Map<K, V> combineMaps(Map<K, V> map1, Map<K, V> map2) {
        Map<K, V> union = new HashMap<>();
        union.putAll(map1);
        union.putAll(map2);
        return union;
    }

    /**
     * 
     * @param dao
     * @param tableName
     * @return
     * @throws DAOException
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getTableData(IDAO dao, String tableName) throws DAOException {
        List<Object[]> info = dao.getNativeQueryResults("SHOW COLUMNS FROM " + tableName);

        List<Object[]> rows = dao.getNativeQueryResults("SELECT * FROM " + tableName);

        List<String> columnNames = info.stream().map(o -> (String) o[0]).collect(Collectors.toList());

        List<Map<String, Object>> table = new ArrayList<>();

        for (Object[] row : rows) {
            Map<String, Object> columns = IntStream.range(0, columnNames.size())
                    .boxed()
                    .filter(i -> row[i] != null)
                    .collect(Collectors.toMap(columnNames::get, i -> row[i]));
            table.add(columns);
        }
        return table;
    }
}
