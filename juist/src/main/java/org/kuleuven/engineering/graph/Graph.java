package org.kuleuven.engineering.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kuleuven.engineering.Location;

public class Graph {
    private final List<GraphNode> nodes;
    private double[][] adjacencyMatrix;
    private final Map<GraphNode, List<Pair<GraphNode, Double>>> adjacencyList;
    private final int vehicleSpeed;
    private final int loadingSpeed;

    public Graph(int vehicleSpeed, int loadingSpeed){
        this.nodes = new ArrayList<>();
        this.adjacencyList = new HashMap<>();
        this.vehicleSpeed = vehicleSpeed;
        this.loadingSpeed = loadingSpeed;
    }

    private double calculateDistance(Location l1, Location l2){
        return Math.sqrt(Math.pow(l2.getX() - l1.getX(), 2) + Math.pow(l2.getY() - l1.getY(), 2));
    }

    public void addNode(GraphNode node){
        if (!nodes.contains(node)) {
            nodes.add(node);
            int n = nodes.size();
            double[][] newMatrix = new double[n][n];
            for (int i = 0; i < n - 1; i++) {
                System.arraycopy(adjacencyMatrix[i], 0, newMatrix[i], 0, n - 1);
                newMatrix[i][n - 1] = Double.POSITIVE_INFINITY;
                newMatrix[n - 1][i] = Double.POSITIVE_INFINITY;
            }
            adjacencyMatrix = newMatrix;
            adjacencyMatrix[n - 1][n - 1] = 0; // Set self distance to 0

            for (int i = 0; i < n - 1; i++) {
                double distance = calculateDistance(node.getLocation(), nodes.get(i).getLocation());
                adjacencyMatrix[n - 1][i] = distance;
                adjacencyMatrix[i][n - 1] = distance;
                adjacencyList.computeIfAbsent(nodes.get(i), k -> new ArrayList<>()).add(new Pair<>(node, distance));
                adjacencyList.computeIfAbsent(node, k -> new ArrayList<>()).add(new Pair<>(nodes.get(i), distance));
            }

            for (List<Pair<GraphNode, Double>> neighbors : adjacencyList.values()) {
                neighbors.sort(Comparator.comparingDouble(pair -> pair.y));
            }
        }
    }

    public GraphNode getNodeByName(String name) {
        for (GraphNode node : nodes) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        return null;
    }

    public double getDistanceToClosestNode(String locationName) {
        GraphNode targetNode = getNodeByName(locationName);
        if (targetNode == null) {
            return Double.POSITIVE_INFINITY;
        }

        double minDistance = Double.POSITIVE_INFINITY;
        for (GraphNode node : nodes) {
            double distance = getDistance(node, targetNode);
            if (distance < minDistance) {
                minDistance = distance;
            }
        }
        return minDistance;
    }

    public List<GraphNode> getNodes() {
        return nodes;
    }

    public Pair<GraphNode, Double> getClosestNode(Location location) {
        GraphNode closestNode = null;
        double minDistance = Double.POSITIVE_INFINITY;

        for (GraphNode node : nodes) {
            double distance = calculateDistance(node.getLocation(), location) * vehicleSpeed;
            if (distance < minDistance) {
                minDistance = distance;
                closestNode = node;
            }
        }

        return new Pair<>(closestNode, minDistance);
    }

    public void calculateAllDistances() {
        int n = nodes.size();
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (adjacencyMatrix[i][j] > adjacencyMatrix[i][k] + adjacencyMatrix[k][j]) {
                        adjacencyMatrix[i][j] = adjacencyMatrix[i][k] + adjacencyMatrix[k][j];
                    }
                }
            }
        }
    }

    public double getDistance(GraphNode node1, GraphNode node2) {
        int index1 = nodes.indexOf(node1);
        int index2 = nodes.indexOf(node2);
        return adjacencyMatrix[index1][index2];
    }
    public double getDistanceLocation(Location start, Location end, boolean vehicle) {
        if (vehicle) {
            return calculateDistance(start, end);
        } else {
            int index1 = -1;
            int index2 = -1;
            for (int i = 0; i < nodes.size(); i++) {
                GraphNode node = nodes.get(i);
                if (node.getLocation().equals(start)) {
                    index1 = i;
                }
                if (node.getLocation().equals(end)) {
                    index2 = i;
                }
                if (index1 != -1 && index2 != -1) {
                    break;
                }
            }
            if (index1 == -1 || index2 == -1) {
                throw new IllegalArgumentException("Start or end location not found in the graph.");
            }
            return adjacencyMatrix[index1][index2];
        }
        
    }

    public class Pair<T, U> {
        public T x;
        public U y;

        Pair(T x, U y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph:\n");
        sb.append(String.format("VehicleSpeed: %d\n", vehicleSpeed));
        sb.append(String.format("LoadDuration: %d\n", loadingSpeed));
        sb.append("Adjacency Matrix:\n");

        int maxWidth = 8;
        for (GraphNode node : nodes) {
            maxWidth = Math.max(maxWidth, node.getName().length());
        }
        for (double[] row : adjacencyMatrix) {
            for (double value : row) {
                if (value != Double.POSITIVE_INFINITY) {
                    maxWidth = Math.max(maxWidth, String.format("%.2f", value).length());
                }
            }
        }
        maxWidth += 2;

        sb.append(String.format("%-" + maxWidth + "s", ""));
        for (GraphNode node : nodes) {
            sb.append(String.format("%-" + maxWidth + "s", node.getName()));
        }
        sb.append("\n");

        for (int i = 0; i < adjacencyMatrix.length; i++) {
            sb.append(String.format("%-" + maxWidth + "s", nodes.get(i).getName()));
            for (int j = 0; j < adjacencyMatrix[i].length; j++) {
                if (adjacencyMatrix[i][j] == Double.POSITIVE_INFINITY) {
                    sb.append(String.format("%-" + maxWidth + "s", "INF"));
                } else {
                    sb.append(String.format("%-" + maxWidth + ".2f", adjacencyMatrix[i][j]));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public int getVehicleSpeed() {
        return vehicleSpeed;
    }

    public int getLoadingSpeed() {
        return loadingSpeed;
    }

}
