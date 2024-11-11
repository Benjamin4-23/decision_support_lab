package org.kuleuven.engineering;

public interface IStorage {
    public int getID();
    public String getName();
    public String addBox(String box);
    public String removeBox();
    public String peek();
    public boolean isFull();
    public int getFreeSpace();
}
