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
package de.intranda.digiverso.presentation.managedbeans;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.intranda.digiverso.presentation.AbstractDatabaseEnabledTest;
import de.intranda.digiverso.presentation.controller.DataManager;
import de.intranda.digiverso.presentation.exceptions.DAOException;
import de.intranda.digiverso.presentation.model.cms.Category;

public class CmsMediaBeanTest extends AbstractDatabaseEnabledTest {

    CmsMediaBean bean;

    /**
     * @throws java.lang.Exception
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        bean = new CmsMediaBean();
    }

    /**
     * @throws java.lang.Exception
     */
    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSelectedTag() {
        String tag = "sampleTag";
        bean.setSelectedTag(tag);
        Assert.assertEquals(tag, bean.getSelectedTag());
    }

    @Test
    public void testGetAllMediaCategories() throws DAOException {
        List<Category> tags = bean.getAllMediaCategories();
        Assert.assertEquals(3, tags.size());
    }

    @Test
    public void testGetMediaItems() throws DAOException {
    	Category tag1 = DataManager.getInstance().getDao().getCategoryByName("tag1");
    	
        Assert.assertEquals(4, bean.getMediaItems(null, "").size());
        Assert.assertEquals(3, bean.getMediaItems(tag1, "").size());
        Assert.assertEquals(4, bean.getMediaItems(null, bean.getImageFilter()).size());
        Assert.assertEquals(0, bean.getMediaItems(null, ".*\\.xml").size());
    }
    
    @Test
    public void testGetImageFilter() {
    	String file1 = "image.jpg";
    	String file2 = "image.JPEG";
    	String file3 = "image.xml";
    	Assert.assertTrue(file1.matches(bean.getImageFilter()));
    	Assert.assertTrue(file2.matches(bean.getImageFilter()));
    	Assert.assertFalse(file3.matches(bean.getImageFilter()));
    }

}
