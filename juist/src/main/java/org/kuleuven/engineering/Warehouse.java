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
    private int currentTime = 0;

    public Warehouse(Graph graph, List<Vehicle> vehicles, List<Request> requests) {
        this.graph = graph;
        this.vehicles = vehicles;
        this.requests = requests;
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
            if (node.getStorage() instanceof Stack) {
                Stack stack = (Stack) node.getStorage();
                for (Box box : stack.getBoxes()) {
                    boxLocationMap.put(box.getId(), node);
                }
            }
        }
    }

    public void scheduleRequests() {
        Queue<Request> requestQueue = new PriorityQueue<>((r1, r2) -> {
            // Prioritize requests based on some criteria, e.g., distance or urgency
            return Double.compare(
                graph.getDistanceToClosestNode(r1.getPickupLocation()),
                graph.getDistanceToClosestNode(r2.getPickupLocation())
            );
        });

        requestQueue.addAll(requests);

        while (!requestQueue.isEmpty()) {
            Request request = requestQueue.poll();
            Vehicle assignedVehicle = findAvailableVehicle(request);

            if (assignedVehicle != null) {
                processRequest(assignedVehicle, request);
            } else {
                // Handle case where no vehicle is available
                System.out.println("No available vehicle for request: " + request.getID());
            }
        }
    }

    private Vehicle findAvailableVehicle(Request request) {
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
        int startX = vehicle.getLocation().getX();
        int startY = vehicle.getLocation().getY();
        int startTime = currentTime;

        currentTime += calculateTravelTime(new GraphNode(vehicle.getLocation()), currentBoxNode, true);
        vehicle.moveTo(currentBoxNode.getLocation().getX(), currentBoxNode.getLocation().getY());

        // Handle relocations if necessary
        if (currentBoxNode.getStorage() instanceof Stack) {
            lockNode(currentBoxNode);
            Stack stack = (Stack) currentBoxNode.getStorage();
            while (!stack.isBoxOnTop(request.getBoxID())) {
                Box topBox = stack.removeBox();
                if (topBox != null) {
                    GraphNode tempNode = findTemporaryLocation(1);
                    if (tempNode != null) {
                        // Move to temporary location
                        int tempStartX = vehicle.getLocation().getX();
                        int tempStartY = vehicle.getLocation().getY();
                        int tempStartTime = currentTime;

                        unlockNode(currentBoxNode);
                        vehicle.moveTo(tempNode.getLocation().getX(), tempNode.getLocation().getY());
                        currentTime += calculateTravelTime(currentBoxNode, tempNode, false);

                        // Unload the box
                        lockNode(tempNode);
                        if (vehicle.unloadBox(topBox)) {
                            currentTime += graph.getLoadingSpeed();
                            logOperation(vehicle, tempStartX, tempStartY, tempStartTime, vehicle.getLocation().getX(), vehicle.getLocation().getY(), currentTime, topBox.getId(), "PL");
                            boxLocationMap.put(topBox.getId(), tempNode); // Update box location
                        }
                        unlockNode(tempNode);

                        // Move back to original stack
                        vehicle.moveTo(currentBoxNode.getLocation().getX(), currentBoxNode.getLocation().getY());
                        currentTime += calculateTravelTime(tempNode, currentBoxNode, false);
                        lockNode(currentBoxNode);
                    }
                }
            }
        }

        // Load the box
        Box box = new Box(request.getBoxID());
        if (vehicle.loadBox(box)) {
            currentTime += graph.getLoadingSpeed();
            logOperation(vehicle, startX, startY, startTime, vehicle.getLocation().getX(), vehicle.getLocation().getY(), currentTime, box.getId(), "PU");
            boxLocationMap.remove(box.getId()); // Box is now on the vehicle
        }
        unlockNode(currentBoxNode);

        // Move vehicle to place location
        GraphNode placeNode = graph.getNodeByName(request.getPlaceLocation());
        startX = vehicle.getLocation().getX();
        startY = vehicle.getLocation().getY();
        startTime = currentTime;

        vehicle.moveTo(placeNode.getLocation().getX(), placeNode.getLocation().getY());
        currentTime += calculateTravelTime(currentBoxNode, placeNode, false);

        // Unload the box
        lockNode(placeNode);
        if (vehicle.unloadBox(box)) {
            currentTime += graph.getLoadingSpeed();
            logOperation(vehicle, startX, startY, startTime, vehicle.getLocation().getX(), vehicle.getLocation().getY(), currentTime, box.getId(), "PL");
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
            if (node.getStorage() instanceof Stack) {
                Stack stack = (Stack) node.getStorage();
                if (stack.getCapacity() - stack.getBoxes().size() >= numberOfBoxes) {
                    return node;
                }
            }
        }
        return null;
    }

    private int calculateTravelTime(GraphNode start, GraphNode end, boolean vehicle) {
        double distance = graph.getDistanceLocation(start, end, vehicle);
        return (int) (distance / graph.getVehicleSpeed());
    }

    private void logOperation(Vehicle vehicle, int startX, int startY, int startTime, int endX, int endY, int endTime, String boxID, String operation) {
        String logEntry = String.format("%s;%d;%d;%d;%d;%d;%d;%s;%s",
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
