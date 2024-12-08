package org.kuleuven.engineering;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

import org.kuleuven.engineering.graph.Graph;
import org.kuleuven.engineering.graph.GraphNode;

public class Warehouse {
    private final Graph graph;
    private final List<Vehicle> vehicles;
    private final List<Request> requests;
    private final HashMap<Integer, Integer> stackIsUsedUntil;
    private final List<Integer[]> activeRelocations = new ArrayList<>();
    private final HashMap<Integer, Integer> waitForRequestFinish = new HashMap<>();
    private boolean noAvailableTempStack = false;
    private boolean targetStackIsUsed = false;
    private final List<String> operationLog = new ArrayList<>();
    private final HashMap<String, GraphNode> boxLocationMap = new HashMap<>(); // Map to track box locations
    private double currentTime = 0;
    private final int loadingSpeed;
    private int round = 0;

    public Warehouse(Graph graph, List<Vehicle> vehicles, List<Request> requests, int loadingSpeed) {
        this.graph = graph;
        this.vehicles = vehicles;
        this.requests = requests;
        this.loadingSpeed = loadingSpeed;
        calculateStartingState();
        // find number of stacks
        this.stackIsUsedUntil = new HashMap<>();
        for (GraphNode node : graph.getNodes()){
            if (node.getStorage() instanceof Stack){
                stackIsUsedUntil.put(((Stack) node.getStorage()).getID(), -1);
            }
        }
        
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
        // doe een ronde requests met topdozen die naar buffer moeten
        scheduleStackToBufferRequestsOfTopBoxes();
        System.out.println("Round 1 done");
        for (Vehicle vehicle : vehicles){
            vehicle.resetStackIDs();
        }
        round++;
        // daarna werk de rest 1 voor 1 af (eerst stack -> buffer requests based on depth)
        scheduleStackToBufferRequests();
        System.out.println("Round 2 done");
        for (Vehicle vehicle : vehicles){
            vehicle.resetStackIDs();
        }
        round++;
        // dan buffer -> stack requests met letten op eindlocaties)
        scheduleBufferToStackRequests();
        System.out.println("Round 3 done");
    }

    



