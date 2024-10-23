package org.kuleuven.engineering;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class Stack implements IStorage {
    private int ID;
    private String name;
    private final int capacity;
    private final List<Box> boxes;

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
        if (!isFull()) {
            boxes.add(box);
            return true;
        }
        return false;
    }

    @Override
    public Box removeBox() {
        if (!boxes.isEmpty()) {
            return boxes.remove(boxes.size() - 1);
        }
        return null;
    }

    @Override
    public Box peek() {
        if (!boxes.isEmpty()) {
            return boxes.get(boxes.size() - 1);
        }
        return null;
    }

    public boolean isFull() {
        return this.boxes.size() >= this.capacity;
    }

    public boolean isBoxOnTop(String boxID) {
        return !boxes.isEmpty() && boxes.get(boxes.size() - 1).getId().equals(boxID);
    }

    public List<Box> relocateBoxesUntil(String boxID) {
        List<Box> relocatedBoxes = new ArrayList<>();
        while (!boxes.isEmpty() && !isBoxOnTop(boxID)) {
            relocatedBoxes.add(removeBox());
        }
        return relocatedBoxes;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<Box> getBoxes() {
        return boxes;
    }
}
