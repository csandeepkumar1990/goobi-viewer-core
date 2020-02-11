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
package io.goobi.viewer.managedbeans;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.goobi.viewer.controller.AlphanumCollatorComparator;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.Helper;
import io.goobi.viewer.controller.SolrConstants;
import io.goobi.viewer.exceptions.IndexUnreachableException;
import io.goobi.viewer.exceptions.PresentationException;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.messages.Messages;
import io.goobi.viewer.model.search.SearchHelper;
import io.goobi.viewer.model.viewer.BrowseDcElement;
import io.goobi.viewer.model.viewer.BrowseTerm;
import io.goobi.viewer.model.viewer.BrowseTerm.BrowseTermRawComparator;
import io.goobi.viewer.model.viewer.BrowsingMenuFieldConfig;
import io.goobi.viewer.model.viewer.CollectionView;
import io.goobi.viewer.model.viewer.CollectionView.BrowseDataProvider;

/**
 * This bean provides the data for collection and term browsing.
 */
@Named
@SessionScoped
public class BrowseBean implements Serializable {

    private static final long serialVersionUID = 7613678633319477862L;

    private static final Logger logger = LoggerFactory.getLogger(BrowseBean.class);

    @Inject
    private BreadcrumbBean breadcrumbBean;
    @Inject
    private SearchBean searchBean;

    /** Hits per page in the browsing menu. */
    private int browsingMenuHitsPerPage = DataManager.getInstance().getConfiguration().getBrowsingMenuHitsPerPage();

    /** Pretty URL variable. */
    private String collectionToExpand = null;
    private String topVisibleCollection = null;
    private String targetCollection = null;

    /** Solr field to browse. */
    private String browsingMenuField = null;
    /** Term list for the current result page (browsing menu). Used for displaying. */
    private List<String> browseTermList;
    /** Escaped term list for the current result page (browsing menu). Used for URL construction. */
    private List<String> browseTermListEscaped;
    private List<Long> browseTermHitCountList;
    private Map<String, List<String>> availableStringFilters = new HashMap<>();
    /** This is used for filtering term browsing by the starting letter. */
    private String currentStringFilter = "";
    /** Optional filter query */
    private String filterQuery;
    private int hitsCount = 0;
    private int currentPage = -1;

    private Map<String, CollectionView> collections = new HashMap<>();
    private String collectionField;

    /**
     * Empty constructor.
     */
    public BrowseBean() {
        // the emptiness inside
    }

    /**
     * Required setter for ManagedProperty injection
     *
     * @param breadcrumbBean the breadcrumbBean to set
     */
    public void setBreadcrumbBean(BreadcrumbBean breadcrumbBean) {
        this.breadcrumbBean = breadcrumbBean;
    }

    /**
     * Required setter for ManagedProperty injection
     *
     * @param searchBean the searchBean to set
     */
    public void setSearchBean(SearchBean searchBean) {
        this.searchBean = searchBean;
    }

    /**
     * Resets all lists for term browsing.
     */
    public void resetTerms() {
        if (browseTermList != null) {
            browseTermList.clear();
        }
        if (browseTermListEscaped != null) {
            browseTermListEscaped.clear();
        }
        if (browseTermHitCountList != null) {
            browseTermHitCountList.clear();
        }
        if (availableStringFilters != null) {
            availableStringFilters.clear();
        }
    }

    /**
     * <p>
     * resetAllLists.
     * </p>
     */
    public void resetAllLists() {
        for (String field : collections.keySet()) {
            collections.get(field).resetCollectionList();
        }
    }

    /**
     * <p>
     * resetDcList.
     * </p>
     */
    public void resetDcList() {
        logger.trace("resetDcList");
        resetList(SolrConstants.DC);
    }

    /**
     * <p>
     * resetList.
     * </p>
     *
     * @param field a {@link java.lang.String} object.
     */
    public void resetList(String field) {
        if (field == null) {
            throw new IllegalArgumentException("field may not be null");
        }
        if (collections.get(field) != null) {
            collections.get(field).resetCollectionList();
        }
    }

    /**
     * <p>
     * getDcList.
     * </p>
     *
     * @return the dcList (Collections)
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     */
    public List<BrowseDcElement> getDcList() throws IndexUnreachableException {
        return getList(SolrConstants.DC);
    }

    /**
     * <p>
     * getList.
     * </p>
     *
     * @param field a {@link java.lang.String} object.
     * @return a {@link java.util.List} object.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     */
    public List<BrowseDcElement> getList(String field) throws IndexUnreachableException {
        return getList(field, -1);
    }

