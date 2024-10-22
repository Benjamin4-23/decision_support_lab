package org.kuleuven.engineering;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Warehouse {
    private final List<Stack2> stacks;
    private final List<Vehicle> vehicles;
    private final List<Request> requests;
    private final HashMap<Integer, Box> boxMap; // HashMap to track all boxes

    public Warehouse(List<Stack2> stacks, List<Vehicle> vehicles) {
        this.stacks = stacks;
        this.vehicles = vehicles;
        this.requests = new ArrayList<>();
        this.boxMap = new HashMap<>(); // Initialize the HashMap
    }

    public void addBoxToStack(Box box, Stack2 stack) { //initial add
        if (stack.addBox(box)) {
            box.setCurrentLocation(stack);
            boxMap.put(box.getId(), box);
        }
    }

    public void moveBox(Box box, Stack2 fromStack, Stack2 toStack) { // veronderstel tostack is vrij
        fromStack.moveUnusedBoxes(stacks, box); // verplaats niet nodige boxen
        addBoxToStack(box, toStack);
    }

    public Stack2 findBoxLocation(int boxId) {
        Box box = boxMap.get(boxId); // Retrieve the box from the HashMap
        return (box != null) ? box.getCurrentLocation() : null; // Return the current location
    }

    // Additional methods for managing vehicles and requests
}