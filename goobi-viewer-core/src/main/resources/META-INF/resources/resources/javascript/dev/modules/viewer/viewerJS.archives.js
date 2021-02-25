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
 */

var viewerJS = ( function( viewer ) {
    'use strict';

    var _debug = false;
    
    var _defaults = {
            initHcSticky: false,
            initSearch: false,
            initTextTree: false
    };
 
    viewer.archives = { 
        init: function( config ) {
            if ( _debug ) {
                console.log( '##############################' );
                console.log( 'viewer.archivesSeparate.init' );
                console.log( '##############################' );
                console.log( 'viewer.archivesSeparate.init: config - ', config );
            }
            this.config = $.extend( true, {}, _defaults, config );
            
            jQuery(document).ready(($) => {

                if(this.config.initHcSticky) {                    
                    this.initHcStickyWithChromeHack();
                }
                viewerJS.jsfAjax.success
                .subscribe(e => {
                    if(this.config.initHcSticky) {                        
                        this.refreshStickyWithChromeHack();
                    }
                    this.setLocation(e.source);
                });
                
                if(this.config.initSearch) {
                    this.initSearch();
                }
            	 
            	 if(this.config.initTextTree) {
            	     this.initTextTree();
            	 }
            	 
            	
            });            
            
        },
        
        initTextTree: function() {
         // toggle text-tree view from stairs to one line
            $('body').on("click", '.archives__text-tree', function() {
                $('.archives__text-tree').toggleClass('-showAsOneLine');
            });
        },
        
        initSearch: function() {
            /* check search field for input value and show clear button */
            if(!$('.archives__search-input').val() == ''){
                $('.archives__search-clear').show();
            }
            $('body').on("click", '.archives__search-clear', function(){
                /* clear value on click*/
            $('.archives__search-input').val("");
                /* trigger empty search on click */
                $('.archives__search-submit-button').click();
            });

             // auto submit search after typing
             let timeSearchInputField = 0;

             $('body').on('input', '.archives__search-input', function () {
                 // Reset the timer while still typing
                 clearTimeout(timeSearchInputField);

                 timeSearchInputField = setTimeout(function() {
                     // submit search query
                     $('.archives__search-submit-button').click();
                 }, 1300);
             });
        },
        
        /**
         * In chome with small window size (1440x900) hcSticky breaks on ajax reload if the page is scrolled
         * all the way to the button. To prevent this we quickly scroll to the top, refresh hcSticky and then scroll back down.
         * The scolling appears to be invisible to the user, probably because it is reset before actually being carried out
         */
        refreshStickyWithChromeHack: function() {
            let currentScrollPosition = $('html').scrollTop();
            $('html').scrollTop(0);
            this.refreshHcSticky();
            if(currentScrollPosition) {
                $('html').scrollTop(currentScrollPosition);
            }
        },
        
        refreshHcSticky: function() {
            if(_debug)console.log("update hc sticky");
//            $('.archives__left-side, .archives__right-side').hcSticky('refresh');
            $('.archives__left-side, .archives__right-side').hcSticky('update', {
                stickTo: $('.archives__wrapper')[0],
                top: 80,
                bottom: 20,
                responsive: {
                    993: {
                      disable: true
                    }
                }
           });
        },
        
        /**
         * In chome with small window size (1440x900) hcSticky breaks on page load if the view was previously scrolled
         * all the way to the button. To prevent this we scroll 5 px up before refreshing hcSticky.
         * The scolling appears to be invisible to the user, probably because it is reset before actually being carried out
         */
        initHcStickyWithChromeHack: function() {
            let currentScrollPosition = $('html').scrollTop();
            $('html').scrollTop(currentScrollPosition-5);
            this.initHcSticky();
//            if(currentScrollPosition) {
//                $('html').scrollTop(currentScrollPosition);
//            }
        },

        
        initHcSticky: function() {
            if(_debug)console.log("init hc sticky");
                        
            // Sticky right side of archives view
            $('.archives__right-side').hcSticky({
                stickTo: $('.archives__wrapper')[0],
                top: 80,
                bottom: 20,
                responsive: {
                    993: {
                      disable: true
                    }
                }
            });
            
            // Sticky left side of archives view
            $('.archives__left-side').hcSticky({
                stickTo: $('.archives__wrapper')[0],
                top: 80,
                bottom: 20,
                responsive: {
                    993: {
                      disable: true
                    }
                }
            });
        },
        
        setLocation: function(element) {
            if(_debug)console.log(" clicked data-select-entry", element);
            let select = $(element).attr("data-select-entry");
            let url = window.location.origin + window.location.pathname;
            if(select) {
                url += ("?selected=" + select + "#selected");
            }
            if(_debug)console.log("set url ", url);
            window.history.pushState({}, '', url);
        }
    };


    return viewer;
} )( viewerJS || {}, jQuery );