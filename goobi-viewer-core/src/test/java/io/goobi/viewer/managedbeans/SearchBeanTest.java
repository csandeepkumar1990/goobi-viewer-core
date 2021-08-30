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

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.goobi.viewer.AbstractDatabaseAndSolrEnabledTest;
import io.goobi.viewer.controller.StringTools;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.model.search.AdvancedSearchFieldConfiguration;
import io.goobi.viewer.model.search.Search;
import io.goobi.viewer.model.search.SearchFacets;
import io.goobi.viewer.model.search.SearchHelper;
import io.goobi.viewer.model.search.SearchQueryGroup;
import io.goobi.viewer.model.search.SearchQueryGroup.SearchQueryGroupOperator;
import io.goobi.viewer.model.search.SearchQueryItem;
import io.goobi.viewer.model.search.SearchQueryItem.SearchItemOperator;
import io.goobi.viewer.solr.SolrConstants;

public class SearchBeanTest extends AbstractDatabaseAndSolrEnabledTest {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    /**
     * @see SearchBean#resetSimpleSearchParameters()
     * @verifies reset variables correctly
     */
    @Test
    public void resetSimpleSearchParameters_shouldResetVariablesCorrectly() throws Exception {
        SearchBean searchBean = new SearchBean();
        searchBean.setSearchString("test");
        Assert.assertEquals("test", searchBean.getSearchString());
        Assert.assertEquals("test", searchBean.getSearchStringForUrl());

        searchBean.resetSimpleSearchParameters();
        Assert.assertEquals("", searchBean.getSearchString());
        Assert.assertEquals("-", searchBean.getSearchStringForUrl());
    }

    /**
     * @see SearchBean#resetAdvancedSearchParameters()
     * @verifies reset variables correctly
     */
    @Test
    public void resetAdvancedSearchParameters_shouldResetVariablesCorrectly() throws Exception {
        SearchBean searchBean = new SearchBean();
        searchBean.setAdvancedSearchGroupOperator(1);
        Assert.assertEquals(1, searchBean.getAdvancedSearchGroupOperator());
        searchBean.getAdvancedQueryGroups().add(new SearchQueryGroup(Locale.GERMAN, 1));
        Assert.assertEquals(1, searchBean.getAdvancedQueryGroups().size());

        searchBean.resetAdvancedSearchParameters(2, 3);
        Assert.assertEquals(0, searchBean.getAdvancedSearchGroupOperator());
        Assert.assertEquals(2, searchBean.getAdvancedQueryGroups().size());
        Assert.assertEquals(3, searchBean.getAdvancedQueryGroups().get(0).getQueryItems().size());
    }

    /**
     * @see SearchBean#resetAdvancedSearchParameters()
     * @verifies re-select collection correctly
     */
    @Test
    public void resetAdvancedSearchParameters_shouldReselectCollectionCorrectly() throws Exception {
        SearchBean searchBean = new SearchBean();
        searchBean.getFacets().setCurrentFacetString("DC:col");

        searchBean.resetAdvancedSearchParameters(1, 1);
        Assert.assertEquals(1, searchBean.getAdvancedQueryGroups().size());
        SearchQueryGroup group = searchBean.getAdvancedQueryGroups().get(0);
        Assert.assertEquals(1, group.getQueryItems().size());
        SearchQueryItem item = group.getQueryItems().get(0);
        Assert.assertEquals(SolrConstants.DC, item.getField());
        Assert.assertEquals(SearchItemOperator.IS, item.getOperator());
        Assert.assertEquals("col", item.getValue());
    }

    /**
     * @see SearchBean#resetSearchAction()
     * @verifies return correct Pretty URL ID
     */
    @Test
    public void resetSearchAction_shouldReturnCorrectPrettyURLID() throws Exception {
        SearchBean searchBean = new SearchBean();

        searchBean.setActiveSearchType(0);
        Assert.assertEquals("pretty:search", searchBean.resetSearchAction());
        searchBean.setActiveSearchType(1);
        //        Assert.assertEquals("pretty:extendedsearch", searchBean.resetSearchAction());
        //        searchBean.setActiveSearchType(2);
        //        Assert.assertEquals("pretty:timelinesearch", searchBean.resetSearchAction());
        //        searchBean.setActiveSearchType(3);
        //        Assert.assertEquals("pretty:calendarsearch", searchBean.resetSearchAction());
    }

