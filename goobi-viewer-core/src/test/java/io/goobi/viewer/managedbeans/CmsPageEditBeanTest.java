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
package io.goobi.viewer.managedbeans;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.goobi.viewer.dao.IDAO;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.model.cms.pages.CMSPage;
import io.goobi.viewer.model.cms.pages.CMSPageTemplate;
import io.goobi.viewer.model.cms.pages.CMSTemplateManager;
import io.goobi.viewer.model.cms.pages.content.CMSComponent;
import io.goobi.viewer.model.cms.pages.content.CMSComponentScope;
import io.goobi.viewer.model.security.user.User;

public class CmsPageEditBeanTest {

    private static final String FILENAME_COMPONENT = "text";
    private static final String DESCRIPTION_COMPONENT = "description_component";
    private static final String NAME_COMPONENT = "name_component";
    private static final Long SELECTED_PAGE_ID = 11l;
    private static final Long PAGE_TEMPLATE_ID = 7l;
    private static final String TEMPLATE_NAME = "Template_sample";
    private static final String PAGE_NAME = "Title of page";
    private static final String RELATED_PI = "AC01";

    CmsPageEditBean bean;
    
    @Before
    public void setup() throws DAOException {
        
        CMSSidebarWidgetsBean widgetsBean = Mockito.mock(CMSSidebarWidgetsBean.class);
        Mockito.when(widgetsBean.getAllWidgets()).thenReturn(Collections.emptyList());
        
        CollectionViewBean collectionViewBean = Mockito.mock(CollectionViewBean.class);
        
        CMSPage selectedPage = new CMSPage();
        selectedPage.setId(SELECTED_PAGE_ID);
        CMSPageTemplate selectedTemplate = new CMSPageTemplate();
        selectedTemplate.setId(PAGE_TEMPLATE_ID);
        IDAO dao = Mockito.mock(IDAO.class);
        Mockito.when(dao.getCMSPage(SELECTED_PAGE_ID)).thenReturn(selectedPage);
        Mockito.when(dao.getCMSPageTemplate(PAGE_TEMPLATE_ID)).thenReturn(selectedTemplate);
        
        bean = new CmsPageEditBean();
        bean.widgetsBean = widgetsBean;
        bean.collectionViewBean = collectionViewBean;
        bean.userBean = mockUserBean(true);
        bean.dao = dao;
    }

    private FacesContext mockFacesContext(Map<String, String> requestParameters) {
        FacesContext facesContext = Mockito.mock(FacesContext.class);
        ExternalContext externalContext = Mockito.mock(ExternalContext.class);
        Mockito.when(facesContext.getExternalContext()).thenReturn(externalContext);
        Mockito.when(externalContext.getRequestParameterMap()).thenReturn(requestParameters);
        return facesContext;
    }
    
    private UserBean mockUserBean(boolean asCmsAdmin) {
        UserBean userBean = Mockito.mock(UserBean.class);
        User user = Mockito.mock(User.class);
        Mockito.when(user.isCmsAdmin()).thenReturn(asCmsAdmin);
        Mockito.when(userBean.getUser()).thenReturn(user);
        Mockito.when(user.hasPrivilegeForAllSubthemeDiscriminatorValues()).thenReturn(true);
        Mockito.when(user.hasPrivilegeForAllCategories()).thenReturn(true);

        return userBean;
    }
    

    private CMSTemplateManager createTemplateManager() {
        CMSTemplateManager templateManager = Mockito.mock(CMSTemplateManager.class);
        CMSComponent component = new CMSComponent(null, NAME_COMPONENT, DESCRIPTION_COMPONENT, null, FILENAME_COMPONENT, CMSComponentScope.PAGEVIEW, Collections.emptyMap(), null);
        Mockito.when(templateManager.getComponent(FILENAME_COMPONENT)).thenReturn(Optional.of(component));
        return templateManager;
    }
    
    /**
     * When calling bean without context-parameters, create an empty new page
     * @throws DAOException
     */
    @Test
    public void testNewPage() throws DAOException {
        FacesContext facesContext = mockFacesContext(Map.of());
        bean.facesContext = facesContext;
        bean.setup();
        assertNotNull(bean.getSelectedPage());
        assertNull(bean.getSelectedPage().getId());
        assertNull(bean.getSelectedPage().getTemplateId());
        assertFalse(bean.isEditMode());
    }
    
    /**
     * When bean is called with context-parameter 'templateId', a new page based on the correspending template should be loaded
     * @throws DAOException
     */
    @Test
    public void testNewPageFromTemplate() throws DAOException {
        FacesContext facesContext = mockFacesContext(Map.of("templateId", PAGE_TEMPLATE_ID.toString()));
        bean.facesContext = facesContext;
        bean.setup();
        assertNotNull(bean.getSelectedPage());
        assertNull(bean.getSelectedPage().getId());
        assertEquals(PAGE_TEMPLATE_ID, bean.getSelectedPage().getTemplateId());
        assertFalse(bean.isEditMode());
    }
    
