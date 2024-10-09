package org.kuleuven.engineering;
import java.util.ArrayList;
import java.util.List;
public class Warehouse {
    private List<Stack> stacks;
    private List<Vehicle> vehicles;
    private List<Request> requests;

    public Warehouse(List<Stack> stacks, List<Vehicle> vehicles) {
        this.stacks = stacks;
        this.vehicles = vehicles;
        this.requests = new ArrayList<>();
    }

    public void addRequest(Request request) {
        // Add request to the list
    }

    public void scheduleRequests() {
        // Implement greedy scheduling algorithm
    }

    // Additional methods for managing vehicles and stacks
}