    /**
     * @see SearchBean#mirrorAdvancedSearchCurrentCollection()
     * @verifies set collection item correctly
     */
    @Test
    public void mirrorAdvancedSearchCurrentCollection_shouldSetCollectionItemCorrectly() throws Exception {
        SearchBean sb = new SearchBean();
        sb.resetAdvancedSearchParameters(2, 3);
        Assert.assertEquals(2, sb.getAdvancedQueryGroups().size());
        SearchQueryGroup group = sb.getAdvancedQueryGroups().get(0);
        Assert.assertEquals(3, group.getQueryItems().size());
        SearchQueryItem item = group.getQueryItems().get(0);
        Assert.assertNull(item.getField());

        sb.getFacets().setCurrentFacetString("DC:a");
        sb.mirrorAdvancedSearchCurrentHierarchicalFacets();
        Assert.assertEquals(SolrConstants.DC, item.getField());
        Assert.assertEquals("a", item.getValue());
    }

    /**
     * @see SearchBean#mirrorAdvancedSearchCurrentCollection()
     * @verifies reset collection item correctly
     */
    @Test
    public void mirrorAdvancedSearchCurrentCollection_shouldResetCollectionItemCorrectly() throws Exception {
        SearchBean sb = new SearchBean();
        sb.resetAdvancedSearchParameters(2, 3);
        Assert.assertEquals(2, sb.getAdvancedQueryGroups().size());
        SearchQueryGroup group = sb.getAdvancedQueryGroups().get(0);
        Assert.assertEquals(3, group.getQueryItems().size());
        SearchQueryItem item = group.getQueryItems().get(0);
        Assert.assertNull(item.getField());

        sb.getFacets().setCurrentFacetString("DC:a");
        sb.mirrorAdvancedSearchCurrentHierarchicalFacets();
        Assert.assertEquals(SolrConstants.DC, item.getField());
        Assert.assertEquals("a", item.getValue());
        sb.getFacets().setCurrentFacetString("-");
        sb.mirrorAdvancedSearchCurrentHierarchicalFacets();
        Assert.assertNull(item.getField());
        Assert.assertNull(item.getValue());
    }

    /**
     * @see SearchBean#mirrorAdvancedSearchCurrentHierarchicalFacets()
     * @verifies mirror facet items to search query items correctly
     */
    @Test
    public void mirrorAdvancedSearchCurrentHierarchicalFacets_shouldMirrorFacetItemsToSearchQueryItemsCorrectly() throws Exception {
        SearchBean sb = new SearchBean();
        sb.resetAdvancedSearchParameters(2, 3);
        Assert.assertEquals(2, sb.getAdvancedQueryGroups().size());
        SearchQueryGroup group = sb.getAdvancedQueryGroups().get(0);
        Assert.assertEquals(3, group.getQueryItems().size());
        SearchQueryItem item1 = group.getQueryItems().get(0);
        Assert.assertNull(item1.getField());
        SearchQueryItem item2 = group.getQueryItems().get(1);
        Assert.assertNull(item2.getField());

        item1.setField(SolrConstants.DC);
        sb.getFacets().setCurrentFacetString("DC:a;;DC:b");
        sb.mirrorAdvancedSearchCurrentHierarchicalFacets();
        Assert.assertEquals(2, sb.getFacets().getCurrentFacets().size());
        Assert.assertEquals(SolrConstants.DC, item1.getField());
        Assert.assertEquals("a", item1.getValue());
        Assert.assertEquals(SolrConstants.DC, item2.getField());
        Assert.assertEquals("b", item2.getValue());
    }

