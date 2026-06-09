package models.entities;

import models.types.AccessState;

public class Flux {

    private String regionA;
    private String regionB;
    private double passengerVolume;
    private AccessState access;

    /**
     * Flux constructor
     * Initialize a new Flux between 2 regions
     * @param regionA         source region name
     * @param regionB         destination region name
     * @param passengerVolume estimated daily traveler count
     * @param access          initial access state
     */
    public Flux(String regionA, String regionB, double passengerVolume, AccessState access) {
        this.regionA = regionA;
        this.regionB = regionB;
        this.passengerVolume = passengerVolume;
        this.access = access;
    }

    public String getRegionA(){ 
        return regionA; 
    }

    public String getRegionB(){ 
        return regionB; 
    }

    public double getPassengerVolume(){ 
        return passengerVolume;
    }

    public AccessState getAccess(){ 
        return access; 
    }

    public void setAccess(AccessState access){ 
        this.access = access; 
    }

    public void setPassengerVolume(double passengerVolume){ 
        this.passengerVolume = passengerVolume; 
    }

    /**
     * Checks whether this flux connects the two given region names (order-independent).
     * @param a first region name
     * @param b second region name
     * @return true if this flux links a and b
     */
    public boolean connects(String a, String b) {
        return (regionA.equals(a) && regionB.equals(b))
            || (regionA.equals(b) && regionB.equals(a));
    }
}
