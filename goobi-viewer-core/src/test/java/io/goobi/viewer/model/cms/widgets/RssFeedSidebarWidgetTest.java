package io.goobi.viewer.model.cms.widgets;

import static org.junit.Assert.*;

import java.util.Locale;

import org.junit.Test;

import io.goobi.viewer.AbstractDatabaseEnabledTest;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.dao.IDAO;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.model.cms.widgets.type.CustomWidgetType;
import io.goobi.viewer.solr.SolrConstants;

public class RssFeedSidebarWidgetTest extends AbstractDatabaseEnabledTest {

    @Test
    public void testPersist() throws DAOException {
        RssFeedSidebarWidget widget = new RssFeedSidebarWidget();
        widget.getDescription().setValue("Beschreibung", Locale.GERMAN);
        widget.getDescription().setValue("Description", Locale.ENGLISH);
        widget.getTitle().setValue("Titel", Locale.GERMAN);
        widget.getTitle().setValue("Title", Locale.ENGLISH);
        widget.setSortField(SolrConstants.YEARPUBLISH);
        widget.setDescendingSorting(false);
        widget.setFilterQuery("+DC:all");
        widget.setNumEntries(9);
        
        IDAO dao = DataManager.getInstance().getDao();
        
        dao.addCustomWidget(widget);
        RssFeedSidebarWidget copy = (RssFeedSidebarWidget) dao.getCustomWidget(widget.getId());
        assertNotNull(copy);
        assertFalse(copy.isEmpty(Locale.GERMAN));
        assertEquals(widget.getSortField(), copy.getSortField());
        assertEquals(widget.getFilterQuery(), copy.getFilterQuery());
        assertEquals(widget.isDescendingSorting(), copy.isDescendingSorting());
        assertEquals(widget.getNumEntries(), copy.getNumEntries());
        
    }
    
    @Test
    public void testClone() {
        RssFeedSidebarWidget widget = new RssFeedSidebarWidget();
        widget.getDescription().setValue("Beschreibung", Locale.GERMAN);
        widget.getDescription().setValue("Description", Locale.ENGLISH);
        widget.setSortField(SolrConstants.LABEL);
        widget.setFilterQuery("+DC:all");
        widget.setDescendingSorting(false);
        widget.setId(2l);
        widget.setNumEntries(11);
        widget.setCollapsed(true);
        widget.setStyleClass("testcssstyle");
        
        RssFeedSidebarWidget clone = new RssFeedSidebarWidget(widget);
        assertEquals(widget.getSortField(), clone.getSortField());
        assertEquals(widget.getFilterQuery(), clone.getFilterQuery());
        assertEquals(widget.isDescendingSorting(), clone.isDescendingSorting());
        assertEquals(widget.getId(), clone.getId());
        assertEquals(widget.getNumEntries(), clone.getNumEntries());
        assertEquals(widget.isCollapsed(), clone.isCollapsed());
        assertEquals(widget.getStyleClass(), clone.getStyleClass());
    }
    
    @Test
    public void testType() {
        assertEquals(CustomWidgetType.WIDGET_RSSFEED, new RssFeedSidebarWidget().getType());
    }

}
