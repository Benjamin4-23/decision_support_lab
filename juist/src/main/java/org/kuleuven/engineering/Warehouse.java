package org.kuleuven.engineering;
import org.kuleuven.engineering.graph.Graph;
import org.kuleuven.engineering.graph.GraphNode;

import java.util.List;
public class Warehouse {
    private Graph graph;
    private List<Vehicle> vehicles;
    private List<Request> requests;
    private double drivenDistance = 0;

    public Warehouse(Graph graph, List<Vehicle> vehicles, List<Request> requests) {
        this.graph = graph;
        this.vehicles = vehicles;
        this.requests = requests;
        calculateStartingState();
    }

    private void calculateStartingState(){
        this.graph.calculateAllDistances();
        // finc closest node to start from
        for (Vehicle vehicle: vehicles) {
            Graph.Pair<GraphNode, Double> pair = this.graph.getClosestNode(vehicle.getLocation());
            GraphNode node = pair.x;
            node.setVehiclePresent(true);
            Double distance = pair.y;
            drivenDistance += distance;
        }
    }
    public void scheduleRequests() {
        // Implement greedy scheduling algorithm
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(graph.toString());
        sb.append("\n");
        for (Vehicle vehicle: vehicles) {
            Graph.Pair<GraphNode, Double> pair = this.graph.getClosestNode(vehicle.getLocation());
            sb.append(String.format(vehicle.getName() + " " + vehicle.getLocation().toString() +" is closest to node "
                    + pair.x.getName() + " " + pair.x.getLocation().toString() + " with distance: %.2f (no sqrt)\n", pair.y));
        }
        return sb.toString();
    }
}