    /**
     * @see SearchBean#mirrorAdvancedSearchCurrentHierarchicalFacets()
     * @verifies not replace query items already in use
     */
    @Test
    public void mirrorAdvancedSearchCurrentHierarchicalFacets_shouldNotReplaceQueryItemsAlreadyInUse() throws Exception {
        SearchBean sb = new SearchBean();
        sb.resetAdvancedSearchParameters(2, 3);
        Assert.assertEquals(2, sb.getAdvancedQueryGroups().size());
        SearchQueryGroup group = sb.getAdvancedQueryGroups().get(0);
        SearchQueryItem item1 = group.getQueryItems().get(0);
        SearchQueryItem item2 = group.getQueryItems().get(1);
        SearchQueryItem item3 = group.getQueryItems().get(2);

        item1.setField("MD_TITLE");
        item1.setValue("text");
        sb.getFacets().setCurrentFacetString("DC:a;;DC:b");
        sb.mirrorAdvancedSearchCurrentHierarchicalFacets();
        Assert.assertEquals("MD_TITLE", item1.getField());
        Assert.assertEquals("text", item1.getValue());
        Assert.assertEquals(SolrConstants.DC, item2.getField());
        Assert.assertEquals("a", item2.getValue());
        Assert.assertEquals(SolrConstants.DC, item3.getField());
        Assert.assertEquals("b", item3.getValue());
    }

    /**
     * @see SearchBean#mirrorAdvancedSearchCurrentHierarchicalFacets()
     * @verifies not add identical hierarchical query items
     */
    @Test
    public void mirrorAdvancedSearchCurrentHierarchicalFacets_shouldNotAddIdenticalHierarchicalQueryItems() throws Exception {
        SearchBean sb = new SearchBean();
        sb.resetAdvancedSearchParameters(1, 1);
        Assert.assertEquals(1, sb.getAdvancedQueryGroups().size());
        SearchQueryGroup group = sb.getAdvancedQueryGroups().get(0);
        SearchQueryItem item1 = group.getQueryItems().get(0);

        item1.setField(SolrConstants.DC);
        item1.setValue("foo");
        sb.getFacets().setCurrentFacetString("DC:foo;;DC:foo");
        sb.mirrorAdvancedSearchCurrentHierarchicalFacets();
        // There should be no second query item generated for the other DC:foo
        Assert.assertEquals(1, sb.getAdvancedQueryGroups().get(0).getQueryItems().size());
        Assert.assertEquals(SolrConstants.DC, item1.getField());
        Assert.assertEquals("foo", item1.getValue());
    }

    /**
     * @see SearchBean#generateAdvancedSearchString()
     * @verifies construct query correctly
     */
    @Test
    public void generateAdvancedSearchString_shouldConstructQueryCorrectly() throws Exception {
        SearchBean sb = new SearchBean();
        sb.resetAdvancedSearchParameters(2, 2);
        sb.setAdvancedSearchGroupOperator(1);
        {
            // First group
            SearchQueryGroup group = sb.getAdvancedQueryGroups().get(0);
            group.setOperator(SearchQueryGroupOperator.OR);
            {
                // OR-operator, search in all fields
                SearchQueryItem item = group.getQueryItems().get(0);
                item.setOperator(SearchItemOperator.OR);
                item.setField(SearchQueryItem.ADVANCED_SEARCH_ALL_FIELDS);
                item.setValue("foo bar");
            }
            {
                // AND-operator, search in MD_TITLE with negation
                SearchQueryItem item = group.getQueryItems().get(1);
                item.setOperator(SearchItemOperator.AND);
                item.setField("MD_TITLE");
                item.setValue("bla \"blup\" -nein");
            }
        }
        {
            // Second group
            SearchQueryGroup group = sb.getAdvancedQueryGroups().get(1);
            group.setOperator(SearchQueryGroupOperator.AND);
            {
                // PHASE-operator
                SearchQueryItem item = group.getQueryItems().get(0);
                item.setOperator(SearchItemOperator.PHRASE);
                item.setField(SolrConstants.FULLTEXT);
                item.setValue("lorem ipsum dolor sit amet");
            }
        }
        Assert.assertEquals("((SUPERDEFAULT:(foo OR bar) OR SUPERFULLTEXT:(foo OR bar) OR SUPERUGCTERMS:(foo OR bar) OR DEFAULT:(foo OR bar)"
                + " OR FULLTEXT:(foo OR bar) OR NORMDATATERMS:(foo OR bar) OR UGCTERMS:(foo OR bar) OR CMS_TEXT_ALL:(foo OR bar))"
                + " OR (MD_TITLE:(bla AND \\\"blup\\\" -nein))) OR (((SUPERFULLTEXT:\"lorem ipsum dolor sit amet\" OR FULLTEXT:\"lorem ipsum dolor sit amet\")))",
                sb.generateAdvancedSearchString(true));
    }

