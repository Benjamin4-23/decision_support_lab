package org.kuleuven.engineering;

public interface IStorage {
    public int getID();
    public String getName();
    public boolean addBox(Box box);

    public Box removeBox();

    public Box peek();
}
