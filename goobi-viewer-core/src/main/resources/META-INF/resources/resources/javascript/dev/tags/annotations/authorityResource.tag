<authorityResource>
	<div class="annotation__body__authority">
		<div if="{normdataList.length == 0}">{authorityId}</div>
		
		<dl class="annotation__body__authority__normdata_list" each="{normdata in normdataList}">
				<dt class="normdata_list__label">{normdata.property}: </dt>
				<dd class="normdata_list__value">{normdata.value}</dd>
		</dl>
	</div>
<script>
    this.normdataList = [];

	this.on("mount", () => {
		this.authorityId = this.opts.resource.id;
	    this.url = this.opts.resturl + "authority/resolver?id=" + this.unicodeEscapeUri(this.authorityId) + "&template=ANNOTATION&lang=" + this.opts.currentlang
		this.update();
	    fetch(this.url)
	    .then(response => {
	        if(!response.ok) {
	            throw "Error: " + response.status;
	        } else {
	            return response;
	        }
	    })
	    .then(response => response.json())
	    .then(response => {
	        this.normdataList = this.parseResponse(response);
	    })
	    .catch(error => {
	        console.error("failed to load ", this.url, ": " + error);
	    })
	    .then(() => this.update());
	})

	unicodeEscapeUri(uri) {
    	return uri.replace(/\//g, 'U002F').replace('/\\/g','U005C').replace('/?/g','U003F').replace('/%/g','U0025');
	}
	
	parseResponse(jsonResponse) {
	    let normdataList = [];
	    $.each( jsonResponse, (i, object ) => {
            $.each( object, ( property, value ) => {
                let stringValue = value.map(v => v.text).join("; ");
                normdataList.push({property: property, value:stringValue});
            });
	    });
	    return normdataList;
	}


</script>

</authorityResource>