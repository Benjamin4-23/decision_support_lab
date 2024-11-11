package org.kuleuven.engineering.graph;

import org.kuleuven.engineering.Bufferpoint;
import org.kuleuven.engineering.IStorage;
import org.kuleuven.engineering.Location;

public class GraphNode {
    private boolean isBuffer;
    IStorage storage;
    private final Location location;
    private int locks = 0;

    public GraphNode(Location location) { // demo constructor voor vehicle object als node mee te geven
        this.location = location;
        this.isBuffer = false;
        this.storage = null;
        
    }

    public GraphNode(IStorage storage, Location location){
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
    public Location getLocation() {
        return location;
    }
    public IStorage getStorage() {
        return storage;
    }

    public void lock(){
        locks++;
    }

    public void unlock(){
        if(locks <= 0) return;
        locks--;
    }

    public boolean isLocked(){
        return locks > 0;
    }

}
