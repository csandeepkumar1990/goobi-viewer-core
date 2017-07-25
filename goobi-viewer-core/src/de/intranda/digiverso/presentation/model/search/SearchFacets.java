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
package de.intranda.digiverso.presentation.model.search;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.intranda.digiverso.presentation.controller.DataManager;
import de.intranda.digiverso.presentation.controller.SolrConstants;
import de.intranda.digiverso.presentation.managedbeans.SearchBean;
import de.intranda.digiverso.presentation.managedbeans.utils.BeanUtils;

/**
 * Current faceting settings for a search.
 */
public class SearchFacets {

    private static final Logger logger = LoggerFactory.getLogger(SearchFacets.class);

    /** Available regular facets for the current search result. */
    private final Map<String, List<FacetItem>> availableFacets = new LinkedHashMap<>();
    /** Available hierarchical facets for the current search result . */
    private final Map<String, List<FacetItem>> availableHierarchicalFacets = new LinkedHashMap<>();
    /** Currently applied facets. */
    private final List<FacetItem> currentFacets = new ArrayList<>();
    /** List representation of all active collection facets. */
    protected final List<FacetItem> currentHierarchicalFacets = new ArrayList<>();
    private final Map<String, Boolean> drillDownExpanded = new HashMap<>();

    
    public void reset() {
        availableFacets.clear();
        availableHierarchicalFacets.clear();
        drillDownExpanded.clear();
    }
    
    /**
     * Returns a list of FacetItem objects in <code>currentFacets</code> where the field name matches the given field name.
     *
     * @param field The field name to match.
     * @return
     */
    public List<FacetItem> getCurrentFacetsForField(String field) {
        List<FacetItem> ret = new ArrayList<>();

        for (FacetItem facet : currentFacets) {
            if (facet.getField().equals(field)) {
                ret.add(facet);
            }
        }

        return ret;
    }

    /**
     * Returns a list of FacetItem objects in <code>currentFacets</code> where the field name matches the given field name.
     *
     * @param field The field name to match.
     * @return
     */
    public List<FacetItem> getCurrentHierarchicalFacetsForField(String field) {
        List<FacetItem> ret = new ArrayList<>();

        for (FacetItem facet : currentHierarchicalFacets) {
            if (facet.getField().equals(field)) {
                ret.add(facet);
            }
        }

        return ret;
    }

