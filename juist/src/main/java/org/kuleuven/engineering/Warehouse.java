package org.kuleuven.engineering;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
        this.openRequests = new ArrayList<>(requests);
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
            GraphNode n = graph.nodeMap.get(request.getPickupLocation());
            if(n.isBuffer()){
                boxLocationMap.put(request.getBoxID(), n);
            }
        }
    }

    public void scheduleRequests() {
        // TODO:vehicle afstand
        Queue<Request> requestQueue = new PriorityQueue<>((r1, r2) -> {
            GraphNode boxLocation1 = boxLocationMap.get(r1.getBoxID());
            GraphNode boxLocation2 = boxLocationMap.get(r2.getBoxID());

            double minTime1 = Double.MAX_VALUE;
            double minTime2 = Double.MAX_VALUE;

            for (Vehicle vehicle : vehicles) {
                if (vehicle.isAvailable()) {
                    double time1 = graph.calculateTime(boxLocation1.getLocation(), vehicle.getLocation());
                    double time2 = graph.calculateTime(boxLocation2.getLocation(), vehicle.getLocation());

                    minTime1 = Math.min(minTime1, time1);
                    minTime2 = Math.min(minTime2, time2);
                }
            }

            return Double.compare(minTime1, minTime2);
        });

        requestQueue.addAll(requests);

        while (!requestQueue.isEmpty() || !openRequests.isEmpty()) { 
            // als er vehicles vrij zijn en minder open requests dan vehicles, dan open request toevoegen om alle vehicles 1 request te geven
            // verder open requests afhandelen
            for (Vehicle vehicle : vehicles){
                if(vehicle.isAvailable(currentTime) && openRequests.size() < vehicles.size() && !requestQueue.isEmpty()){
                    openRequests.add(requestQueue.poll());
                }
            }
            if(!openRequests.isEmpty()){
                processOpenRequests();
            }
            currentTime++;
        }
    }

    private boolean handleRequest(Vehicle vehicle, Request request, double time){
        // todo: start tijd en locatie bijhouden, dest geen buffer en full? dan eerst dest relocate, als src buffer dan is remove ipv peek? move naar src, en relocaten tot huidige box vrij en naar dest brengen 
        // letten op vehicle capacity, vehicle lock, locatie lock, log


        // request status kan initial zijn dan kijken of dest stack is en vrij, dan naar src gaan PU, anders naar andere stack brengen?
        // request status kan "at src" zijn, dan kijken of relocation nodig, dan  move to temp stack en PL, anders naar dest en PL
        // request status kan "at src relocation" zijn, dan move terug naar src en PU
        // request status kan "at dest niet vrij" zijn, dan eerst naar temp en PL
        // request status kan "at dest relocation" zijn, dan move to dest 

        Location startLocation = vehicle.getLocation();

        if (request.getStatus().equals("initial")){
            // move naar src en PU na beweegtijd+loadingSpeed als dest stack is vrij of dest is buffer, anders naar dest en die vrijmaken
            // voeg log toe
            // status wordt "at src"
        }
        if (request.getStatus().equals("at src")){
            // kijken of relocation nodig, dan move to temp stack en PL, anders naar dest en PL
            // voeg log toe
            // status wordt "at src relocation" of "at dest"
        }
        if (request.getStatus().equals("at src relocation")){
            // move terug naar src en PU
            // voeg log toe
            // status wordt "at src"
        }
        if (request.getStatus().equals("at dest")){
            // kijken of relocation nodig, dan move to dest en PL, anders naar dest en PL
            // voeg log toe
            // status wordt "at dest relocation" of "at dest"
        }
        if (request.getStatus().equals("at dest relocation")){
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

    private void processOpenRequests(){
        for (Vehicle vehicle : vehicles){
            if(vehicle.isAvailable(currentTime)){
                // is zijn huidige request afgerond?
                if(vehicle.getCurrentRequestID() != -1 && openRequests.get(vehicle.getCurrentRequestID()-1) == null){ //huidige request niet meer open
                    vehicle.setCurrentRequestID(-1);
                }
                // werk 1 van de open requests af
                if(vehicle.getCurrentRequestID() == -1){
                    // kies request die nog niet aan ander vehicle is gegeven en koppel request aan vehicle
                    for (Request request : openRequests){
                        if(request.getAssignedVehicle().equals("")){
                            request.setAssignedVehicle(vehicle.getName());
                            vehicle.setCurrentRequestID(request.getID()-1);
                            break;
                        }
                    }
                }
                // nu het vehicle een request heeft, kan het de volgende stap in het request afhandelen
                if(vehicle.getCurrentRequestID() != -1){
                    handleRequest(vehicle, openRequests.get(vehicle.getCurrentRequestID()), currentTime);
                }
            }
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
