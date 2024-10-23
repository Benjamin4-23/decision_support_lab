package org.kuleuven.engineering;

import org.json.JSONArray;
import org.json.JSONObject;

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

    public Stack(JSONObject object, int capacity) {
        ID = object.getInt("ID");
        name = object.getString("name");
        this.capacity = capacity;
        this.boxes = new ArrayList<>(capacity);
        JSONArray boxArray = object.getJSONArray("boxes");
        for (int i = 0; i < boxArray.length(); i++) {
            String boxName = boxArray.getString(i);
            this.boxes.add(new Box(boxName));
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
    public Box peek() {
        return new Box("");
    }

    public boolean isFull() {
        return this.boxes.size() >= this.capacity;
    }
}
