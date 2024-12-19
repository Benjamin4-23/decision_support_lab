package org.kuleuven.engineering.scheduling;

import java.util.HashMap;
import java.util.List;

import org.kuleuven.engineering.Request;
import org.kuleuven.engineering.Vehicle;
import org.kuleuven.engineering.graph.Graph;

public abstract class SchedulingStrategy {

    public abstract void schedule();
    protected abstract void executeSchedulingLoop();
} 