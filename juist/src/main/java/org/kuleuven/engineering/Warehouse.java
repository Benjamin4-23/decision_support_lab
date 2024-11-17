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
        // doe een ronde requests met topdozen die naar buffer moeten
        if (true) {
            // zoek requests met topdozen en de dozen op plek onder die request die ook opgehaald moeten worden voor naar buffer te gaan
            List<Request> requestsWithoutRelocation = new ArrayList<>();
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
                return (minTime1 > minTime2) ? 1 : -1;
            });
            requestQueueWithoutRelocation.addAll(requestsWithoutRelocation);

            // ga naar eerste locatie, check of nog plaats op vehicle, dan naar andere pickup, als vol naar buffer
            // enkel met eerste vehicle
            boolean firsGetAnother = false;
            while (!requestQueueWithoutRelocation.isEmpty() || !openRequests.isEmpty()){
                if (!requestQueueWithoutRelocation.isEmpty() && vehicles.get(0).isAvailable(currentTime) && !firsGetAnother && vehicles.get(0).getCapacity() > vehicles.get(0).getCarriedBoxesCount() && !requestsWithoutRelocation.isEmpty()){
                    Request request = requestQueueWithoutRelocation.poll();
                    openRequests.add(request);
                    handleRequest(vehicles.get(0), request, currentTime > 0 ? currentTime-1 : 0,0);
                    if (vehicles.get(0).getCapacity() > vehicles.get(0).getCarriedBoxesCount() && !requestQueueWithoutRelocation.isEmpty()){
                        firsGetAnother = true;
                    }
                }
                else if (!requestQueueWithoutRelocation.isEmpty() && vehicles.get(0).isAvailable(currentTime) && firsGetAnother){
                    Request request = requestQueueWithoutRelocation.poll();
                    openRequests.add(request);
                    handleRequest(vehicles.get(0), request, currentTime > 0 ? currentTime-1 : 0,0);
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
                        Request request = openRequests.remove(0);
                        while (!request.isDone()){
                            if (vehicles.get(0).isAvailable(currentTime)){
                                handleRequest(vehicles.get(0), request, currentTime > 0 ? currentTime-1 : 0,0);
                            }
                            currentTime++;
                        }
                        if (openRequests.isEmpty()){
                            done = true;
                        }
                    }

                }
                currentTime++;
            }

        }
        
        // daarna werk de rest 1 voor 1 af (eerst stack -> buffer requests based on depth)
        if (true) {
            requestsCopy = new ArrayList<>();
            for (Request request : requests){
                if (request.getPickupLocation().getStorage() instanceof Stack && request.getPlaceLocation().isBuffer()){
                    requestsCopy.add(request);
                }
            }
            while (!requestsCopy.isEmpty() || !openRequests.isEmpty()) {  
                requestsCopy = new ArrayList<>(); 
                for (Request request : requests){
                    if (request.getPickupLocation().getStorage() instanceof Stack && request.getPlaceLocation().isBuffer()){
                        requestsCopy.add(request);
                    }
                }
                for (Vehicle vehicle : vehicles){
                    if(vehicle.isAvailable(currentTime)) {
                        // is zijn huidige assigned request afgerond?
                        if(vehicle.getCurrentRequestID() != -1 && openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().isEmpty()) {
                            vehicle.setCurrentRequestID(-1);
                        }

                        // als vehicle available maar geen request, geef hem een nieuwe request
                        if(vehicle.getCurrentRequestID() == -1){
                            // sorteer requests op distance tot vehicle
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
                            // sorteer requests die van stack naar buffer moeten op diepte in stack om eerst requests van hogere dozen af te werken
                            List<Request> requestListStackToBuffer = new ArrayList<>(requestQueue);
                            requestListStackToBuffer.sort((r1, r2) -> {
                                if (r1.getPickupLocation().getStorage() instanceof Stack stack1 && r2.getPickupLocation().getStorage() instanceof Stack stack2) {
                                    return Integer.compare(stack1.getDepthOfBox(r1.getBoxID()), stack2.getDepthOfBox(r2.getBoxID()));
                                }
                                return 0;
                            });
                            
                            
                            openRequests.add(requestListStackToBuffer.remove(0));
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
                            handleRequest(vehicle, openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0), currentTime > 0 ? currentTime-1 : 0, 0);
                            Request request = openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                            if (request.isDone()) {
                                openRequests.remove(request);
                                requests.remove(request);
                                vehicle.setCurrentRequestID(-1);
                            }
                        }
                    }
                }
                currentTime++;
            }
        }
        
        // dan buffer -> stack requests met letten op eindlocaties)
        if (true) {
            // maak list voor buffer -> stack requests
            requestsCopy = new ArrayList<>(requests);
            while (!requestsCopy.isEmpty() || !openRequests.isEmpty()) {  
                requestsCopy = new ArrayList<>(requests);
                for (Vehicle vehicle : vehicles){
                    if(vehicle.isAvailable(currentTime)) {
                        // is zijn huidige assigned request afgerond?
                        if(vehicle.getCurrentRequestID() != -1 && openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().isEmpty()) {
                            vehicle.setCurrentRequestID(-1);
                        }

                        boolean alreadyGivenRequest = false;
                        // als vehicle available maar geen request, geef hem een nieuwe request 
                        if(vehicle.getCurrentRequestID() == -1){ 
                            alreadyGivenRequest = true;
                            openRequests.add(requestsCopy.remove(0));
                            requests.remove(openRequests.get(openRequests.size()-1));
                            for (Request request : openRequests){
                                if(request.getAssignedVehicle() == -1) {
                                    request.setAssignedVehicle(vehicle.getID());
                                    vehicle.setCurrentRequestID(request.getID());
                                    break;
                                }
                            }
                        }

                        // werk zijn assigned request af, kijken hoeveel andere requests ook naar dezelfde stack gaan, zoveel plaats vrijmaken op dest stack, dan al die requests afwerken
                        if(vehicle.getCurrentRequestID() != -1){
                            // Count how many requests in requestsCopy have the same destination stack
                            int sameDestStackCount = 0;
                            Request currentRequest = openRequests.stream()
                                .filter(x -> x.getID() == vehicle.getCurrentRequestID())
                                .findFirst()
                                .orElse(null);
                            if (currentRequest != null) {
                                GraphNode currentDest = currentRequest.getPlaceLocation();
                                for (Request req : requestsCopy) {
                                    if (req.getPlaceLocation().equals(currentDest)) {
                                        sameDestStackCount++;
                                    }
                                }
                            }
                            else {
                                System.out.println("No current request found for vehicle laatste loop" + vehicle.getID());
                            }

                            // staan we op pickup en hebben nog plaats en nog request met zelfde dest?
                            if (!alreadyGivenRequest && vehicle.getCarriedBoxesCount() < vehicle.getCapacity() && sameDestStackCount > 0 && vehicle.getCurrentNode() == currentRequest.getPickupLocation()){
                                Request newRequest = null;
                                for (Request req : requestsCopy){
                                    if (req.getPlaceLocation() == currentRequest.getPlaceLocation()){
                                        openRequests.add(req);
                                        requests.remove(req);
                                        newRequest = req;
                                        break;
                                    }
                                }
                                if (newRequest != null) {
                                    // haal nieuwe box op van dit request
                                    handleRequest(vehicle, newRequest, currentTime > 0 ? currentTime-1 : 0, 0);
                                }
                            }
                            else {
                                // werk zijn assigned request af
                                // maak plaats vrij op dest stack voor zoveel requests als er zijn die ook naar dezelfde stack gaan
                                handleRequest(vehicle, openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0), currentTime > 0 ? currentTime-1 : 0,sameDestStackCount);
                                Request request = openRequests.stream().filter( x -> { return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                                if (request.isDone()) {
                                    GraphNode currentDest = currentRequest.getPlaceLocation();
                                    openRequests.remove(request);

                                    // geef hem nieuwe request met zelfde dest stack uit openrequests
                                    int newRequestID = -1;
                                    for (Request req : openRequests){
                                        if (req.getPlaceLocation() == currentDest){
                                            vehicle.setCurrentRequestID(req.getID());
                                            newRequestID = req.getID();
                                            break;
                                        }
                                    }
                                    if (newRequestID == -1){
                                        // geef hem nieuwe request met zelfde dest stack uit requestsCopy
                                        for (Request req : requestsCopy){
                                            if (req.getPlaceLocation() == currentDest){
                                                openRequests.add(req);
                                                requests.remove(req);
                                                vehicle.setCurrentRequestID(req.getID());
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                currentTime++;
            }
        }

    }

    private boolean handleRequest(Vehicle vehicle, Request request, double time, int sameDestStackCount){
        Location startLocation = vehicle.getLocation();
        double timeAfterMove = time;
        double timeAfterOperation;

        if (request.getStatus() == REQUEST_STATUS.INITIAL){
            // als het vehicle niet leeg is en bij een stack, probeer vehicle zo veel mogelijk leeg te maken
            // check if vehicle got a needed box and is collecting more boxes
            boolean vehicleGotRequestBox = false;
            for (Request req : openRequests) {
                if (vehicle.hasBox(req.getBoxID())) {
                    vehicleGotRequestBox = true;
                    break;
                }
            }
            if (vehicle.getCarriedBoxesCount() > 0 && vehicle.getCurrentNode().getStorage() instanceof Stack stack && !vehicleGotRequestBox ){
                if (stack.getFreeSpace() != 0){
                    String box = vehicle.getLastBox();
                    timeAfterOperation = timeAfterMove + loadingSpeed;
                    if (!vehicle.getCurrentNode().checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                        return false;
                    }
                    vehicle.setUnavailableUntil(timeAfterOperation);
                    stack.addBox(box);
                    vehicle.removeBox(box);
                    addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_RELOC);
                    return true;
                }
            }
            // als dest een stack is en full, dan moet er gerelocate worden of check of meerdere requests ook naar dezelfde stack gaan, zoveel plaats vrijmaken op dest stack, dan al die requests afwerken
            GraphNode dest = request.getPlaceLocation();
            if (dest.getStorage() instanceof Stack stack && stack.getFreeSpace() < sameDestStackCount+1){
                // maak plaats vrij op dest stack 
                if (dest.getStorage() instanceof Stack stack2) {
                    if (startLocation != dest.getLocation()){
                        timeAfterMove += graph.getTravelTime(vehicle, dest);
                    }
                    timeAfterOperation = timeAfterMove + loadingSpeed;
                    if (!dest.checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                        return false;
                    }
                    vehicle.setUnavailableUntil(timeAfterOperation);
                    vehicle.moveTo(dest);
                    String box = stack2.removeBox();
                    vehicle.addBox(box);
                    addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.DEST_PU);
                    request.setStatus(REQUEST_STATUS.DEST_PU);
                    return true;
                }
            }

            

            // ga naar src en PU
            if (true) {
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
                    vehicle.addBox(request.getBoxID());
                }
                addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.SRC);
                request.setStatus(REQUEST_STATUS.SRC);
                return true;
            }
        }



        if (request.getStatus() == REQUEST_STATUS.SRC){
            // kijken of relocation nodig (we hebben box niet op vehicle), als vehicle vol en nog niet juiste box, ga naar temp stack
            if (!vehicle.hasBox(request.getBoxID()) && vehicle.getCapacity() == vehicle.getCarriedBoxesCount()){
                String box = vehicle.getLastBox();
                // move to temp stack
                List<GraphNode> tempStacks = findNStorage(1, request.getPickupLocation(), request.getPlaceLocation(), request.getStatus());
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
                request.setStatus(REQUEST_STATUS.INITIAL);
                return true;
            }
            // anders als vehicle niet vol en nog niet juiste box, probeer nog 1 op te nemen
            if (!vehicle.hasBox(request.getBoxID()) && vehicle.getCapacity() > vehicle.getCarriedBoxesCount()){
                // probeer nog 1 op te nemen
                timeAfterOperation = timeAfterMove + loadingSpeed;
                if (!vehicle.getCurrentNode().checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                    return false;
                }
                vehicle.setUnavailableUntil(timeAfterOperation);
                String box = "";
                if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                    box = stack.removeBox();
                    vehicle.addBox(box);
                }
                else{
                    // box = ((Bufferpoint)src.getStorage()).removeBox();
                    vehicle.addBox(request.getBoxID());
                }
                addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.SRC);
                return true;
            }

            // anders naar dest en PL
            if (true) {
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
                vehicle.moveTo(dest);
                if (dest.getStorage() instanceof Stack){
                    ((Stack)dest.getStorage()).addBox(request.getBoxID());
                }
                // else{
                //     ((Bufferpoint)dest.getStorage()).addBox(request.getBoxID()); // mag weg, niks returnen in storage func ipv string
                // }
                vehicle.removeBox(request.getBoxID());
                addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, request.getBoxID(), REQUEST_STATUS.DEST);
                request.setStatus(REQUEST_STATUS.DEST);
                return true;
            }
        }



        if (request.getStatus() == REQUEST_STATUS.DEST_PU){
            // als vehicle nog niet vol en sameDestStackCount > 0 en stack.freeSpace < sameDestStackCount+1, probeer nog 1 op te nemen
            if (vehicle.getCapacity() > vehicle.getCarriedBoxesCount() && sameDestStackCount > 0 && ((Stack)request.getPlaceLocation().getStorage()).getFreeSpace() < sameDestStackCount+1){
                // probeer nog 1 op te nemen
                timeAfterOperation = timeAfterMove + loadingSpeed;
                if (!vehicle.getCurrentNode().checkAndSetAvailable(time, timeAfterMove, timeAfterOperation)){
                    return false;
                }
                vehicle.setUnavailableUntil(timeAfterOperation);
                String box = "";
                if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                    box = stack.removeBox();
                    vehicle.addBox(box);
                }
                else{
                    vehicle.addBox(request.getBoxID());
                }
                addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.DEST_PU);
                return true;
            }

            // anders ga naar temp stack
            if (true) {
                String box = vehicle.getLastBox();
                List<GraphNode> tempStacks = findNStorage(1, request.getPickupLocation(), request.getPlaceLocation(), request.getStatus());
                if (tempStacks.isEmpty()){
                    System.out.println("No temp stack found");
                }
                GraphNode tempStack = tempStacks.get(0); //voorlopig per 1 relocaten dus remove niet nodig aangezien lijst niet meer nodig
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
        }

        return false;
    }


    private List<GraphNode> findNStorage(int N, GraphNode src, GraphNode dest, REQUEST_STATUS status){
        if(N == 0) return null;
        List<GraphNode> nodes = new ArrayList<>();
        int i = 0;

        // zoek naar stack die niet meer in requests voorkomt
        if (true) {
            List<GraphNode> requestStacks = new ArrayList<>();
            for (Request request : requests){
                if (request.getPickupLocation().getStorage() instanceof Stack){
                    requestStacks.add(request.getPickupLocation());
                }
                if (request.getPlaceLocation().getStorage() instanceof Stack){
                    requestStacks.add(request.getPlaceLocation());
                }
            }
            PriorityQueue<GraphNode> nodesByDistance = new PriorityQueue<>((node1, node2) -> {
                double distance1 = (status == REQUEST_STATUS.SRC) ? graph.calculateTime(src.getLocation(), node1.getLocation()) : graph.calculateTime(dest.getLocation(), node1.getLocation());
                double distance2 = (status == REQUEST_STATUS.SRC) ? graph.calculateTime(src.getLocation(), node2.getLocation()) : graph.calculateTime(dest.getLocation(), node2.getLocation());
                return Double.compare(distance1, distance2);
            });
            nodesByDistance.addAll(graph.getNodes());
            for (GraphNode node : nodesByDistance){
                if(node == src || node == dest || requestStacks.contains(node)){
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
            if(i>=N){
                return nodes;
            }
        }
        
        
        // anders vul aan tot N met dichtstbijzijnde stacks (dichtstbijzijnde bij src of dest afhankelijk van status)
        if (true) {
            PriorityQueue<GraphNode> nodesByDistance = new PriorityQueue<>((node1, node2) -> {
                double distance1 = (status == REQUEST_STATUS.SRC) ? graph.calculateTime(src.getLocation(), node1.getLocation()) : graph.calculateTime(dest.getLocation(), node1.getLocation());
                double distance2 = (status == REQUEST_STATUS.SRC) ? graph.calculateTime(src.getLocation(), node2.getLocation()) : graph.calculateTime(dest.getLocation(), node2.getLocation());
                return Double.compare(distance1, distance2);
            });
            nodesByDistance.addAll(graph.getNodes());
            for (GraphNode node : nodesByDistance){
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

        return null;
    }

    public void addLogEntry(String vehicleName, Location startLocation, double startTime, Location endLocation, double endTime, String boxId, REQUEST_STATUS type){
        String operation = switch (type){
            case SRC -> "PU";
            case SRC_RELOC -> "PL_RELOC";
            case DEST -> "PL";
            case DEST_PU -> "PU";
            case DEST_RELOC -> "PL_RELOC";
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
