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
package io.goobi.viewer.faces.converters;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;

import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.model.security.user.UserGroup;

/**
 * <p>
 * UserConverter class.
 * </p>
 */
@FacesConverter("userGroupConverter")
public class UserGroupConverter implements Converter<UserGroup> {

    /** {@inheritDoc} */
    @Override
    public final UserGroup getAsObject(final FacesContext context, final UIComponent component, final String value) {
        try {
            long id = Long.valueOf(value);
            return DataManager.getInstance().getDao().getUserGroup(id);
        } catch (NumberFormatException e) {
            return null;
        } catch (DAOException e) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public final String getAsString(final FacesContext context, final UIComponent component, final UserGroup object) {
        if (object == null) {
            return null;
        }

        try {
            return String.valueOf(object.getId());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}
