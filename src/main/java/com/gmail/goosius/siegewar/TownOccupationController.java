package com.gmail.goosius.siegewar;

import com.gmail.goosius.siegewar.metadata.SiegeMetaDataController;
import com.gmail.goosius.siegewar.metadata.TownMetaDataController;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TownOccupationController {

    private static Map<Nation, List<Town>> nationTownsOccupationMap = new HashMap<>();
    private final static String NATION_TOWNS_OCCUPATION_MAP_LOCK = "";

    public static void clearTownOccupations() {
        nationTownsOccupationMap.clear();
    }

    public static boolean loadAll() {
        try {
            System.out.println(SiegeWar.prefix + "Loading town occupation data...");
            loadTownOccupationData();
            System.out.println(SiegeWar.prefix + "Town occupation data loaded.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void loadTownOccupationData() {
        String occupyingNationUUID;
        Nation occupyingNation = null;
        List<Town> occupiedTowns;

        for (Town town : TownyUniverse.getInstance().getTowns()) {
            occupyingNationUUID = TownMetaDataController.getOccupyingNationUUID(town);
            if (occupyingNationUUID == null) {
                //Occupier uuid not found. Fix data if required and move to next town
                if (town.isConquered()) {
                    town.setConquered(false);
                    town.save();
                }
                continue;
            } else {
                //Occupier uuid found
                if(TownyUniverse.getInstance().hasNation(UUID.fromString(occupyingNationUUID))) {
                    //Nation UUID is known to towny. Fixed data if required.
                    if (!town.isConquered()) {
                        town.setConquered(true);
                        town.save();
                    }
                } else {
                    //Nation UUID is not known to Towny. Fix data if required and move to next town
                    if(town.isConquered()) {
                        town.setConquered(false);
                        town.save();
                        continue;
                    }
                }
            }

            //At this point, valid occupier data has been found
            //Get nation
            occupyingNation = TownyUniverse.getInstance().getNation(UUID.fromString(occupyingNationUUID));
            //Populate the map
            if (nationTownsOccupationMap.containsKey(occupyingNation)) {
                occupiedTowns = nationTownsOccupationMap.get(occupyingNation);
                occupiedTowns.add(town);
            } else {
                occupiedTowns = new ArrayList<>();
                occupiedTowns.add(town);
                nationTownsOccupationMap.put(occupyingNation, occupiedTowns);
            }

        }
    }

    public static List<Town> getOccupiedForeignTowns(Nation nation) {
        Map<Nation, List<Town>> nationTownsOccupationMapCopy = new HashMap<>(nationTownsOccupationMap);

        if (nationTownsOccupationMapCopy.containsKey(nation)) {
            return new ArrayList<>(nationTownsOccupationMapCopy.get(nation));
        } else {
            return new ArrayList<>();
        }
    }

    public static List<Town> getOccupiedHomeTowns(Nation nation) {
        List<Town> occupiedHomeTowns = new ArrayList<>();
        Map<Nation, List<Town>> nationTownsOccupationMapCopy = new HashMap<>(nationTownsOccupationMap);

        for(List<Town> occupiedTowns: nationTownsOccupationMapCopy.values()) {
            for(Town occupiedTown: occupiedTowns) {
                try {
                    if(occupiedTown.hasNation() && occupiedTown.getNation() == nation)
                        occupiedHomeTowns.add(occupiedTown);
                } catch (NotRegisteredException ignored) {}
            }
        }
        return occupiedHomeTowns;
    }

    /**
     * Determine if a town is occupied.
     *
     * In the SW codebase, do NOT use town.isConquered() for that purpose.
     *
     * Because the logic behind town.conquered is not controlled by siegewar.
     * For example, Towny may potentially set it to false if a town leaves a nation.
     *
     * Thus, town.isConquered() is never used within siegewar (except to correct errors).
     * It is used within Towny, to display whether a town is occupied.
     *
     */
     public static boolean isTownOccupied(Town occupiedTown) {
         String occupierUUID = TownMetaDataController.getOccupyingNationUUID(occupiedTown);
         if (occupierUUID == null) {
             if(occupiedTown.isConquered())
                 occupiedTown.setConquered(false); //Fix data if required
             return false;
         } else {
             if (!occupiedTown.isConquered())
                 occupiedTown.setConquered(true); //Fix data if required
             return true;
         }
    }

    /**
     * Get the occupying nation
     *
     * If there is any uncertainty whether the town is actually occupied,
     * make sure to call isTownOccupied(town) before calling this method
     *
     * @param occupiedTown the occupied town
     * @return the occupying nation.
     * @throws RuntimeException if no occupier is found
     */
    public static Nation getTownOccupier(Town occupiedTown) {
        String occupierUUID = TownMetaDataController.getOccupyingNationUUID(occupiedTown);
        if (occupierUUID == null) {
            throw new RuntimeException("Occupier not found");
        } else {
            if(TownyUniverse.getInstance().hasNation(UUID.fromString(occupierUUID))) {
                if(!occupiedTown.isConquered()) {
                    occupiedTown.setConquered(true); //Fix data if required
                    occupiedTown.save();
                }
                return TownyUniverse.getInstance().getNation(UUID.fromString(occupierUUID));
            } else {
                //Nation could not be loaded. Fix data
                TownMetaDataController.removeOccupationMetadata(occupiedTown);
                occupiedTown.setConquered(false);
                occupiedTown.save();
                throw new RuntimeException("Error loading occupier data for " + occupiedTown.getName() + " Data fixed automatically by de-occupying town");
            }
        }
    }

    public static void removeTownOccupation(Town occupiedTown) {
        //Remove occupation
        TownMetaDataController.removeOccupationMetadata(occupiedTown);
        occupiedTown.setConquered(false);
        occupiedTown.save();
        //Adjust occupation map
        synchronized (NATION_TOWNS_OCCUPATION_MAP_LOCK) {
            //Remove town if it appears anywhere in the occupation map
            for(Map.Entry<Nation, List<Town>> mapEntry: new HashMap<>(nationTownsOccupationMap).entrySet()) {
                if(mapEntry.getValue().contains(occupiedTown)) {
                    if(mapEntry.getValue().size() == 1) {
                        //This it the only town on the list
                        nationTownsOccupationMap.remove(mapEntry.getKey());
                    } else {
                        //There are more towns on the list
                        nationTownsOccupationMap.get(mapEntry.getKey()).remove(occupiedTown);
                    }
                    return; //Set operation finished
                }
            }
        }
    }

    public static void setTownOccupation(Town occupiedTown, @NotNull Nation occupyingNation) {
        //Add occupation
        TownMetaDataController.setOccupyingNationUUID(occupiedTown, occupyingNation.getUUID().toString());
        occupiedTown.setConquered(true);
        occupiedTown.save();
        //Adjust occupation map
        synchronized (NATION_TOWNS_OCCUPATION_MAP_LOCK) {
            if (nationTownsOccupationMap.containsKey(occupyingNation)) {
                //Nation already on map
                List<Town> occupiedTownsList = nationTownsOccupationMap.get(occupyingNation);
                occupiedTownsList.add(occupiedTown);
            } else {
                //Nation not yet on map
                List<Town> occupiedTownsList = new ArrayList<>();
                occupiedTownsList.add(occupiedTown);
                nationTownsOccupationMap.put(occupyingNation, occupiedTownsList);
            }
        }
    }

    public static void removeForeignTownOccupations(Nation nation) {
        synchronized (NATION_TOWNS_OCCUPATION_MAP_LOCK) {
            if(nationTownsOccupationMap.containsKey(nation)) {
                for(Town occupiedTown: nationTownsOccupationMap.get(nation)) {
                    occupiedTown.setConquered(false);
                    TownMetaDataController.removeOccupationMetadata(occupiedTown);
                    occupiedTown.save();
                }
                nationTownsOccupationMap.remove(nation);
            }
        }
    }
}