    /**
     * @see SearchBean#generateAdvancedSearchString(boolean)
     * @verifies construct query info correctly
     */
    @Test
    public void generateAdvancedSearchString_shouldConstructQueryInfoCorrectly() throws Exception {
        SearchBean sb = new SearchBean();
        sb.resetAdvancedSearchParameters(2, 2);
        sb.setAdvancedSearchGroupOperator(1);
        {
            // First group
            SearchQueryGroup group = sb.getAdvancedQueryGroups().get(0);
            group.setOperator(SearchQueryGroupOperator.OR);
            {
                // OR-operator, search in all fields
                SearchQueryItem item = group.getQueryItems().get(0);
                item.setOperator(SearchItemOperator.OR);
                item.setField(SearchQueryItem.ADVANCED_SEARCH_ALL_FIELDS);
                item.setValue("foo bar");
            }
            {
                // AND-operator, search in MD_TITLE with negation
                SearchQueryItem item = group.getQueryItems().get(1);
                item.setOperator(SearchItemOperator.AND);
                item.setField("MD_TITLE");
                item.setValue("bla \"blup\" -nein");
            }
        }
        {
            // Second group
            SearchQueryGroup group = sb.getAdvancedQueryGroups().get(1);
            group.setOperator(SearchQueryGroupOperator.AND);
            {
                // PHASE-operator
                SearchQueryItem item = group.getQueryItems().get(0);
                item.setOperator(SearchItemOperator.PHRASE);
                item.setField(SolrConstants.FULLTEXT);
                item.setValue("lorem ipsum dolor sit amet");
            }
        }
        sb.generateAdvancedSearchString(true);
        Assert.assertEquals("(All fields: foo bar OR Title: bla \"blup\" -nein) OR\n<br />(Full text: \"lorem ipsum dolor sit amet\")",
                sb.getAdvancedSearchQueryInfo());
    }

    /**
     * @see SearchBean#generateAdvancedSearchString(boolean)
     * @verifies add multiple facets for the same field correctly
     */
    @Test
    public void generateAdvancedSearchString_shouldAddMultipleFacetsForTheSameFieldCorrectly() throws Exception {
        SearchBean bean = new SearchBean();
        bean.resetAdvancedSearchParameters(2, 2);
        bean.setAdvancedSearchGroupOperator(1);

        SearchQueryGroup group = bean.getAdvancedQueryGroups().get(0);
        {
            SearchQueryItem item = group.getQueryItems().get(0);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
            Assert.assertTrue(item.isHierarchical());
        }
        {
            SearchQueryItem item = group.getQueryItems().get(1);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("bar");
            Assert.assertTrue(item.isHierarchical());
        }
        bean.generateAdvancedSearchString(true);

        Assert.assertEquals(URLEncoder.encode(SolrConstants.DC + ":foo;;" + SolrConstants.DC + ":bar;;", StringTools.DEFAULT_ENCODING),
                bean.getFacets().getCurrentFacetString());
    }

    /**
     * @see SearchBean#generateAdvancedSearchString(boolean)
     * @verifies add multiple facets for the same field correctly if field already in current facets
     */
    @Test
    public void generateAdvancedSearchString_shouldAddMultipleFacetsForTheSameFieldCorrectlyIfFieldAlreadyInCurrentFacets() throws Exception {
        SearchBean bean = new SearchBean();
        bean.resetAdvancedSearchParameters(1, 2);
        bean.setAdvancedSearchGroupOperator(1);
        bean.getFacets().setCurrentFacetString(SolrConstants.DC + ":foo;;"); // current facet string already contains this field

        SearchQueryGroup group = bean.getAdvancedQueryGroups().get(0);
        {
            SearchQueryItem item = group.getQueryItems().get(0);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
            Assert.assertTrue(item.isHierarchical());
        }
        {
            SearchQueryItem item = group.getQueryItems().get(1);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("bar");
            Assert.assertTrue(item.isHierarchical());
        }
        bean.generateAdvancedSearchString(true);

        Assert.assertEquals(URLEncoder.encode(SolrConstants.DC + ":foo;;" + SolrConstants.DC + ":bar;;", StringTools.DEFAULT_ENCODING),
                bean.getFacets().getCurrentFacetString());
    }