    /**
     * Checks whether the given facet is currently in use.
     *
     * @param facet The facet to check.
     * @return
     */
    public boolean isFacetCurrentlyUsed(FacetItem facet) {
        for (FacetItem fi : getCurrentFacetsForField(facet.getField())) {
            if (fi.getLink().equals(facet.getLink())) {
                return true;
            }
        }
        for (FacetItem fi : getCurrentHierarchicalFacetsForField(facet.getField())) {
            if (fi.getLink().equals(facet.getLink())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the size of the full element list of the facet for the given field.
     */
    public int getAvailableFacetsListSizeForField(String field) {
        if (availableFacets.get(field) != null) {
            return availableFacets.get(field).size();
        }
        if (availableHierarchicalFacets.get(field) != null) {
            return availableHierarchicalFacets.get(field).size();
        }

        return 0;
    }

    /**
     *
     * @return Size of <code>currentFacets</code>.
     */
    public int getCurrentFacetsSizeForField(String field) {
        return getCurrentFacetsForField(field).size();
    }

    /**
     *
     * @return Size of <code>currentFacets</code>.
     */
    public int getCurrentHierarchicalFacetsSizeForField(String field) {
        return getCurrentHierarchicalFacetsForField(field).size();
    }

    /**
     * Returns a collapsed sublist of the available facet elements for the given field.
     *
     * @param field
     * @return
     * @should return full DC facet list if expanded
     * @should return full DC facet list if list size less than default
     * @should return reduced DC facet list if list size larger than default
     * @should return full facet list if expanded
     * @should return full facet list if list size less than default
     * @should return reduced facet list if list size larger than default
     * @should not contain currently used facets
     */
    public List<FacetItem> getLimitedFacetListForField(String field) {
        List<FacetItem> facetItems = availableFacets.get(field);
        if (facetItems != null) {
            // Remove currently used facets
            facetItems.removeAll(currentFacets);
            if (!isDrillDownExpanded(field) && facetItems.size() > DataManager.getInstance().getConfiguration().getInitialDrillDownElementNumber(
                    field)) {
                return facetItems.subList(0, DataManager.getInstance().getConfiguration().getInitialDrillDownElementNumber(field));
            }
            // logger.trace("facet items {}: {}", field, facetItems.size());
            return facetItems;
        }

        return null;
    }

    /**
     * Returns a collapsed sublist of the available facet elements for the given field.
     *
     * @param field
     * @return
     * @should return full DC facet list if expanded
     * @should return full DC facet list if list size less than default
     * @should return reduced DC facet list if list size larger than default
     * @should return full facet list if expanded
     * @should return full facet list if list size less than default
     * @should return reduced facet list if list size larger than default
     * @should not contain currently used facets
     */
    public List<FacetItem> getLimitedHierarchicalFacetListForField(String field) {
        List<FacetItem> facetItems = availableHierarchicalFacets.get(field);
        if (facetItems != null) {
            // Remove currently used facets
            facetItems.removeAll(currentHierarchicalFacets);
            if (!isDrillDownExpanded(field) && facetItems.size() > DataManager.getInstance().getConfiguration().getInitialDrillDownElementNumber(
                    field)) {
                return facetItems.subList(0, DataManager.getInstance().getConfiguration().getInitialDrillDownElementNumber(field));
            }
            // logger.trace("facet items {}: {}", field, facetItems.size());
            return facetItems;
        }

        return null;
    }

    /**
     * If the drill-down for given field is expanded, return the size of the facet, otherwise the initial (collapsed) number of elements as
     * configured.
     *
     * @return
     * @should return full facet size if expanded
     * @should return default if collapsed
     */
    public int getDrillDownElementDisplayNumber(String field) {
        if (isDrillDownExpanded(field) && availableFacets.get(field) != null) {
            return availableFacets.get(field).size();
        }
        return DataManager.getInstance().getConfiguration().getInitialDrillDownElementNumber(field);
    }

    /**
     * Sets the expanded flag to <code>true</code> for the given drill-down field.
     *
     * @param field
     */
    public void expandDrillDown(String field) {
        logger.trace("expandDrillDown: {}", field);
        drillDownExpanded.put(field, true);
    }

    /**
     * Sets the expanded flag to <code>false</code> for the given drill-down field.
     *
     * @param field
     */
    public void collapseDrillDown(String field) {
        logger.trace("collapseDrillDown: {}", field);
        drillDownExpanded.put(field, false);
    }

    /**
     * Returns true if the "(more)" link is to be displayed for a drill-down box. This is the case if the facet has more elements than the initial
     * number of displayed elements and the facet hasn't been manually expanded yet.
     *
     * @param field
     * @return
     * @should return true if DC facet collapsed and has more elements than default
     * @should return true if facet collapsed and has more elements than default
     * @should return false if DC facet expanded
     * @should return false if facet expanded
     * @should return false if DC facet smaller than default
     * @should return false if facet smaller than default
     */
    public boolean isDisplayDrillDownExpandLink(String field) {
        List<FacetItem> facetItems = availableFacets.get(field);
        if (facetItems == null) {
            facetItems = availableHierarchicalFacets.get(field);
        }
        if (facetItems != null && !isDrillDownExpanded(field) && facetItems.size() > DataManager.getInstance().getConfiguration()
                .getInitialDrillDownElementNumber(field)) {
            return true;
        }

        return false;
    }

    /**
     *
     * @param field
     * @return
     */
    public boolean isDisplayDrillDownCollapseLink(String field) {
        return isDrillDownExpanded(field);
    }

    /**
     * Returns a URL encoded SSV string of facet fields and values from the elements in currentFacets (hyphen if empty).
     *
     * @return SSV string of facet queries or "-" if empty
     * @should contain queries from all FacetItems
     * @should return hyphen if currentFacets empty
     */
    public String getCurrentFacetString() {
        String ret = generateFacetPrefix(currentFacets, true);
        if (StringUtils.isEmpty(ret)) {
            ret = "-";
        }
        try {
            return URLEncoder.encode(ret, SearchBean.URL_ENCODING);
        } catch (UnsupportedEncodingException e) {
            return ret;
        }
    }

    /**
     * @return the currentCollection
     */
    public String getCurrentHierarchicalFacetString() {
        String ret = generateFacetPrefix(currentHierarchicalFacets, true);
        if (StringUtils.isEmpty(ret)) {
            ret = "-";
        }
        try {
            return URLEncoder.encode(ret, SearchBean.URL_ENCODING);
        } catch (UnsupportedEncodingException e) {
            return ret;
        }
    }

    /**
     * @return the currentCollection
     */
    public String getCurrentCollection() {
        String ret = generateFacetPrefix(currentHierarchicalFacets, true);
        if (StringUtils.isEmpty(ret)) {
            ret = "-";
        }

        return ret;
    }

    /**
     * Receives an SSV string of facet fields and values (FIELD1:value1;FIELD2:value2;FIELD3:value3) and generates new Elements for currentFacets.
     *
     * @param currentFacetString
     * @should create FacetItems from all links
     * @should decode slashes and backslashes
     */
    public void setCurrentFacetString(String currentFacetString) {
        logger.trace("setCurrentFacetString: {}", currentFacetString);
        parseFacetString(currentFacetString, currentFacets, false);
    }

    /**
     * Receives an SSV string of facet fields and values (FIELD1:value1;FIELD2:value2;FIELD3:value3) and generates new Elements for
     * currentHierarchicalFacets.
     *
     * @param currentFacetString
     */
    public void setCurrentHierarchicalFacetString(String currentHierarchicalFacetString) {
        logger.trace("setCurrentHierarchicalFacetString: {}", currentHierarchicalFacetString);
        parseFacetString(currentHierarchicalFacetString, currentHierarchicalFacets, true);
        // do not mirror the values into the advanced query items here
    }

    /**
     * @param currentCollection the currentCollection to set
     */
    @Deprecated
    public void setCurrentCollection(String currentCollection) {
        setCurrentHierarchicalFacetString(currentCollection);
    }

    /**
     * 
     * @param facetString
     * @param facetItems
     * @should fill list correctly
     * @should empty list before filling
     * @should add DC field prefix if no field name is given
     * @should set hierarchical status correctly
     */
    static void parseFacetString(String facetString, List<FacetItem> facetItems, boolean hiearchical) {
        if (facetItems == null) {
            facetItems = new ArrayList<>();
        } else {
            facetItems.clear();
        }
        if (StringUtils.isNotEmpty(facetString) && !"-".equals(facetString)) {
            try {
                facetString = URLDecoder.decode(facetString, "utf-8");
                facetString = BeanUtils.unescapeCriticalUrlChracters(facetString);
            } catch (UnsupportedEncodingException e) {
            }
            String[] facetStringSplit = facetString.split(";;");
            for (String facetLink : facetStringSplit) {
                if (StringUtils.isNotEmpty(facetLink)) {
                    if (!facetLink.contains(":")) {
                        facetLink = new StringBuilder(SolrConstants.DC).append(':').append(facetLink).toString();
                    }
                    facetItems.add(new FacetItem(facetLink, hiearchical));
                }
            }
        }
    }

    /**
     * 
     */
    public void resetCurrentFacetString() {
        logger.trace("resetCurrentFacetString");
        setCurrentFacetString("-");
    }

    /**
     * 
     */
    public void resetCurrentCollection() {
        logger.trace("resetCurrentCollection");
        setCurrentCollection("-");
    }

    /**
     * Returns a URL encoded value returned by generateFacetPrefix() for regular facets.
     *
     * @return
     */
    public String getCurrentFacetStringPrefix() {
        try {
            return URLEncoder.encode(generateFacetPrefix(currentFacets, true), SearchBean.URL_ENCODING);
        } catch (UnsupportedEncodingException e) {
            return generateFacetPrefix(currentFacets, true);
        }
    }

    /**
     * Returns a URL encoded value returned by generateFacetPrefix() for hierarchical facets.
     *
     * @return
     */
    public String getCurrentHierarchicalFacetPrefix() {
        try {
            return URLEncoder.encode(generateFacetPrefix(currentHierarchicalFacets, true), SearchBean.URL_ENCODING);
        } catch (UnsupportedEncodingException e) {
            return generateFacetPrefix(currentHierarchicalFacets, true);
        }
    }

    /**
     * Generates an SSV string of facet fields and values from the elements in the given List<FacetString> (empty string if empty).
     *
     * @param facetItems
     * @param escapeSlashes If true, slashes and backslashes are replaced with URL-compatible replacement strings
     * @return
     * @should encode slashed and backslashes
     */
    static String generateFacetPrefix(List<FacetItem> facetItems, boolean escapeSlashes) {
        if (facetItems == null) {
            throw new IllegalArgumentException("facetItems may not be null");
        }
        StringBuilder sb = new StringBuilder();
        for (FacetItem facetItem : facetItems) {
            if (escapeSlashes) {
                sb.append(BeanUtils.escapeCriticalUrlChracters(facetItem.getLink()));
            } else {
                sb.append(facetItem.getLink());
            }
            sb.append(";;");
        }

        return sb.toString();
    }

    /**
     * 
     * @param facetQuery
     * @return
     * @should remove facet correctly
     */
    public String removeHierarchicalFacetAction(String facetQuery, String ret) {
        // facetQuery = facetQuery.replace("/", SLASH_REPLACEMENT).replace("\\", BACKSLASH_REPLACEMENT);
        logger.trace("removeHierarchicalFacetAction: {}", facetQuery);
        String currentCollection = generateFacetPrefix(currentHierarchicalFacets, false);
        logger.trace("currentCollection: {}", currentCollection);
        if (currentCollection.contains(facetQuery)) {
            currentCollection = currentCollection.replaceAll("(" + facetQuery + ")(?=;|(?=/))", "").replace(";;;;", ";;");
            setCurrentCollection(currentCollection);
        }

        return ret;
    }

    /**
     * 
     * @param facetQuery
     * @return
     * @should remove facet correctly
     */
    public String removeFacetAction(String facetQuery, String ret) {
        // facetQuery = facetQuery.replace("/", SLASH_REPLACEMENT).replace("\\", BACKSLASH_REPLACEMENT);
        logger.trace("removeFacetAction: {}", facetQuery);
        String currentFacetString = generateFacetPrefix(currentFacets, false);
        if (currentFacetString.contains(facetQuery)) {
            currentFacetString = currentFacetString.replaceAll("(" + facetQuery + ")(?=;|(?=/))", "").replace(";;;;", ";;");
            setCurrentFacetString(currentFacetString);
        }

        return ret;
    }

    /**
     * Returns true if the value for the given field type in <code>drillDownExpanded</code> has been previously set to true.
     *
     * @param field
     * @return
     * @should return false if value null
     * @should return true if value true
     */
    public boolean isDrillDownExpanded(String field) {
        return drillDownExpanded.get(field) != null && drillDownExpanded.get(field);
    }

    /**
     * 
     * @param field
     * @return
     */
    public boolean isDrillDownCollapsed(String field) {
        return !isDrillDownExpanded(field);
    }

    /**
     * @return the availableFacets
     */
    public Map<String, List<FacetItem>> getAvailableFacets() {
        return availableFacets;
    }

    /**
     * @return the availableHierarchicalFacets
     */
    public Map<String, List<FacetItem>> getAvailableHierarchicalFacets() {
        return availableHierarchicalFacets;
    }

    /**
     * @return the currentFacets
     */
    public List<FacetItem> getCurrentFacets() {
        return currentFacets;
    }

    /**
     * @return the currentHierarchicalFacets
     */
    public List<FacetItem> getCurrentHierarchicalFacets() {
        return currentHierarchicalFacets;
    }
}