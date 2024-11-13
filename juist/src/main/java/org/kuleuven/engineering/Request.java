package org.kuleuven.engineering;

import org.json.JSONException;
import org.json.JSONObject;

public class Request {
    private final int ID;
    private String pickupLocation;
    private String placeLocation;
    private final String boxID;
    private String assignedVehicle = "";
    private String status = "initial";

    public Request(JSONObject object) {
        try{
            pickupLocation = object.getJSONArray("pickupLocation").getString(0);
            placeLocation = object.getJSONArray("placeLocation").getString(0);
        } catch (JSONException e){
            pickupLocation = object.getString("pickupLocation");
            placeLocation = object.getString("placeLocation");
        }

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

    public String getAssignedVehicle() {
        return assignedVehicle;
    }

    public void setAssignedVehicle(String vehicle) {
        this.assignedVehicle = vehicle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