    /**
     * @see SearchBean#generateAdvancedSearchString(boolean)
     * @verifies only add identical facets once
     */
    @Test
    public void generateAdvancedSearchString_shouldOnlyAddIdenticalFacetsOnce() throws Exception {
        SearchBean bean = new SearchBean();
        bean.resetAdvancedSearchParameters(1, 2);
        bean.setAdvancedSearchGroupOperator(1);

        SearchQueryGroup group = bean.getAdvancedQueryGroups().get(0);
        {
            SearchQueryItem item = group.getQueryItems().get(0);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
            Assert.assertTrue(item.isHierarchical());
        }
        {
            SearchQueryItem item = group.getQueryItems().get(1);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
            Assert.assertTrue(item.isHierarchical());
        }
        bean.generateAdvancedSearchString(true);

        Assert.assertEquals(URLEncoder.encode(SolrConstants.DC + ":foo;;", StringTools.DEFAULT_ENCODING),
                bean.getFacets().getCurrentFacetString());
    }

    /**
     * @see SearchBean#generateAdvancedSearchString(boolean)
     * @verifies not add more facets if field value combo already in current facets
     */
    @Test
    public void generateAdvancedSearchString_shouldNotAddMoreFacetsIfFieldValueComboAlreadyInCurrentFacets() throws Exception {
        SearchBean bean = new SearchBean();
        bean.resetAdvancedSearchParameters(1, 2);
        bean.setAdvancedSearchGroupOperator(1);

        bean.getFacets().setCurrentFacetString(SolrConstants.DC + ":foo;;");

        SearchQueryGroup group = bean.getAdvancedQueryGroups().get(0);
        {
            SearchQueryItem item = group.getQueryItems().get(0);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
            Assert.assertTrue(item.isHierarchical());
        }
        {
            SearchQueryItem item = group.getQueryItems().get(1);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
            Assert.assertTrue(item.isHierarchical());
        }
        bean.generateAdvancedSearchString(true);

        Assert.assertEquals(URLEncoder.encode(SolrConstants.DC + ":foo;;", StringTools.DEFAULT_ENCODING),
                bean.getFacets().getCurrentFacetString());
    }

    /**
     * @see SearchBean#generateAdvancedSearchString(boolean)
     * @verifies not replace obsolete facets with duplicates
     */
    @Test
    public void generateAdvancedSearchString_shouldNotReplaceObsoleteFacetsWithDuplicates() throws Exception {
        SearchBean bean = new SearchBean();
        bean.resetAdvancedSearchParameters(1, 2);
        bean.setAdvancedSearchGroupOperator(1);

        // Current facets are DC:foo and DC:bar
        bean.getFacets().setCurrentFacetString(SolrConstants.DC + ":foo;;" + SolrConstants.DC + ":bar;;");

        // Passing DC:foo and DC:foo from the advanced search
        SearchQueryGroup group = bean.getAdvancedQueryGroups().get(0);
        {
            SearchQueryItem item = group.getQueryItems().get(0);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
            Assert.assertTrue(item.isHierarchical());
        }
        {
            SearchQueryItem item = group.getQueryItems().get(1);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
            Assert.assertTrue(item.isHierarchical());
        }
        bean.generateAdvancedSearchString(true);

        // Only one DC:foo should be in the facets
        Assert.assertEquals(URLEncoder.encode(SolrConstants.DC + ":foo;;", StringTools.DEFAULT_ENCODING),
                bean.getFacets().getCurrentFacetString());
    }

