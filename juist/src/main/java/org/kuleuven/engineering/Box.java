package org.kuleuven.engineering;


public class Box {
    private int id;
    private Stack2 currentLocation; // Reference to the current stack location

    public Box(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Stack2 getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Stack2 currentLocation) {
        this.currentLocation = currentLocation;
    }

    // Getters and setters
}