    /**
     * <p>
     * getList.
     * </p>
     *
     * @param field a {@link java.lang.String} object.
     * @param depth a int.
     * @return a {@link java.util.List} object.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     */
    public List<BrowseDcElement> getList(String field, int depth) throws IndexUnreachableException {
        if (collections.get(field) == null) {
            initializeCollection(field, field);
            populateCollection(field);
        }
        if (collections.get(field) != null) {
            CollectionView collection = collections.get(field);
            collection.expandAll(depth);
            collection.calculateVisibleDcElements();
            return new ArrayList<>(collection.getVisibleDcElements());
        }

        return Collections.emptyList();
    }

    /**
     * <p>
     * populateCollection.
     * </p>
     *
     * @param field a {@link java.lang.String} object.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     */
    public void populateCollection(String field) throws IndexUnreachableException {
        if (collections.containsKey(field)) {
            collections.get(field).populateCollectionList();
        }
    }

    /**
     * <p>
     * getVisibleDcList.
     * </p>
     *
     * @return a {@link java.util.List} object.
     */
    @Deprecated
    public List<BrowseDcElement> getVisibleDcList() {
        logger.debug("getVisibleDcList");
        if (!collections.containsKey(SolrConstants.DC)) {
            initializeDCCollection();
        }
        return new ArrayList<>(collections.get(SolrConstants.DC).getVisibleDcElements());
    }

    /**
     * Populates <code>visibledcList</code> with elements to be currently show in the UI. Prior to using this method, <code>dcList</code> must be
     * sorted and each <code>BrowseDcElement.hasSubElements</code> must be set correctly.
     *
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     */
    @Deprecated
    public void calculateVisibleDcElements() throws IndexUnreachableException {
        if (!collections.containsKey(SolrConstants.DC)) {
            initializeDCCollection();
        }
    }

    /**
     * <p>
     * Getter for the field <code>collectionToExpand</code>.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getCollectionToExpand() {
        return collectionToExpand;
    }

    /**
     * <p>
     * Setter for the field <code>collectionToExpand</code>.
     * </p>
     *
     * @param collectionToExpand a {@link java.lang.String} object.
     */
    public void setCollectionToExpand(String collectionToExpand) {
        synchronized (this) {
            this.collectionToExpand = collectionToExpand;
            this.topVisibleCollection = collectionToExpand;
        }
    }

    /**
     * <p>
     * Getter for the field <code>topVisibleCollection</code>.
     * </p>
     *
     * @return the topVisibleCollecion
     */
    public String getTopVisibleCollection() {
        if (topVisibleCollection == null && collectionToExpand != null) {
            return collectionToExpand;
        }
        return topVisibleCollection;
    }

    /**
     * <p>
     * Setter for the field <code>topVisibleCollection</code>.
     * </p>
     *
     * @param topVisibleCollecion the topVisibleCollecion to set
     */
    public void setTopVisibleCollection(String topVisibleCollecion) {
        this.topVisibleCollection = topVisibleCollecion;
    }

    /**
     * Use this method of a certain collections needs to be expanded via URL.
     *
     * @param levels a int.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     */
    public void expandCollection(int levels) throws IndexUnreachableException {
        expandCollection(SolrConstants.DC, SolrConstants.FACET_DC, levels);
    }

    /**
     * <p>
     * expandCollection.
     * </p>
     *
     * @param collectionField a {@link java.lang.String} object.
     * @param facetField a {@link java.lang.String} object.
     * @param levels a int.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     */
    public void expandCollection(String collectionField, String facetField, int levels) throws IndexUnreachableException {
        synchronized (this) {
            initializeCollection(collectionField, facetField);
            collections.get(collectionField).setBaseLevels(levels);
            collections.get(collectionField).setBaseElementName(getCollectionToExpand());
            collections.get(collectionField).setTopVisibleElement(getTopVisibleCollection());
            collections.get(collectionField).populateCollectionList();
        }
    }

