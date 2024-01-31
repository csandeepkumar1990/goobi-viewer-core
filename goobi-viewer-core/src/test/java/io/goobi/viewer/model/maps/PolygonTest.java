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
package io.goobi.viewer.model.maps;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * @author florian
 *
 */
class PolygonTest {

    @Test
    void test() {
        Polygon p1 = new Polygon(Arrays.asList(new Point(Double.parseDouble("1"), 2)));
        Polygon p2 = new Polygon(Arrays.asList(new Point(Double.parseDouble("1"), 2)));
        Polygon p3 = new Polygon(Arrays.asList(new Point(2, 2)));

        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
    }

}
