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
package io.goobi.viewer.servlets.rest.cms;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.goobi.viewer.AbstractDatabaseEnabledTest;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.exceptions.CmsElementNotFoundException;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.model.cms.CMSContentItem;
import io.goobi.viewer.model.cms.CMSPage;
import io.goobi.viewer.model.cms.CMSSidebarElement;
import io.goobi.viewer.model.cms.CMSTemplateManager;
import io.goobi.viewer.servlets.rest.cms.CMSContentResource;

public class CMSContentResourceTest extends AbstractDatabaseEnabledTest {

    CMSContentResource resource = new CMSContentResource();

    /**
     * @throws java.lang.Exception
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        File webContent = new File("src/main/resources/META-INF/resources/").getAbsoluteFile();
        Assert.assertTrue(webContent.isDirectory());
        String webContentPath = webContent.toURI().toString();
        //        if (webContentPath.startsWith("file:/")) {
        //            webContentPath = webContentPath.replace("file:/", "");
        //        }
        CMSTemplateManager.getInstance(webContentPath, null);
    }

    /**
     * @throws java.lang.Exception
     */
    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test method for
     * {@link io.goobi.viewer.servlets.rest.cms.CMSContentResource#getContentHtml(java.lang.Long, java.lang.String, java.lang.String)}.
     * 
     * @throws ServletException
     * @throws DAOException
     * @throws IOException
     */
    @Test
    public void testGetContentHtml() throws IOException, DAOException, ServletException {
        String output = resource.getContentHtml(1l, "de", "C1");
        String expectedOutput = "&lt;b&gt;Hello CMS&lt;/b&gt;";
        Assert.assertEquals(resource.wrap(expectedOutput, true), output);
    }

    /**
     * Test method for {@link io.goobi.viewer.servlets.rest.cms.CMSContentResource#getSidebarElementHtml(java.lang.Long)}.
     * 
     * @throws ServletException
     * @throws DAOException
     * @throws IOException
     */
    @Test
    public void testGetSidebarElementHtml() throws IOException, DAOException, ServletException {
        String output = resource.getSidebarElementHtml(1l);
        String expectedOutput = "&lt;h1&gt;Hello Sidebar&lt;/h1&gt;";
        Assert.assertEquals(resource.wrap(expectedOutput, true), output);
    }

    /**
     * Test method for
     * {@link io.goobi.viewer.servlets.rest.cms.CMSContentResource#getContentUrl(io.goobi.viewer.model.cms.CMSContentItem)}.
     * 
     * @throws DAOException
     * @throws CmsElementNotFoundException
     */
    @Test
    public void testGetContentUrl() throws DAOException, CmsElementNotFoundException {
        CMSPage page = DataManager.getInstance().getDao().getCMSPage(1l);
        CMSContentItem item = page.getContentItem("C1", "de");
        String url = CMSContentResource.getContentUrl(item);
        url = url.substring(0, url.indexOf("?"));

        Assert.assertEquals("/rest/cms/content/1/de/C1/", url);
    }

    /**
     * Test method for
     * {@link io.goobi.viewer.servlets.rest.cms.CMSContentResource#getSidebarElementUrl(io.goobi.viewer.model.cms.CMSSidebarElement)}.
     * 
     * @throws DAOException
     */
    @Test
    public void testGetSidebarElementUrl() throws DAOException {
        CMSSidebarElement element = DataManager.getInstance().getDao().getCMSSidebarElement(1);
        String url = CMSContentResource.getSidebarElementUrl(element);
        url = url.substring(0, url.indexOf("?"));
        Assert.assertEquals("/rest/cms/sidebar/1/", url);
    }

}