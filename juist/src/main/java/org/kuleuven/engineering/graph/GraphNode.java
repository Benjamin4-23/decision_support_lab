package org.kuleuven.engineering.graph;

import java.util.ArrayList;
import java.util.List;

import org.kuleuven.engineering.Bufferpoint;
import org.kuleuven.engineering.IStorage;
import org.kuleuven.engineering.Location;

public class GraphNode {
    private boolean isBuffer;
    IStorage storage;
    private final Location location;
    private List<List<Double>> unavailableIntervals = new ArrayList<>();


    public GraphNode(Location location) { // demo constructor voor vehicle object als node mee te geven
        this.location = location;
        this.isBuffer = false;
        this.storage = null;
    }

    public GraphNode(IStorage storage, Location location){
        this.location = location;
        this.storage = storage;
        this.isBuffer = storage instanceof Bufferpoint;
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
    public Location getLocation() {
        return location;
    }
    public IStorage getStorage() {
        return storage;
    }


}