    /**
     * @see SearchBean#generateAdvancedSearchString(boolean)
     * @verifies remove facets that are not matched among query items
     */
    @Test
    public void generateAdvancedSearchString_shouldRemoveFacetsThatAreNotMatchedAmongQueryItems() throws Exception {
        SearchBean bean = new SearchBean();
        bean.resetAdvancedSearchParameters(1, 1);
        bean.setAdvancedSearchGroupOperator(1);

        bean.getFacets().setCurrentFacetString(SolrConstants.DC + ":foo;;" + SolrConstants.DC + ":bar;;");
        Assert.assertEquals(2, bean.getFacets().getCurrentFacets().size());
        Assert.assertTrue(bean.getFacets().getCurrentFacets().get(0).isHierarchial());

        SearchQueryGroup group = bean.getAdvancedQueryGroups().get(0);
        {
            SearchQueryItem item = group.getQueryItems().get(0);
            item.setOperator(SearchItemOperator.IS);
            item.setField(SolrConstants.DC);
            item.setValue("foo");
        }
        bean.generateAdvancedSearchString(true);

        Assert.assertEquals(URLEncoder.encode(SolrConstants.DC + ":foo;;", StringTools.DEFAULT_ENCODING),
                bean.getFacets().getCurrentFacetString());
    }

    /**
     * @see SearchBean#getSearchUrl()
     * @verifies return correct url
     */
    @Test
    public void getSearchUrl_shouldReturnCorrectUrl() throws Exception {
        SearchBean sb = new SearchBean();
        NavigationHelper nh = new NavigationHelper();
        sb.setNavigationHelper(nh);

        sb.activeSearchType = SearchHelper.SEARCH_TYPE_ADVANCED;
        Assert.assertEquals(nh.getAdvancedSearchUrl(), sb.getSearchUrl());

        sb.activeSearchType = SearchHelper.SEARCH_TYPE_REGULAR;
        Assert.assertEquals(nh.getSearchUrl(), sb.getSearchUrl());
    }

    /**
     * @see SearchBean#getSearchUrl()
     * @verifies return null if navigationHelper is null
     */
    @Test
    public void getSearchUrl_shouldReturnNullIfNavigationHelperIsNull() throws Exception {
        SearchBean sb = new SearchBean();
        Assert.assertNull(sb.getSearchUrl());
    }

    /**
     * @see SearchBean#increaseCurrentHitIndex()
     * @verifies increase index correctly
     */
    @Test
    public void increaseCurrentHitIndex_shouldIncreaseIndexCorrectly() throws Exception {
        SearchBean sb = new SearchBean();
        sb.setCurrentSearch(new Search());
        sb.getCurrentSearch().setHitsCount(10);

        // Regular case
        sb.setHitIndexOperand(1);
        sb.currentHitIndex = 6;
        sb.increaseCurrentHitIndex();
        Assert.assertEquals(7, sb.currentHitIndex);

        // Edge case (min)
        sb.setHitIndexOperand(1);
        sb.currentHitIndex = 0;
        sb.increaseCurrentHitIndex();
        Assert.assertEquals(1, sb.currentHitIndex);

        // Edge case (max)
        sb.setHitIndexOperand(1);
        sb.currentHitIndex = 8;
        sb.increaseCurrentHitIndex();
        Assert.assertEquals(9, sb.currentHitIndex);
    }

    /**
     * @see SearchBean#increaseCurrentHitIndex()
     * @verifies decrease index correctly
     */
    @Test
    public void increaseCurrentHitIndex_shouldDecreaseIndexCorrectly() throws Exception {
        SearchBean sb = new SearchBean();
        sb.setCurrentSearch(new Search());
        sb.getCurrentSearch().setHitsCount(10);

        // Regular case
        sb.setHitIndexOperand(-1);
        sb.currentHitIndex = 6;
        sb.increaseCurrentHitIndex();
        Assert.assertEquals(5, sb.currentHitIndex);

        // Edge case (min)
        sb.setHitIndexOperand(-1);
        sb.currentHitIndex = 1;
        sb.increaseCurrentHitIndex();
        Assert.assertEquals(0, sb.currentHitIndex);

        // Edge case (max)
        sb.setHitIndexOperand(-1);
        sb.currentHitIndex = 9;
        sb.increaseCurrentHitIndex();
        Assert.assertEquals(8, sb.currentHitIndex);
    }

