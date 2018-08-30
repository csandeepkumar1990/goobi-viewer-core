/*!
 * This file is part of the Goobi viewer - a content presentation and management application for digitized objects.
 *
 * Visit these websites for more information.
 * - http://www.intranda.com
 * - http://digiverso.com
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 *//**
 * This loader loads a mesh from a ply file and adds a surface material of the
 * given color
 */
THREE.FBXMeshLoader = function(manager, color) {

	this.color = color;
	this.manager = (manager !== undefined) ? manager
			: THREE.DefaultLoadingManager;

};

THREE.FBXMeshLoader.prototype = {

	constructor : THREE.FBXMeshLoader,

	load : function(url, onLoad, onProgress, onError) {

		var loader = new THREE.FBXLoader(this.manager);
		var color = this.color;
		
		Q($.getJSON(url)).then(function(info) {
			console.log("loading object info = ", info);
			var baseResourceUrl = info.uri.substring(0, info.uri.lastIndexOf("/"));
			var objUrl = info.uri;

			loader.load(objUrl, function(geometry) {
				console.log("loaded mesh ", geometry);
//				geometry.computeVertexNormals();
//				var material = new THREE.MeshStandardMaterial({
//					color : color,
//					shading : THREE.FlatShading
//				});
//				var mesh = new THREE.Mesh(geometry, material);
//				mesh.castShadow = true;
//				mesh.receiveShadow = true;
				onLoad(geometry);
			}, function(status) {
				console.log("loading ", status);
			}, function(error) {
				console.log("error ", error)
			});
		});
	}
}

/**
 * This loader loads a mesh from a .gltf file which may point to a .bin file and a number of texture image files
 */
THREE.GLTFDRACOLoader = function(manager) {

	this.manager = (manager !== undefined) ? manager
			: THREE.DefaultLoadingManager;

};

THREE.GLTFDRACOLoader.prototype = {

	constructor : THREE.GLTFDRACOLoader,

	load : function(url, onLoad, onProgress, onError) {

		var gltfLoader = new THREE.GLTFLoader(this.manager);
		THREE.DRACOLoader.setDecoderPath( '/../three/dependencies/draco' );
		gltfLoader.setDRACOLoader( new THREE.DRACOLoader() );
		
		Q($.getJSON(url)).then(function(info) {
			console.log("loading object info = ", info);
			var baseResourceUrl = info.uri.substring(0, info.uri.lastIndexOf("/"));
			var objUrl = info.uri;

			gltfLoader.load(objUrl, function(gltf) {
				console.log("loaded gltf ", gltf);
//		        scene.add( gltf.scene );
				onLoad(gltf.scene);
			}, function(status) {
				console.log("loading ", status);
			}, function(error) {
				console.log("error ", error)
			});
		});
	}
}
/**
 * This loader loads a mesh and surface from an obj file and a mtl file. The
 * given url should be of the obj file and it is assumed the mtl url is the same
 * with .obj replaced by .mtl
 */
THREE.OBJMTLLoader = function(manager) {

	this.manager = (manager !== undefined) ? manager
			: THREE.DefaultLoadingManager;

};

THREE.OBJMTLLoader.prototype = {

	constructor : THREE.OBJMTLLoader,

	load : function(url, onLoad, onProgress, onError) {

		Q($.getJSON(url)).then(function(info) {
			console.log("loading object info = ", info);
			var baseResourceUrl = info.uri.substring(0, info.uri.lastIndexOf("/"));
			var objUrl = info.uri;
			var mtlUrl = info.resources.filter(function(resource) {
				return resource.endsWith(".mtl");
			}).shift();
			var textureLoader = new THREE.MTLLoader(this.manager);
			var objectLoader = new THREE.OBJLoader(this.manager);

			textureLoader.setTexturePath(mtlUrl.substring(0, mtlUrl.lastIndexOf("/")) + "/");

			var texture = textureLoader.load(mtlUrl, function(materials) {
				materials.preload();
				objectLoader.setMaterials(materials);
				objectLoader.load(objUrl, onLoad, onProgress, onError);

			}, onProgress, onError);
		});
	}
}

/**
 * This loader loads a mesh from a ply file and adds a surface material of the
 * given color
 */
THREE.PLYMeshLoader = function(manager, color) {

	this.color = color;
	this.manager = (manager !== undefined) ? manager
			: THREE.DefaultLoadingManager;

};

