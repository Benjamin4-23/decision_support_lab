package org.kuleuven.engineering;

import org.json.JSONException;
import org.json.JSONObject;
import org.kuleuven.engineering.graph.GraphNode;

public class Request {
    private final int ID;
    private GraphNode pickupLocation;
    private GraphNode placeLocation;
    private final String boxID;
    private String assignedVehicle = "";
    private REQUEST_STATUS status = REQUEST_STATUS.INITIAL;

    public Request(GraphNode pickup, GraphNode place, int ID, String boxID) {
        this.ID = ID;
        this.boxID = boxID;
        this.pickupLocation = pickup;
        this.placeLocation = place;
    }

    public GraphNode getPickupLocation() {
        return pickupLocation;
    }

    public GraphNode getPlaceLocation() {
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

    public REQUEST_STATUS getStatus() {
        return status;
    }

    public void setStatus(REQUEST_STATUS status) {
        this.status = status;
    }

    public enum REQUEST_STATUS{
        INITIAL,
        SRC,
        SRC_RELOC,
        DEST,
        DEST_RELOC
    }
}
