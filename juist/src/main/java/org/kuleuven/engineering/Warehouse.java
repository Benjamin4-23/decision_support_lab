package org.kuleuven.engineering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.kuleuven.engineering.graph.Graph;
import org.kuleuven.engineering.graph.GraphNode;

public class Warehouse {
    private final Graph graph;
    private final List<Vehicle> vehicles;
    private final List<Request> requests;
    private final Set<GraphNode> lockedNodes = new HashSet<>();
    private final List<String> operationLog = new ArrayList<>();
    private final HashMap<String, GraphNode> boxLocationMap = new HashMap<>(); // Map to track box locations
    private double currentTime = 0;
    private final int loadingSpeed;

    public Warehouse(Graph graph, List<Vehicle> vehicles, List<Request> requests, int loadingSpeed) {
        this.graph = graph;
        this.vehicles = vehicles;
        this.requests = requests;
        this.loadingSpeed = loadingSpeed;
        calculateStartingState();
    }

    private void calculateStartingState() {
        this.graph.calculateAllDistances();
        for (Vehicle vehicle : vehicles) {
            Graph.Pair<GraphNode, Double> pair = this.graph.getClosestNode(vehicle.getLocation());
            GraphNode node = pair.x;
            node.setVehiclePresent(true);
        }
        // Initialize box locations
        for (GraphNode node : graph.getNodes()) {
            if (node.getStorage() instanceof Stack stack) {
                for (Box box : stack.getBoxes()) {
                    boxLocationMap.put(box.getId(), node);
                }
            }
        }

        Set<Request> removeSet = new HashSet<>();
        for (Request request : requests){
            if(boxLocationMap.get(request.getBoxID())==null){
                removeSet.add(request);
            }
        }
        for (Request r : removeSet){
            requests.remove(r);
        }
    }

    public void scheduleRequests() {
        Queue<Request> requestQueue = new PriorityQueue<>((r1, r2) -> {
            // Prioritize requests based on some criteria, e.g., distance or urgency
            return Double.compare(
                graph.getTimeToClosestNode(r1.getPickupLocation()),
                graph.getTimeToClosestNode(r2.getPickupLocation())
            );
        });

        requestQueue.addAll(requests);

        while (!requestQueue.isEmpty()) {
            Request request = requestQueue.poll();
            Vehicle assignedVehicle = findAvailableVehicle();

            if (assignedVehicle != null) {
                processRequest(assignedVehicle, request);
            } else {
                // Handle case where no vehicle is available
                System.out.println("No available vehicle for request: " + request.getID());
            }
        }
    }

    private Vehicle findAvailableVehicle() {
        for (Vehicle vehicle : vehicles) {
            if (!vehicle.isFull()) {
                return vehicle;
            }
        }
        return null;
    }

    private void processRequest(Vehicle vehicle, Request request) {
        // Find the current location of the box
        GraphNode currentBoxNode = boxLocationMap.get(request.getBoxID());
        if (currentBoxNode == null) {
            System.out.println("Box " + request.getBoxID() + " not found.");
            return;
        }

        // Move vehicle to the current location of the box
        Location startLocation = vehicle.getLocation();
        double startTime = currentTime;

        currentTime += graph.getTravelTime(vehicle, currentBoxNode);
        vehicle.moveTo(currentBoxNode.getLocation());

        // Handle relocations if necessary
        if (currentBoxNode.getStorage() instanceof Stack stack) {
            lockNode(currentBoxNode);
            while (!stack.isBoxOnTop(request.getBoxID())) {
                Box topBox = stack.removeBox();
                if (topBox != null) {
                    GraphNode tempNode = findTemporaryLocation(1);
                    if (tempNode != null) {
                        // Move to temporary location
                        Location tempLocation = vehicle.getLocation();
                        double tempStartTime = currentTime;
                        unlockNode(currentBoxNode);
                        vehicle.moveTo(tempNode.getLocation());
                        currentTime += graph.getTravelTime(currentBoxNode, tempNode);

                        // Unload the box
                        lockNode(tempNode);
                        if (vehicle.unloadBox(topBox)) {
                            currentTime += loadingSpeed;
                            logOperation(vehicle, tempLocation, tempStartTime, vehicle.getLocation(), currentTime, topBox.getId(), "PL");
                            boxLocationMap.put(topBox.getId(), tempNode); // Update box location
                        }
                        unlockNode(tempNode);

                        // Move back to original stack
                        vehicle.moveTo(currentBoxNode.getLocation());
                        currentTime += graph.getTravelTime(tempNode, currentBoxNode);
                        lockNode(currentBoxNode);
                    }
                }
            }
        }

        // Load the box
        Box box = new Box(request.getBoxID());
        if (vehicle.loadBox(box)) {
            currentTime += loadingSpeed;
            logOperation(vehicle, startLocation, startTime, vehicle.getLocation(), currentTime, box.getId(), "PU");
            boxLocationMap.remove(box.getId()); // Box is now on the vehicle
        }
        unlockNode(currentBoxNode);

        // Move vehicle to place location
        GraphNode placeNode = graph.nodeMap.get(request.getPlaceLocation());
        startLocation = vehicle.getLocation();
        startTime = currentTime;

        vehicle.moveTo(placeNode);
        currentTime += graph.getTravelTime(currentBoxNode, placeNode);

        // Unload the box
        lockNode(placeNode);
        if (vehicle.unloadBox(box)) {
            currentTime += loadingSpeed;
            logOperation(vehicle, startLocation, startTime, vehicle.getLocation(), currentTime, box.getId(), "PL");
            boxLocationMap.put(box.getId(), placeNode); // Update box location
        }
        unlockNode(placeNode);
    
    }

    private boolean lockNode(GraphNode node) {
        while (!lockedNodes.add(node)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return true; // keep waiting until the node is unlocked
    }

    private void unlockNode(GraphNode node) {
        lockedNodes.remove(node);
    }

    private GraphNode findTemporaryLocation(int numberOfBoxes) {
        for (GraphNode node : graph.getNodes()) {
            if (node.getStorage() instanceof Stack stack) {
                if (stack.getCapacity() - stack.getBoxes().size() >= numberOfBoxes) {
                    return node;
                }
            }
        }
        return null;
    }

    private void logOperation(Vehicle vehicle, Location startLocation , double startTime, Location endLocation, double endTime, String boxID, String operation) {
        int startX = startLocation.getX();
        int startY = startLocation.getY();
        int endX = endLocation.getX();
        int endY = endLocation.getY();
        String logEntry = String.format("%s;%d;%d;%.0f;%d;%d;%.0f;%s;%s",
                vehicle.getName(), startX, startY, startTime, endX, endY, endTime, boxID, operation);
        operationLog.add(logEntry);
    }

    public void printOperationLog() {
        for (String logEntry : operationLog) {
            System.out.println(logEntry);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(graph.toString());
        sb.append("\n");
        for (Vehicle vehicle : vehicles) {
            Graph.Pair<GraphNode, Double> pair = this.graph.getClosestNode(vehicle.getLocation());
            sb.append(String.format(vehicle.getName() + " " + vehicle.getLocation().toString() + " is closest to node "
                    + pair.x.getName() + " " + pair.x.getLocation().toString() + " with distance: %.2f (no sqrt)\n", pair.y));
        }
        return sb.toString();
    }
}
