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
package io.goobi.viewer.model.cms;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.goobi.viewer.dao.converter.TranslatedTextConverter;
import io.goobi.viewer.model.translations.TranslatedText;

/**
 * Class holding a formatted text related to a single PI which may be edited in the admin/cms-backend and displayed in a (sidebar) widget
 * 
 * @author florian
 *
 */
@Entity
@DiscriminatorValue("MULTI")
public class CMSMultiRecordNote extends CMSRecordNote {

    private static final Logger logger = LoggerFactory.getLogger(CMSMultiRecordNote.class);

    /**
     * PI of the record this note relates to. Should be effectively final, but can't be for DAO campatibility
     */
    @Column(name = "query", nullable = true)
    private String query;

    public CMSMultiRecordNote() {
    }

    /**
     * @param pi
     */
    public CMSMultiRecordNote(String query) {
        super();
        this.query = query;
    }

    /**
     * @param o
     */
    public CMSMultiRecordNote(CMSRecordNote source) {
        super(source);
        if(source instanceof CMSMultiRecordNote) {            
            this.query = ((CMSMultiRecordNote)source).query;
        }
    }

    /**
     * @return the query
     */
    public String getQuery() {
        return query;
    }
    
    /**
     * @param query the query to set
     */
    public void setQuery(String query) {
        this.query = query;
    }

}
