package org.kuleuven.engineering;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Request {
    //ID int, pickupLocation str, placeLocation str, boxID str
    private int ID;
    private String pickupLocation;
    private String placeLocation;
    private String box;

    public Request(JsonObject object) {
        pickupLocation = object.get("pickupLocation").getAsJsonArray().get(0).getAsString();
        placeLocation = object.get("placeLocation").getAsJsonArray().get(0).getAsString();
        ID = object.get("ID").getAsInt();
        box = object.get("boxID").getAsString();
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public String getPlaceLocation() {
        return placeLocation;
    }
}