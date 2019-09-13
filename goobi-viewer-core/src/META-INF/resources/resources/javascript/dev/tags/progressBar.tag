<progressBar>

	<div class="goobi-progress-bar">
		<div each="{value, index in this.values}" 
		class="goobi-progress-bar__bar" style="width: {getRelativeWidth(value)}; background-color:{colors[index]}">
		</div>
	</div>

<script>
	this.values = JSON.parse(this.opts.values);
	this.colors = JSON.parse(this.opts.colors);
	console.log("init progressbar ", this.values, this.colors);
	
	this.on("mount", function() {
	    let bar = this.root.querySelector(".goobi-progress-bar");
	    this.totalBarWidth = bar.offsetWidth;
	    
		this.update();
	})

	getRelativeWidth(value) {
		    let barWidth = value/this.opts.total*this.totalBarWidth;
		    return barWidth + "px"; 
	}
	
	loaded() {
	    console.log("on load");
	}

</script>

</progressBar>