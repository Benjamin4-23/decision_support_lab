package org.kuleuven.engineering;
import java.util.ArrayList;
import java.util.List;

public class Stack2 {
    private int x;
    private int y;
    private int capacity;
    private List<Box> boxes;

    public Stack2(int x, int y, int capacity) {
        this.x = x;
        this.y = y;
        this.capacity = capacity;
        this.boxes = new ArrayList<>(capacity);
    }

    public boolean addBox(Box box) {
        if (!isFull()) {
            boxes.add(box);
            return true;
        }
        return false;
    }

    public Box removeBox() {
        if (!boxes.isEmpty()) {
            Box box = boxes.remove(boxes.size() - 1); // Remove from top
            box.setCurrentLocation(null); // Clear the location when removed
            return box;
        }
        return null; // No box to remove
    }

    public boolean isFull() {
        return this.boxes.size() == this.capacity;
    }

    public void moveUnusedBoxes(List<Stack2> targetStacks, Box targetBox) {

        // Count how many boxes need to be moved
        while (!boxes.isEmpty() && !boxes.get(boxes.size() - 1).equals(targetBox)) {
            Box boxToMove = boxes.get(boxes.size() - 1);
            boolean boxMoved = false;
            for (Stack2 targetStack : targetStacks) {
                if (targetStack.addBox(boxToMove)) {
                    removeBox(); // Remove the box from this stack
                    boxToMove.setCurrentLocation(targetStack);
                    boxMoved = true;
                    break;
                }
            }
            if (!boxMoved) {
                // If we couldn't move the box, we need to stop to avoid an infinite loop
                System.out.println("box couldn't be moved because no space available");
                break;
            }
        }
    }

    // Getters and setters
}