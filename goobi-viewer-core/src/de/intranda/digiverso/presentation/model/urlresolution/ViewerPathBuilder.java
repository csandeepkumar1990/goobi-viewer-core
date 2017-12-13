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
package de.intranda.digiverso.presentation.model.urlresolution;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;

import com.ocpsoft.pretty.PrettyContext;

import de.intranda.digiverso.presentation.controller.DataManager;
import de.intranda.digiverso.presentation.exceptions.DAOException;
import de.intranda.digiverso.presentation.model.cms.CMSPage;
import de.intranda.digiverso.presentation.model.viewer.PageType;
import de.intranda.digiverso.presentation.servlets.utils.ServletUtils;

/**
 * This class offers static methods to create {@link ViewerPath ViewerPaths} from a http request. 
 * 
 * @author Florian Alpers
 *
 */
public class ViewerPathBuilder {



    /**
     * Returns the request path of the given {@code httpRequest} as a {@link ViewerPath}, including information
     * on associated CMSPage and targeted PageType
     * 
     * If the url has a pretty-url context and only consists of the server url, "/index" is appended to the url to redirect to the index pretty-mapping
     * Any occurrences of "index.(x)html" are removed from the url to get the actual pretty url
     * 
     * @param httpRequest       The request from which the path is generated
     * @return
     * @throws DAOException
     */
    public static Optional<ViewerPath> createPath(HttpServletRequest httpRequest) throws DAOException {
        String serverUrl = ServletUtils.getServletPathWithHostAsUrlFromRequest(httpRequest); // http://localhost:8080/viewer
        String serviceUrl = httpRequest.getServletPath(); // /resources/.../index.xhtml
        String serverName = httpRequest.getContextPath(); // /viewer
        PrettyContext context = PrettyContext.getCurrentInstance(httpRequest);
        if (!serviceUrl.contains("/cms/") && context != null && context.getRequestURL() != null) {
            serviceUrl = ServletUtils.getServletPathWithHostAsUrlFromRequest(httpRequest) + 
                    ("/".equals(context.getRequestURL().toURL()) ? "/index" : context.getRequestURL().toURL());
        }
        serviceUrl = serviceUrl.replaceAll("\\/index\\.x?html", "/");
        return createPath(serverUrl, serverName, serviceUrl);

    }
    

    /**
     * Create a combined path from the given url. 
     * 
     * If the url leads to a known PageType, associates the PageType with the combined path.
     * If the path leads to a cmsPage, either through direct url {@code /cmds/...}, the cmsPages alternative url or a static page mapping,
     * the cmsPage is associated with this path
     * 
     * @param applicationUrl    The absolute url of the web-application including the application name ('viewer')
     * @param applicationName   The name of the web-application. This is always the last part of the {@code hostUrl}. May be empty
     * @param serviceUrl        The complete requested url, optionally including the hostUrl 
     * @return                  A {@link ViewerPath} containing the complete path information
     * @throws DAOException
     */
    public static Optional<ViewerPath> createPath(String applicationUrl, String applicationName, String serviceUrl) throws DAOException {
        serviceUrl = serviceUrl.replace(applicationUrl, "").replaceAll("^\\/", ""); 
        final Path servicePath = Paths.get(serviceUrl);
        
        ViewerPath currentPath = new ViewerPath();
        currentPath.setApplicationUrl(applicationUrl);
        currentPath.setApplicationName(applicationName);
        
        if(servicePath.startsWith("cms") && servicePath.getName(1).toString().matches("\\d+")) {
            Long cmsPageId = Long.parseLong(servicePath.getName(1).toString());
            CMSPage page = DataManager.getInstance().getDao().getCMSPage(cmsPageId);
            if(page != null) {
                currentPath.setCmsPage(page);
            }
            currentPath.setPagePath(servicePath.subpath(0, 2));
            currentPath.setParameterPath(currentPath.getPagePath().relativize(servicePath));
        } else {
            Optional<PageType> pageType = getPageType(servicePath);
            if(pageType.isPresent()) {
                currentPath.setPagePath(Paths.get(pageType.get().getName()));
                currentPath.setParameterPath(currentPath.getPagePath().relativize(servicePath));
                currentPath.setPageType(pageType.get());
                if(pageType.get().isHandledWithCms()) {
                    Optional<CMSPage> oCmsPage = DataManager.getInstance().getDao().getAllCMSPages().stream()
                            .filter(page -> StringUtils.isNotBlank(page.getStaticPageName()))
                            .filter(page -> pageType.get().matches(page.getStaticPageName()))
                            .findFirst();
                    if(oCmsPage.isPresent()) {
                        currentPath.setCmsPage(oCmsPage.get());
                    }
                }
            } else {
                Optional<CMSPage> cmsPage = getCmsPage(servicePath);
                if(cmsPage.isPresent()) {
                    currentPath.setPagePath(Paths.get(cmsPage.get().getPersistentUrl()));
                    currentPath.setParameterPath(currentPath.getPagePath().relativize(servicePath));
                    currentPath.setCmsPage(cmsPage.get());
                }
            }
        }
        if(StringUtils.isNotBlank(currentPath.getPagePath().toString())) {            
            return Optional.of(currentPath);
        }
        return Optional.empty();
    }

    
    /**
     * Gets the best matching CMSPage which alternative url ('persistent url') matches the beginning of the given path
     * 
     * @param servicePath
     * @return
     * @throws DAOException 
     */
    public static Optional<CMSPage> getCmsPage(Path servicePath) throws DAOException {
        return DataManager.getInstance().getDao().getAllCMSPages().stream()
                .filter(cmsPage -> StringUtils.isNotBlank(cmsPage.getPersistentUrl()))
                .filter(page -> servicePath.startsWith(page.getPersistentUrl().replaceAll("^\\/|\\/$", "")))
                .sorted((page1, page2) -> Integer.compare(page1.getPersistentUrl().length(), page2.getPersistentUrl().length()))
                .findFirst();
    }
    
    /**
     * Gets the {@link PageType} that the given path refers to, if any
     * 
     * @param servicePath
     * @return
     */
    public static Optional<PageType> getPageType(final Path servicePath) {
        Optional<PageType> pageNameOfType = Arrays.stream(PageType.values())
        .filter(type -> type.matches(servicePath))
        .sorted((type1, type2) -> Integer.compare(type1.getName().length(), type2.getName().length()))
        .findFirst();
        return pageNameOfType;
    }

    
}
