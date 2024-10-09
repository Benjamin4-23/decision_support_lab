package org.kuleuven.engineering;
import java.util.ArrayList;
import java.util.List;

public class Vehicle {
    private final int id;
    private final int capacity;
    private final double x;
    private final double y;
    private final List<Box> carriedBoxes;

    public Vehicle(int id, int capacity, double initialX, double initialY) {
        this.id = id;
        this.capacity = capacity;
        this.x = initialX;
        this.y = initialY;
        this.carriedBoxes = new ArrayList<>();
    }

    public boolean loadBox(Box box, Stack2 stack) {
        if (carriedBoxes.size() < capacity && stack.peek() != null && stack.peek().getId() == box.getId()) {
            carriedBoxes.add(stack.removeBox());
            return true;
        }
        return false; // Cannot load box
    }

    public boolean unloadBox(Box box, Stack2 stack) {
        if (carriedBoxes.contains(box)) {
            carriedBoxes.remove(box);
            stack.addBox(box);
            return true;
        }
        return false; // Cannot unload box
    }

    // Getters and setters
}