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
    private final List<Request> openRequests;
    private final List<Request> initialRequests;
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
        this.openRequests = new ArrayList<>();
        this.initialRequests = new ArrayList<>(requests);
    }

    private void calculateStartingState() {
        this.graph.calculateAllDistances();
        // boxes die in een stack zitten mappen
        for (GraphNode node : graph.getNodes()) {
            if (node.getStorage() instanceof Stack stack) {
                for (String box : (java.util.Stack<String>)stack.getBoxes()) {
                    boxLocationMap.put(box, node);
                }
            }
        }

        // boxes in een bufferpoint mappen
        for (Request request : requests){
            GraphNode n = request.getPickupLocation();
            if(n.isBuffer()){
                boxLocationMap.put(request.getBoxID(), n);
            }
        }
    }

    public void scheduleRequests() {
        List<Request> requestsCopy = new ArrayList<>(requests);
        while (!requestsCopy.isEmpty() || !openRequests.isEmpty()) { 
            requestsCopy = new ArrayList<>(requests);
            for (Vehicle vehicle : vehicles){
                if(vehicle.isAvailable(currentTime)) {
                    // is zijn huidige assigned request afgerond?
                    if(vehicle.getCurrentRequestID() != -1 && openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().isEmpty()) {
                        vehicle.setCurrentRequestID(-1);
                    }

                    // als vehicle available maar geen request, geef hem een nieuwe request
                    if(vehicle.getCurrentRequestID() == -1){
                        Queue<Request> requestQueue = new PriorityQueue<>((r1, r2) -> {
                            GraphNode boxLocation1 = boxLocationMap.get(r1.getBoxID());
                            GraphNode boxLocation2 = boxLocationMap.get(r2.getBoxID());
                            double minTime1 = Double.MAX_VALUE;
                            double minTime2 = Double.MAX_VALUE;
                            double time1 = graph.calculateTime(boxLocation1.getLocation(), vehicle.getLocation());
                            double time2 = graph.calculateTime(boxLocation2.getLocation(), vehicle.getLocation());
                            minTime1 = Math.min(minTime1, time1);
                            minTime2 = Math.min(minTime2, time2);
                            return Double.compare(minTime1, minTime2);
                        });
                        requestQueue.addAll(requestsCopy);
                        openRequests.add(requestQueue.poll());
                        for (Request request : openRequests){
                            if(request.getAssignedVehicle() == -1) {
                                request.setAssignedVehicle(vehicle.getID());
                                vehicle.setCurrentRequestID(request.getID());
                                break;
                            }
                        }
                    }

                    // werk zijn assigned request af (probeer als niet locked, voorlopig anders niks doen)
                    if(vehicle.getCurrentRequestID() != -1){
                        handleRequest(vehicle, openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0), currentTime > 0 ? currentTime-1 : 0);
                    }
                }

                // if(vehicle.isAvailable(currentTime) && openRequests.size() < vehicles.size() && !requestsCopy.isEmpty()){
                //     //beste request toevoegen voor vrij vehicle
                //     Queue<Request> requestQueue = new PriorityQueue<>((r1, r2) -> {
                //         GraphNode boxLocation1 = boxLocationMap.get(r1.getBoxID());
                //         GraphNode boxLocation2 = boxLocationMap.get(r2.getBoxID());
                //         double minTime1 = Double.MAX_VALUE;
                //         double minTime2 = Double.MAX_VALUE;
                //         double time1 = graph.calculateTime(boxLocation1.getLocation(), vehicle.getLocation());
                //         double time2 = graph.calculateTime(boxLocation2.getLocation(), vehicle.getLocation());
                //         minTime1 = Math.min(minTime1, time1);
                //         minTime2 = Math.min(minTime2, time2);
                //         return Double.compare(minTime1, minTime2);
                //     });
                //     requestQueue.addAll(requestsCopy);
                //     openRequests.add(requestQueue.poll());
                // }
            }
            currentTime++;
        }
    }

    private boolean handleRequest(Vehicle vehicle, Request request, double time){
        // System.out.println("handling request " + request.getID());
        Location startLocation = vehicle.getLocation();
        double timeAfterMove = time;
        double timeAfterOperation;

        if (request.getStatus() == REQUEST_STATUS.INITIAL){
            // als dest een stack is en full, dan moet er gerelocate worden



            GraphNode src = request.getPickupLocation();
            // bereken wanneer hij aan src is
            if (startLocation != src.getLocation()){
                timeAfterMove += graph.getTravelTime(vehicle, src);
            }
            // bereken wanneer hij klaar zou zijn met pickup
            timeAfterOperation = timeAfterMove + loadingSpeed;
            //probeer lock te krijgen voor pickup van timeaftermove tot timeafteroperation
            if (!src.checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                return false;
            }
            // als hier geraakt dan kan hij naar src en PU doen
            vehicle.setUnavailableUntil(timeAfterOperation);
            // System.out.println("vehicle moved from " + vehicle.getLocation().toString());
            vehicle.moveTo(src);
            // System.out.println("vehicle moved to " + src.getLocation().toString()+ " src is " + src.getName());
            String box = "";
            if (src.getStorage() instanceof Stack){
                box = ((Stack)src.getStorage()).removeBox();
                vehicle.addBox(box);
            }
            else{
                // box = ((Bufferpoint)src.getStorage()).removeBox();
                vehicle.addBox(request.getBoxID());
            }
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, request.getBoxID(), REQUEST_STATUS.SRC);
            request.setStatus(REQUEST_STATUS.SRC);
            return true;
        }

        
        if (request.getStatus() == REQUEST_STATUS.SRC){
            // kijken of relocation nodig, dan move to temp stack en PL, anders naar dest en PL
            // voeg log toe
            // status wordt "at src relocation" of "at dest"

            GraphNode dest = request.getPlaceLocation();
            // bereken wanneer hij aan dest is
            if (startLocation != dest.getLocation()){
                timeAfterMove += graph.getTravelTime(vehicle, dest);
            }
            // bereken wanneer hij klaar zou zijn met pickup
            timeAfterOperation = timeAfterMove + loadingSpeed;
            //probeer lock te krijgen voor pickup van timeaftermove tot timeafteroperation
            if (!dest.checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                return false;
            }
            // als hier geraakt dan kan hij naar dest en PL doen
            vehicle.setUnavailableUntil(timeAfterOperation);
            // System.out.println("vehicle moved from " + vehicle.getLocation().toString());
            vehicle.moveTo(dest);
            // System.out.println("vehicle moved to " + dest.getLocation().toString());
            if (dest.getStorage() instanceof Stack){
                ((Stack)dest.getStorage()).addBox(request.getBoxID());
            }
            // else{
            //     ((Bufferpoint)dest.getStorage()).addBox(request.getBoxID()); // mag weg, niks returnen in storage func ipv string
            // }
            vehicle.removeBox(request.getBoxID());
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, request.getBoxID(), REQUEST_STATUS.DEST);
            request.setStatus(REQUEST_STATUS.DEST);
            request.setDone(true);
            openRequests.remove(request);
            requests.remove(request);
            return true;
        }
        if (request.getStatus() == REQUEST_STATUS.SRC_RELOC){
            // move terug naar src en PU
            // voeg log toe
            // status wordt "at src"
        }
        if (request.getStatus() == REQUEST_STATUS.DEST){
            // kijken of relocation nodig, dan move to dest en PL, anders naar dest en PL
            // voeg log toe
            // status wordt "at dest relocation" of "at dest"
        }
        if (request.getStatus() == REQUEST_STATUS.DEST_RELOC){
            // move terug naar dest en PL
            // voeg log toe
            // status wordt "at dest"
        }


        
        // Location startLocation = vehicle.getLocation();
        // double startTime = time;
        // GraphNode src = graph.nodeMap.get(request.getPickupLocation());
        // GraphNode dest = graph.nodeMap.get(request.getPlaceLocation());
        // //voorlopig beide locken
        // if(src == null || (src.isLocked() && dest.isLocked())){
        //     return false;
        // }
        // //is dest beschikbaar?
        // if(dest.isBuffer() && dest.getStorage().isFull()){
        //     //moet gerelocate worden
        //     vehicle.moveTo(dest);
        //     vehicle.eventQueue.add(new Event(vehicle.getName(),dest.getStorage().peek(), startLocation.toString(), dest.getLocation().toString(), Event.EventType.PU_RELOCATE));
        // }
        // //is src beschikbaar?
        // boolean relocateSrc = false;
        // if(!src.isBuffer() && !Objects.equals(src.getStorage().peek(), request.getBoxID())){
        //     //moet gerelocate worden
        //     relocateSrc = true;
        // }
        // int N = 0;
        // Queue<String> boxList = new ArrayDeque<>();
        // // add relocations if needed
        // if(!src.isBuffer() && !Objects.equals(src.getStorage().peek(), request.getBoxID())){
        //     //relocate boxes at source
        //     Stack s = (Stack) src.getStorage();
        //     java.util.Stack<String> boxes = (java.util.Stack<String>)s.getBoxes();
        //     String box = boxes.pop();
        //     while (!Objects.equals(box, request.getBoxID())){
        //         boxList.add(box);
        //         N++;
        //         box = boxes.pop();
        //     }
        // }
        // if(dest.getStorage().isFull()){
        //     //relocate boxes at dest
        //     N++;
        //     boxList.add(dest.getStorage().peek());
        // }
        // if(N> 0){
        //     List<GraphNode> Storage = findNStorage(N, src, dest);
        //     if(Storage == null) {
        //         return false;
        //     }
        //     int j = 0;
        //     int tempN = N;
        //     while (!boxList.isEmpty()){
        //         GraphNode n = Storage.get(j);
        //         int freeSpace = n.getStorage().getFreeSpace();
        //         for (int i = 0; i < Math.min(tempN, freeSpace); i++) {
        //             // go to relocate stack
        //             vehicle.eventQueue.add(new Event(vehicle.getName(), boxList.poll(), n.getName(), Event.EventType.RELOCATE));
        //             // return to original stack
        //             vehicle.eventQueue.add(new Event(vehicle.getName(),null, src.getName(), Event.EventType.RELOCATE_RETURN));
        //             n.lock();
        //             src.lock();
        //         }
        //         tempN -= freeSpace;
        //         j++;
        //     }
        // }
        // vehicle.setAvailability(false);
        // src.lock();
        // dest.lock();
        // // add to src and to dest
        // vehicle.eventQueue.add(new Event(vehicle.getName(),request.getBoxID(), src.getName(), Event.EventType.PICK_UP));
        // vehicle.eventQueue.add(new Event(vehicle.getName(),request.getBoxID(), dest.getName(), Event.EventType.PLACE));
        // //process the first one already
        // processEvent(vehicle, time);
        return false;
    }

    private List<GraphNode> findNStorage(int N, GraphNode src, GraphNode dest){
        if(N == 0) return null;
        List<GraphNode> nodes = new ArrayList<>();
        int i = 0;
        for (GraphNode node : graph.getNodes()){
            if(node == src || node == dest){
                continue;
            }
            if(node.getStorage() instanceof Stack stack && !stack.isFull()){
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


    /*private void processEvent(Vehicle vehicle, double time){
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
            if(!srcNode.isBuffer()){
                String box = ((Stack)srcNode.getStorage()).removeBox();
                assert Objects.equals(box, event.boxId);
            }
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
    }*/

    public void addLogEntry(String vehicleName, Location startLocation, double startTime, Location endLocation, double endTime, String boxId, REQUEST_STATUS type){
        String operation = switch (type){
            case SRC -> "PU";
            case SRC_RELOC -> "PL_RELOCATE";
            case DEST -> "PL";
            case DEST_PU -> "PU";
            case DEST_RELOC -> "PL_RELOCATE";
            default -> "";
        };
        System.out.println(vehicleName + ";" + startLocation.getX() + ";"+ startLocation.getY() + ";" + (int) startTime  + ";" + endLocation.getX() + ";" + endLocation.getY() + ";" + (int)endTime + ";"+ boxId + ";" + operation);
        operationLog.add(vehicleName + ";" + startLocation.getX() + ";"+ startLocation.getY() + ";" + (int) startTime  + ";" + endLocation.getX() + ";" + endLocation.getY()   + ";" + (int)endTime + ";"+ boxId + ";" + operation);

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