    /**
     * <p>
     * searchTerms.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     * @throws io.goobi.viewer.exceptions.PresentationException if any.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     */
    public String searchTerms() throws PresentationException, IndexUnreachableException {
        synchronized (this) {
            logger.trace("searchTerms");
            if (breadcrumbBean != null) {
                breadcrumbBean.updateBreadcrumbsWithCurrentUrl("browseTitle", BreadcrumbBean.WEIGHT_SEARCH_TERMS);
            }
            if (searchBean != null) {
                searchBean.setSearchString("");
                searchBean.resetSearchParameters(true);
            }
            hitsCount = 0;

            List<BrowseTerm> terms = null;
            BrowsingMenuFieldConfig currentBmfc = null;
            List<BrowsingMenuFieldConfig> bmfcList = DataManager.getInstance().getConfiguration().getBrowsingMenuFields();
            for (BrowsingMenuFieldConfig bmfc : bmfcList) {
                if (bmfc.getField().equals(browsingMenuField)) {
                    currentBmfc = bmfc;
                    break;
                }
            }
            if (currentBmfc == null) {
                logger.error("No configuration found for term field '{}'.", browsingMenuField);
                resetTerms();
                Messages.error(Helper.getTranslation("browse_errFieldNotConfigured", null).replace("{0}", browsingMenuField));
                return "searchTermList";
            }
            if (StringUtils.isEmpty(currentStringFilter) || availableStringFilters.get(browsingMenuField) == null) {
                terms = SearchHelper.getFilteredTerms(currentBmfc, "", filterQuery, new BrowseTermRawComparator(),
                        DataManager.getInstance().getConfiguration().isAggregateHits());

                // Populate the list of available starting characters with ones that actually exist in the complete terms list
                if (availableStringFilters.get(browsingMenuField) == null || filterQuery != null) {
                    logger.debug("Populating search term filters for field '{}'...", browsingMenuField);
                    availableStringFilters.put(browsingMenuField, new ArrayList<String>());
                    for (BrowseTerm term : terms) {
                        String firstChar;
                        if (StringUtils.isNotEmpty(term.getSortTerm())) {
                            firstChar = term.getSortTerm().substring(0, 1).toUpperCase();
                        } else {
                            firstChar = term.getTerm().substring(0, 1).toUpperCase();
                        }
                        // logger.debug(term.getTerm() + ": " + firstChar);
                        if (!availableStringFilters.get(browsingMenuField).contains(firstChar) && !"-".equals(firstChar)) {
                            availableStringFilters.get(browsingMenuField).add(firstChar);
                        }
                    }
                }

                // Sort filters
                Locale locale = null;
                NavigationHelper navigationHelper = BeanUtils.getNavigationHelper();
                if (navigationHelper != null) {
                    locale = navigationHelper.getLocale();
                } else {
                    locale = Locale.GERMAN;
                }
                Collections.sort(availableStringFilters.get(browsingMenuField), new AlphanumCollatorComparator(Collator.getInstance(locale)));
                // logger.debug(availableStringFilters.toString());
            }

            // Get the terms again, this time using the requested filter. The search over all terms the first time is necessary to get the list of available filters.
            if (StringUtils.isNotEmpty(currentStringFilter)) {
                terms = SearchHelper.getFilteredTerms(currentBmfc, currentStringFilter, filterQuery, new BrowseTermRawComparator(),
                        DataManager.getInstance().getConfiguration().isAggregateHits());
            }
            hitsCount = terms.size();
            if (hitsCount > 0) {
                if (currentPage > getLastPage()) {
                    currentPage = getLastPage();
                }

                int start = (currentPage - 1) * browsingMenuHitsPerPage;
                int end = currentPage * browsingMenuHitsPerPage;
                if (end > terms.size()) {
                    end = terms.size();
                }

                browseTermList = new ArrayList<>(end - start);
                browseTermHitCountList = new ArrayList<>(browseTermList.size());
                for (int i = start; i < end; ++i) {
                    browseTermList.add(terms.get(i).getTerm().intern());
                    browseTermHitCountList.add(terms.get(i).getHitCount());
                }

                browseTermListEscaped = new ArrayList<>(browseTermList.size());
                // URL encode all terms
                for (String s : browseTermList) {
                    // Escape characters such as quotation marks
                    String term = ClientUtils.escapeQueryChars(s);
                    term = BeanUtils.escapeCriticalUrlChracters(term);
                    try {
                        term = URLEncoder.encode(term, SearchBean.URL_ENCODING);
                    } catch (UnsupportedEncodingException e) {
                        logger.error(e.getMessage());
                    }
                    browseTermListEscaped.add(term.intern());
                }
            } else {
                resetTerms();
            }

            return "searchTermList";
        }
    }

    /**
     * <p>
     * Getter for the field <code>browsingMenuField</code>.
     * </p>
     *
     * @return the browsingMenuField
     */
    public String getBrowsingMenuField() {
        if (StringUtils.isEmpty(browsingMenuField)) {
            return "-";
        }

        return browsingMenuField;
    }

