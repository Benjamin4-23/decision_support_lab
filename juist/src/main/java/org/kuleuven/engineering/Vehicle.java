package org.kuleuven.engineering;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class Vehicle {
    private int ID;
    private String name;
    private int capacity;
    private Location location;
    private List<Box> carriedBoxes;

    public Vehicle(int ID, int capacity, int initialX, int initialY) {
        this.ID = ID;
        this.capacity = capacity;
        this.location = new Location(initialX, initialY);
        this.carriedBoxes = new ArrayList<>();
    }

    public Vehicle(JsonObject object) {
        ID = object.get("ID").getAsInt();
        name = object.get("name").getAsString();
        this.location = new Location(object.get("xCoordinate").getAsInt(), object.get("yCoordinate").getAsInt());
        capacity = object.get("capacity").getAsInt();

        this.carriedBoxes = new ArrayList<>(capacity);
    }

    public int getID(){
        return ID;
    }
    public String getName(){
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public void moveTo(double targetX, double targetY) {
        // Implement movement logic
    }

    public boolean loadBox(Box box) {
        // Implement loading logic
        return false;
    }

    public boolean unloadBox(Box box) {
        // Implement unloading logic
        return false;
    }

    // Getters and setters
}