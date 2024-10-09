package org.kuleuven.engineering;

import com.google.gson.JsonObject;

public class Bufferpoint implements IStorage {
    private int ID;
    private String name;

    public Bufferpoint(JsonObject object) {
        ID = object.get("ID").getAsInt();
        name = object.get("name").getAsString();
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
