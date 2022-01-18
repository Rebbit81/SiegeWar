package com.gmail.goosius.siegewar.integration.maptowny;

import com.gmail.goosius.siegewar.TownOccupationController;

import me.silverwolfg11.maptowny.MapTowny;
import me.silverwolfg11.maptowny.managers.TownyLayerManager;

public class MapTownyIntegration {
	private final MapTowny plugin;
	
	public MapTownyIntegration(MapTowny plugin) {
		this.plugin = plugin;
		registerReplacements();
	}

	private void registerReplacements() {
		TownyLayerManager mgr = plugin.getLayerManager(); 
		mgr.registerReplacement("%occupier%",
			t -> TownOccupationController.isTownOccupied(t) 
			? TownOccupationController.getTownOccupier(t).getName()
			: "");
	}

}