    /**
     * <p>
     * Setter for the field <code>browsingMenuField</code>.
     * </p>
     *
     * @param browsingMenuField the browsingMenuField to set
     */
    public void setBrowsingMenuField(String browsingMenuField) {
        synchronized (this) {
            if ("-".equals(browsingMenuField)) {
                browsingMenuField = "";
            }
            try {
                this.browsingMenuField = URLDecoder.decode(browsingMenuField, SearchBean.URL_ENCODING);
            } catch (UnsupportedEncodingException e) {
                this.browsingMenuField = browsingMenuField;
            }
        }
    }

    /**
     * <p>
     * Getter for the field <code>browseTermList</code>.
     * </p>
     *
     * @return the browseTermList
     */
    public List<String> getBrowseTermList() {
        return browseTermList;
    }

    /**
     * <p>
     * Getter for the field <code>browseTermListEscaped</code>.
     * </p>
     *
     * @return the browseTermListEscaped
     */
    public List<String> getBrowseTermListEscaped() {
        return browseTermListEscaped;
    }

    /**
     * <p>
     * Getter for the field <code>browseTermHitCountList</code>.
     * </p>
     *
     * @return the browseTermHitCountList
     */
    public List<Long> getBrowseTermHitCountList() {
        return browseTermHitCountList;
    }

    /**
     * <p>
     * getPrevTermUrl.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getPrevTermUrl() {
        int page = 1;
        if (currentPage > 1) {
            page = currentPage - 1;
        }
        return new StringBuilder("/").append(browsingMenuField)
                .append('/')
                .append(getCurrentStringFilter())
                .append('/')
                .append(page)
                .append('/')
                .toString();
    }

    /**
     * <p>
     * getNextTermUrl.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getNextTermUrl() {
        int page = getLastPage();
        if (currentPage < page) {
            page = currentPage + 1;
        }
        return new StringBuilder("/").append(browsingMenuField)
                .append('/')
                .append(getCurrentStringFilter())
                .append('/')
                .append(page)
                .append('/')
                .toString();
    }

    /**
     * <p>
     * Getter for the field <code>availableStringFilters</code>.
     * </p>
     *
     * @return the availableStringFilters
     */
    public List<String> getAvailableStringFilters() {
        return availableStringFilters.get(browsingMenuField);
    }

    /**
     * <p>
     * Getter for the field <code>currentStringFilter</code>.
     * </p>
     *
     * @return the currentStringFilter
     */
    public String getCurrentStringFilter() {
        if (StringUtils.isEmpty(currentStringFilter)) {
            return "-";
        }
        return currentStringFilter;
    }

    /**
     * <p>
     * Setter for the field <code>currentStringFilter</code>.
     * </p>
     *
     * @param currentStringFilter the currentStringFilter to set
     */
    public void setCurrentStringFilter(String currentStringFilter) {
        synchronized (this) {
            if (StringUtils.equals(currentStringFilter, "-")) {
                currentStringFilter = "";
            }
            try {
                this.currentStringFilter = URLDecoder.decode(currentStringFilter, SearchBean.URL_ENCODING);
            } catch (UnsupportedEncodingException e) {
                this.currentStringFilter = currentStringFilter;
            }
        }
    }

    /**
     * <p>
     * Getter for the field <code>filterQuery</code>.
     * </p>
     *
     * @return the filterQuery
     */
    public String getFilterQuery() {
        if (StringUtils.isEmpty(filterQuery)) {
            return "-";
        }
        return filterQuery;
    }

    /**
     * <p>
     * Setter for the field <code>filterQuery</code>.
     * </p>
     *
     * @param filterQuery the filterQuery to set
     */
    public void setFilterQuery(String filterQuery) {
        this.filterQuery = "-".equals(filterQuery) ? null : filterQuery;
    }

    /**
     * <p>
     * getCurrentPageResetFilter.
     * </p>
     *
     * @return a int.
     */
    public int getCurrentPageResetFilter() {
        return currentPage;
    }

    /**
     * This is used when a term search query doesn't contain a filter value. In this case, the value is reset.
     *
     * @param currentPage a int.
     * @deprecated Reset the currentStringFilter value in the PrettyFaces mapping
     */
    @Deprecated
    public void setCurrentPageResetFilter(int currentPage) {
        synchronized (this) {
            currentStringFilter = null;
            this.currentPage = currentPage;
        }
    }

    /**
     * <p>
     * Getter for the field <code>currentPage</code>.
     * </p>
     *
     * @return the currentPage
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * <p>
     * Setter for the field <code>currentPage</code>.
     * </p>
     *
     * @param currentPage the currentPage to set
     */
    public void setCurrentPage(int currentPage) {
        synchronized (this) {
            this.currentPage = currentPage;
        }
    }

