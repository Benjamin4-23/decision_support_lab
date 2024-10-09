package org.kuleuven.engineering;
import java.util.ArrayList;
import java.util.List;

public class Stack {
    private int x;
    private int y;
    private int capacity;
    private List<Box> boxes;

    public Stack(int x, int y, int capacity) {
        this.x = x;
        this.y = y;
        this.capacity = capacity;
        this.boxes = new ArrayList<>();
    }

    // Methods for adding, removing boxes, checking capacity, etc.
}