    /**
     * @see SearchBean#increaseCurrentHitIndex()
     * @verifies reset operand afterwards
     */
    @Test
    public void increaseCurrentHitIndex_shouldResetOperandAfterwards() throws Exception {
        SearchBean sb = new SearchBean();
        sb.setCurrentSearch(new Search());
        sb.getCurrentSearch().setHitsCount(10);

        sb.setHitIndexOperand(1);
        sb.currentHitIndex = 6;
        Assert.assertEquals(1, sb.getHitIndexOperand());
        sb.increaseCurrentHitIndex();
        Assert.assertEquals(0, sb.getHitIndexOperand());
    }

    /**
     * @see SearchBean#increaseCurrentHitIndex()
     * @verifies do nothing if hit index at the last hit
     */
    @Test
    public void increaseCurrentHitIndex_shouldDoNothingIfHitIndexAtTheLastHit() throws Exception {
        SearchBean sb = new SearchBean();
        sb.setCurrentSearch(new Search());
        sb.getCurrentSearch().setHitsCount(10);
        sb.setHitIndexOperand(1);
        sb.currentHitIndex = 9;

        sb.increaseCurrentHitIndex();
        Assert.assertEquals(9, sb.currentHitIndex);
    }

    /**
     * @see SearchBean#increaseCurrentHitIndex()
     * @verifies do nothing if hit index at 0
     */
    @Test
    public void increaseCurrentHitIndex_shouldDoNothingIfHitIndexAt0() throws Exception {
        SearchBean sb = new SearchBean();
        sb.setCurrentSearch(new Search());
        sb.getCurrentSearch().setHitsCount(10);
        sb.setHitIndexOperand(-1);
        sb.currentHitIndex = 0;

        sb.increaseCurrentHitIndex();
        Assert.assertEquals(0, sb.currentHitIndex);
    }

    @Test
    public void testGetHierarchicalFacets() {
        String facetString = "DC:sonstiges.ocr.antiqua;;DOCSTRCT:monograph;;MD_TOPICS_UNTOKENIZED:schulbuch";
        List<String> hierarchicalFacetFields = Arrays.asList(new String[] { "A", "MD_TOPICS", "B", "DC", "C" });

        List<String> facets = SearchFacets.getHierarchicalFacets(facetString, hierarchicalFacetFields);
        Assert.assertEquals(2, facets.size());
        Assert.assertEquals("sonstiges.ocr.antiqua", facets.get(1));
        Assert.assertEquals("schulbuch", facets.get(0));
    }

    /**
     * @see SearchBean#getAdvancedSearchAllowedFields()
     * @verifies omit languaged fields for other languages
     */
    @Test
    public void getAdvancedSearchAllowedFields_shouldOmitLanguagedFieldsForOtherLanguages() throws Exception {
        List<AdvancedSearchFieldConfiguration> fields = SearchBean.getAdvancedSearchAllowedFields("en");
        boolean en = false;
        boolean de = false;
        boolean es = false;
        for (AdvancedSearchFieldConfiguration field : fields) {
            switch (field.getField()) {
                case "MD_FOO_LANG_EN":
                    en = true;
                    break;
                case "MD_FOO_LANG_DE":
                    de = true;
                    break;
                case "MD_FOO_LANG_ES":
                    es = true;
                    break;
            }
        }
        Assert.assertTrue(en);
        Assert.assertFalse(de);
        Assert.assertFalse(es);
    }

    /**
     * @see SearchBean#findCurrentHitIndex(String,int,boolean)
     * @verifies set currentHitIndex to minus one if no search hits
     */
    @Test
    public void findCurrentHitIndex_shouldSetCurrentHitIndexToMinusOneIfNoSearchHits() throws Exception {
        SearchBean sb = new SearchBean();

        sb.findCurrentHitIndex("PPN123", 1, true);
        Assert.assertEquals(-1, sb.getCurrentHitIndex());

        sb.setCurrentSearch(new Search());
        Assert.assertEquals(0, sb.getCurrentSearch().getHitsCount());
        sb.findCurrentHitIndex("PPN123", 1, true);
        Assert.assertEquals(-1, sb.getCurrentHitIndex());
    }