THREE.PLYMeshLoader.prototype = {

	constructor : THREE.PLYMeshLoader,

	load : function(url, onLoad, onProgress, onError) {

		var loader = new THREE.PLYLoader(this.manager);
		var color = this.color;
		
		Q($.getJSON(url)).then(function(info) {
			console.log("loading object info = ", info);
			var baseResourceUrl = info.uri.substring(0, info.uri.lastIndexOf("/"));
			var objUrl = info.uri;
		
			loader.load(objUrl, function(geometry) {
				geometry.computeVertexNormals();
				var material = new THREE.MeshStandardMaterial({
					color : color,
					shading : THREE.FlatShading
				});
				var mesh = new THREE.Mesh(geometry, material);
				mesh.castShadow = true;
				mesh.receiveShadow = true;
				onLoad(mesh);
			}, onProgress, onError);
		});
	}
}

/**
 * This loader loads a mesh from a ply file and adds a surface material of the
 * given color
 */
THREE.STLMeshLoader = function(manager, color) {

	this.color = color;
	this.manager = (manager !== undefined) ? manager
			: THREE.DefaultLoadingManager;

};

THREE.STLMeshLoader.prototype = {

	constructor : THREE.STLMeshLoader,

	load : function(url, onLoad, onProgress, onError) {
		var loader = new THREE.STLLoader(this.manager);
		var color = this.color;

		Q($.getJSON(url)).then(function(info) {
			console.log("loading object info = ", info);
			var baseResourceUrl = info.uri.substring(0, info.uri.lastIndexOf("/"));
			var objUrl = info.uri;
            console.log("loader ", loader);
		
    			loader.load(objUrl, function(geometry) {
    				geometry.computeVertexNormals();
    				var material = new THREE.MeshStandardMaterial({
    					color : color,
    					shading : THREE.FlatShading
    				});
    				var mesh = new THREE.Mesh(geometry, material);
    				mesh.castShadow = true;
    				mesh.receiveShadow = true;
    				onLoad(mesh);
    			}, onProgress, onError);

		})
		.catch(function(error) {
		    onError(error);
		});
	}
}

/**
 * This loader loads a mesh from a ply file and adds a surface material of the
 * given color
 */
THREE.TDSMeshLoader = function(manager, color) {

	this.color = color;
	this.manager = (manager !== undefined) ? manager
			: THREE.DefaultLoadingManager;

};

THREE.TDSMeshLoader.prototype = {

	constructor : THREE.TDSMeshLoader,

	load : function(url, onLoad, onProgress, onError) {
		
		var geometryLoader = new THREE.TDSLoader(this.manager);
		var textureLoader = new THREE.TextureLoader();
		
		Q($.getJSON(url))
		.then(function(info) {
			console.log("loading object info = ", info);
			var objUrl = info.uri;
			var textureUrls = info.resources.filter(function(resource) {
				return resource.match(/je?pg|png$/i);
			});

		
			geometryLoader.load(objUrl, function(object) {
				console.log("LOADING 3DS OBJECT", object);
				// geometry.computeVertexNormals();
				
				if(object instanceof THREE.Mesh) {
					console.log("object is already a mesh");
				}

				var textures = textureUrls
				.map(function(url) {
					return textureLoader.load( url );
				})
				console.log("loaded textures ", textures);
				
				object.traverse( function ( child ) {
	
					if ( child instanceof THREE.Mesh ) {
						var texture = textures.shift();
						child.material.map = texture;
					}
	
				} );
				
				object.castShadow = true;
				object.receiveShadow = true;
				onLoad(object);
			}, onProgress, onError);

		});
	}
}

/**
 * This loader an object from an x3d file
 */
X3DLoader = function() {
};

X3DLoader.prototype = {

    constructor : X3DLoader,

    load : function($image, url, onLoad, onProgress, onError) {

        return Q($.getJSON(url)).then(function(info) {
            console.log("Loading x3dom ", info);
            var imageWidth = $image.width() + "px";
            var imageHeight =  $image.height() + "px";
            var x3d = '<x3d width="' + imageWidth + '" height="' + imageHeight + '"><scene><inline url="' + info.uri + '"></inline></scene></x3d>';
            $image.get(0).innerHTML += x3d;
//            $image.append($x3d);
            x3dom.reload()
            onLoad();
        });
    }
}

