package org.kuleuven.engineering;

public class Request {
    private Stack pickupLocation;
    private Stack placeLocation;
    private Box box;

    public Request(Stack pickupLocation, Stack placeLocation, Box box) {
        this.pickupLocation = pickupLocation;
        this.placeLocation = placeLocation;
        this.box = box;
    }

    public Stack getPickupLocation() {
        return pickupLocation;
    }

    public Stack getPlaceLocation() {
        return placeLocation;
    }

    public Box getBox() {   
        return box;
    }

    public void setPickupLocation(Stack pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public void setPlaceLocation(Stack placeLocation) {
        this.placeLocation = placeLocation;
    }   

    public void setBox(Box box) {
        this.box = box;
    }   

    // Getters and setters
}