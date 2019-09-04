/**
 * This file is part of the Goobi viewer - a content presentation and management
 * application for digitized objects.
 * 
 * Visit these websites for more information. - http://www.intranda.com -
 * http://digiverso.com
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * @description Base-Module which initialize the global admin object. * 
 * @version 3.4.0
 * @module Crowdsourcing.js
 * @requires jQuery
 */
var Crowdsourcing = ( function(crowdsourcing) {
    'use strict';

    crowdsourcing.AreaSelector = function(item, multiple, colors) {
        
        this.drawer = null;
        this.transformer = null;
        this.rects = [];
        this.finishedDrawing = new Rx.Subject();
        this.finishedTransforming = new Rx.Subject();
        this.lastRectangleId = -1;
        
        this.crowdsourcingItem = item;
        this.multiRect = multiple;
        this.colors = colors ? new ImageView.ColorIterator(colors) : new ImageView.ColorIterator(ImageView.ColorIterator.randomColor);
    }
    
    crowdsourcing.AreaSelector.prototype.init = function() {
        this.crowdsourcingItem.onImageOpen( (imageView) => {
            if(!this.drawer) {
                this.createDrawer(imageView);
                this.createTransformer(imageView);
            }
        });
        
    }

    crowdsourcing.AreaSelector.prototype.createDrawer = function (imageView) {
        this.drawer = new ImageView.Draw(imageView.viewer, this.getStyle(), (e) => e.shiftKey);
        this.drawer.finishedDrawing().subscribe(function(rect) {
            if(this.rect && !this.multiRect) {
                this.removeOverlay(this.rect)
            }
            rect.id = ++this.lastRectangleId;
            rect.style = this.drawer.style;
            this.drawer.style = this.getStyle();
    		rect.draw();
            if(this.transformer) {	   
    			this.transformer.addOverlay(rect);
            }
    		this.rect = rect;
    		this.rects.push(this.rect);
    	}.bind(this))
    	this.drawer.finishedDrawing().map((rect) => _getResultObject(rect, imageView)).subscribe(this.finishedDrawing);
    }
    
    crowdsourcing.AreaSelector.prototype.reset = function() {
        this.rects.forEach(function(rect) {
            this.transformer.removeOverlay(rect);
            rect.remove();
        }.bind(this));
        this.rects = [];
    }

    crowdsourcing.AreaSelector.prototype.createTransformer = function(imageView) {
        this.transformer = new ImageView.Transform(imageView.viewer, this.drawStyle, (e) => !e.shiftKey); 
        this.transformer.finishedTransforming().map((rect) => _getResultObject(rect, imageView)).subscribe(this.finishedTransforming);

    }

    crowdsourcing.AreaSelector.prototype.getStyle = function(color) {
        let style = {
        		borderWidth: 2,
        		borderColor: color ? color : this.colors.next()
        }
        return style;
    }
    
    crowdsourcing.AreaSelector.prototype.getRect = function(id) {
        return this.rects.find( rect => rect.id == id);
    }
    
    crowdsourcing.AreaSelector.prototype.addOverlay = function(object, viewer) {
        
        let rect = ImageView.CoordinateConversion.convertRectFromImageToOpenSeadragon(object.region, viewer, viewer.world.getItemAt(0).source);
        let overlay = new ImageView.Overlay(rect, viewer, this.getStyle(object.color));
        overlay.id = object.id;
        this.lastRectangleId = overlay.id;
        overlay.draw();
        if(this.transformer) {     
            this.transformer.addOverlay(overlay);
        }
        this.rects.push(overlay);
    }

    crowdsourcing.AreaSelector.prototype.removeOverlay = function(object) {
        
        let rect = this.rects.find(rect => rect.id == object.id);
        if(rect) {
            rect.remove();
            let index = this.rects.indexOf(rect);
            if(index > -1) {                
                this.rects.splice(index, 1);
            }
            if(this.rect == rect) {
                this.rect = undefined;
            }
            if(this.transformer) {              
                this.transformer.removeOverlay(rect)
            }
        }
    }

    
    function _getResultObject(rect, image) {
        let result = {
                id: rect.id,
                color: rect.style.borderColor,
                region: ImageView.CoordinateConversion.convertRectFromOpenSeadragonToImage(rect.rect, image.viewer, image.viewer.world.getItemAt(0).source)
        }
        return result;
    }

return crowdsourcing;

} )( Crowdsourcing );

