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

    // Methods for moving, loading, unloading boxes, etc.
}