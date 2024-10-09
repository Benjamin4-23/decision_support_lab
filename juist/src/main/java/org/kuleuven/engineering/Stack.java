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
        this.boxes = new ArrayList<>(capacity);
    }

    public boolean addBox(Box box) {
        return false;
    }

    public Box removeBox() {
        return new Box(0);
    }

    public Box peek(){
        return new Box(0);
    }

    public boolean isFull(){
        return this.boxes.size() == this.capacity;
    }



}