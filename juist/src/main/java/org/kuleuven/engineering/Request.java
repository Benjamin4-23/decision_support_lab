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

    // Getters and setters
}