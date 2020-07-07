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
package io.goobi.viewer.servlets.rest;

import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

import io.goobi.viewer.api.rest.ViewerRestServiceBinding;

/**
 * <p>
 * ViewerApplication class.
 * </p>
 */
@ApplicationPath("rest")
@ViewerRestServiceBinding
public class ViewerApplication extends ResourceConfig {

    /**
     * <p>
     * Constructor for ViewerApplication.
     * </p>
     */
    public ViewerApplication() {
        super();
        register(MultiPartFeature.class);
        packages(true, "io.goobi.viewer.servlets.rest");
        packages(true, "io.goobi.viewer.api.rest.filters");
        packages(true, "io.goobi.viewer.api.rest.exceptions");
        packages(true, "de.unigoettingen.sub.commons.contentlib.servlet.rest");

    }

}
