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
package io.goobi.viewer.api.rest.v1;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.goobi.viewer.Version;
import io.goobi.viewer.api.rest.AbstractApiUrlManager;
import io.goobi.viewer.api.rest.AbstractApiUrlManager.ApiInfo;
import io.goobi.viewer.controller.DataManager;

/**
 * @author florian
 *
 */
@Path("/")
public class ApplicationResource {

    @Inject
    AbstractApiUrlManager urls;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ApiInfo getApiInfo() {
        ApiInfo info = new ApiInfo();
        info.name = "Goobi viewer REST API";
        info.version = "v1";
        info.specification = urls.getApiUrl() + "/openapi.json";
        return info;
    }
}