    private void scheduleStackToBufferRequestsOfTopBoxes() {
        // zoek requests met topdozen en de dozen op plek onder die request die ook opgehaald moeten worden voor naar buffer te gaan
        List<Request> requestListWithoutRelocation= new ArrayList<>();
        List<Request> tempList = new ArrayList<>(requests); 
        for (Request request : tempList){
            IStorage storage = request.getPickupLocation().getStorage();
            if (!request.getPickupLocation().isBuffer() && storage.peek().equals(request.getBoxID()) && request.getPlaceLocation().isBuffer()){
                requestListWithoutRelocation.add(request);
                requests.remove(request);
                // zoek of de doos eronder ook opgehaald moet worden
                for (int i = 1; i < ((Stack)storage).getBoxesSize(); i++){
                    String boxIDBelow = ((Stack)storage).peakAtDepth(i);
                    // als die box ook opgehaald moet worden, voeg die request toe
                    boolean found = false;
                    for (Request request2 : tempList){
                        if (request2.getBoxID().equals(boxIDBelow)){
                            requestListWithoutRelocation.add(request2);
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
        // sorteer op stack depth
        requestListWithoutRelocation.sort((r1, r2) -> {
            if (r1.getPickupLocation().getStorage() instanceof Stack stack1 && r2.getPickupLocation().getStorage() instanceof Stack stack2) {
                return Integer.compare(stack1.getDepthOfBox(r1.getBoxID()), stack2.getDepthOfBox(r2.getBoxID()));
            }
            return 0;
        });
        // verdeel over vehicles
        requests.removeAll(requestListWithoutRelocation);
        distributeRequests(requestListWithoutRelocation, true);
        stackToBufferRequestsLoop(0);
    }

    private void scheduleStackToBufferRequests() {
        List<Request> requestsCopy = new ArrayList<>();
        for (Request request : requests){
            if (request.getPickupLocation().getStorage() instanceof Stack && request.getPlaceLocation().isBuffer()){
                requestsCopy.add(request);
            }
        }
        requestsCopy.sort((r1, r2) -> {
            if (r1.getPickupLocation().getStorage() instanceof Stack stack1 && r2.getPickupLocation().getStorage() instanceof Stack stack2) {
                return Integer.compare(stack1.getDepthOfBox(r1.getBoxID()), stack2.getDepthOfBox(r2.getBoxID()));
            }
            return 0;
        });
        requests.removeAll(requestsCopy);
        distributeRequests(requestsCopy, true);
        stackToBufferRequestsLoop(1);
    }

    private void stackToBufferRequestsLoop(int type){
        // ga naar eerste locatie, check of nog plaats op vehicle, dan naar andere pickup, als vol naar buffer
        // enkel met eerste vehicle
        // while niet alle vehicles hun requests hebben afgerond
        boolean allRequestsDone = true;
        for (Vehicle vehicle : vehicles){
            if (!vehicle.getRequests().isEmpty() || !vehicle.getOpenRequests().isEmpty()){
                allRequestsDone = false;
                break;
            }
        }
        boolean[] firstGetAnother = new boolean[vehicles.size()];
        for (int i = 0; i < vehicles.size(); i++){
            firstGetAnother[i] = false;
        }
        while (!allRequestsDone){
            for (Vehicle vehicle : vehicles){
                if (vehicle.isAvailable(currentTime)){
                    boolean hasSpace = vehicle.getCapacity() > vehicle.getCarriedBoxesCount();
                    boolean notWorkingOnRequest = vehicle.getCurrentRequestID() == -1;
                    boolean hasRequestAvailable = !vehicle.getRequests().isEmpty();
                    boolean getAnotherFirst = firstGetAnother[vehicles.indexOf(vehicle)];
                    if (!getAnotherFirst && hasSpace && notWorkingOnRequest && hasRequestAvailable && vehicle.getOpenRequests().isEmpty()) {
                        boolean success = vehicle.setNewOpenRequest(stackIsUsedUntil, currentTime, type, false);
                        if (!success) continue; // kan hij geen reuest openen omdat de stack waar hij naartoe moet nog gebruikt wordt? wacht
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        
                        // voor eerste ronde liggen alle boxes vanboven dus 1 capacity is voldoende
                        if (type == 0) firstGetAnother[vehicles.indexOf(vehicle)] = vehicle.getCapacity() > vehicle.getCarriedBoxesCount() && !vehicle.getRequests().isEmpty();
                        else {
                            // hier controle tussen capacity en volgende doos zijn depth en kijken of die stack nog niet gebruikt wordt voor relocation
                            List<Request> nextRequests = vehicle.getRequests().stream().toList();
                            if (!nextRequests.isEmpty()){
                                int neededCapacity = ((Stack) nextRequests.get(0).getPickupLocation().getStorage()).getDepthOfBox(nextRequests.get(0).getBoxID());
                                boolean hasEnoughCapacity = (vehicle.getCapacity() - vehicle.getCarriedBoxesCount()) >= neededCapacity;
                                firstGetAnother[vehicles.indexOf(vehicle)] = stackIsUsedUntil.get(nextRequests.get(0).getPickupLocation().getStorage().getID()) < currentTime && hasEnoughCapacity && !vehicle.getRequests().isEmpty() && vehicle.hasBox(request.getBoxID()) && vehicle.getCurrentNode() != request.getPlaceLocation();
                            }
                            else firstGetAnother[vehicles.indexOf(vehicle)] = false;
                        }
                    }

                    else if (getAnotherFirst){
                        boolean success = vehicle.setNewOpenRequest(stackIsUsedUntil, currentTime, type, true);
                        if (!success) continue; // kan hij geen reuest openen omdat de stack waar hij naartoe moet nog gebruikt wordt? wacht
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        // voor eerste ronde liggen alle boxes vanboven dus 1 capacity is voldoende
                        if (type == 0) firstGetAnother[vehicles.indexOf(vehicle)] = vehicle.getCapacity() > vehicle.getCarriedBoxesCount() && !vehicle.getRequests().isEmpty();
                        else {
                            // hier controle tussen capacity en volgende doos zijn depth
                            List<Request> nextRequests = vehicle.getRequests().stream().toList();
                            if (!nextRequests.isEmpty()){
                                int neededCapacity = ((Stack) nextRequests.get(0).getPickupLocation().getStorage()).getDepthOfBox(nextRequests.get(0).getBoxID());
                                boolean hasEnoughCapacity = (vehicle.getCapacity() - vehicle.getCarriedBoxesCount()) >= neededCapacity;
                                firstGetAnother[vehicles.indexOf(vehicle)] = stackIsUsedUntil.get(nextRequests.get(0).getPickupLocation().getStorage().getID()) < currentTime && hasEnoughCapacity && !vehicle.getRequests().isEmpty() && vehicle.hasBox(request.getBoxID()) && vehicle.getCurrentNode() != request.getPlaceLocation();
                            }
                            else firstGetAnother[vehicles.indexOf(vehicle)] = false;
                        }
                    }

                    else if (!notWorkingOnRequest && !getAnotherFirst && !vehicle.getOpenRequests().isEmpty()){
                        // ga naar buffer en werk de open requests 1 voor 1 af
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getAssignedVehicle() == vehicle.getID();}).toList().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (type == 1) {
                            // als hier zit omdat het niet direct de juist doos vasthad, kijk toch nog eens of er nog een doos opgehaald kan worden (als hij nog niet op dest is, anders werk gewoon requests af van reeds opgehaalde dozen)
                            List<Request> nextRequests = vehicle.getRequests().stream().toList();
                            if (!nextRequests.isEmpty()){
                                int neededCapacity = ((Stack) nextRequests.get(0).getPickupLocation().getStorage()).getDepthOfBox(nextRequests.get(0).getBoxID());
                                boolean hasEnoughCapacity = (vehicle.getCapacity() - vehicle.getCarriedBoxesCount()) >= neededCapacity;
                                firstGetAnother[vehicles.indexOf(vehicle)] = stackIsUsedUntil.get(nextRequests.get(0).getPickupLocation().getStorage().getID()) < currentTime && hasEnoughCapacity && !vehicle.getRequests().isEmpty() && vehicle.hasBox(request.getBoxID()) && vehicle.getCurrentNode() != request.getPlaceLocation();
                            }
                            else firstGetAnother[vehicles.indexOf(vehicle)] = false;
                        }
                        if (request.isDone()){
                            vehicle.closeRequest(request);
                            // als er een relocation op deze request wacht
                            if (waitForRequestFinish.containsKey(request.getID())){
                                // zet de vehicle die wacht op deze request weer vrij
                                int vehicleID = waitForRequestFinish.get(request.getID());
                                for (Vehicle v : vehicles){
                                    if (v.getID() == vehicleID){
                                        v.setUnavailableUntil(vehicle.getUnavailableUntil());
                                        break;
                                    }
                                }
                                // verwijder de request uit de hashmap
                                waitForRequestFinish.remove(request.getID());
                            }
                        }
                    }
                }
            }
            
            // loop over vehicles en kijk of ze allemaal een lege requestlist hebben
            allRequestsDone = true;
            for (Vehicle vehicle : vehicles){
                if (!vehicle.getRequests().isEmpty() || !vehicle.getOpenRequests().isEmpty()){
                    allRequestsDone = false;
                    break;
                }
            }


            currentTime++;
            // verwijder alle relocations die klaar zijn
            activeRelocations.removeIf(x -> x[3] < currentTime);
        }
    }





    
    private void scheduleBufferToStackRequests() {
        // dan buffer -> stack requests met letten op eindlocaties)
        // maak list voor buffer -> stack requests (alle overige requests)
        List<Request> requestsCopy = new ArrayList<>(requests);
        requests.removeAll(requestsCopy);
        // check of genoeg plaats voor alle requests
        int totalFreeSpace = 0;
        for (GraphNode node : graph.getNodes()){
            if (node.getStorage() instanceof Stack stack){
                totalFreeSpace += stack.getFreeSpace();
            }
        }
        if (totalFreeSpace < requestsCopy.size()){
            System.out.println("not enough space for requests");
            return;
        }

        
        distributeRequests(requestsCopy, false);
        bufferToStackRequestsLoop(2);
    }

    private void bufferToStackRequestsLoop(int type) {
        boolean allRequestsDone = false;
        boolean[] firstGetAnother = new boolean[vehicles.size()];
        for (int i = 0; i < vehicles.size(); i++){
            firstGetAnother[i] = false;
        }

        // per stack open requests op een voertuig en maak simulated requests aan
        // voer request voor request uit in simulated requests, als simulated requests klaar zijn, werk de open requests af
        // ga over naar requests van volgende stack
        while (!allRequestsDone){
            for (Vehicle vehicle : vehicles){
                if (vehicle.isAvailable(currentTime) && (!vehicle.getRequests().isEmpty() || !vehicle.getOpenRequests().isEmpty())){
                    if (!vehicle.getRequests().isEmpty() && vehicle.getOpenRequests().isEmpty()){
                        // open requests met zelfde dest
                        Location dest = vehicle.getRequests().get(0).getPlaceLocation().getLocation();
                        List<Request> requestsWithSameDest = vehicle.getRequests().stream().filter(x -> {return x.getPlaceLocation().getLocation() == dest;}).toList();
                        for (Request request : requestsWithSameDest){
                            vehicle.addOpenRequest(request);
                            vehicle.getRequests().remove(request);
                        }
                    }

                    // bereken hoeveel plaats nog nodig op dest stack
                    int neededCapacity = vehicle.getOpenRequests().size();
                    Stack stack  = (Stack) vehicle.getOpenRequests().get(0).getPlaceLocation().getStorage();
                    int freeSpace = stack.getFreeSpace();
                    int requiredExtraCapacity = neededCapacity - freeSpace;

                    if (vehicle.getCurrentRequestID() == -1 && requiredExtraCapacity > 0){
                        // maak simulated request om topbox te verplaatsen naar tempstack
                        GraphNode src = vehicle.getOpenRequests().get(0).getPickupLocation();
                        GraphNode dest = vehicle.getOpenRequests().get(0).getPlaceLocation();
                        List<GraphNode> tempstacks = findNStorage(1, src, dest, REQUEST_STATUS.SIMULATED, vehicle);
                        if (tempstacks.isEmpty()){
                            System.out.println("waiting for tempstack vehicle " + vehicle.getName());
                            continue;
                        }
                        GraphNode tempStack = tempstacks.get(0);
                        Request simulatedRequest = new Request(vehicle.getOpenRequests().get(0).getPlaceLocation(), tempStack, Integer.MAX_VALUE-(vehicle.getSimulatedRequests().size()+vehicle.getOpenRequests().size()), stack.peek());
                        vehicle.addSimulatedRequest(simulatedRequest);
                        vehicle.setNewOpenSimulatedRequest();
                        Request request = vehicle.getOpenSimulatedRequests().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                    }
                    else if (vehicle.getCurrentRequestID() != -1 && requiredExtraCapacity >= 0 && !vehicle.getOpenSimulatedRequests().isEmpty()){
                        // werk simulated requests af
                        Request request = vehicle.getOpenSimulatedRequests().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (request.isDone()){
                            vehicle.closeSimulatedRequest(request);
                        }
                    }
                    else if (vehicle.getCurrentRequestID() == -1 && requiredExtraCapacity <= 0 && !vehicle.getOpenRequests().isEmpty()) {
                        // begin aan open requests
                        Request request = vehicle.getOpenRequests().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (request.isDone()){
                            vehicle.closeRequest(request);
                        }
                    }
                    else if (vehicle.getCurrentRequestID() != -1 && requiredExtraCapacity <= 0 && !vehicle.getOpenRequests().isEmpty()){
                        // werk open requests af
                        Request request = vehicle.getOpenRequests().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (request.isDone()){
                            vehicle.closeRequest(request);
                        }
                    }

                }
            }

            // loop over vehicles en kijk of ze allemaal een lege requestlist hebben
            allRequestsDone = true;
            for (Vehicle vehicle : vehicles){
                if (!vehicle.getRequests().isEmpty() || !vehicle.getOpenRequests().isEmpty()){
                    allRequestsDone = false;
                    break;
                }
            }

            currentTime++;
            activeRelocations.removeIf(x -> x[3] < currentTime);
        }

    }
    




    // verdeel requests over vehicles op basis van stack load
    public void distributeRequests(List<Request> requestList, boolean usePickupLocation) {
        // calculate stack load en verdeel over # vehicles:
        HashMap<Integer, Integer> stackLoad = calculateStackLoad(requestList, usePickupLocation);
        int requestsPerVehicle = (requestList.size() / vehicles.size()) + 1;
        List<List<Request>> requestsPerVehicleList = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++) {
            requestsPerVehicleList.add(new ArrayList<>());
        }

        // sorteer stackIDs op stackLoad grootte
        List<Integer> stackIDs = new ArrayList<>(stackLoad.keySet());
        stackIDs.sort((s1, s2) -> stackLoad.get(s2).compareTo(stackLoad.get(s1)));

        int vehicleIndex = 0;
        for (Integer stackID : stackIDs) {
            // als een vehicle genoeg requests heeft, ga naar de volgende vehicle
            while (requestsPerVehicleList.get(vehicleIndex).size() >= requestsPerVehicle) {
                vehicleIndex++;
            }

            // Filter requests based on location type
            List<Request> requestsForStack = requestList.stream()
                .filter(x -> {
                    Stack stack = (Stack) (usePickupLocation ?
                        x.getPickupLocation().getStorage() :
                        x.getPlaceLocation().getStorage());
                    return stack.getID() == stackID;
                })
                .toList();

            requestsPerVehicleList.get(vehicleIndex).addAll(requestsForStack);
            vehicleIndex++;
            if (vehicleIndex == vehicles.size()) {
                vehicleIndex = 0;
            }
        }

        // geef elk vehicle zijn requests
        for (int i = 0; i < vehicles.size(); i++) {
            vehicles.get(i).setRequests(requestsPerVehicleList.get(i));
        }
    }
    // returns a map with the stack id as key and the number of requests for that stack as value
    public HashMap<Integer, Integer> calculateStackLoad(List<Request> requestList, boolean usePickupLocation) {
        HashMap<Integer, Integer> stackLoad = new HashMap<>();
        for (Request request : requestList) {
            Stack stack = (Stack) (usePickupLocation ?
                request.getPickupLocation().getStorage() :
                request.getPlaceLocation().getStorage());

            int stackId = stack.getID();
            stackLoad.merge(stackId, 1, Integer::sum);
        }
        return stackLoad;
    }

    private boolean handleRequest(Vehicle vehicle, Request request, double time, int sameDestStackCount){
        Location startLocation = vehicle.getLocation();
        double timeAfterMove = time;

        if (request.getStatus() == REQUEST_STATUS.INITIAL){
            // als het vehicle niet leeg is en bij een stack, probeer vehicle zo veel mogelijk leeg te maken
            // check if vehicle got a needed box and is collecting more boxes
            boolean result = leegVehicle(vehicle, startLocation, timeAfterMove, time, request);
            if (targetStackIsUsed) {
                targetStackIsUsed = false;
                return false;
            }
            // als dest een stack is en full, dan moet er gerelocate worden of check of meerdere requests ook naar dezelfde stack gaan, zoveel plaats vrijmaken op dest stack, dan al die requests afwerken
            if (!result) {
                result = maakPlaatsVrijOpDest(vehicle, startLocation, timeAfterMove, time, request);
                if (targetStackIsUsed) {
                    targetStackIsUsed = false;
                    return false;
                }
            }
            // ga naar src en PU
            if (!result) {
                PickupSrc(vehicle, startLocation, timeAfterMove, time, request);
                if (targetStackIsUsed) {
                    targetStackIsUsed = false;
                    return false;
                }
            } 
            return true;
        }

        if (request.getStatus() == REQUEST_STATUS.SRC){
           // kijken of relocation nodig (we hebben box niet op vehicle), als vehicle vol en nog niet juiste box, ga naar temp stack
           boolean result = boxesRelocatenNaarTempStack(vehicle, startLocation, timeAfterMove, timeAfterMove, request);
           if (noAvailableTempStack) {
                noAvailableTempStack = false;
                return false;
            }
            if (targetStackIsUsed) {
                targetStackIsUsed = false;
                return false;
            }
            // anders als vehicle niet vol en nog niet juiste box, probeer nog 1 op te nemen
            if (!result) result = NeemNogEenboxOpBijSrc(vehicle, startLocation, timeAfterMove, time, request);
            if (targetStackIsUsed) {
                targetStackIsUsed = false;
                return false;
            }
           // anders naar dest en PL
           if (!result) placeBoxBijDest(vehicle, startLocation, timeAfterMove, time, request);
           if (targetStackIsUsed) {
                targetStackIsUsed = false;
                return false;
            }
           return true;
        }

        if (request.getStatus() == REQUEST_STATUS.DEST_PU){
            // als vehicle nog niet vol en sameDestStackCount > 0 en stack.freeSpace < sameDestStackCount+1, probeer nog 1 op te nemen
            boolean result = neemNogBoxOpDest(vehicle, startLocation, timeAfterMove, time, request, sameDestStackCount);
            if (targetStackIsUsed) {
                targetStackIsUsed = false;
                return false;
            }
            // anders ga naar temp stack
            if (!result) placeAtTempStackDest(vehicle, startLocation, timeAfterMove, time, request);
            if (noAvailableTempStack) {
                noAvailableTempStack = false;
                return false;
            }
            if (targetStackIsUsed) {
                targetStackIsUsed = false;
                return false;
            }
            return true;
        }
        return false;
    }





    private boolean leegVehicle(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        boolean vehicleGotRequestBox = false;
        for (Request req : vehicle.getOpenRequests()) {
            if (vehicle.hasBox(req.getBoxID())) {
                vehicleGotRequestBox = true;
                break;
            }
        }
        if (vehicle.getCarriedBoxesCount() > 0 && vehicle.getCurrentNode().getStorage() instanceof Stack stack
                && !vehicleGotRequestBox && vehicle.currentNode != request.getPickupLocation()){
            if (stack.getFreeSpace() > 0){
                String box = vehicle.getLastBox();
                double timeAfterOperation = timeAfterMove + loadingSpeed;
                if (stackIsUsedUntil.get(stack.getID()) < currentTime){
                    stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
                }
                else {
                    targetStackIsUsed = true;
                    return false;
                }
                vehicle.setUnavailableUntil(timeAfterOperation);
                stack.addBox(box);
                vehicle.removeBox(box);
                addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_RELOC);
                return true;
            }
        }
        return false;
    }
    private boolean maakPlaatsVrijOpDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        GraphNode dest = request.getPlaceLocation();
        if (dest.getStorage() instanceof Stack stack && stack.getFreeSpace() < 1){
            // maak plaats vrij op dest stack 
            if (startLocation != dest.getLocation()){
                timeAfterMove += graph.getTravelTime(vehicle, dest);
            }
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            if (stackIsUsedUntil.get(stack.getID()) < currentTime){
                stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            }
            else {
                targetStackIsUsed = true;
                return false;
            }
            vehicle.setUnavailableUntil(timeAfterOperation);
            vehicle.moveTo(dest);
            String box = stack.removeBox();
            if (box.equals("B708")){
                System.out.println("Remove box from stack");
            }
            vehicle.addBox(box);
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.DEST_PU);
            request.setStatus(REQUEST_STATUS.DEST_PU);
            return true;
        }
        return false;
    }
    private void PickupSrc(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        GraphNode src = request.getPickupLocation();
        if (startLocation != src.getLocation()){
            timeAfterMove += graph.getTravelTime(vehicle, src);
        }
        double timeAfterOperation = timeAfterMove + loadingSpeed;
        if (src.getStorage() instanceof Stack stack){
            if (stackIsUsedUntil.get(stack.getID()) < currentTime){
                stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            }
            else {
                targetStackIsUsed = true;
                return;
            }
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
    }

    private boolean boxesRelocatenNaarTempStack(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        if (!vehicle.hasBox(request.getBoxID()) && vehicle.getCapacity() == vehicle.getCarriedBoxesCount() && vehicle.getCarriedBoxesCount() > 0){
            String box = vehicle.getLastBox();
            // move to temp stack
            GraphNode src = request.getPickupLocation();
            GraphNode dest = request.getPlaceLocation();
            REQUEST_STATUS status = request.getStatus();
            List<GraphNode> tempStacks = findNStorage(1, src, dest, status, vehicle);
            if (tempStacks.isEmpty()){
                System.out.println("No availabletemp stack found, waiting...");
                noAvailableTempStack = true;
                return false;
            }
            GraphNode tempStack = tempStacks.get(0); //voorlopig per 1 relocaten
            
            timeAfterMove += graph.getTravelTime(vehicle, tempStack);
            double timeAfterOperation = timeAfterMove + loadingSpeed;

            // als er al een relocation bezig is van die stack naar hier, wacht tot de andere klaar is met dat request (anders werken ze elkaar tegen)
            for (Integer[] relocation : activeRelocations){
                if (relocation[1] == ((Stack) vehicle.getCurrentNode().getStorage()).getID() && relocation[0] == ((Stack) tempStack.getStorage()).getID()){
                    waitForRequestFinish.put(relocation[2], vehicle.getID());
                    vehicle.setUnavailableUntil(Double.MAX_VALUE);
                    noAvailableTempStack = true;
                    return false;
                }
            }


            if (stackIsUsedUntil.get(tempStack.getStorage().getID()) < currentTime){
                stackIsUsedUntil.put(tempStack.getStorage().getID(), (int) timeAfterOperation);
            }
            else {
                targetStackIsUsed = true;
                return false;
            }
            vehicle.setUnavailableUntil(timeAfterOperation);
            int prevVehicleLocation = ((Stack) vehicle.getCurrentNode().getStorage()).getID();
            vehicle.moveTo(tempStack);
            if (tempStack.getStorage() instanceof Stack stack){
                stack.addBox(box);
            }
            vehicle.removeBox(box);
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.SRC_RELOC);
            request.setStatus(REQUEST_STATUS.INITIAL);
            stackIsUsedUntil.put(((Stack) tempStack.getStorage()).getID(), (int) (timeAfterOperation));
            activeRelocations.add(new Integer[]{prevVehicleLocation, ((Stack) tempStack.getStorage()).getID(), request.getID(), (int) timeAfterOperation});
            return true;
        }
        return false;
    }
    private boolean NeemNogEenboxOpBijSrc(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        if (!vehicle.hasBox(request.getBoxID()) && vehicle.getCapacity() > vehicle.getCarriedBoxesCount()){
            // probeer nog 1 op te nemen
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                if (stackIsUsedUntil.get(stack.getID()) < currentTime){
                    stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
                }
                else {
                    targetStackIsUsed = true;
                    return false;
                }
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
        return false;
    }
    private void placeBoxBijDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        GraphNode dest = request.getPlaceLocation();
        if (dest.getStorage() instanceof Stack stack && stack.getFreeSpace() == 0){
            System.out.println("Stack is full, waiting...");
            return;
        }
        // bereken wanneer hij aan dest is
        if (startLocation != dest.getLocation()){
            timeAfterMove += graph.getTravelTime(vehicle, dest);
        }
        // bereken wanneer hij klaar zou zijn met pickup
        double timeAfterOperation = timeAfterMove + loadingSpeed;
        // als hier geraakt dan kan hij naar dest en PL doen
        if (dest.getStorage() instanceof Stack stack){
            if (stackIsUsedUntil.get(stack.getID()) < currentTime){
                stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            }
            else {
                targetStackIsUsed = true;
                return;
            }
        }
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
    }

    private boolean neemNogBoxOpDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, int sameDestStackCount) {
        if (vehicle.getCapacity() > vehicle.getCarriedBoxesCount() && sameDestStackCount > 0 && ((Stack)request.getPlaceLocation().getStorage()).getFreeSpace() < sameDestStackCount+1){
            // probeer nog 1 op te nemen
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                if (stackIsUsedUntil.get(stack.getID()) < currentTime){
                    stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
                }
                else {
                    targetStackIsUsed = true;
                    return false;
                }
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
        return false;
    }
    private void placeAtTempStackDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        String box = vehicle.getLastBox();
        GraphNode src = request.getPickupLocation();
        GraphNode dest = request.getPlaceLocation();
        REQUEST_STATUS status = request.getStatus();
        List<GraphNode> tempStacks = findNStorage(1, src, dest, status, vehicle);
        if (tempStacks.isEmpty()){
            System.out.println("No availabletemp stack found, waiting...");
            noAvailableTempStack = true;
            return;
        }
        GraphNode tempStack = tempStacks.get(0); //voorlopig per 1 relocaten dus remove niet nodig aangezien lijst niet meer nodig
        timeAfterMove += graph.getTravelTime(vehicle, tempStack);
        double timeAfterOperation = timeAfterMove + loadingSpeed;

        // als er al een relocation bezig is van die stack naar hier, wacht tot de andere klaar is met dat request (anders werken ze elkaar tegen)
        for (Integer[] relocation : activeRelocations){
            if (relocation[1] == ((Stack) vehicle.getCurrentNode().getStorage()).getID() && relocation[0] == ((Stack) tempStack.getStorage()).getID()){
                waitForRequestFinish.put(relocation[2], vehicle.getID());
                vehicle.setUnavailableUntil(Double.MAX_VALUE);
                noAvailableTempStack = true;
                return;
            }
        }

        if (stackIsUsedUntil.get(tempStack.getStorage().getID()) < currentTime){
            stackIsUsedUntil.put(tempStack.getStorage().getID(), (int) timeAfterOperation);
        }
        else {
            targetStackIsUsed = true;
            return;
        }
        vehicle.setUnavailableUntil(timeAfterOperation);
        
        int prevVehicleLocation = ((Stack) vehicle.getCurrentNode().getStorage()).getID();
        vehicle.moveTo(tempStack);
        tempStack.getStorage().addBox(box);
        vehicle.removeBox(box);
        addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_RELOC);
        request.setStatus(REQUEST_STATUS.INITIAL);
        stackIsUsedUntil.put(((Stack) tempStack.getStorage()).getID(), (int) (timeAfterOperation));
        activeRelocations.add(new Integer[]{prevVehicleLocation, ((Stack) tempStack.getStorage()).getID(), request.getID(), (int) timeAfterOperation});
    }



    private List<GraphNode> findNStorage(int N, GraphNode src, GraphNode dest, REQUEST_STATUS status, Vehicle currentVehicle){
        if(N == 0) return null;
        List<GraphNode> nodes = new ArrayList<>();

        if (round == 1 || round == 2){
            boolean found = false;
            // kijk of hij naar één van zijn eigen stacks kan gaan waar hij nu niet staat
            for (Integer stackID : currentVehicle.getMyStackIDs()){
                int srcID = src.getStorage().getID();
                int destID = dest.getStorage().getID();
                if (stackID != srcID && stackID != destID && (currentVehicle.getCurrentNode() == null || (currentVehicle.getCurrentNode() != null && currentVehicle.getCurrentNode().getStorage() instanceof Stack stack && stack.getID() != stackID && graph.getStackByID(stackID).getStorage() instanceof Stack stack2 && !stack2.isFull()))){
                    found = true;
                    int time1 = stackIsUsedUntil.get(stackID);
                    int time2 = (int) currentTime;
                    boolean stackIsUsed = time1 < time2;
                    if (stackIsUsed){
                        nodes.add(graph.getStackByID(stackID));
                        return nodes;
                    }
                }
            }
            if (found) return nodes;
        }
        

        // zoek naar stack nooit meer requests voorkomt
        List<GraphNode> requestStacks = new ArrayList<>();
        for (Vehicle vehicle : vehicles){
            for (Integer stackID : vehicle.getMyStackIDs()){
                if (currentVehicle.getCurrentNode() == null || (currentVehicle.getCurrentNode() != null && currentVehicle.getCurrentNode().getStorage() instanceof Stack stack && stackID != stack.getID())){
                    requestStacks.add(graph.getStackByID(stackID));
                }
            }
        }
        PriorityQueue<GraphNode> nodesByDistance = new PriorityQueue<>((node1, node2) -> {
            double distance1 = (status == REQUEST_STATUS.SRC) ? graph.calculateTime(src.getLocation(), node1.getLocation()) : graph.calculateTime(dest.getLocation(), node1.getLocation());
            double distance2 = (status == REQUEST_STATUS.SRC) ? graph.calculateTime(src.getLocation(), node2.getLocation()) : graph.calculateTime(dest.getLocation(), node2.getLocation());
            return Double.compare(distance1, distance2);
        });
        nodesByDistance.addAll(graph.getNodes());
        boolean found = false;
        for (GraphNode node : nodesByDistance){
            if(!(node == src || node == dest || requestStacks.contains(node)) && node.getStorage() instanceof Stack stack && !stack.isFull()){
                found = true;
                if ((stackIsUsedUntil.get(stack.getID()) < currentTime) || status == REQUEST_STATUS.SIMULATED){
                    nodes.add(node);
                    return nodes;
                }
            }
        }
        if (found) return nodes; // return lege lijst als vehicle moet wachten tot een temp stack vrij is
        

        // zoek naar stack die in request van ander vehicle voorkomt
        requestStacks = new ArrayList<>();
        for (Vehicle vehicle : vehicles){
            if (vehicle == currentVehicle) continue;
            for (Integer stackID : vehicle.getMyStackIDs()){
                if (currentVehicle.getCurrentNode() == null || (currentVehicle.getCurrentNode() != null && currentVehicle.getCurrentNode().getStorage() instanceof Stack stack && stack.getID() != stackID)){
                    requestStacks.add(graph.getStackByID(stackID));
                }
            }
        }
        PriorityQueue<GraphNode> nodesByDistance2 = new PriorityQueue<>((node1, node2) -> {
            double distance1 = (status == REQUEST_STATUS.SRC) ? graph.calculateTime(src.getLocation(), node1.getLocation()) : graph.calculateTime(dest.getLocation(), node1.getLocation());
            double distance2 = (status == REQUEST_STATUS.SRC) ? graph.calculateTime(src.getLocation(), node2.getLocation()) : graph.calculateTime(dest.getLocation(), node2.getLocation());
            return Double.compare(distance1, distance2);
        });
        nodesByDistance2.addAll(requestStacks);
        for (GraphNode node : nodesByDistance2){
            if(!(node == src || node == dest) && node.getStorage() instanceof Stack stack && !stack.isFull()){
                if (stackIsUsedUntil.get(stack.getID()) < currentTime){
                    nodes.add(node);
                    return nodes;
                }
            }
        }
        return nodes;
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
        System.out.println(vehicleName + ";" + startLocation.getX() + ";"+ startLocation.getY() + ";" + (int) startTime  + ";" + endLocation.getX() + ";" + endLocation.getY()   + ";" + (int)endTime + ";"+ boxId + ";" + operation);
        operationLog.add(vehicleName + ";" + startLocation.getX() + ";"+ startLocation.getY() + ";" + (int) startTime  + ";" + endLocation.getX() + ";" + endLocation.getY()   + ";" + (int)endTime + ";"+ boxId + ";" + operation);

    }

    public void writeOperationLog() {
        StringBuilder output = new StringBuilder();
        System.out.println("--------------------------------------------------------------------------------------------Writing operation log");
        for (String logEntry : operationLog) {
            output.append(logEntry+'\n');
            System.out.println(logEntry);
        }
        try(FileWriter fw = new FileWriter("output.txt")){
            fw.write("%vehicle;startx;starty;starttime;endx;endy;endtime;box;operation\n"+output);
        } catch (Exception e){
            System.out.println(e);
        }
        System.out.println("aantal moves: " + operationLog.size());
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
