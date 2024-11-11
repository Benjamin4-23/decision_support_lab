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
    public String addBox(String box) {
        return null;
    }

    @Override
    public String removeBox() {
        return null;
    }

    @Override
    public String peek() {
        return null;
    }

    @Override
    public boolean isFull() {
        return false;
    }

    @Override
    public int getFreeSpace() {
        return 0;
    }
}
