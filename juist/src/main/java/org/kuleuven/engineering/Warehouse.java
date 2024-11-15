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
        //requests ordenen voor optimale volgorde voor eerste ronde evtl

        // doe een ronde requests met dozen zonder relocation
        if (true) {
            // zoek requests met topdozen en de dozen op plek onder die request die ook opgehaald moeten worden voor naar buffer te gaan
            List<Request> requestsWithoutRelocation = new ArrayList<>();
            List<Request> requestsWithoutRelocationOpen = new ArrayList<>();
            for (Request request : requestsCopy){
                if (!request.getPickupLocation().isBuffer() && request.getPickupLocation().getStorage().peek().equals(request.getBoxID()) && request.getPlaceLocation().isBuffer()){ // && request.getPlaceLocation().isBuffer()
                    requestsWithoutRelocation.add(request);
                    requests.remove(request);
                    // zoek of de doos eronder ook opgehaald moet worden
                    for (int i = 1; i < ((Stack)request.getPickupLocation().getStorage()).getBoxesSize(); i++){
                        String boxIDBelow = ((Stack)request.getPickupLocation().getStorage()).peakAtDepth(i);
                        // als die box ook opgehaald moet worden, voeg die request toe
                        boolean found = false;
                        for (Request request2 : requestsCopy){
                            if (request2.getBoxID().equals(boxIDBelow)){
                                requestsWithoutRelocation.add(request2);
                                requests.remove(request2);
                                found = true;
                                break;
                            }
                        }
                        if (!found){
                            break;
                        }
                    }
                }
            }
            Queue<Request> requestQueueWithoutRelocation = new PriorityQueue<>((r1, r2) -> {
                GraphNode boxLocation1 = boxLocationMap.get(r1.getBoxID());
                GraphNode boxLocation2 = boxLocationMap.get(r2.getBoxID());
                double minTime1 = Double.MAX_VALUE;
                double minTime2 = Double.MAX_VALUE;
                double time1 = graph.calculateTime(boxLocation1.getLocation(), vehicles.get(0).getLocation());
                double time2 = graph.calculateTime(boxLocation2.getLocation(), vehicles.get(0).getLocation());
                minTime1 = Math.min(minTime1, time1);
                minTime2 = Math.min(minTime2, time2);
                return (minTime1 - minTime2) > 0 ? 1 : -1;
            });
            requestQueueWithoutRelocation.addAll(requestsWithoutRelocation);


            // ga naar eerste locatie, check of nog plaats op vehicle, dan naar andere pickup, als vol naar buffer
            // enkel met eerste vehicle
            boolean firsGetAnother = false;
            while (!requestQueueWithoutRelocation.isEmpty() || !requestsWithoutRelocationOpen.isEmpty()){
                if (!requestQueueWithoutRelocation.isEmpty() && vehicles.get(0).isAvailable(currentTime) && !firsGetAnother && vehicles.get(0).getCapacity() > vehicles.get(0).getCarriedBoxesCount() && !requestsWithoutRelocation.isEmpty()){
                    Request request = requestQueueWithoutRelocation.poll();
                    requestsWithoutRelocationOpen.add(request);
                    handleRequest(vehicles.get(0), request, currentTime > 0 ? currentTime-1 : 0);
                    if (vehicles.get(0).getCapacity() > vehicles.get(0).getCarriedBoxesCount() && !requestQueueWithoutRelocation.isEmpty()){
                        firsGetAnother = true;
                    }
                }
                else if (!requestQueueWithoutRelocation.isEmpty() && vehicles.get(0).isAvailable(currentTime) && firsGetAnother){
                    Request request = requestQueueWithoutRelocation.poll();
                    requestsWithoutRelocationOpen.add(request);
                    handleRequest(vehicles.get(0), request, currentTime > 0 ? currentTime-1 : 0);
                    if (vehicles.get(0).getCapacity() > vehicles.get(0).getCarriedBoxesCount() && !requestQueueWithoutRelocation.isEmpty()){
                        firsGetAnother = true;
                    }
                    else{
                        firsGetAnother = false;
                    }
                }
                else if (vehicles.get(0).isAvailable(currentTime) && !firsGetAnother){
                    // ga naar buffer en werk de open requests 1 voor 1 af
                    boolean done = false;
                    while (!done){
                        Request request = requestsWithoutRelocationOpen.remove(0);
                        while (!request.isDone()){
                            if (vehicles.get(0).isAvailable(currentTime)){
                                handleRequest(vehicles.get(0), request, currentTime > 0 ? currentTime-1 : 0);
                            }
                            currentTime++;
                        }
                        if (requestsWithoutRelocationOpen.isEmpty()){
                            done = true;
                        }
                    }

                }
                currentTime++;
            }

        }
        
        // daarna werk de rest 1 voor 1 af
        if (true) {
            requestsCopy = new ArrayList<>(requests);
            while (!requestsCopy.isEmpty() || !openRequests.isEmpty()) {      /// aanpassen voor meerdere vehicles dat relocations niet tegenwerken
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
                            Request request = openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                            if (request.isDone()) {
                                openRequests.remove(request);
                                requests.remove(request);
                            }
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
    }

    private boolean handleRequest(Vehicle vehicle, Request request, double time){
        // System.out.println("handling request " + request.getID());
        Location startLocation = vehicle.getLocation();
        double timeAfterMove = time;
        double timeAfterOperation;

        if (request.getStatus() == REQUEST_STATUS.INITIAL){
            // als dest een stack is en full, dan moet er gerelocate worden
            GraphNode dest = request.getPlaceLocation();
            if (dest.getStorage() instanceof Stack stack && stack.isFull()){
                if (startLocation != dest.getLocation()){
                    timeAfterMove += graph.getTravelTime(vehicle, dest);
                }
                timeAfterOperation = timeAfterMove + loadingSpeed;
                if (!dest.checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                    return false;
                }
                vehicle.setUnavailableUntil(timeAfterOperation);
                vehicle.moveTo(dest);
                String box = stack.removeBox();
                vehicle.addBox(box);
                addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_PU);
                request.setStatus(REQUEST_STATUS.DEST_PU);
                return true;
            }



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
            if (src.getStorage() instanceof Stack stack){
                box = stack.removeBox();
                vehicle.addBox(box);
            }
            else{
                // box = ((Bufferpoint)src.getStorage()).removeBox();
                vehicle.addBox(request.getBoxID());
            }
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.SRC);
            request.setStatus(REQUEST_STATUS.SRC);
            return true;
        }

        
        if (request.getStatus() == REQUEST_STATUS.SRC){
            // kijken of relocation nodig, dan move to temp stack en PL, status wordt "at src relocation" 
            if (!vehicle.hasBox(request.getBoxID())){
                String box = vehicle.getLastBox();
                // move to temp stack
                List<GraphNode> tempStacks = findNStorage(1, request.getPickupLocation(), request.getPlaceLocation());
                if (tempStacks.isEmpty()){
                    System.out.println("No temp stack found");
                }
                GraphNode tempStack = tempStacks.get(0); //voorlopig per 1 relocaten
                if (startLocation != tempStack.getLocation()){
                    timeAfterMove += graph.getTravelTime(vehicle, tempStack);
                }
                // bereken wanneer hij klaar zou zijn met pickup
                timeAfterOperation = timeAfterMove + loadingSpeed;
                if (!tempStack.checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                    return false;
                }
                vehicle.setUnavailableUntil(timeAfterOperation);
                vehicle.moveTo(tempStack);
                if (tempStack.getStorage() instanceof Stack stack){
                    stack.addBox(box);
                }
                vehicle.removeBox(box);
                addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.SRC_RELOC);
                request.setStatus(REQUEST_STATUS.SRC_RELOC);
                return true;
            }



            
            // anders naar dest en PL
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
            return true;
        }

        if (request.getStatus() == REQUEST_STATUS.SRC_RELOC){
            // move terug naar src en PU
            GraphNode src = request.getPickupLocation();
            if (startLocation != src.getLocation()){
                timeAfterMove += graph.getTravelTime(vehicle, src);
            }
            timeAfterOperation = timeAfterMove + loadingSpeed;
            if (!src.checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                return false;
            }
            vehicle.setUnavailableUntil(timeAfterOperation);
            vehicle.moveTo(src);
            String box = "";
            if (src.getStorage() instanceof Stack stack){
                box = stack.removeBox();
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
        
        
        
        if (request.getStatus() == REQUEST_STATUS.DEST_PU){
            String box = vehicle.getLastBox();
            List<GraphNode> tempStacks = findNStorage(1, request.getPickupLocation(), request.getPlaceLocation());
            if (tempStacks.isEmpty()){
                System.out.println("No temp stack found");
            }
            GraphNode tempStack = tempStacks.get(0); //voorlopig per 1 relocaten
            if (startLocation != tempStack.getLocation()){
                timeAfterMove += graph.getTravelTime(vehicle, tempStack);
            }
            timeAfterOperation = timeAfterMove + loadingSpeed;
            if (!tempStack.checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                return false;
            }
            vehicle.setUnavailableUntil(timeAfterOperation);
            vehicle.moveTo(tempStack);
            tempStack.getStorage().addBox(box);
            vehicle.removeBox(box);
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_RELOC);
            request.setStatus(REQUEST_STATUS.INITIAL);
            return true;
        }

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
            case SRC -> "PU_at_src";
            case SRC_RELOC -> "PL_at_temp_SRC_RELOC";
            case DEST -> "PL_at_dest";
            case DEST_PU -> "PU_at_dest";
            case DEST_RELOC -> "PL_at_temp_DEST_RELOC";
            default -> "";
        };
        System.out.println(vehicleName + ";\t" + startLocation.getX() + ";"+ startLocation.getY() + ";" + (int) startTime  + ";\t" + endLocation.getX() + ";" + endLocation.getY() + ";" + (int)endTime + ";\t"+ boxId + ";" + operation);
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
