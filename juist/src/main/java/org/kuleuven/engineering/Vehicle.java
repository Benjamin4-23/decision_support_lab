package org.kuleuven.engineering;
import java.util.ArrayDeque;
import java.util.Queue;

import org.json.JSONException;
import org.json.JSONObject;
import org.kuleuven.engineering.graph.GraphNode;

public class Vehicle {
    private final int ID;
    private String name;
    private final int capacity;
    private Location location;
    private int currentRequestID = -1;
    public GraphNode currentNode = null;
    double unavailableUntil = 0;
    public Queue<Event> eventQueue = new ArrayDeque<>();
    private int carriedBoxes;
    private boolean availability = true;

    public Vehicle(JSONObject object) {
        try{
            this.location = new Location(object.getInt("xCoordinate"), object.getInt("yCoordinate"));
        } catch (JSONException e){
            this.location = new Location(object.getInt("x"), object.getInt("y"));
        }
        ID = object.getInt("ID");
        name = object.getString("name");
        capacity = object.getInt("capacity");
        this.carriedBoxes = 0;
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

    public int getCurrentRequestID(){
        return currentRequestID;
    }
    
    public void setCurrentRequestID(int id){
        this.currentRequestID = id;
    }

    public void moveTo(double targetX, double targetY) {
        // Implement movement logic
        this.location.setX((int) targetX);
        this.location.setY((int) targetY);
    }

    public void moveTo(Location location) {
        this.location = location;
    }

    public void moveTo(GraphNode node) {
        this.location = node.getLocation();
        this.currentNode = node;
    }

    public void setAvailability(boolean available){
        this.availability = available;
    }

    public boolean isAvailable(double time) {
        return capacity > carriedBoxes && time >= unavailableUntil;
    }
    // Getters and setters
}
