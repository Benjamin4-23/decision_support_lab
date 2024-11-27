package org.kuleuven.engineering;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
    double unavailableUntil = -1;
    public Queue<Event> eventQueue = new ArrayDeque<>();
    private ArrayList<String> carriedBoxes;
    private int carriedBoxesCount;

    public Vehicle(JSONObject object) {
        try{
            // this.location = new Location(object.getInt("xCoordinate"), object.getInt("yCoordinate"));
            this.location = new Location(object.getInt("x"), object.getInt("y"));
        } catch (JSONException e){
            this.location = new Location(object.getInt("x"), object.getInt("y"));
        }
        ID = object.getInt("ID");
        name = object.getString("name");
        capacity = object.getInt("capacity");
        this.carriedBoxesCount = 0;
        this.carriedBoxes = new ArrayList<>();
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

    public int getCarriedBoxesCount(){
        return carriedBoxesCount;
    }
    public int getCapacity(){
        return capacity;
    }

    public GraphNode getCurrentNode(){
        return currentNode;
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

    public boolean isAvailable(double time) {
        return time > unavailableUntil;
    }

    public void setUnavailableUntil(double time){
        this.unavailableUntil = time;
    }
    
    // Getters and setters
    public boolean removeBox(String boxId){
        if (carriedBoxes.contains(boxId)){
            carriedBoxesCount--;
            return carriedBoxes.remove(boxId);
        }
        System.out.println("Box not found in vehicle at time of removal");
        return false;
    }
    public void addBox(String boxId){
        this.carriedBoxes.add(boxId);
        this.carriedBoxesCount++;
        if (carriedBoxesCount > capacity){
            throw new RuntimeException("Vehicle capacity exceeded");
        }
    }

    public boolean hasBox(String boxId){
        return carriedBoxes.contains(boxId);
    }
    public String getLastBox(){
        return carriedBoxes.get(carriedBoxes.size() - 1);
    }
}
