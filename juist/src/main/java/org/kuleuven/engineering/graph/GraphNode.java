package org.kuleuven.engineering.graph;

import org.kuleuven.engineering.Bufferpoint;
import org.kuleuven.engineering.IStorage;
import org.kuleuven.engineering.Location;

public class GraphNode {
    private boolean isBuffer;
    private boolean isVehiclePresent;
    IStorage storage;
    private Location location;

    public GraphNode(IStorage storage, Location location){
        isVehiclePresent = false;
        this.location = location;
        this.storage = storage;
        if(storage instanceof Bufferpoint){
            isBuffer = true;
        } else {
            isBuffer = false;
        }
    }
    public String getName() {
        return storage.getName();
    }
    public boolean isBuffer() {
        return isBuffer;
    }
    public void setBuffer(boolean buffer) {
        isBuffer = buffer;
    }
    public boolean isVehiclePresent() {
        return isVehiclePresent;
    }
    public void setVehiclePresent(boolean vehiclePresent) {
        isVehiclePresent = vehiclePresent;
    }
    public Location getLocation() {
        return location;
    }
}
