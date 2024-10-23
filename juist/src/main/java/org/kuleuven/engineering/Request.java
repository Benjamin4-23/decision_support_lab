package org.kuleuven.engineering;

import org.json.JSONObject;

public class Request {
    private int ID;
    private String pickupLocation;
    private String placeLocation;
    private String box;

    public Request(JSONObject object) {
        pickupLocation = object.getJSONArray("pickupLocation").getString(0);
        placeLocation = object.getJSONArray("placeLocation").getString(0);
        ID = object.getInt("ID");
        box = object.getString("boxID");
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public String getPlaceLocation() {
        return placeLocation;
    }
}