var WorldGenerator = (function() {
    var _lightIntensity = 0.7;
	var _defaultConfig = {
	        camera: {
                fieldOfView: 35,
                viewPadding: 0,
                nearPlane: 0.1,
                farPlane: 10000,
                position:  { x:0, y:0, z: 0 },
                offset:  { x:0, y:0, z:0 },
            },
    		container: {
    			id: "container"
    		},
    		light: {
                background: {
                    color: 0xffffff
                },
                ambient: {
                    color: 0x909090,
                    intensity: _lightIntensity
                },
                directional: [
//                  {//front spot
//                      color: 0xaaaaaa,
//                      intensity: lightIntensity,
//                      position: { x:0, y:0, z:10 },
//                      castShadow: true,
//                      showHelper: false
//                  },
//                  {//back spot
//                      color: 0xaaaaaa,
//                      intensity: lightIntensity,
//                      position: { x:0, y:0, z:-10 },
//                      castShadow: true,
//                      showHelper: false
//                  },
                    {//top spot
                        color: 0xaaaaaa,
                        intensity: _lightIntensity,
                        position: { x:0, y:10, z:0 },
                        castShadow: true,
                        showHelper: false
                    },
                    {//bottom spot
                        color: 0xaaaaaa,
                        intensity: _lightIntensity,
                        position: { x:0, y:-10, z:0 },
                        castShadow: true,
                        showHelper: false
                    },
                    {//left spot
                        color: 0xaaaaaa,
                        intensity: _lightIntensity,
                        position: { x:-10, y:0, z:0 },
                        castShadow: true,
                        showHelper: false
                    },
                    {//right spot
                        color: 0xaaaaaa,
                        intensity: _lightIntensity,
                        position: { x:10, y:0, z:0 },
                        castShadow: true,
                        showHelper: false
                    }
                ]
            }
    };
	
	var _getObjectLoader = function(config, manager) {
		
		var objectInfoUrl = config.url;
		var objectMainUrl = config.url.replace("/info.json", "");
		var suffix = objectMainUrl.substring(objectMainUrl.lastIndexOf(".")+1);
		switch(suffix.toLowerCase()) {
		case "obj":
			return new THREE.OBJMTLLoader(manager);
		case "ply":
			return new THREE.PLYMeshLoader(manager, config.material.color);
		case "stl":
			return new THREE.STLMeshLoader(manager, config.material.color);
		case "fbx":
			return new THREE.FBXMeshLoader(manager, config.material.color);
		case "3ds":
			return new THREE.TDSMeshLoader(manager);
		case "gltf":
            return new THREE.GLTFDRACOLoader(manager);
		default:
			console.log("no loader defined for " + suffix);
		}
	}
	
	var _getDistance = function(vector) {
		return Math.sqrt(vector.x*vector.x + vector.y*vector.y + vector.z*vector.z);
	}
	
	var _getRotated = function(origRotation, rotateBy) {
	    var rot = {
	            x : rotateBy.x === undefined ? origRotation._x*180/Math.PI : origRotation._x*180/Math.PI+rotateBy.x,
	            y : rotateBy.y === undefined ? origRotation._y*180/Math.PI : origRotation._y*180/Math.PI+rotateBy.y,
	            z : rotateBy.z === undefined ? origRotation._z*180/Math.PI : origRotation._z*180/Math.PI+rotateBy.z,
	    }
	    return rot;
	}
	
	var _rotate = function(point, degrees, axis, pivot) {
	    if(!pivot) {
	        pivot = {x:0, y:0, z:0};
	    }
	    var rad = degrees/180.0*Math.PI;
	
	    var p0 = {
	            x: point.x-pivot.x,
	            y: point.y-pivot.y,
	            z: point.z-pivot.z
	    }
	    var p1 = {x:p0.x, y:p0.y, z:p0.z};
	    switch(axis) {
	        case 'x':
	            p1.y = p0.y * Math.cos(rad) - p0.z * Math.sin(rad);
	            p1.z = p0.y * Math.sin(rad) + p0.z * Math.cos(rad);
	            break;
	        case 'y':
                p1.x = p0.x * Math.cos(rad) - p0.z * Math.sin(rad);
                p1.z = p0.x * Math.sin(rad) + p0.z * Math.cos(rad);
                break;
	        case 'z':
                p1.x = p0.x * Math.cos(rad) - p0.y * Math.sin(rad);
                p1.y = p0.x * Math.sin(rad) + p0.y * Math.cos(rad);
                break;
            default:
                throw "Third parameter 'axis' must be one of 'x', 'y' or 'z'";
	    }
	    var p2 = {
                x: p1.x+pivot.x,
                y: p1.y+pivot.y,
                z: p1.z+pivot.z
        }
	    return p2;
	}
	
	var Generator = {
			
			create: function(config) {
				
				var localConfig = {};
				$.extend(true, localConfig, _defaultConfig);
				$.extend(true, localConfig, config);
				
				return new World(localConfig);
			}
			
	}
	
    var World = function(config) {
			console.log("Constructing world with config ", config);
			this.config = config;
			this.time = 0;
			this.container = document.getElementById(config.container.id);
			this.scene = new THREE.Scene();
			// CAMERA//
			this.camera = new THREE.PerspectiveCamera(
					config.camera.fieldOfView,
					this.container.clientWidth / this.container.clientHeight,
					config.camera.nearPlane,
					config.camera.farPlane);
			this.camera.position.set(config.camera.position.x, config.camera.position.y, config.camera.position.z)              

			// RENDERER//
			this.renderer = new THREE.WebGLRenderer();
			this.renderer.setSize(this.container.clientWidth, this.container.clientHeight);
			this.renderer.setClearColor( config.light.background.color );
			this.renderer.shadowMap.enabled = true;
			this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
			this.container.appendChild(this.renderer.domElement);
			// CONTROLS//
			this.controls = new THREE.OrbitControls( this.camera, this.container );
			// LOADING MANAGER//
			this.loadingManager = new THREE.LoadingManager();
			this.loadingManager.onProgress = function(item, loaded, total) {
				console.log("Progress on load ", item, loaded, total);
			};
			
//			this.addSphere({
//			    size: 3,
//			    material: {
//			        color: 'blue'
//			    },
//			    position: {
//			        x:0,y:0,z:0
//			    }
//			})
			
			// LIGHTS
			this.directionalLights = [];
			if(config.light.ambient) {
				var light = new THREE.AmbientLight( config.light.ambient.color, config.light.ambient.intensity );
				this.scene.add(light);
				this.ambientLight = light;
			}
			if(config.light.directional) {
				var lights = config.light.directional
				if(!$.isArray(lights)) {
					lights = [lights];
				}
				for(var i=0; i < lights.length; i++) {		
					var lightConfig = lights[i];
					var light = this.createShadowedLight(
							lightConfig.position,
							lightConfig.color,
							lightConfig.intensity,
							_getDistance(lightConfig.position),
							lightConfig.castShadow,
							lightConfig.showHelper);
					this.directionalLights.push(light);
					this.scene.add(light);
				}			
			}

			
			this.tick = new Rx.Subject();
		}
		
	World.prototype.initControls = function(object, controlsConfig, objectConfig) {
		    var world = this;
		    if(controlsConfig.xAxis) {
		        var $rotateLeftX = $(controlsConfig.xAxis.rotateLeft);
		        if($rotateLeftX.length > 0) {
		            $rotateLeftX.on("click", function(event) {
		                world.rotate(object, _getRotated(object.rotation, {x:-90}));
		                world.center(object, object.position);
//		                world.rotateLights(-90, 'x');
		            })
		        }
		        var $rotateRightX = $(controlsConfig.xAxis.rotateRight);
                if($rotateRightX.length > 0) {
                    $rotateRightX.on("click", function(event) {
                        world.rotate(object, _getRotated(object.rotation, {x:90}));
                        world.center(object, object.position);
//                        world.rotateLights(90, 'x');
                    })
                }
		    }
		    
		    if(controlsConfig.yAxis) {
                var $rotateLeftY= $(controlsConfig.yAxis.rotateLeft);
                if($rotateLeftY.length > 0) {
                    $rotateLeftY.on("click", function(event) {
                        world.rotate(object, _getRotated(object.rotation, {y:-90}));
                        world.center(object, object.position);
//                        world.rotateLights(-90, 'y');
                    })
                }
                var $rotateRightY = $(controlsConfig.yAxis.rotateRight);
                if($rotateRightY.length > 0) {
                    $rotateRightY.on("click", function(event) {
                        world.rotate(object, _getRotated(object.rotation, {y:90}));
                        world.center(object, object.position);
//                        world.rotateLights(90, 'y');
                    })
                }
            }
		    
		    if(controlsConfig.zAxis) {
                var $rotateLeftZ = $(controlsConfig.zAxis.rotateLeft);
                if($rotateLeftZ.length > 0) {
                    $rotateLeftZ.on("click", function(event) {
                        world.rotate(object, _getRotated(object.rotation, {z:-90}));
                        world.center(object, object.position);
//                        world.rotateLights(-90, 'z');
                    })
                }
                var $rotateRightZ = $(controlsConfig.zAxis.rotateRight);
                if($rotateRightZ.length > 0) {
                    $rotateRightZ.on("click", function(event) {
                        world.rotate(object, _getRotated(object.rotation, {z:90}));
                        world.center(object, object.position);
//                        world.rotateLights(90, 'z');
                    })
                }
            }
		    
            if(controlsConfig.position) {                    
                var $resetPosition = $(controlsConfig.position.reset);
                if($resetPosition.length > 0) {
                    $resetPosition.on("click", function(event) {
                        world.controls.reset();
                    })
                }
            }
		}
		
	World.prototype.rotateLights = function(degrees, axis) {
		    for(var index in this.directionalLights) {
		        var light = this.directionalLights[index];
		        var pos = _rotate(light.position, degrees, axis);
	            light.position.x = pos.x;
	            light.position.y = pos.y;
	            light.position.z = pos.z;
		    } 
		}
		
		/**
		 * config.size: diameter of sphere config.material.color: color of the
		 * sphere config.material.opacity: if defined, opacity of sphere
		 * config.offset: Vector3D defining the offset of the sphere's center
		 * from point 0
		 */
	World.prototype.addSphere = function(config) {
			var sphereGeometry = new THREE.SphereGeometry(config.size/2, 64,64);
			var sphereMaterial = new THREE.MeshLambertMaterial({
				color: config.material.color,
				transparent: config.material.opacity ? true : false,
				opacity: config.material.opacity
			});
			var sphere = new THREE.Mesh(sphereGeometry, sphereMaterial);

			this.center(sphere, config.position);
			if(config.focus) {							
				this.zoomToObject(sphere, this.config.camera.viewPadding, this.config.camera.fieldOfView);
			}
			if(config.onTick) {
				this.tick.subscribe(function(time) {
					config.onTick(sphere, time);
				})
			}
			this.scene.add(sphere);
		}
	World.prototype.addBlock = function(config) {
			var geometry = new THREE.BoxGeometry(
					config.box.max.x-config.box.min.x, 
					config.box.max.y-config.box.min.y, 
					config.box.max.z-config.box.min.z);
			var material = new THREE.MeshLambertMaterial({
				color: config.material.color,
				transparent: config.material.opacity ? true : false,
				opacity: config.material.opacity
			});
			var box = new THREE.Mesh(geometry, material);

			this.center(box, config.position);
			if(config.focus) {							
				this.zoomToObject(box, this.config.camera.viewPadding, this.config.camera.fieldOfView);
			}
			if(config.onTick) {
				this.tick.subscribe(function(time) {
					config.onTick(box, time);
				})
			}
			this.scene.add(box);
		}
	World.prototype.addPlane = function(config) {
			var geometry = new THREE.PlaneGeometry(config.size, config.size);
			var material = new THREE.MeshLambertMaterial({
				color: config.material.color,
				transparent: config.material.opacity ? true : false,
				opacity: config.material.opacity
			});
			var plane = new THREE.Mesh(geometry, material);
			plane.receiveShadow = true;
			plane.position.set(config.offset.x, config.offset.y, config.offset.z);
			plane.rotation.set(config.rotation.x * Math.PI / 180, config.rotation.y * Math.PI / 180, config.rotation.z * Math.PI / 180);
			if(config.onTick) {
				this.tick.subscribe(function(time) {
					config.onTick(plane, time);
				})
			}
			this.scene.add(plane);
		}
	World.prototype.loadObject = function(config) {
		    console.log("Load object ", config);
		    
			var loader = _getObjectLoader(config, this.loadingManager);
			var world = this;
			var deferred = Q.defer();
			if(loader) {
				loader.load(config.url, function(object) {
					world.addObject(object, config);
					deferred.resolve(object);
				}, function(){}, function(error) {
					deferred.reject(error);
				});
			}
			return deferred.promise;
		}
	World.prototype.addObject = function(object, config) {
			
			this.setSize(object, config.size);
			this.rotate(object, config.rotation);
			this.center(object, config.position);
			this.initControls(object, this.config.controls, config);
			if(config.focus) {				
				this.zoomToObject(object, this.config.camera.viewPadding, this.config.camera.fieldOfView);
			}
			if(config.onTick) {
				this.tick.subscribe(function(time) {
					config.onTick(object, time);
				})
			}
			this.scene.add(object);
			this.controls.saveState();
			this.object = object;
			return object;
		}
	World.prototype.rotate = function(object, rotation) {
		    console.log("rotate object by ", rotation);
			object.rotation.set(rotation.x * Math.PI / 180, rotation.y * Math.PI / 180, rotation.z * Math.PI / 180);
		}
	World.prototype.center = function(object, position) {
			var sphere = this.getBoundingSphere(object);
			var offset = sphere.center;
			object.position.set(position.x-offset.x, position.y-offset.y, position.z-offset.z);
		}
	World.prototype.setSize = function(object, size) {
			var r = this.getBoundingSphere(object).radius;
			var scale = size/r;
			object.scale.set(scale, scale, scale);
		}
	World.prototype.getBoundingSphere = function(object) {
			var sphere;
			if(object.geometry && object.geometry.computeBoundingSphere) {
				if(!object.geometry.boundingSphere) {				
					object.geometry.computeBoundingSphere();
				}
				sphere = object.geometry.boundingSphere;
				sphere.radius *= object.scale.x;
			} else {
				var box = new THREE.Box3().setFromObject( object );
				sphere = box.getBoundingSphere();
			}
			return {
				center: sphere.center,
				radius: sphere.radius
			}
		}
	World.prototype.zoomToObject = function(object, padding, fieldOfView) {
			var sphere = this.getBoundingSphere(object);
			this.zoomToPosition(sphere.center, 2*sphere.radius+padding, fieldOfView);
		}
	World.prototype.zoomToPosition = function(position, size, fieldOfView) {
			var d = size/(2*Math.sin(Math.PI / 180 * fieldOfView/2));
			this.camera.position.set(position.x, position.y, position.z+d);
//			this.camera.lookAt(position);
		}
	World.prototype.createShadowedLight = function( position, color, intensity, d, castShadow, showHelper) {
			var directionalLight = new THREE.DirectionalLight( color, intensity );
			directionalLight.position.set( position.x, position.y, position.z );
			this.scene.add( directionalLight );
			directionalLight.castShadow = castShadow;
			directionalLight.shadow.camera.left = -d;
			directionalLight.shadow.camera.right = d;
			directionalLight.shadow.camera.top = d;
			directionalLight.shadow.camera.bottom = -d;
			directionalLight.shadow.camera.near = d/10;
			directionalLight.shadow.camera.far = d*10;
			directionalLight.shadow.mapSize.width = 1024;
			directionalLight.shadow.mapSize.height = 1024;
			directionalLight.shadow.bias = -0.005;
			
			if(showHelper) {				
				var helper = new THREE.DirectionalLightHelper( directionalLight, d/10 );
				this.scene.add( helper );
			}
			
			return directionalLight;
		}
	World.prototype.render = function() {
		    if(this.disposed) {
		        return;
		    }
			this.time +=1;
			this.renderer.render(this.scene, this.camera);
			this.tick.onNext(this.time);
			var world = this;
			window.requestAnimationFrame(function() {
				world.render();
			});
		}
		
	World.prototype.dispose = function() {
		    this.disposeObject(this.object);
		    this.disposed = true;
		}
		
	World.prototype.disposeObject = function(object) {
		    for(var index in object.children) {
		        this.disposeObject(object.children[index]);
		    }
		    if(object.geometry) {
		        if(object.geometry.dispose) {		            
		            object.geometry.dispose();
		        }
		        object.geometry = undefined;
		    }
		    if(object.material) {
		        if(object.material.map) {
		            if(object.material.map.dispose) {		                
		                object.material.map.dispose();
		            }
	                object.material.map = undefined;
	            }
		        if(object.material.dispose) {		            
		            object.material.dispose();
		        }
                object.material = undefined;
            }
		}
	
	return Generator;
	
})();
//# sourceMappingURL=objectView.js.map