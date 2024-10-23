package org.kuleuven.engineering;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class Vehicle {
    private final int ID;
    private String name;
    private final int capacity;
    private final Location location;
    private final List<Box> carriedBoxes;

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
        this.location.setX((int) targetX);
        this.location.setY((int) targetY);
    }

    public boolean loadBox(Box box) {
        if (carriedBoxes.size() < capacity) {
            carriedBoxes.add(box);
            return true;
        }
        return false;
    }

    public boolean unloadBox(Box box) {
        return carriedBoxes.remove(box);
    }

    public boolean isFull() {
        return carriedBoxes.size() >= capacity;
    }

    // Getters and setters
}
