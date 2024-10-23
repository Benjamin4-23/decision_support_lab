package org.kuleuven.engineering;

import org.json.JSONObject;

public class Request {
    private final int ID;
    private final String pickupLocation;
    private final String placeLocation;
    private final String boxID;

    public Request(JSONObject object) {
        pickupLocation = object.getJSONArray("pickupLocation").getString(0);
        placeLocation = object.getJSONArray("placeLocation").getString(0);
        ID = object.getInt("ID");
        boxID = object.getString("boxID");
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public String getPlaceLocation() {
        return placeLocation;
    }

    public String getBoxID() {
        return boxID;
    }

    public int getID() {
        return ID;
    }
}
