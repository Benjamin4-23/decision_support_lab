package org.kuleuven.engineering;

import java.util.ArrayList;
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
        // Move vehicle to pickup location
        GraphNode pickupNode = graph.getNodeByName(request.getPickupLocation());
        if (lockNode(pickupNode)) {
            int startX = vehicle.getLocation().getX();
            int startY = vehicle.getLocation().getY();
            int startTime = currentTime;

            currentTime += calculateTravelTime(vehicle.getLocation(), pickupNode.getLocation(), true); //staat vehicle bij node
            vehicle.moveTo(pickupNode.getLocation().getX(), pickupNode.getLocation().getY());
            
            // Handle relocations if necessary
            if (pickupNode.getStorage() instanceof Stack) {
                Stack stack = (Stack) pickupNode.getStorage();
                if (!stack.isBoxOnTop(request.getBoxID())) {
                    List<Box> relocatedBoxes = stack.relocateBoxesUntil(request.getBoxID());
                    // Find a temporary location for relocated boxes
                    GraphNode tempNode = findTemporaryLocation(relocatedBoxes.size());
                    if (tempNode != null) {
                        currentTime += calculateTravelTime(pickupNode.getLocation(), tempNode.getLocation(), false);
                        vehicle.moveTo(tempNode.getLocation().getX(), tempNode.getLocation().getY());
                        for (Box box : relocatedBoxes) {
                            if (tempNode.getStorage().addBox(box)) {
                                System.out.println("Relocated box " + box.getId() + " to " + tempNode.getName());
                            }
                        }
                    }
                }
            }

            // Load the box
            Box box = new Box(request.getBoxID());
            if (vehicle.loadBox(box)) {
                currentTime += graph.getLoadingSpeed();
                logOperation(vehicle, startX, startY, startTime, vehicle.getLocation().getX(), vehicle.getLocation().getY(), currentTime, box.getId(), "PU");
            }

            // Move vehicle to place location
            GraphNode placeNode = graph.getNodeByName(request.getPlaceLocation());
            startX = vehicle.getLocation().getX();
            startY = vehicle.getLocation().getY();
            startTime = currentTime;

            vehicle.moveTo(placeNode.getLocation().getX(), placeNode.getLocation().getY());
            currentTime += calculateTravelTime(pickupNode.getLocation(), placeNode.getLocation(), false);

            // Unload the box
            if (vehicle.unloadBox(box)) {
                currentTime += graph.getLoadingSpeed();
                logOperation(vehicle, startX, startY, startTime, vehicle.getLocation().getX(), vehicle.getLocation().getY(), currentTime, box.getId(), "PL");
            }

            unlockNode(pickupNode);
        } else {
            System.out.println("Node is currently locked, cannot process request: " + request.getID());
        }
    }

    private boolean lockNode(GraphNode node) {
        return lockedNodes.add(node);
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

    private int calculateTravelTime(Location start, Location end, boolean vehicle) {
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
