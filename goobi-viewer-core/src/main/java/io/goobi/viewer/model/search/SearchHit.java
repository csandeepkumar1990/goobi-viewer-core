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
package io.goobi.viewer.model.search;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.jdom2.JDOMException;
import org.jsoup.Jsoup;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.intranda.metadata.multilanguage.IMetadataValue;
import de.intranda.metadata.multilanguage.MultiLanguageMetadataValue;
import io.goobi.viewer.controller.DataFileTools;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.StringTools;
import io.goobi.viewer.controller.TEITools;
import io.goobi.viewer.controller.imaging.ThumbnailHandler;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.exceptions.IndexUnreachableException;
import io.goobi.viewer.exceptions.PresentationException;
import io.goobi.viewer.exceptions.ViewerConfigurationException;
import io.goobi.viewer.managedbeans.CmsMediaBean;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.messages.ViewerResourceBundle;
import io.goobi.viewer.model.cms.media.CMSMediaHolder;
import io.goobi.viewer.model.cms.media.CMSMediaItem;
import io.goobi.viewer.model.cms.pages.CMSPage;
import io.goobi.viewer.model.cms.pages.content.CMSContent;
import io.goobi.viewer.model.cms.pages.content.PersistentCMSComponent;
import io.goobi.viewer.model.cms.pages.content.TranslatableCMSContent;
import io.goobi.viewer.model.metadata.Metadata;
import io.goobi.viewer.model.security.AccessConditionUtils;
import io.goobi.viewer.model.viewer.StringPair;
import io.goobi.viewer.model.viewer.StructElement;
import io.goobi.viewer.solr.SolrConstants;
import io.goobi.viewer.solr.SolrConstants.DocType;
import io.goobi.viewer.solr.SolrTools;

/**
 * Wrapper class for search hits. Contains the corresponding <code>BrowseElement</code>
 */
public class SearchHit implements Comparable<SearchHit> {

    public enum HitType {
        ACCESSDENIED,
        DOCSTRCT,
        PAGE,
        METADATA, // grouped metadata
        UGC, // user-generated content
        PERSON, // UGC/metadata person
        CORPORATION, // UGC/meadata corporation
        ADDRESS, // UGC address
        COMMENT, // UGC comment
        EVENT, // LIDO event
        GROUP, // convolute/series
        CMS; // CMS page type for search hits

        /**
         * 
         * @param name
         * @return
         * @should return all known types correctly
         * @should return null if name unknown
         */
        public static HitType getByName(String name) {
            if (name != null) {
                if ("OVERVIEWPAGE".equals(name)) {
                    return HitType.CMS;
                }
                for (HitType type : HitType.values()) {
                    if (type.name().equals(name)) {
                        return type;
                    }
                }
            }

            return null;
        }

        public String getLabel(Locale locale) {
            return ViewerResourceBundle.getTranslation(new StringBuilder("doctype_").append(name()).toString(), locale);
        }
    }

    private static final Logger logger = LogManager.getLogger(SearchHit.class);

    private static final String SEARCH_HIT_TYPE_PREFIX = "searchHitType_";

    private final HitType type;
    /** Translated label for the search hit type. */
    private final String translatedType;
    private final BrowseElement browseElement;
    @JsonIgnore
    private List<SolrDocument> childDocs;
    @JsonIgnore
    private final Map<String, SearchHit> ownerHits = new HashMap<>();
    @JsonIgnore
    private final Map<String, SolrDocument> ownerDocs = new HashMap<>();
    @JsonIgnore
    private final Set<String> ugcDocIddocs = new HashSet<>();
    @JsonIgnore
    private final Map<String, Set<String>> searchTerms;
    /** Docstruct metadata that matches the search terms. */
    private final List<StringPair> foundMetadata = new ArrayList<>();
    private final String url;
    @JsonIgnore
    private final Locale locale;
    private final List<SearchHit> children = new ArrayList<>();
    private final Map<HitType, Integer> hitTypeCounts = new EnumMap<>(HitType.class);
    /** Metadata for Excel export. */
    @JsonIgnore
    private final Map<String, String> exportMetadata = new HashMap<>();
    @JsonIgnore
    private int hitsPopulated = 0;
    @JsonIgnore
    private SolrDocument solrDoc = null;
    @JsonIgnore
    private int proximitySearchDistance = 0;

