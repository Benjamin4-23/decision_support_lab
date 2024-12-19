package org.kuleuven.engineering;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;

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
    private double currentTime = 0;
    private final int loadingSpeed;
    private int round = 0;
    private long startingTime;
    private final boolean[] firstGetAnother;
    private final List<List<Request>> requestsPerVehicleList;

    public Warehouse(Graph graph, List<Vehicle> vehicles, List<Request> requests, int loadingSpeed) {
        this.graph = graph;
        this.vehicles = vehicles;
        this.requests = requests;
        this.loadingSpeed = loadingSpeed;
        this.stackIsUsedUntil = new HashMap<>();
        for (GraphNode node : graph.getNodes()){
            if (node.getStorage() instanceof Stack stack){
                stackIsUsedUntil.put(stack.getID(), -1);
            }
        }
        firstGetAnother = new boolean[vehicles.size()];
        requestsPerVehicleList = new ArrayList<>();
    }

    public void scheduleRequests() {
        startingTime = System.currentTimeMillis();

        // Finish requests with topboxes that need to be moved to buffer
        scheduleStackToBufferRequestsOfTopBoxes();
        resetVehicleStackIDs();
        round++;

        // After finishing requests with topboxes that need to be moved to buffer, work on the rest 1 for 1 (stack -> buffer requests based on depth)
        scheduleStackToBufferRequests();
        resetVehicleStackIDs();
        round++;

        // then buffer -> stack requests with attention to end locations
        scheduleBufferToStackRequests();
    }

    
    
    // round 0 and 1 are the same, just with different priorities
    private void scheduleStackToBufferRequestsOfTopBoxes() {
        // find requests with topboxes and the boxes on the location below that request that also need to be picked up to go to buffer
        List<Request> requestListWithoutRelocation = findTopBoxRequests();

        // remove requests from main list
        requests.removeAll(requestListWithoutRelocation);

        // sort based on stack depth
        requestListWithoutRelocation = sortRequests(requestListWithoutRelocation);
        
        // distribute over vehicles
        distributeRequests(requestListWithoutRelocation, true);

        // finish requests 
        stackToBufferRequestsLoop(round);
    }

    private void scheduleStackToBufferRequests() {
        // get all remainingrequests that need to be picked up from stack to buffer
        List<Request> requestsCopy = getStackToBufferRequests();

        // remove requests from main list
        requests.removeAll(requestsCopy);

        // sort based on stack depth
        requestsCopy = sortRequests(requestsCopy);

        // distribute over vehicles
        distributeRequests(requestsCopy, true);

        // finish requests
        stackToBufferRequestsLoop(round);
    }

    private void stackToBufferRequestsLoop(int round){
        boolean allRequestsDone = false;

        // keep track of whether the vehicle has to get another box first (initialize)
        initializeFirstGetAnother();

        while (!allRequestsDone){
            for (Vehicle vehicle : vehicles){
                if (vehicle.isAvailable(currentTime)){
                    boolean hasSpace = vehicle.getCapacity() > vehicle.getCarriedBoxesCount();
                    boolean notWorkingOnRequest = vehicle.getCurrentRequestID() == -1;
                    boolean hasRequestAvailable = !vehicle.getRequests().isEmpty();
                    boolean getAnotherFirst = firstGetAnother[vehicles.indexOf(vehicle)];
                    boolean hasOpenRequests = !vehicle.getOpenRequests().isEmpty();

                    if (!getAnotherFirst && hasSpace && notWorkingOnRequest && hasRequestAvailable && !hasOpenRequests) {
                        boolean success = vehicle.setNewOpenRequest(stackIsUsedUntil, currentTime, round, false);
                        if (!success) continue; // can't open request because the stack it needs to go to is still used? wait
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        updateFirstGetAnother(vehicle, currentTime, round);
                    }

                    else if (getAnotherFirst){
                        boolean success = vehicle.setNewOpenRequest(stackIsUsedUntil, currentTime, round, true);
                        if (!success) continue;
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        updateFirstGetAnother(vehicle, currentTime, round);
                    }

                    else if (!notWorkingOnRequest && !getAnotherFirst && !vehicle.getOpenRequests().isEmpty()){
                        // go to buffer and finish open requests one by one
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getAssignedVehicle() == vehicle.getID();}).toList().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        updateFirstGetAnother(vehicle, currentTime, round);

                        // handle finished requests
                        if (request.isDone()){
                            vehicle.closeRequest(request);
                            // if there is another vehicle waiting for the completion of this request
                            if (waitForRequestFinish.containsKey(request.getID())){
                                int vehicleID = waitForRequestFinish.get(request.getID());
                                Vehicle vehicleWaiting = vehicles.stream().filter(v -> v.getID() == vehicleID).toList().get(0);
                                vehicleWaiting.setUnavailableUntil(vehicle.getUnavailableUntil());
                                waitForRequestFinish.remove(request.getID());
                            }
                        }
                    }
                }
            }
            
            // loop over vehicles and check if they all have an empty requestlist
            allRequestsDone = checkIfAllRequestsDone();

            if (!allRequestsDone) currentTime++;

            // remove all relocations that are done
            activeRelocations.removeIf(x -> x[3] < currentTime);
        }
    }
    
    // round 0 and 1 helper functions
    private List<Request> findTopBoxRequests(){
        List<Request> requestListWithoutRelocation= new ArrayList<>();
        List<Request> tempList = new ArrayList<>(requests); 
        for (Request request : tempList){
            IStorage storage = request.getPickupLocation().getStorage();
            if (!request.getPickupLocation().isBuffer() && storage.peek().equals(request.getBoxID()) && request.getPlaceLocation().isBuffer()){
                requestListWithoutRelocation.add(request);
                requests.remove(request);
                // find if the box below also needs to be picked up
                for (int i = 1; i < ((Stack)storage).getBoxesSize(); i++){
                    String boxIDBelow = ((Stack)storage).peakAtDepth(i);
                    // if that box also needs to be picked up, add that request
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
        return requestListWithoutRelocation;
    }
    private List<Request> sortRequests(List<Request> requests){
        requests.sort((r1, r2) -> {
            if (r1.getPickupLocation().getStorage() instanceof Stack stack1 && r2.getPickupLocation().getStorage() instanceof Stack stack2) {
                return Integer.compare(stack1.getDepthOfBox(r1.getBoxID()), stack2.getDepthOfBox(r2.getBoxID()));
            }
            return 0;
        });
        return requests;
    }
    private List<Request> getStackToBufferRequests(){
        List<Request> requestsCopy = new ArrayList<>();
        for (Request request : requests){
            if (request.getPickupLocation().getStorage() instanceof Stack && request.getPlaceLocation().isBuffer()){
                requestsCopy.add(request);
            }
        }
        return requestsCopy;
    }
    private void updateFirstGetAnother(Vehicle vehicle, double currentTime, int round){
        if (round == 0) firstGetAnother[vehicles.indexOf(vehicle)] = vehicle.getCapacity() > vehicle.getCarriedBoxesCount() && !vehicle.getRequests().isEmpty();
        else {
            List<Request> nextRequests = vehicle.getRequests().stream().toList();
            if (!nextRequests.isEmpty()){
                Request nextRequest = nextRequests.get(0);
                Stack targetStack = (Stack) nextRequest.getPickupLocation().getStorage();
                int neededCapacity = targetStack.getDepthOfBox(nextRequest.getBoxID());
                boolean hasEnoughCapacity = (vehicle.getCapacity() - vehicle.getCarriedBoxesCount()) >= neededCapacity;
                boolean isStackUsed = stackIsUsedUntil.get(nextRequest.getPickupLocation().getStorage().getID()) < currentTime;
                boolean isBoxOnVehicle = vehicle.hasBox(nextRequest.getBoxID());
                boolean isCurrentNodeNotTargetNode = vehicle.getCurrentNode() != nextRequest.getPlaceLocation();
                firstGetAnother[vehicles.indexOf(vehicle)] = isStackUsed && hasEnoughCapacity && isBoxOnVehicle && isCurrentNodeNotTargetNode;
            }
            else firstGetAnother[vehicles.indexOf(vehicle)] = false;
        }
    }
    private boolean checkIfAllRequestsDone(){
        boolean allRequestsDone = true;
        for (Vehicle vehicle : vehicles){
            if (!vehicle.getRequests().isEmpty() || !vehicle.getOpenRequests().isEmpty()){
                allRequestsDone = false;
                break;
            }
        }
        return allRequestsDone;
    }
    
    
    
    // round 2
    private void scheduleBufferToStackRequests() {
        List<Request> requestsCopy = new ArrayList<>(requests);
        requests.removeAll(requestsCopy);

        // check if enough space for requests
        if (!checkIfEnoughSpace(requestsCopy)) return;

        // distribute requests over vehicles
        distributeRequests(requestsCopy, false);
        
        // finish requests
        bufferToStackRequestsLoop();
    }

    private void bufferToStackRequestsLoop() {
        boolean allRequestsDone = false;
        initializeFirstGetAnother();

        while (!allRequestsDone){
            for (Vehicle vehicle : vehicles){
                if (vehicle.isAvailable(currentTime) && (!vehicle.getRequests().isEmpty() || !vehicle.getOpenRequests().isEmpty())){
                    if (!vehicle.getRequests().isEmpty() && vehicle.getOpenRequests().isEmpty()){
                        // open requests with same destination
                        Location dest = vehicle.getRequests().get(0).getPlaceLocation().getLocation();
                        List<Request> requestsWithSameDest = vehicle.getRequests().stream().filter(x -> {return x.getPlaceLocation().getLocation() == dest;}).toList();
                        for (Request request : requestsWithSameDest){
                            vehicle.addOpenRequest(request);
                            vehicle.getRequests().remove(request);
                        }
                    }

                    // calculate how much space is still needed on dest stack
                    int neededCapacity = vehicle.getOpenRequests().size();
                    Stack stack  = (Stack) vehicle.getOpenRequests().get(0).getPlaceLocation().getStorage();
                    int freeSpace = stack.getFreeSpace();
                    int requiredExtraCapacity = neededCapacity - freeSpace;

                    if (vehicle.getCurrentRequestID() == -1 && requiredExtraCapacity > 0){
                        // make simulated request to move topbox to tempstack to make space on dest stack
                        makeSimulatedRequest(vehicle, stack);
                        Request request = vehicle.getOpenSimulatedRequests().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                    }

                    else if (vehicle.getCurrentRequestID() != -1 && requiredExtraCapacity >= 0 && !vehicle.getOpenSimulatedRequests().isEmpty()){
                        // finish simulated requests
                        Request request = vehicle.getOpenSimulatedRequests().get(0);
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (request.isDone()) vehicle.closeSimulatedRequest(request);
                    }

                    else if (vehicle.getCurrentRequestID() == -1 && requiredExtraCapacity <= 0 && !vehicle.getOpenRequests().isEmpty()) {
                        // begin with open requests
                        Request request = vehicle.getOpenRequests().get(0);
                        vehicle.setCurrentRequestID(request.getID());
                        handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (request.isDone()) vehicle.closeRequest(request);
                    }

                    else if (vehicle.getCurrentRequestID() != -1 && requiredExtraCapacity <= 0 && !vehicle.getOpenRequests().isEmpty()){
                        // if box of first request is picked up, check if another box can be picked up
                        Request currentRequest = vehicle.getOpenRequests().stream().filter(x -> x.getID() == vehicle.getCurrentRequestID()).toList().get(0);
                        Request nextRequest = findNextRequest(vehicle, currentRequest);

                        if (nextRequest != null){
                            vehicle.setCurrentRequestID(nextRequest.getID());
                            currentRequest = vehicle.getOpenRequests().stream().filter(x -> x.getID() == vehicle.getCurrentRequestID()).toList().get(0);
                        }

                        // handle current request
                        handleRequest(vehicle, currentRequest, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (currentRequest.isDone()){
                            // handle finished request
                            List<Request> openRequests = vehicle.getOpenRequests().stream().filter(x -> vehicle.hasBox(x.getBoxID())).toList();
                            if (!openRequests.isEmpty()) vehicle.closeRequestLastRound(currentRequest, openRequests.get(0).getID());
                            else vehicle.closeRequest(currentRequest);
                        }
                    }

                }
            }

            
            allRequestsDone = checkIfAllRequestsDone();
            if (!allRequestsDone) currentTime++;
            activeRelocations.removeIf(x -> x[3] < currentTime);
        }

    }
    
    //helper functions round 2
    private boolean checkIfEnoughSpace(List<Request> requestsCopy){
        // check of genoeg plaats voor alle requests
        int totalFreeSpace = 0;
        for (GraphNode node : graph.getNodes()){
            if (node.getStorage() instanceof Stack stack){
                totalFreeSpace += stack.getFreeSpace();
            }
        }
        if (totalFreeSpace < requestsCopy.size()){
            System.out.println("not enough space for requests");
            return false;
        }
        return true;
    }
    private void makeSimulatedRequest(Vehicle vehicle, Stack stack){
        GraphNode src = vehicle.getOpenRequests().get(0).getPickupLocation();
        GraphNode dest = vehicle.getOpenRequests().get(0).getPlaceLocation();
        List<GraphNode> tempstacks = findNStorage(1, src, dest, REQUEST_STATUS.SIMULATED, vehicle);
        if (tempstacks.isEmpty()){
            return;
        }
        GraphNode tempStack = tempstacks.get(0);
        int newID = Integer.MAX_VALUE-(vehicle.getSimulatedRequests().size()+vehicle.getOpenRequests().size());
        Request simulatedRequest = new Request(dest, tempStack, newID, stack.peek());
        vehicle.addSimulatedRequest(simulatedRequest);
        vehicle.setNewOpenSimulatedRequest();
    }
    private Request findNextRequest(Vehicle vehicle, Request currentRequest){
        boolean hasBoxOnVehicle = currentRequest.getBoxID().equals(vehicle.getLastBox());
        boolean hasEnoughCapacity = vehicle.getCapacity() > vehicle.getCarriedBoxesCount();
        boolean isCurrentNodeSameAsPickupLocation = vehicle.getCurrentNode() == currentRequest.getPickupLocation();

        if (vehicle.getOpenRequests().size() > 1 && hasBoxOnVehicle && isCurrentNodeSameAsPickupLocation && hasEnoughCapacity){
            Predicate<Request> checkfunction = x -> x.getPickupLocation().equals(currentRequest.getPickupLocation()) && !vehicle.hasBox(x.getBoxID());
            List<Request> openRequestsSameSrc = vehicle.getOpenRequests().stream().filter(checkfunction).toList();
            if (!openRequestsSameSrc.isEmpty()) return openRequestsSameSrc.get(0);
            else {
                Predicate<Request> checkfunction2 = x -> x.getPickupLocation().isBuffer() && !vehicle.hasBox(x.getBoxID());
                List<Request> openRequestsDifferentSrc = vehicle.getOpenRequests().stream().filter(checkfunction2).toList();
                if (!openRequestsDifferentSrc.isEmpty()) return openRequestsDifferentSrc.get(0);
            }
        }
        return null;
    }



    

    // distribute requests over vehicles based on stack load    
    public void distributeRequests(List<Request> requestList, boolean usePickupLocation) {
        HashMap<Integer, Integer> stackLoad = calculateStackLoad(requestList, usePickupLocation);
        int requestsPerVehicle = (requestList.size() / vehicles.size()) + 1;
        initializeRequestsPerVehicleList();

        // sort stackIDs based on stackLoad size
        List<Integer> stackIDs = new ArrayList<>(stackLoad.keySet());
        stackIDs.sort((s1, s2) -> stackLoad.get(s2).compareTo(stackLoad.get(s1)));

        // create lists of requests for each vehicle
        createVehicleRequestLists(requestList, usePickupLocation, requestsPerVehicle, stackIDs);

        // give each vehicle its requests
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
    private void initializeRequestsPerVehicleList(){
        for (Vehicle vehicle : vehicles) {
            requestsPerVehicleList.add(new ArrayList<>());
        }
    }
    private void initializeFirstGetAnother(){
        for (int i = 0; i < vehicles.size(); i++){
            firstGetAnother[i] = false;
        }
    }
    private void createVehicleRequestLists(List<Request> requestList, boolean usePickupLocation, int requestsPerVehicle, List<Integer> stackIDs){
        int vehicleIndex = 0;
        for (Integer stackID : stackIDs) {
            // if a vehicle has enough requests, go to the next vehicle
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
    }
    private void resetVehicleStackIDs() {
        for (Vehicle vehicle : vehicles){
            vehicle.resetStackIDs();
        }
    }
    



    // handle request based on status
    private boolean handleRequest(Vehicle vehicle, Request request, double time, int sameDestStackCount){
        Location startLocation = vehicle.getLocation();
        double timeAfterMove = time;

        return switch (request.getStatus()) {
            case INITIAL -> handleIinitialStatus(vehicle, request, time, startLocation, timeAfterMove);
            case SRC -> handleSrcStatus(vehicle, request, time, startLocation, timeAfterMove);
            case DEST_PU -> handleDestPUStatus(vehicle, request, time, sameDestStackCount, startLocation, timeAfterMove);
            default -> false;
        };
    }
    
    private boolean checkAndResetTargetStackUsed(boolean condition) {
        if (condition) {
            targetStackIsUsed = false;
            return false;
        }
        return true;
    }
    private boolean resetNoAvailableTempStack(boolean condition){
        if (condition){
            noAvailableTempStack = false;
            return false;
        }
        return true;
    }
    private boolean handleIinitialStatus(Vehicle vehicle, Request request, double time, Location startLocation, double timeAfterMove){
        // if vehicle is not empty and at a stack, try to empty vehicle as much as possible
        // check if vehicle got a needed box and is collecting more boxes
        boolean result = leegVehicle(vehicle, startLocation, timeAfterMove, time, request);
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        // if dest is a stack and full, then relocate or check if multiple requests also go to the same stack, so much space is freed on dest stack, 
        // then all those requests are finished
        if (!result) {
            result = maakPlaatsVrijOpDest(vehicle, startLocation, timeAfterMove, time, request);
            if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        }
        // go to src and PU
        if (!result) {
            PickupSrc(vehicle, startLocation, timeAfterMove, time, request);
            if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        } 
        return true;
    }
    private boolean handleSrcStatus(Vehicle vehicle, Request request, double time, Location startLocation, double timeAfterMove){
        // check if relocation is needed (we don't have the box on vehicle), if vehicle is full and not the correct box, go to temp stack
        boolean result = boxesRelocatenNaarTempStack(vehicle, startLocation, timeAfterMove, timeAfterMove, request);
        if (!resetNoAvailableTempStack(noAvailableTempStack)) return false;
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        
        // if vehicle is not full and not the correct box, try to take 1 more
        if (!result) result = NeemNogEenboxOpBijSrc(vehicle, startLocation, timeAfterMove, time, request);
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;

        // go to dest and PL
        if (!result) placeBoxBijDest(vehicle, startLocation, timeAfterMove, time, request);
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        return true;
    }
    private boolean handleDestPUStatus(Vehicle vehicle, Request request, double time, int sameDestStackCount, Location startLocation, double timeAfterMove){
        // if vehicle is not full and sameDestStackCount > 0 and stack.freeSpace < sameDestStackCount+1, try to take 1 more
        boolean result = neemNogBoxOpDest(vehicle, startLocation, timeAfterMove, time, request, sameDestStackCount);
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;

        // go to temp stack
        if (!result) placeAtTempStackDest(vehicle, startLocation, timeAfterMove, time, request);
        if (!resetNoAvailableTempStack(noAvailableTempStack)) return false;
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        return true;
    }




    

    // helper functions
    private boolean leegVehicle(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        boolean vehicleGotRequestBox = hasBoxInOpenRequests(vehicle);
        boolean canUnloadUnwantedBox = !vehicleGotRequestBox && vehicle.getCarriedBoxesCount() > 0;
        boolean notAtPickupLocation = vehicle.getCurrentNode() != request.getPickupLocation();

        if (canUnloadUnwantedBox && notAtPickupLocation && vehicle.getCurrentNode().getStorage() instanceof Stack stack && stack.getFreeSpace() > 0){
            String box = vehicle.getLastBox();
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            
            if (isStackAvailable(stack, currentTime)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return false;

            vehicle.setUnavailableUntil(timeAfterOperation);
            stack.addBox(box);
            vehicle.removeBox(box);
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_RELOC);
            return true;
        }
        return false;
    }
    private boolean maakPlaatsVrijOpDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        GraphNode dest = request.getPlaceLocation();
        if (dest.getStorage() instanceof Stack stack && stack.getFreeSpace() < 1){
            // maak plaats vrij op dest stack 
            if (startLocation != dest.getLocation()) timeAfterMove += graph.getTravelTime(vehicle, dest);
            double timeAfterOperation = timeAfterMove + loadingSpeed;

            if (isStackAvailable(stack, currentTime)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return false;

            vehicle.setUnavailableUntil(timeAfterOperation);
            vehicle.moveTo(dest);
            String box = stack.removeBox();
            vehicle.addBox(box);
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.DEST_PU);
            request.setStatus(REQUEST_STATUS.DEST_PU);
            return true;
        }
        return false;
    }
    private void PickupSrc(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        GraphNode src = request.getPickupLocation();
        if (startLocation != src.getLocation()) timeAfterMove += graph.getTravelTime(vehicle, src);
        double timeAfterOperation = timeAfterMove + loadingSpeed;

        if (src.getStorage() instanceof Stack stack){
            if (isStackAvailable(stack, currentTime)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return;
        }

        vehicle.setUnavailableUntil(timeAfterOperation);
        vehicle.moveTo(src);

        String box = "";
        if (src.getStorage() instanceof Stack stack){
            box = stack.removeBox();
            vehicle.addBox(box);
        }
        else vehicle.addBox(request.getBoxID()); 

        addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.SRC);
        request.setStatus(REQUEST_STATUS.SRC);
    }
    
    private boolean boxesRelocatenNaarTempStack(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        if (!vehicle.hasBox(request.getBoxID()) && vehicle.getCapacity() == vehicle.getCarriedBoxesCount() && vehicle.getCarriedBoxesCount() > 0){
            String box = vehicle.getLastBox();
            GraphNode src = request.getPickupLocation();
            GraphNode dest = request.getPlaceLocation();
            REQUEST_STATUS status = request.getStatus();
            List<GraphNode> tempStacks = findNStorage(1, src, dest, status, vehicle);
            if (tempStacks.isEmpty()){
                noAvailableTempStack = true;
                return false;
            }
            GraphNode tempStack = tempStacks.get(0);
            Stack stack = (Stack) tempStack.getStorage();
            
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

            if (isStackAvailable(stack, currentTime)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return false;

            vehicle.setUnavailableUntil(timeAfterOperation);
            int prevVehicleLocation = ((Stack) vehicle.getCurrentNode().getStorage()).getID();
            vehicle.moveTo(tempStack);
            stack.addBox(box);
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
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                if (isStackAvailable(stack, currentTime)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
                else return false;
            }
            vehicle.setUnavailableUntil(timeAfterOperation);
            String box = "";
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                box = stack.removeBox();
                vehicle.addBox(box);
            }
            else vehicle.addBox(request.getBoxID());
            addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.SRC);
            return true;
        }
        return false;
    }
    private void placeBoxBijDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request) {
        GraphNode dest = request.getPlaceLocation();
        if (dest.getStorage() instanceof Stack stack && stack.getFreeSpace() == 0){
            return;
        }
        if (startLocation != dest.getLocation()) timeAfterMove += graph.getTravelTime(vehicle, dest);
        double timeAfterOperation = timeAfterMove + loadingSpeed;

        if (dest.getStorage() instanceof Stack stack){
            if (isStackAvailable(stack, currentTime)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return;
        }
        
        vehicle.setUnavailableUntil(timeAfterOperation);
        vehicle.moveTo(dest);
        if (dest.getStorage() instanceof Stack stack){
            stack.addBox(request.getBoxID());
        }
        vehicle.removeBox(request.getBoxID());
        addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, request.getBoxID(), REQUEST_STATUS.DEST);
        request.setStatus(REQUEST_STATUS.DEST);
    }

    private boolean neemNogBoxOpDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, int sameDestStackCount) {
        if (vehicle.getCapacity() > vehicle.getCarriedBoxesCount() && sameDestStackCount > 0 && ((Stack)request.getPlaceLocation().getStorage()).getFreeSpace() < sameDestStackCount+1){
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                if (isStackAvailable(stack, currentTime)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
                else return false;
            }
            vehicle.setUnavailableUntil(timeAfterOperation);
            String box = "";
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                box = stack.removeBox();
                vehicle.addBox(box);
            }
            else vehicle.addBox(request.getBoxID());
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
            noAvailableTempStack = true;
            return;
        }
        GraphNode tempStack = tempStacks.get(0);
        Stack stack = (Stack) tempStack.getStorage();
        timeAfterMove += graph.getTravelTime(vehicle, tempStack);
        double timeAfterOperation = timeAfterMove + loadingSpeed;

        for (Integer[] relocation : activeRelocations){
            if (relocation[1] == ((Stack) vehicle.getCurrentNode().getStorage()).getID() && relocation[0] == ((Stack) tempStack.getStorage()).getID()){
                waitForRequestFinish.put(relocation[2], vehicle.getID());
                vehicle.setUnavailableUntil(Double.MAX_VALUE);
                noAvailableTempStack = true;
                return;
            }
        }

        if (isStackAvailable(stack, currentTime)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
        else return;
        vehicle.setUnavailableUntil(timeAfterOperation);
        
        int prevVehicleLocation = ((Stack) vehicle.getCurrentNode().getStorage()).getID();
        vehicle.moveTo(tempStack);
        stack.addBox(box);
        vehicle.removeBox(box);
        addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_RELOC);
        request.setStatus(REQUEST_STATUS.INITIAL);
        stackIsUsedUntil.put(((Stack) tempStack.getStorage()).getID(), (int) (timeAfterOperation));
        activeRelocations.add(new Integer[]{prevVehicleLocation, ((Stack) tempStack.getStorage()).getID(), request.getID(), (int) timeAfterOperation});
    }

    private boolean isStackAvailable(Stack stack, double time) {
        if (stackIsUsedUntil.get(stack.getID()) < time) return true;
        targetStackIsUsed = true;
        return false;
    }
    private boolean hasBoxInOpenRequests(Vehicle vehicle) {
        for (Request req : vehicle.getOpenRequests()) {
            if (vehicle.hasBox(req.getBoxID())) {
                return true;
            }
        }
        return false;
    }
    





    private List<GraphNode> findNStorage(int N, GraphNode src, GraphNode dest, REQUEST_STATUS status, Vehicle currentVehicle){
        if(N == 0) return null;
        
        // check if current vehicle can go to one of its own stacks (to avoid interference with other vehicles)
        List<GraphNode> accessibleStacks = findOwnAccessibleStacks(currentVehicle, src, dest);
        if (!accessibleStacks.isEmpty()) return accessibleStacks;
        
        // find stack that no vehicle has requests for
        List<GraphNode> requestStacks = findAvailableStack(currentVehicle, src, dest, status);
        if (!requestStacks.isEmpty()) return requestStacks;

        // find stack that is in request of other vehicles
        List<GraphNode> remainingStacks = findOtherVehicleStacks(currentVehicle, src, dest, status);
        return remainingStacks;
    }

    private List<GraphNode> findOwnAccessibleStacks(Vehicle currentVehicle, GraphNode src, GraphNode dest) {
        List<GraphNode> accessibleStacks = new ArrayList<>();
        for (Integer stackID : currentVehicle.getMyStackIDs()) {
            int srcID = src.getStorage().getID();
            int destID = dest.getStorage().getID();
            boolean notSrcOrDest = stackID != srcID && stackID != destID;
            boolean notCurrentlyAtNode = currentVehicle.getCurrentNode() == null;
            boolean currentNodeIsStack = currentVehicle.getCurrentNode().getStorage() instanceof Stack;
            Stack currentNodeStack = currentNodeIsStack ? (Stack) currentVehicle.getCurrentNode().getStorage() : null;
            Stack stack2 = (Stack) graph.getStackByID(stackID).getStorage();

            
            if (notSrcOrDest && ( notCurrentlyAtNode || (currentNodeIsStack && currentNodeStack.getID() != stackID && !stack2.isFull()))) {
                int time1 = stackIsUsedUntil.get(stackID);
                int time2 = (int) currentTime;
                boolean stackIsUsed = time1 < time2;
                if (stackIsUsed) {
                    accessibleStacks.add(graph.getStackByID(stackID));
                }
            }
        }
        return accessibleStacks;
    }
    private List<GraphNode> findRequestStacks(Vehicle currentVehicle, GraphNode src, GraphNode dest, int type) {
        List<GraphNode> requestStacks = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (type == 2 && vehicle == currentVehicle) continue;
            for (Integer stackID : vehicle.getMyStackIDs()) {
                if (currentVehicle.getCurrentNode() == null || isDifferentStack(currentVehicle, stackID)) {
                    requestStacks.add(graph.getStackByID(stackID));
                }
            }
        }
        return requestStacks;
    }
    private boolean isDifferentStack(Vehicle currentVehicle, Integer stackID) {
        return currentVehicle.getCurrentNode().getStorage() instanceof Stack stack && stackID != stack.getID();
    }
    private boolean isValidNode(GraphNode node, GraphNode src, GraphNode dest, List<GraphNode> requestStacks) {
        return !(node == src || node == dest || requestStacks.contains(node)) && node.getStorage() instanceof Stack stack && !stack.isFull();
    }
    private List<GraphNode> findAvailableStack(Vehicle currentVehicle, GraphNode src, GraphNode dest, REQUEST_STATUS status) {
        // find stack that no vehicle has requests for
        List<GraphNode> requestStacks = findRequestStacks(currentVehicle, src, dest,1);

        // find stack that is closest to src or dest
        PriorityQueue<GraphNode> nodesByDistance = new PriorityQueue<>((node1, node2) -> {
            GraphNode node = (status == REQUEST_STATUS.SRC) ? src : dest;
            double distance1 = graph.calculateTime(node.getLocation(), node1.getLocation());
            double distance2 = graph.calculateTime(node.getLocation(), node2.getLocation());
            return Double.compare(distance1, distance2);
        });
        nodesByDistance.addAll(graph.getNodes());

        List<GraphNode> nodes = new ArrayList<>();
        for (GraphNode node : nodesByDistance) {
            if (isValidNode(node, src, dest, requestStacks)) {
                Stack stack = (Stack) node.getStorage();
                if ((stackIsUsedUntil.get(stack.getID()) < currentTime) || status == REQUEST_STATUS.SIMULATED) {
                    nodes.add(node);
                    return nodes;
                }
            }
        }
        return nodes; 
    }
    private List<GraphNode> findOtherVehicleStacks(Vehicle currentVehicle, GraphNode src, GraphNode dest, REQUEST_STATUS status) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphNode> requestStacks = findRequestStacks(currentVehicle, src, dest, 2);

        // find stack that is closest to src or dest
        PriorityQueue<GraphNode> nodesByDistance2 = new PriorityQueue<>((node1, node2) -> {
            GraphNode node = (status == REQUEST_STATUS.SRC) ? src : dest;
            double distance1 = graph.calculateTime(node.getLocation(), node1.getLocation());
            double distance2 = graph.calculateTime(node.getLocation(), node2.getLocation());
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
        operationLog.add(vehicleName + ";" + startLocation.getX() + ";"+ startLocation.getY() + ";" + (int) startTime  + ";" + endLocation.getX() + ";" + endLocation.getY()   + ";" + (int)endTime + ";"+ boxId + ";" + operation);

    }

    public void writeOperationLog() {
        long time = System.currentTimeMillis() - startingTime;
        StringBuilder output = new StringBuilder();
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
        System.out.println("Computation time(ms): " + time);
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
