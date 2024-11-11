package org.kuleuven.engineering;

import java.util.*;

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
    private int runningEvents = 0;
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
        for (GraphNode node : graph.getNodes()) {
            if (node.getStorage() instanceof Stack stack) {
                for (String box : (java.util.Stack<String>)stack.getBoxes()) {
                    boxLocationMap.put(box, node);
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

        while (!requestQueue.isEmpty() || runningEvents > 0) {
            for (Vehicle vehicle : vehicles){
                if(vehicle.isAvailable() && !requestQueue.isEmpty()){
                    //kan beter
                    Request request = requestQueue.peek();
                    if(handleRequest(vehicle, request, currentTime)){
                        requestQueue.poll();
                    }
                }
                if(!vehicle.eventQueue.isEmpty()){
                    processEvents(vehicle, currentTime);
                }
            }
            currentTime++;
        }
    }

    private boolean handleRequest(Vehicle vehicle, Request request, double time){
        // src can be null if box is currently moving to another stack, skip until later
        GraphNode src = boxLocationMap.get(request.getBoxID());
        GraphNode dest = graph.nodeMap.get(request.getPlaceLocation());
        if(src == null || (src.isLocked() && dest.isLocked())){
            return false;
        }

        int N = 0;
        Queue<String> boxList = new ArrayDeque<>();
        // add relocations if needed
        if(!Objects.equals(src.getStorage().peek(), request.getBoxID())){
            //relocate boxes at source
            Stack s = (Stack) src.getStorage();
            java.util.Stack<String> boxes = (java.util.Stack<String>)s.getBoxes();
            String box = boxes.pop();
            while (!Objects.equals(box, request.getBoxID())){
                boxList.add(box);
                N++;
                box = boxes.pop();
            }
        }

        if(dest.getStorage().isFull()){
            //relocate boxes at dest
            N++;
            boxList.add(dest.getStorage().peek());
        }

        if(N> 0){
            List<GraphNode> Storage = findNStorage(N, src, dest);
            if(Storage == null) {
                return false;
            }

            int j = 0;
            int tempN = N;
            while (!boxList.isEmpty()){
                GraphNode n = Storage.get(j);
                int freeSpace = n.getStorage().getFreeSpace();
                for (int i = 0; i < Math.min(tempN, freeSpace); i++) {
                    // go to relocate stack
                    vehicle.eventQueue.add(new Event(vehicle.getName(), boxList.poll(), n.getName(), Event.EventType.RELOCATE));
                    // return to original stack
                    vehicle.eventQueue.add(new Event(vehicle.getName(),null, src.getName(), Event.EventType.RELOCATE_RETURN));
                    n.lock();
                    src.lock();
                }
                tempN -= freeSpace;
                j++;
            }
        }

        vehicle.setAvailability(false);
        src.lock();
        dest.lock();

        // add to src and to dest
        vehicle.eventQueue.add(new Event(vehicle.getName(),request.getBoxID(), src.getName(), Event.EventType.PICK_UP));
        vehicle.eventQueue.add(new Event(vehicle.getName(),request.getBoxID(), dest.getName(), Event.EventType.PLACE));

        //process the first one already
        processEvent(vehicle, time);
        return true;
    }

    private List<GraphNode> findNStorage(int N, GraphNode src, GraphNode dest){
        if(N == 0) return null;
        List<GraphNode> nodes = new ArrayList<>();
        int i = 0;
        for (GraphNode node : graph.getNodes()){
            if(node == src || node == dest){
                continue;
            }
            if(node.getStorage() instanceof Stack stack && !stack.isFull() && !node.isLocked()){
                nodes.add(node);
                i += stack.getFreeSpace();
                if(i>=N){
                    return nodes;
                }
            }
        }

        if(i<N){
            return null;
        }
        return nodes;
    }

    private void processEvents(Vehicle vehicle, double time){
        if(vehicle.eventQueue.peek().isHandled(time)){
            Event e = vehicle.eventQueue.poll();
            GraphNode destNode = graph.nodeMap.get(e.location);
            if(e.type == Event.EventType.PLACE || e.type == Event.EventType.RELOCATE) {
                destNode.getStorage().addBox(e.boxId);
                boxLocationMap.put(e.boxId, destNode);
            }
            destNode.unlock();
            runningEvents--;
            e.log();
            // do next one
            processEvent(vehicle, time);
        }
    }

    private void processEvent(Vehicle vehicle, double time){
        if(vehicle.eventQueue.isEmpty()) {
            vehicle.setAvailability(true);
            return;
        }

        Event event = vehicle.eventQueue.peek();
        GraphNode destNode = graph.nodeMap.get(event.location);
        GraphNode srcNode = boxLocationMap.get(event.boxId);
        double vehicleTime = 0;

        if(event.type ==  Event.EventType.RELOCATE || event.type == Event.EventType.PICK_UP){
            //src node is null in relocate return
            assert srcNode != null;
            String box = ((Stack)srcNode.getStorage()).removeBox();
            assert Objects.equals(box, event.boxId);
            boxLocationMap.remove(event.boxId);

            if(vehicle.getLocation() != srcNode.getLocation()){
                vehicleTime += graph.getTravelTime(vehicle, srcNode);
                vehicle.moveTo(srcNode);
            }
        }

        vehicleTime += graph.getTravelTime(vehicle, destNode);
        if(vehicleTime > 0){
            vehicle.moveTo(destNode);
        }
        switch (event.type){
            case PLACE, PICK_UP ->{
                vehicleTime += loadingSpeed;
            }
            case RELOCATE -> {
                vehicleTime += loadingSpeed * 2;
            }
            case RELOCATE_RETURN -> {
            }
        }
        event.setStartTime(time);
        event.setTimer(time + vehicleTime);
        runningEvents++;
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