    /**
     * When bean is called with context-parameter 'templateId', a new page based on the correspending template should be loaded
     * @throws DAOException
     */
    @Test
    public void testNewPageFromTemplateWithTitleAndPi() throws DAOException {
        FacesContext facesContext = mockFacesContext(Map.of("templateId", PAGE_TEMPLATE_ID.toString(), "title", PAGE_NAME, "relatedPi", RELATED_PI));
        bean.facesContext = facesContext;
        bean.setup();
        assertNotNull(bean.getSelectedPage());
        assertNull(bean.getSelectedPage().getId());
        assertEquals(PAGE_TEMPLATE_ID, bean.getSelectedPage().getTemplateId());
        assertEquals(PAGE_NAME, bean.getSelectedPage().getTitle());
        assertEquals(RELATED_PI, bean.getSelectedPage().getRelatedPI());
        assertFalse(bean.isEditMode());
    }
    
    /**
     * When Bean is called with context-parameter 'selectedPageId', load the corresponding page
     * @throws DAOException
     */
    @Test
    public void testEditPage() throws DAOException {
        FacesContext facesContext = mockFacesContext(Map.of("selectedPageId", SELECTED_PAGE_ID.toString()));
        bean.facesContext = facesContext;
        bean.setup();
        assertNotNull(bean.getSelectedPage());
        assertEquals(SELECTED_PAGE_ID, bean.getSelectedPage().getId());
        assertTrue(bean.isEditMode());
    }
    
    /**
     * When calling save as admin, {@link IDAO#updateCMSPage(CMSPage)} should be called.
     * Also {@link CollectionViewBean#removeCollectionsForPage(CMSPage)} should be called to reset collections loaded by this page
     * @throws DAOException
     */
    @Test
    public void testSavePage() throws DAOException {
        FacesContext facesContext = mockFacesContext(Map.of("selectedPageId", SELECTED_PAGE_ID.toString()));
        bean.facesContext = facesContext;
        bean.setup();
        bean.saveSelectedPage();
        Mockito.verify(bean.dao, Mockito.times(1)).updateCMSPage(bean.getSelectedPage());
        Mockito.verify(bean.collectionViewBean, Mockito.times(1)).removeCollectionsForPage(bean.getSelectedPage());
    }
    
    /**
     * When saving page with setSaveAsTemplate == true, make sure a template with the set name ans lockComponents property is created
     * @throws DAOException
     */
    @Test
    public void testSaveAsTemplate() throws DAOException {
        FacesContext facesContext = mockFacesContext(Map.of("selectedPageId", SELECTED_PAGE_ID.toString()));
        bean.facesContext = facesContext;
        bean.setup();
        bean.setSaveAsTemplate(true);
        bean.setTemplateLockComponents(true);
        bean.setTemplateName(TEMPLATE_NAME);
        bean.saveSelectedPage();
        Mockito.verify(bean.dao, Mockito.times(1)).addCMSPageTemplate(Mockito.argThat(template -> template.getName() == TEMPLATE_NAME));
        Mockito.verify(bean.dao, Mockito.times(1)).addCMSPageTemplate(Mockito.argThat(template -> template.isLockComponents()));

    }
    
    /**
     * If the user is no cmsAdmin, calling save should not update page
     * @throws DAOException
     */
    @Test
    public void testSavePageNoAdmin() throws DAOException {
        FacesContext facesContext = mockFacesContext(Map.of("selectedPageId", SELECTED_PAGE_ID.toString()));
        bean.userBean = mockUserBean(false);
        bean.facesContext = facesContext;
        bean.setup();
        bean.saveSelectedPage();
        Mockito.verify(bean.dao, Mockito.times(0)).updateCMSPage(Mockito.any());
    }
    
    /**
     * When calling delete in bean, call {@link IDAO#deleteCMSPage(CMSPage)}
     * @throws DAOException
     */
    @Test
    public void testDeletePage() throws DAOException {
        FacesContext facesContext = mockFacesContext(Map.of("selectedPageId", SELECTED_PAGE_ID.toString()));
        bean.facesContext = facesContext;
        bean.setup();
        CMSPage page = bean.getSelectedPage();
        bean.deleteSelectedPage();
        Mockito.verify(bean.dao, Mockito.times(1)).deleteCMSPage(page);
        assertNull(bean.getSelectedPage());
    }
    
    @Test
    public void testAddComponent() {
        FacesContext facesContext = mockFacesContext(Map.of("selectedPageId", SELECTED_PAGE_ID.toString()));
        bean.facesContext = facesContext;
        bean.templateManager = createTemplateManager();
        bean.setup();
        bean.setSelectedComponent(FILENAME_COMPONENT);
        assertTrue(bean.getSelectedPage().getComponents().isEmpty());
        bean.addComponent();
        assertFalse(bean.getSelectedPage().getComponents().isEmpty());
        assertEquals(NAME_COMPONENT, bean.getSelectedPage().getComponents().get(0).getLabel());
    }
    
    @Test
    public void testDeleteComponent() {
        FacesContext facesContext = mockFacesContext(Map.of("selectedPageId", SELECTED_PAGE_ID.toString()));
        bean.facesContext = facesContext;
        bean.templateManager = createTemplateManager();
        bean.setup();
        bean.setSelectedComponent(FILENAME_COMPONENT);
        assertTrue(bean.getSelectedPage().getComponents().isEmpty());
        bean.addComponent();
        assertFalse(bean.getSelectedPage().getComponents().isEmpty());
        bean.deleteComponent(bean.getSelectedPage().getComponents().get(0));
        assertTrue(bean.getSelectedPage().getComponents().isEmpty());
    }

    

}
