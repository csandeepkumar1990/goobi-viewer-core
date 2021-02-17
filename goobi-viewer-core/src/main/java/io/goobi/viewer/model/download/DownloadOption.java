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
package io.goobi.viewer.model.download;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import de.unigoettingen.sub.commons.contentlib.imagelib.transform.Scale;
import io.goobi.viewer.controller.DataManager;

/**
 * Download option configuration item.
 */
public class DownloadOption {

    private String label;
    private String format;
    private String boxSizeInPixel;

    /**
     * 
     * @return true if all properties are set; false otherwise
     */
    public boolean isValid() {
        return StringUtils.isNotEmpty(label) && StringUtils.isNotEmpty(format) && StringUtils.isNotEmpty(boxSizeInPixel);
    }

    /**
     * Retrieves the <code>DownloadOption</code> with the given label from configuration.
     * 
     * @param label Label of the <code>DownloadOption</code> to find
     * @return <code>DownloadOption</code> that matches label; null if none found
     */
    public static DownloadOption getByLabel(String label) {
        if (label == null) {
            return null;
        }

        List<DownloadOption> options = DataManager.getInstance().getConfiguration().getSidebarWidgetUsagePageDownloadOptions();
        if (options == null) {
            return null;
        }
        for (DownloadOption option : options) {
            if (label.equals(option.getLabel())) {
                return option;
            }
        }

        return null;
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @param label the label to set
     * @return this
     */
    public DownloadOption setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * @return the format
     */
    public String getFormat() {
        return format;
    }

    /**
     * @param format the format to set
     * @return this
     */
    public DownloadOption setFormat(String format) {
        this.format = format;
        return this;
    }

    /**
     * @return the boxSizeInPixel
     */
    public String getBoxSizeInPixel() {
        return boxSizeInPixel;
    }

    /**
     * @param boxSizeInPixel the boxSizeInPixel to set
     * @return this
     */
    public DownloadOption setBoxSizeInPixel(String boxSizeInPixel) {
        this.boxSizeInPixel = boxSizeInPixel;
        return this;
    }

    @Override
    public String toString() {
        return label;
    }
}
