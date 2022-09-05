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
package io.goobi.viewer.dao.converter;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.apache.commons.lang3.StringUtils;

import io.goobi.viewer.model.administration.legal.ConsentScope;
import io.goobi.viewer.model.administration.legal.DisplayScope;
import io.goobi.viewer.model.administration.legal.DisplayScope.PageScope;

@Converter
public class DisplayScopeConverter implements AttributeConverter<DisplayScope, String> {

    @Override
    public String convertToDatabaseColumn(DisplayScope attribute) {
        return attribute.getAsJson();
    }

    @Override
    public DisplayScope convertToEntityAttribute(String dbData) {
        if(StringUtils.isNotBlank(dbData)) {
            try {
                return new DisplayScope(dbData);
            } catch(IllegalArgumentException e) {
                return new DisplayScope(PageScope.RECORD, dbData);
            }
        } else {
            return new DisplayScope(PageScope.RECORD, "");
        }
    }


}