    /**
     * <p>
     * getLastPage.
     * </p>
     *
     * @return a int.
     */
    public int getLastPage() {
        int hitsPerPageLocal = browsingMenuHitsPerPage;
        int answer = new Double(Math.floor(hitsCount / hitsPerPageLocal)).intValue();
        if (hitsCount % hitsPerPageLocal != 0 || answer == 0) {
            answer++;
        }

        //        logger.trace(hitsCount + "/" + hitsPerPageLocal + "=" + answer);
        return answer;
    }

    /**
     * <p>
     * isBrowsingMenuEnabled.
     * </p>
     *
     * @return a boolean.
     */
    public boolean isBrowsingMenuEnabled() {
        return DataManager.getInstance().getConfiguration().isBrowsingMenuEnabled();
    }

    /**
     * <p>
     * getBrowsingMenuItems.
     * </p>
     *
     * @param language a {@link java.lang.String} object.
     * @return List of browsing menu items
     * @should skip items for language-specific fields if no language was given
     * @should skip items for language-specific fields if they don't match given language
     */
    public List<String> getBrowsingMenuItems(String language) {
        if (language != null) {
            language = language.toUpperCase();
        }
        List<String> ret = new ArrayList<>();
        for (BrowsingMenuFieldConfig bmfc : DataManager.getInstance().getConfiguration().getBrowsingMenuFields()) {
            if (bmfc.getField().contains(SolrConstants._LANG_) && (language == null || !bmfc.getField().contains(SolrConstants._LANG_ + language))) {
                logger.trace("Skipped {}", bmfc.getField());
                continue;
            }
            ret.add(bmfc.getField());
        }
        return ret;
    }

    /**
     * <p>
     * Getter for the field <code>targetCollection</code>.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getTargetCollection() {
        return targetCollection;
    }

    /**
     * <p>
     * Setter for the field <code>targetCollection</code>.
     * </p>
     *
     * @param targetCollection a {@link java.lang.String} object.
     */
    public void setTargetCollection(String targetCollection) {
        this.targetCollection = targetCollection;
    }

    /**
     * <p>
     * openWorkInTargetCollection.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     * @throws io.goobi.viewer.exceptions.PresentationException if any.
     */
    public String openWorkInTargetCollection() throws IndexUnreachableException, PresentationException {
        if (StringUtils.isBlank(getTargetCollection())) {
            return null;
        }
        String url = SearchHelper.getFirstWorkUrlWithFieldValue(SolrConstants.DC, getTargetCollection(), true, true,
                DataManager.getInstance().getConfiguration().getCollectionSplittingChar(SolrConstants.DC), BeanUtils.getLocale());
        url = url.replace("http://localhost:8082/viewer/", "");
        return "pretty:" + url;
    }

    /**
     * <p>
     * getDcCollection.
     * </p>
     *
     * @return a {@link io.goobi.viewer.model.viewer.CollectionView} object.
     */
    public CollectionView getDcCollection() {
        return getCollection(SolrConstants.DC);
    }

    /**
     * <p>
     * getCollection.
     * </p>
     *
     * @param field a {@link java.lang.String} object.
     * @return a {@link io.goobi.viewer.model.viewer.CollectionView} object.
     */
    public CollectionView getCollection(String field) {
        return collections.get(field);
    }

    /**
     * <p>
     * initializeDCCollection.
     * </p>
     */
    public void initializeDCCollection() {
        initializeCollection(SolrConstants.DC, SolrConstants.FACET_DC);
    }

    /**
     * Adds a CollectionView object for the given field to the map and populates its values.
     *
     * @param collectionField a {@link java.lang.String} object.
     * @param facetField a {@link java.lang.String} object.
     */
    public void initializeCollection(final String collectionField, final String facetField) {
        logger.trace("initializeCollection: {}", collectionField);
        collections.put(collectionField, new CollectionView(collectionField, new BrowseDataProvider() {

            @Override
            public Map<String, Long> getData() throws IndexUnreachableException {
                Map<String, Long> dcStrings = SearchHelper.findAllCollectionsFromField(collectionField, facetField, null, true, true,
                        DataManager.getInstance().getConfiguration().getCollectionSplittingChar(collectionField));
                return dcStrings;
            }
        }));
    }

    /**
     * <p>
     * Getter for the field <code>collectionField</code>.
     * </p>
     *
     * @return the collectionField
     */
    public String getCollectionField() {
        return collectionField;
    }

    /**
     * <p>
     * Setter for the field <code>collectionField</code>.
     * </p>
     *
     * @param collectionField the collectionField to set
     */
    public void setCollectionField(String collectionField) {
        this.collectionField = collectionField;
    }
}