    /**
     * Package-private constructor. Use createSearchHit() from other classes.
     *
     * @param type
     * @param browseElement
     * @param doc
     * @param searchTerms
     * @param locale
     */
    SearchHit(HitType type, BrowseElement browseElement, SolrDocument doc, Map<String, Set<String>> searchTerms, Locale locale) {
        this.type = type;
        this.translatedType = type != null ? ViewerResourceBundle.getTranslation(SEARCH_HIT_TYPE_PREFIX + type.name(), locale) : null;
        this.browseElement = browseElement;
        this.searchTerms = searchTerms;
        this.locale = locale;
        if (browseElement != null) {
            // Add self to owner hits to avoid adding self to child hits
            this.ownerHits.put(Long.toString(browseElement.getIddoc()), this);
            this.ownerDocs.put(Long.toString(browseElement.getIddoc()), doc);
            if (searchTerms != null) {
                addLabelHighlighting();
            } else {
                String label = browseElement.getLabel(locale);
                // Escape HTML tags
                label = StringEscapeUtils.escapeHtml4(label);

                IMetadataValue labelShort = new MultiLanguageMetadataValue();
                labelShort.setValue(label, locale);
                browseElement.setLabelShort(labelShort);
            }
            this.url = browseElement.getUrl();
        } else {
            this.url = null;
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    /** {@inheritDoc} */
    @Override
    public int compareTo(SearchHit other) {
        return Integer.compare(this.getBrowseElement().getImageNo(), other.getBrowseElement().getImageNo());
    }

    /**
     * <p>
     * createSearchHit.
     * </p>
     *
     * @param doc a {@link org.apache.solr.common.SolrDocument} object.
     * @param ownerDoc a {@link org.apache.solr.common.SolrDocument} object.
     * @param ownerAlreadyHasMetadata
     * @param locale a {@link java.util.Locale} object.
     * @param fulltext Optional fulltext (page docs only).
     * @param searchTerms a {@link java.util.Map} object.
     * @param exportFields Optional fields for (Excel) export purposes.
     * @param sortFields
     * @param ignoreAdditionalFields a {@link java.util.Set} object.
     * @param translateAdditionalFields a {@link java.util.Set} object.
     * @param oneLineAdditionalFields
     * @param overrideType a {@link io.goobi.viewer.model.search.SearchHit.HitType} object.
     * @param proximitySearchDistance
     * @param thumbnailHandler
     * @return a {@link io.goobi.viewer.model.search.SearchHit} object.
     * @throws io.goobi.viewer.exceptions.PresentationException if any.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     * @throws io.goobi.viewer.exceptions.DAOException if any.
     * @throws io.goobi.viewer.exceptions.ViewerConfigurationException if any.
     * @should add export fields correctly
     */
    public static SearchHit createSearchHit(SolrDocument doc, SolrDocument ownerDoc, Set<String> ownerAlreadyHasMetadata,
            Locale locale, String fulltext, Map<String, Set<String>> searchTerms, List<String> exportFields,
            List<StringPair> sortFields, Set<String> ignoreAdditionalFields, Set<String> translateAdditionalFields,
            Set<String> oneLineAdditionalFields, HitType overrideType, int proximitySearchDistance, ThumbnailHandler thumbnailHandler)
            throws PresentationException, IndexUnreachableException {
        List<String> fulltextFragments =
                (fulltext == null || searchTerms == null) ? null : SearchHelper.truncateFulltext(searchTerms.get(SolrConstants.FULLTEXT), fulltext,
                        DataManager.getInstance().getConfiguration().getFulltextFragmentLength(), true, true, proximitySearchDistance);
        StructElement se = new StructElement(Long.valueOf((String) doc.getFieldValue(SolrConstants.IDDOC)), doc, ownerDoc);
        String docstructType = se.getDocStructType();
        if (DocType.METADATA.name().equals(se.getMetadataValue(SolrConstants.DOCTYPE))) {
            docstructType = DocType.METADATA.name();
        }

        Map<String, List<String>> searchedFields = new HashMap<>(se.getMetadataFields());
        searchedFields.put(SolrConstants.FULLTEXT, Collections.singletonList(fulltext));
        Map<String, Set<String>> foundSearchTerms = getActualSearchTerms(searchTerms, searchedFields);

        List<Metadata> metadataList = DataManager.getInstance().getConfiguration().getSearchHitMetadataForTemplate(docstructType);
        BrowseElement browseElement = new BrowseElement(se, metadataList, locale,
                (fulltextFragments != null && !fulltextFragments.isEmpty()) ? fulltextFragments.get(0) : null, foundSearchTerms,
                thumbnailHandler);
        // Add additional metadata fields that aren't configured for search hits but contain search term values
        browseElement.addAdditionalMetadataContainingSearchTerms(se, foundSearchTerms, ignoreAdditionalFields, translateAdditionalFields,
                oneLineAdditionalFields);
        // Add sorting fields (should be added after all other metadata to avoid duplicates)
        browseElement.addSortFieldsToMetadata(se, sortFields, ignoreAdditionalFields);

        // Determine hit type
        String docType = se.getMetadataValue(SolrConstants.DOCTYPE);
        if (docType == null) {
            docType = (String) doc.getFieldValue(SolrConstants.DOCTYPE);
        }
        // logger.trace("docType: {}", docType);
        HitType hitType = overrideType;
        if (hitType == null) {
            hitType = HitType.getByName(docType);
            if (DocType.METADATA.name().equals(docType)) {
                // For metadata hits use the metadata type for the hit type
                String metadataType = se.getMetadataValue(SolrConstants.METADATATYPE);
                if (StringUtils.isNotEmpty(metadataType)) {
                    hitType = HitType.getByName(metadataType);
                }
            } else if (DocType.UGC.name().equals(docType)) {
                // For user-generated content hits use the metadata type for the hit type
                String ugcType = se.getMetadataValue(SolrConstants.UGCTYPE);
                logger.trace("ugcType: {}", ugcType);
                if (StringUtils.isNotEmpty(ugcType)) {
                    hitType = HitType.getByName(ugcType);
                    logger.trace("hit type found: {}", hitType);
                }
            }
        }

        SearchHit hit = new SearchHit(hitType, browseElement, doc, searchTerms, locale);
        hit.populateFoundMetadata(doc, ownerAlreadyHasMetadata,
                ignoreAdditionalFields, translateAdditionalFields, oneLineAdditionalFields);
        hit.proximitySearchDistance = proximitySearchDistance;

        // Export fields for Excel export
        if (exportFields != null && !exportFields.isEmpty()) {
            for (String field : exportFields) {
                String value = se.getMetadataValue(field);
                if (value != null) {
                    hit.getExportMetadata().put(field, value);
                }
            }
        }
        return hit;
    }

    /**
     * replaces any terms with a fuzzy search token with the matching strings found in the valus of fields
     *
     * @param origTerms
     * @param fields
     * @return
     */
    private static Map<String, Set<String>> getActualSearchTerms(Map<String, Set<String>> origTerms, Map<String, List<String>> resultFields) {
        String foundValues = resultFields.values().stream().flatMap(Collection::stream).collect(Collectors.joining(" "));
        Map<String, Set<String>> newFieldTerms = new HashMap<>();
        if (origTerms == null) {
            return newFieldTerms;
        }
        for (Entry<String, Set<String>> entry : origTerms.entrySet()) {
            Set<String> newTerms = new HashSet<>();
            Set<String> terms = entry.getValue();
            for (String term : terms) {
                term = term.replaceAll("(^\\()|(\\)$)", "");
                term = StringTools.removeDiacriticalMarks(term);
                if (FuzzySearchTerm.isFuzzyTerm(term)) {
                    FuzzySearchTerm fuzzy = new FuzzySearchTerm(term);
                    Matcher m = Pattern.compile(FuzzySearchTerm.WORD_PATTERN).matcher(foundValues);
                    while (m.find()) {
                        String word = m.group();
                        if (fuzzy.matches(word)) {
                            newTerms.add(word);
                        }
                    }
                } else {
                    newTerms.add(term);
                }
            }
            newFieldTerms.put(entry.getKey(), newTerms);
        }
        return newFieldTerms;
    }

    /**
     * First truncate and unescape the label, then add highlighting (overrides BrowseElement.labelShort).
     *
     * @should modify label correctly from default
     * @should modify label correctly from title
     * @should do nothing if searchTerms null
     */
    void addLabelHighlighting() {
        if (searchTerms == null) {
            return;
        }

        IMetadataValue labelShort = new MultiLanguageMetadataValue();
        for (Locale loc : ViewerResourceBundle.getAllLocales()) {

            String label = browseElement.getLabel(loc);

            if (searchTerms.get(SolrConstants.DEFAULT) != null) {
                label = SearchHelper.applyHighlightingToPhrase(label, searchTerms.get(SolrConstants.DEFAULT));
            } else if (searchTerms.get("MD_TITLE") != null) {
                label = SearchHelper.applyHighlightingToPhrase(label, searchTerms.get("MD_TITLE"));
            }

            // Escape HTML tags
            label = StringEscapeUtils.escapeHtml4(label);

            // Then replace highlighting placeholders with HTML tags
            label = SearchHelper.replaceHighlightingPlaceholders(label);

            labelShort.setValue(label, loc);
        }

        browseElement.setLabelShort(labelShort);
    }

    /**
     * Creates child hit elements for each hit matching a CMS page text, if CMS page texts were also searched.
     *
     * @throws io.goobi.viewer.exceptions.DAOException if any.
     * @should do nothing if searchTerms do not contain key
     * @should do nothing if no cms pages for record found
     */
    public void addCMSPageChildren() throws DAOException {
        if (searchTerms == null || !searchTerms.containsKey(SolrConstants.CMS_TEXT_ALL)) {
            return;
        }

        List<CMSPage> cmsPages = DataManager.getInstance().getDao().getCMSPagesForRecord(browseElement.getPi(), null);
        if (cmsPages.isEmpty()) {
            return;
        }

        SortedMap<CMSPage, List<String>> hitPages = new TreeMap<>();
        // Collect relevant texts
        for (CMSPage page : cmsPages) {
            List<String> texts = new ArrayList<>();
            for (PersistentCMSComponent component : page.getPersistentComponents()) {
                for (CMSContent content : component.getContentItems()) {
                    if (content instanceof TranslatableCMSContent) {
                        TranslatableCMSContent trCont = (TranslatableCMSContent) content;
                        for (Locale loc : trCont.getText().getLocales()) {
                            texts.add(trCont.getText().getText(loc));
                        }
                    } else if (content instanceof CMSMediaHolder) {
                        CMSMediaItem media = ((CMSMediaHolder) content).getMediaItem();
                        if (media != null && media.isHasExportableText()) {
                            texts.add(CmsMediaBean.getMediaFileAsString(media));

                        }
                    }
                }
            }
            List<String> truncatedStrings = texts.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(s -> {
                        String value = Jsoup.parse(s).text();
                        String highlightedValue = SearchHelper.applyHighlightingToPhrase(value, searchTerms.get(SolrConstants.CMS_TEXT_ALL));
                        if (!highlightedValue.equals(value)) {
                            return SearchHelper.truncateFulltext(searchTerms.get(SolrConstants.CMS_TEXT_ALL), highlightedValue,
                                    DataManager.getInstance().getConfiguration().getFulltextFragmentLength(), false, true, proximitySearchDistance);
                        }
                        return new ArrayList<String>();
                    })
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            hitPages.put(page, truncatedStrings);
        }

        // Add hits (one for each page)
        if (!hitPages.isEmpty()) {
            for (Entry<CMSPage, List<String>> entry : hitPages.entrySet()) {
                int count = 0;
                SearchHit cmsPageHit = new SearchHit(HitType.CMS,
                        new BrowseElement(browseElement.getPi(), 1, ViewerResourceBundle.getTranslation(entry.getKey().getMenuTitle(), locale), null,
                                locale, null, entry.getKey().getRelativeUrlPath()),
                        null,
                        searchTerms, locale);
                children.add(cmsPageHit);
                for (String text : entry.getValue()) {
                    cmsPageHit.getChildren()
                            .add(new SearchHit(HitType.CMS,
                                    new BrowseElement(browseElement.getPi(), 1, entry.getKey().getMenuTitle(), text, locale, null,
                                            entry.getKey().getRelativeUrlPath()),
                                    null, searchTerms, locale));
                    count++;
                }
                hitTypeCounts.put(HitType.CMS, count);
                logger.trace("Added {} CMS page child hits", count);
            }
        }
    }

    /**
     * Creates a child hit element for TEI full-texts, with child hits of its own for each truncated fragment containing search terms.
     *
     * @param doc Solr page doc
     * @param language a {@link java.lang.String} object.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     * @throws io.goobi.viewer.exceptions.DAOException if any.
     * @throws io.goobi.viewer.exceptions.ViewerConfigurationException if any.
     * @should throw IllegalArgumentException if doc null
     * @should do nothing if searchTerms does not contain fulltext
     * @should do nothing if tei file name not found
     */
    public void addFulltextChild(SolrDocument doc, String language) throws IndexUnreachableException, DAOException, ViewerConfigurationException {
        if (doc == null) {
            throw new IllegalArgumentException("doc may not be null");
        }

        if (searchTerms == null) {
            return;
        }
        if (!searchTerms.containsKey(SolrConstants.FULLTEXT)) {
            return;
        }

        if (language == null) {
            language = "en";
        }

        // Check whether TEI is available at all
        String teiFilename = (String) doc.getFirstValue(SolrConstants.FILENAME_TEI + SolrConstants.MIDFIX_LANG + language.toUpperCase());
        if (StringUtils.isEmpty(teiFilename)) {
            teiFilename = (String) doc.getFirstValue(SolrConstants.FILENAME_TEI);
        }
        if (StringUtils.isEmpty(teiFilename)) {
            return;
        }

        try {
            String fulltext = null;
            if (BeanUtils.getRequest() != null
                    && AccessConditionUtils.checkAccess(BeanUtils.getRequest(), "text", browseElement.getPi(), teiFilename, false).isGranted()) {
                fulltext = DataFileTools.loadTei((String) doc.getFieldValue(SolrConstants.PI), language);
            }
            if (fulltext != null) {
                fulltext = TEITools.getTeiFulltext(fulltext);
                fulltext = Jsoup.parse(fulltext).text();
            }
            // logger.trace(fulltext);
            List<String> fulltextFragments = fulltext == null ? null : SearchHelper.truncateFulltext(searchTerms.get(SolrConstants.FULLTEXT),
                    fulltext, DataManager.getInstance().getConfiguration().getFulltextFragmentLength(), false, false, proximitySearchDistance);

            int count = 0;
            if (fulltextFragments != null && !fulltextFragments.isEmpty()) {
                SearchHit hit = new SearchHit(HitType.PAGE,
                        new BrowseElement(browseElement.getPi(), 1, ViewerResourceBundle.getTranslation("TEI", locale), null, locale, null, null),
                        doc,
                        searchTerms,
                        locale);
                for (String fragment : fulltextFragments) {
                    hit.getChildren()
                            .add(new SearchHit(HitType.PAGE, new BrowseElement(browseElement.getPi(), 1, "TEI", fragment, locale, null, null), doc,
                                    searchTerms, locale));
                    count++;
                }
                children.add(hit);
                // logger.trace("Added {} fragments", count);
                int oldCount = hit.getHitTypeCounts().get(HitType.PAGE) != null ? hit.getHitTypeCounts().get(HitType.PAGE) : 0;
                hitTypeCounts.put(HitType.PAGE, oldCount + count);
            }
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage());
        } catch (IOException | JDOMException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * <p>
     * populateChildren.
     * </p>
     *
     * @param number a int.
     * @param skip a int.
     * @param locale a {@link java.util.Locale} object.
     * @param request a {@link javax.servlet.http.HttpServletRequest} object.
     * @param thumbnailHandler
     * @throws io.goobi.viewer.exceptions.PresentationException if any.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     * @throws io.goobi.viewer.exceptions.DAOException if any.
     * @throws io.goobi.viewer.exceptions.ViewerConfigurationException if any.
     */
    public void populateChildren(int number, int skip, Locale locale, HttpServletRequest request, ThumbnailHandler thumbnailHandler)
            throws PresentationException, IndexUnreachableException, DAOException, ViewerConfigurationException {
        logger.trace("populateChildren START");

        // Create child hits
        String pi = browseElement.getPi();
        if (pi == null || childDocs == null) {
            logger.trace("Nothing to populate");
            return;
        }

        logger.trace("{} child hit(s) found for {}", childDocs.size(), pi);
        if (number + skip > childDocs.size()) {
            number = childDocs.size() - skip;
        }
        Set<String> ignoreFields = new HashSet<>(DataManager.getInstance().getConfiguration().getDisplayAdditionalMetadataIgnoreFields());
        Set<String> translateFields = new HashSet<>(DataManager.getInstance().getConfiguration().getDisplayAdditionalMetadataTranslateFields());
        Set<String> oneLineFields = new HashSet<>(DataManager.getInstance().getConfiguration().getDisplayAdditionalMetadataOnelineFields());
        for (int i = 0; i < number; ++i) {
            SolrDocument childDoc = childDocs.get(i + skip);
            String fulltext = null;
            DocType docType = DocType.getByName((String) childDoc.getFieldValue(SolrConstants.DOCTYPE));
            if (docType == null) {
                logger.warn("Document {} has no DOCTYPE field, cannot add to child search hits.", childDoc.getFieldValue(SolrConstants.IDDOC));
                continue;
            }
            // logger.trace("Found child doc: {}", docType);
            boolean acccessDeniedType = false;
            switch (docType) {
                case PAGE: //NOSONAR, no break on purpose to run through all cases
                    String altoFilename = (String) childDoc.getFirstValue(SolrConstants.FILENAME_ALTO);
                    String plaintextFilename = (String) childDoc.getFirstValue(SolrConstants.FILENAME_FULLTEXT);
                    try {
                        if (StringUtils.isNotBlank(plaintextFilename)) {
                            boolean access = AccessConditionUtils.checkAccess(request, "text", pi, plaintextFilename, false).isGranted();
                            if (access) {
                                fulltext = DataFileTools.loadFulltext(null, plaintextFilename, false, request);
                            } else {
                                acccessDeniedType = true;
                            }
                        } else if (StringUtils.isNotBlank(altoFilename)) {
                            boolean access = AccessConditionUtils.checkAccess(request, "text", pi, altoFilename, false).isGranted();
                            if (access) {
                                fulltext = DataFileTools.loadFulltext(altoFilename, null, false, request);
                            } else {
                                acccessDeniedType = true;
                            }
                        }
                    } catch (FileNotFoundException e) {
                        logger.error("{}: {}", e.getMessage(), StringUtils.isNotBlank(plaintextFilename) ? plaintextFilename : altoFilename);
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                    }

                    // Skip page hits without a proper full-text
                    if (StringUtils.isBlank(fulltext)) {
                        continue;
                    }
                case METADATA:
                case UGC:
                case EVENT: {
                    String ownerIddoc = (String) childDoc.getFieldValue(SolrConstants.IDDOC_OWNER);
                    SearchHit ownerHit = ownerHits.get(ownerIddoc);
                    boolean populateHit = false;
                    if (ownerHit == null) {
                        SolrDocument ownerDoc = DataManager.getInstance().getSearchIndex().getDocumentByIddoc(ownerIddoc);
                        if (ownerDoc != null) {
                            ownerHit = createSearchHit(ownerDoc, null, null, locale, fulltext, searchTerms, null, null, ignoreFields,
                                    translateFields, oneLineFields, null, proximitySearchDistance, thumbnailHandler);
                            children.add(ownerHit);
                            ownerHits.put(ownerIddoc, ownerHit);
                            ownerDocs.put(ownerIddoc, ownerDoc);
                            populateHit = true;
                            // logger.trace("owner doc found: {}", ownerDoc.getFieldValue("LOGID"));
                        }
                    }
                    if (ownerHit == null) {
                        logger.error("No document found for IDDOC {}", ownerIddoc);
                        continue;
                    }
                    // If the owner hit the is the main element, create an intermediary to avoid the child label being displayed twice
                    if (ownerHit.equals(this)) {
                        SearchHit newOwnerHit =
                                createSearchHit(ownerDocs.get(ownerIddoc), null, null, locale, fulltext, searchTerms,
                                        null, null, ignoreFields, translateFields, oneLineFields, null, proximitySearchDistance, thumbnailHandler);
                        ownerHit.getChildren().add(newOwnerHit);
                        ownerHit = newOwnerHit;
                        ownerHits.put(ownerIddoc, newOwnerHit);
                    }
                    // logger.trace("owner doc of {}: {}", childDoc.getFieldValue(SolrConstants.IDDOC), ownerHit.getBrowseElement().getIddoc());
                    {

                        SearchHit childHit =
                                createSearchHit(childDoc, ownerDocs.get(ownerIddoc),
                                        ownerHit.getBrowseElement().getExistingMetadataFields(), locale, fulltext, searchTerms, null,
                                        null, ignoreFields, translateFields, oneLineFields, acccessDeniedType ? HitType.ACCESSDENIED : null,
                                        proximitySearchDistance, thumbnailHandler);
                        // Skip grouped metadata child hits that have no additional (unique) metadata to display
                        if (DocType.METADATA.equals(docType) && childHit.getFoundMetadata().isEmpty()) {
                            // TODO This will result in an infinite loading animation if all child hits are skipped
                            continue;
                        }
                        if (!DocType.UGC.equals(docType)) {
                            // Add all found additional metadata to the owner doc (minus duplicates) so it can be displayed
                            for (StringPair metadata : childHit.getFoundMetadata()) {
                                // Found metadata lists will usually be very short, so it's ok to iterate through the list on every check
                                if (!ownerHit.getFoundMetadata().contains(metadata)) {
                                    ownerHit.getFoundMetadata().add(metadata);
                                }
                            }
                        }
                        ownerHit.getChildren().add(childHit);
                        populateHit = true;
                        if (populateHit) {
                            hitsPopulated++;
                        }
                    }
                }
                    break;
                case DOCSTRCT:
                    // Docstruct hits are immediate children of the main hit
                    String iddoc = (String) childDoc.getFieldValue(SolrConstants.IDDOC);
                    if (!ownerHits.containsKey(iddoc)) {
                        SearchHit childHit =
                                createSearchHit(childDoc, null, null, locale, fulltext, searchTerms, null, null, ignoreFields,
                                        translateFields, oneLineFields, null, proximitySearchDistance, thumbnailHandler);
                        children.add(childHit);
                        ownerHits.put(iddoc, childHit);
                        ownerDocs.put(iddoc, childDoc);
                        hitsPopulated++;
                    }
                    break;
                case GROUP:
                default:
                    break;
            }
        }

        //            childDocs = childDocs.subList(number, childDocs.size());
        if (childDocs.isEmpty()) {
            ownerDocs.clear();
            ownerHits.clear();
        }
        logger.trace("Remaning child docs: {}", childDocs.size());
    }

    /**
     * <p>
     * populateFoundMetadata.
     * </p>
     *
     * @param doc a {@link org.apache.solr.common.SolrDocument} object.
     * @param ownerAlreadyHasFields List of metadata field+value combos that the owner already has
     * @param ignoreFields Fields to be skipped
     * @param translateFields Fields to be translated
     * @param oneLineFields
     * @should add field values pairs that match search terms
     * @should add MD fields that contain terms from DEFAULT
     * @should not add duplicate values
     * @should not add ignored fields
     * @should not add field values that equal the label
     * @should translate configured field values correctly
     * @should write one line fields into a single string
     */
    public void populateFoundMetadata(SolrDocument doc, Set<String> ownerAlreadyHasFields, Set<String> ignoreFields, Set<String> translateFields,
            Set<String> oneLineFields) {
        // logger.trace("populateFoundMetadata: {}", searchTerms);
        if (searchTerms == null) {
            return;
        }

        for (Entry<String, Set<String>> entry : searchTerms.entrySet()) {
            // Skip fields that are in the ignore list
            if (ignoreFields != null && ignoreFields.contains(entry.getKey())) {
                continue;
            }
            switch (entry.getKey()) {
                case SolrConstants.DEFAULT:
                case SolrConstants.NORMDATATERMS:
                    // If searching in DEFAULT, add all fields that contain any of the terms (instead of DEFAULT)
                    for (String docFieldName : doc.getFieldNames()) {
                        if (!(docFieldName.startsWith("MD_") || docFieldName.startsWith("NORM_"))
                                || docFieldName.endsWith(SolrConstants.SUFFIX_UNTOKENIZED)) {
                            continue;
                        }
                        if (ignoreFields != null && ignoreFields.contains(docFieldName)) {
                            continue;
                        }
                        // Prevent showing child hit metadata that's already displayed on the parent hit
                        if (ownerAlreadyHasFields != null) {
                            switch (browseElement.getDocType()) {
                                case METADATA:
                                    if (ownerAlreadyHasFields.contains(doc.getFieldValue(SolrConstants.LABEL))) {
                                        logger.trace("child hit metadata field {} already exists", browseElement.getLabel());
                                        continue;
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }

                        List<String> fieldValues = SolrTools.getMetadataValues(doc, docFieldName);
                        if (oneLineFields != null && oneLineFields.contains(docFieldName)) {
                            // All values into a single field value
                            StringBuilder sb = new StringBuilder();
                            for (String fieldValue : fieldValues) {
                                // Skip values that are equal to the hit label
                                if (fieldValue.equals(browseElement.getLabel())) {
                                    continue;
                                }
                                String highlightedValue = SearchHelper.applyHighlightingToPhrase(fieldValue, entry.getValue());
                                if (!highlightedValue.equals(fieldValue)) {
                                    // Translate values for certain fields, keeping the highlighting
                                    if (translateFields != null && (translateFields.contains(entry.getKey())
                                            || translateFields.contains(SearchHelper.adaptField(entry.getKey(), null)))) {
                                        String translatedValue = ViewerResourceBundle.getTranslation(fieldValue, locale);
                                        highlightedValue = highlightedValue.replaceAll("(\\W)(" + Pattern.quote(fieldValue) + ")(\\W)",
                                                "$1" + translatedValue + "$3");
                                    }
                                    highlightedValue = SearchHelper.replaceHighlightingPlaceholders(highlightedValue);
                                    // Only add one instance of NORM_ALTNAME (as there can be dozens)
                                    if ("NORM_ALTNAME".equals(docFieldName)) {
                                        break;
                                    }
                                    if (sb.length() > 0) {
                                        sb.append(", ");
                                    }
                                    sb.append(highlightedValue);
                                }
                            }
                            if (sb.length() > 0) {
                                foundMetadata.add(new StringPair(ViewerResourceBundle.getTranslation(docFieldName, locale), sb.toString()));
                                // logger.trace("found metadata: {}:{}", docFieldName, fieldValue);
                            }
                        } else {
                            for (String fieldValue : fieldValues) {
                                // Skip values that are equal to the hit label
                                if (fieldValue.equals(browseElement.getLabel())) {
                                    continue;
                                }
                                String highlightedValue = SearchHelper.applyHighlightingToPhrase(fieldValue, entry.getValue());
                                if (!highlightedValue.equals(fieldValue)) {
                                    // Translate values for certain fields, keeping the highlighting
                                    if (translateFields != null && (translateFields.contains(entry.getKey())
                                            || translateFields.contains(SearchHelper.adaptField(entry.getKey(), null)))) {
                                        String translatedValue = ViewerResourceBundle.getTranslation(fieldValue, locale);
                                        highlightedValue = highlightedValue.replaceAll("(\\W)(" + Pattern.quote(fieldValue) + ")(\\W)",
                                                "$1" + translatedValue + "$3");
                                    }
                                    highlightedValue = SearchHelper.replaceHighlightingPlaceholders(highlightedValue);
                                    foundMetadata.add(new StringPair(ViewerResourceBundle.getTranslation(docFieldName, locale), highlightedValue));
                                    // Only add one instance of NORM_ALTNAME (as there can be dozens)
                                    if ("NORM_ALTNAME".equals(docFieldName)) {
                                        break;
                                    }
                                    // logger.trace("found metadata: {}:{}", docFieldName, fieldValue);
                                }
                            }
                        }
                    }
                    break;
                default:
                    // Look up the exact field name in he Solr doc and add its values that contain any of the terms for that field
                    if (doc.containsKey(entry.getKey())) {
                        List<String> fieldValues = SolrTools.getMetadataValues(doc, entry.getKey());

                        if (oneLineFields != null && oneLineFields.contains(entry.getKey())) {
                            // All values into a single field value
                            StringBuilder sb = new StringBuilder();
                            for (String fieldValue : fieldValues) {
                                // Skip values that are equal to the hit label
                                if (fieldValue.equals(browseElement.getLabel())) {
                                    continue;
                                }
                                // Prevent showing child hit metadata that's already displayed on the parent hit
                                if (ownerAlreadyHasFields != null) {
                                    switch (browseElement.getDocType()) {
                                        case METADATA:
                                            if (ownerAlreadyHasFields.contains(doc.getFieldValue(SolrConstants.LABEL))) {
                                                logger.trace("child hit metadata field {} already exists", browseElement.getLabel());
                                                continue;
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                }

                                String highlightedValue = SearchHelper.applyHighlightingToPhrase(fieldValue, entry.getValue());
                                if (!highlightedValue.equals(fieldValue)) {
                                    // Translate values for certain fields, keeping the highlighting
                                    if (translateFields != null && (translateFields.contains(entry.getKey())
                                            || translateFields.contains(SearchHelper.adaptField(entry.getKey(), null)))) {
                                        String translatedValue = ViewerResourceBundle.getTranslation(fieldValue, locale);
                                        highlightedValue = highlightedValue.replaceAll("(\\W)(" + Pattern.quote(fieldValue) + ")(\\W)",
                                                "$1" + translatedValue + "$3");
                                    }
                                    highlightedValue = SearchHelper.replaceHighlightingPlaceholders(highlightedValue);
                                    if (sb.length() > 0) {
                                        sb.append(", ");
                                    }
                                    sb.append(highlightedValue);
                                }
                            }
                            if (sb.length() > 0) {
                                foundMetadata.add(new StringPair(ViewerResourceBundle.getTranslation(entry.getKey(), locale), sb.toString()));
                                // logger.trace("found metadata: {}:{}", docFieldName, fieldValue);
                            }

                        } else {
                            for (String fieldValue : fieldValues) {
                                // Skip values that are equal to the hit label
                                if (fieldValue.equals(browseElement.getLabel())) {
                                    continue;
                                }
                                // Prevent showing child hit metadata that's already displayed on the parent hit
                                if (ownerAlreadyHasFields != null) {
                                    switch (browseElement.getDocType()) {
                                        case METADATA:
                                            if (ownerAlreadyHasFields.contains(doc.getFieldValue(SolrConstants.LABEL))) {
                                                logger.trace("child hit metadata field {} already exists", browseElement.getLabel());
                                                continue;
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                }

                                String highlightedValue = SearchHelper.applyHighlightingToPhrase(fieldValue, entry.getValue());
                                if (!highlightedValue.equals(fieldValue)) {
                                    // Translate values for certain fields, keeping the highlighting
                                    if (translateFields != null && (translateFields.contains(entry.getKey())
                                            || translateFields.contains(SearchHelper.adaptField(entry.getKey(), null)))) {
                                        String translatedValue = ViewerResourceBundle.getTranslation(fieldValue, locale);
                                        highlightedValue = highlightedValue.replaceAll("(\\W)(" + Pattern.quote(fieldValue) + ")(\\W)",
                                                "$1" + translatedValue + "$3");
                                    }
                                    highlightedValue = SearchHelper.replaceHighlightingPlaceholders(highlightedValue);
                                    foundMetadata.add(new StringPair(ViewerResourceBundle.getTranslation(entry.getKey(), locale), highlightedValue));
                                }
                            }
                        }
                    }
                    break;
            }

        }
    }

    /**
     * <p>
     * Getter for the field <code>type</code>.
     * </p>
     *
     * @return the type
     */
    public HitType getType() {
        return type;
    }

    /**
     * <p>
     * Getter for the field <code>translatedType</code>.
     * </p>
     *
     * @return the translatedType
     */
    public String getTranslatedType() {
        return translatedType;
    }

    /**
     * <p>
     * Getter for the field <code>browseElement</code>.
     * </p>
     *
     * @return the browseElement
     */
    public BrowseElement getBrowseElement() {
        return browseElement;
    }

    /**
     * <p>
     * Getter for the field <code>childDocs</code>.
     * </p>
     *
     * @return the childDocs
     */
    public List<SolrDocument> getChildDocs() {
        return childDocs;
    }

    /**
     * <p>
     * Getter for the field <code>hitsPopulated</code>.
     * </p>
     *
     * @return the hitsPopulated
     */
    public int getHitsPopulated() {
        return hitsPopulated;
    };

    /**
     * <p>
     * Setter for the field <code>childDocs</code>.
     * </p>
     *
     * @param childDocs the childDocs to set
     */
    public void setChildDocs(SolrDocumentList childDocs) {
        this.childDocs = childDocs;
    }

    /**
     * Returns true if this hit has populated child elements.
     *
     * @return a boolean.
     */
    public boolean isHasChildren() {
        return children != null && !children.isEmpty();
    }

    /**
     * Returns true if this hit has any unpopulated child hits left.
     *
     * @return a boolean.
     */
    public boolean isHasMoreChildren() {
        return childDocs != null && !childDocs.isEmpty() && getHitsPopulated() < childDocs.size();
    }

    /**
     * <p>
     * Getter for the field <code>ugcDocIddocs</code>.
     * </p>
     *
     * @return the ugcDocIddocs
     */
    public Set<String> getUgcDocIddocs() {
        return ugcDocIddocs;
    }

    /**
     * <p>
     * Getter for the field <code>children</code>.
     * </p>
     *
     * @return the children
     */
    public List<SearchHit> getChildren() {
        return children;
    }

    /**
     * <p>
     * Getter for the field <code>hitTypeCounts</code>.
     * </p>
     *
     * @return the hitTypeCounts
     */
    public Map<HitType, Integer> getHitTypeCounts() {
        return hitTypeCounts;
    }

    /**
     * <p>
     * isHasHitCount.
     * </p>
     *
     * @return a boolean.
     */
    public boolean isHasHitCount() {
        for (Entry<HitType, Integer> entry : hitTypeCounts.entrySet()) {
            if (entry.getValue() > 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * <p>
     * getCmsPageHitCount.
     * </p>
     *
     * @return a int.
     */
    public int getCmsPageHitCount() {
        if (hitTypeCounts.get(HitType.CMS) != null) {
            return hitTypeCounts.get(HitType.CMS);
        }

        return 0;
    }

    /**
     * <p>
     * getDocstructHitCount.
     * </p>
     *
     * @return a int.
     */
    public int getDocstructHitCount() {
        if (hitTypeCounts.get(HitType.DOCSTRCT) != null) {
            return hitTypeCounts.get(HitType.DOCSTRCT);
        }

        return 0;
    }

    /**
     * <p>
     * getPageHitCount.
     * </p>
     *
     * @return a int.
     */
    public int getPageHitCount() {
        if (hitTypeCounts.get(HitType.PAGE) != null) {
            return hitTypeCounts.get(HitType.PAGE);
        }

        return 0;
    }

    /**
     * <p>
     * getMetadataHitCount.
     * </p>
     *
     * @return a int.
     */
    public int getMetadataHitCount() {
        if (hitTypeCounts.get(HitType.METADATA) != null) {
            return hitTypeCounts.get(HitType.METADATA);
        }

        return 0;
    }

    /**
     * <p>
     * getEventHitCount.
     * </p>
     *
     * @return a int.
     */
    public int getEventHitCount() {
        if (hitTypeCounts.get(HitType.EVENT) != null) {
            return hitTypeCounts.get(HitType.EVENT);
        }

        return 0;
    }

    /**
     * <p>
     * getUgcHitCount.
     * </p>
     *
     * @return a int.
     */
    public int getUgcHitCount() {
        if (hitTypeCounts.get(HitType.UGC) != null) {
            return hitTypeCounts.get(HitType.UGC);
        }

        return 0;
    }

    /**
     * <p>
     * Getter for the field <code>foundMetadata</code>.
     * </p>
     *
     * @return the foundMetadata
     */
    public List<StringPair> getFoundMetadata() {
        return foundMetadata;
    }

    /**
     * <p>
     * Getter for the field <code>url</code>.
     * </p>
     *
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * <p>
     * Getter for the field <code>exportMetadata</code>.
     * </p>
     *
     * @return the exportMetadata
     */
    public Map<String, String> getExportMetadata() {
        return exportMetadata;
    }

    /**
     * Generates HTML fragment for this search hit for notification mails.
     *
     * @param count a int.
     * @return a {@link java.lang.String} object.
     * @should generate fragment correctly
     */
    public String generateNotificationFragment(int count) {
        return "<tr><td>" + count + ".</td><td><img src=\"" + browseElement.getThumbnailUrl() + "\" alt=\"" + browseElement.getLabel()
                + "\" /></td><td>" + browseElement.getLabel()
                + "</td></tr>";
    }

    /**
     * @param doc
     */
    public void setSolrDoc(SolrDocument doc) {
        this.solrDoc = doc;
    }

    public SolrDocument getSolrDoc() {
        return this.solrDoc;
    }
    
    public String getCssClass() {
        String docStructType = this.getBrowseElement().getDocStructType();
        if(StringUtils.isNotBlank(docStructType)) {
            return "docstructtype__" + docStructType;
        } else {
            return "";
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getBrowseElement().getLabelShort();
    }
}
