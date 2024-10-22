package org.kuleuven.engineering;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class Stack implements IStorage {
    private int ID;
    private String name;
    private int capacity;
    private List<Box> boxes;

    public Stack(int capacity) {
        this.capacity = capacity;
        this.boxes = new ArrayList<>(capacity);
    }

    public Stack(JsonObject object, int capacity) {
        ID = object.get("ID").getAsInt();
        name = object.get("name").getAsString();
        this.capacity = capacity;
        this.boxes = new ArrayList<>(capacity);
        for (JsonElement element : object.get("boxes").getAsJsonArray()) {
            String name = element.getAsString();
            this.boxes.add(new Box(name));
        }
    }

    @Override
    public int getID() {
        return ID;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean addBox(Box box) {
        return false;
    }

    @Override
    public Box removeBox() {
        return new Box("");
    }

    @Override
    public Box peek(){
        return new Box("");
    }

    public boolean isFull(){
        return this.boxes.size() >= this.capacity;
    }
}