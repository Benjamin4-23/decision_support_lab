package org.kuleuven.engineering;

import org.json.JSONObject;

public class Bufferpoint implements IStorage {
    private int ID;
    private String name;

    public Bufferpoint(JSONObject object) {
        ID = object.getInt("ID");
        name = object.getString("name");
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
        return null;
    }

    @Override
    public Box peek() {
        return null;
    }
}