    /**
     * @see SearchBean#findCurrentHitIndex(String,int,boolean)
     * @verifies set currentHitIndex correctly
     */
    @Test
    public void findCurrentHitIndex_shouldSetCurrentHitIndexCorrectly() throws Exception {
        SearchBean sb = new SearchBean();
        sb.setCurrentSearch(new Search());
        sb.getCurrentSearch().setPage(1);
        sb.getCurrentSearch().setQuery("+DC:dcimage* +ISWORK:true -IDDOC_PARENT:*");
        sb.getCurrentSearch().setSortString("SORT_TITLE");
        sb.getCurrentSearch().execute(new SearchFacets(), null, 10, 0, null, true);
        Assert.assertEquals(18, sb.getCurrentSearch().getHitsCount());

        sb.findCurrentHitIndex("PPN9462", 1, true);
        Assert.assertEquals(0, sb.getCurrentHitIndex());

        sb.findCurrentHitIndex("633114553", 1, true);
        Assert.assertEquals(1, sb.getCurrentHitIndex());

        sb.findCurrentHitIndex("PPN407465633d27302e312e312e27_40636c6173736e756d3d27312e27_407369673d27313527", 1, true);
        Assert.assertEquals(2, sb.getCurrentHitIndex());

        sb.findCurrentHitIndex("808996762", 1, true);
        Assert.assertEquals(3, sb.getCurrentHitIndex());

        sb.findCurrentHitIndex("02008011811811", 1, true);
        Assert.assertEquals(4, sb.getCurrentHitIndex());

        sb.findCurrentHitIndex("iiif_test_image", 1, true);
        Assert.assertEquals(6, sb.getCurrentHitIndex());

        sb.findCurrentHitIndex("339471409", 1, true);
        Assert.assertEquals(7, sb.getCurrentHitIndex());

        sb.findCurrentHitIndex("02008012412069", 1, true);
        Assert.assertEquals(8, sb.getCurrentHitIndex());

        sb.findCurrentHitIndex("02008012412076", 1, true);
        Assert.assertEquals(9, sb.getCurrentHitIndex());
    }

    /**
     * @see SearchBean#searchSimple()
     * @verifies not reset facets
     */
    @Test
    public void searchSimple_shouldNotResetFacets() throws Exception {
        SearchBean sb = new SearchBean();
        sb.getFacets().setCurrentFacetString("foo:bar");
        sb.searchSimple();
        Assert.assertEquals(URLEncoder.encode("foo:bar;;", SearchBean.URL_ENCODING), sb.getFacets().getCurrentFacetString());
    }

    /**
     * @see SearchBean#searchSimple(boolean,boolean)
     * @verifies not reset facets if resetFacets false
     */
    @Test
    public void searchSimple_shouldNotResetFacetsIfResetFacetsFalse() throws Exception {
        SearchBean sb = new SearchBean();
        sb.getFacets().setCurrentFacetString("foo:bar");
        sb.searchSimple(true, false);
        Assert.assertEquals(URLEncoder.encode("foo:bar;;", SearchBean.URL_ENCODING), sb.getFacets().getCurrentFacetString());
    }

    /**
     * @see SearchBean#getExactSearchString()
     * @verifies not url escape string
     */
    @Test
    public void getExactSearchString_shouldNotUrlEscapeString() throws Exception {
        SearchBean sb = new SearchBean();
        sb.setExactSearchString("PI:*");
        Assert.assertEquals("PI:*", sb.getExactSearchString());
    }

    /**
     * @see SearchBean#getExactSearchString()
     * @verifies escape critical chars
     */
    @Test
    public void getExactSearchString_shouldEscapeCriticalChars() throws Exception {
        SearchBean sb = new SearchBean();
        sb.setExactSearchString("PI:foo/bar");
        Assert.assertEquals("PI:foo" + BeanUtils.SLASH_REPLACEMENT + "bar", sb.getExactSearchString());
    }
}
