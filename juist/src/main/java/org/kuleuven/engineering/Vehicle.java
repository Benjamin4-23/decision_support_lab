package org.kuleuven.engineering;
import java.util.ArrayList;
import java.util.List;

public class Vehicle {
    private int id;
    private int capacity;
    private double x;
    private double y;
    private List<Box> carriedBoxes;

    public Vehicle(int id, int capacity, double initialX, double initialY) {
        this.id = id;
        this.capacity = capacity;
        this.x = initialX;
        this.y = initialY;
        this.carriedBoxes = new ArrayList<>();
    }

    public void moveTo(double targetX, double targetY) {
        // Implement movement logic
    }

    public boolean loadBox(Box box) {
        // Implement loading logic
    }

    public boolean unloadBox(Box box) {
        // Implement unloading logic
    }

    // Getters and setters
}