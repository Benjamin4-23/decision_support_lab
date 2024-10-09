package org.kuleuven.engineering;

public class Request {
    private Stack2 pickupLocation;
    private Stack2 placeLocation;
    private Box box;

    public Request(Stack2 pickupLocation, Stack2 placeLocation, Box box) {
        this.pickupLocation = pickupLocation;
        this.placeLocation = placeLocation;
        this.box = box;
    }

    public Stack2 getPickupLocation() {
        return pickupLocation;
    }

    public Stack2 getPlaceLocation() {
        return placeLocation;
    }

    public Box getBox() {   
        return box;
    }

    public void setPickupLocation(Stack2 pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public void setPlaceLocation(Stack2 placeLocation) {
        this.placeLocation = placeLocation;
    }   

    public void setBox(Box box) {
        this.box = box;
    }   

    // Getters and setters
}