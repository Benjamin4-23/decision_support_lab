package org.kuleuven.engineering;
import org.json.JSONObject;

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

    public Vehicle(JSONObject object) {
        ID = object.getInt("ID");
        name = object.getString("name");
        this.location = new Location(object.getInt("xCoordinate"), object.getInt("yCoordinate"));
        capacity = object.getInt("capacity");

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
