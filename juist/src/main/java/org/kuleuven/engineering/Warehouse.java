package org.kuleuven.engineering;
import java.util.ArrayList;
import java.util.List;
public class Warehouse {
    private List<Stack> stacks;
    private List<Vehicle> vehicles;
    private List<Request> requests;
    private Stack bufferPoint;
    private double vehicleSpeed;
    private double loadUnloadDuration;

    public Warehouse(List<Stack> stacks, List<Vehicle> vehicles, Stack bufferPoint, 
                     double vehicleSpeed, double loadUnloadDuration) {
        this.stacks = stacks;
        this.vehicles = vehicles;
        this.bufferPoint = bufferPoint;
        this.vehicleSpeed = vehicleSpeed;
        this.loadUnloadDuration = loadUnloadDuration;
        this.requests = new ArrayList<>();
    }

    // Methods for adding requests, scheduling, etc.
}