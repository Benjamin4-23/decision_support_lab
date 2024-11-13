package org.kuleuven.engineering;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.kuleuven.engineering.graph.Graph;
import org.kuleuven.engineering.graph.GraphNode;

public class DataReader {
    public static Warehouse read(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JSONObject object = JsonParser.parseString(content);

            int loadingDuration = object.getInt("loadingduration");
            int vehicleSpeed = object.getInt("vehiclespeed");
            int stackCapacity = object.getInt("stackcapacity");

            List<Map<String, Object>> Jstacks = JsonParser.toList(object.getJSONArray("stacks"));
            List<Map<String, Object>> Jbufferpoints = JsonParser.toList(object.getJSONArray("bufferpoints"));
            List<Map<String, Object>> Jvehicles = JsonParser.toList(object.getJSONArray("vehicles"));
            List<Map<String, Object>> Jrequests = JsonParser.toList(object.getJSONArray("requests"));

            Graph graph = new Graph(vehicleSpeed);
            HashMap<String, GraphNode> nodeMap = new HashMap<>();

            for (Map<String, Object> Jobject : Jstacks) {
                Stack stack = new Stack(new JSONObject(Jobject), stackCapacity);
                Location location = new Location((int) Jobject.get("x"), (int) Jobject.get("y"));
                graph.addNode(new GraphNode(stack, location));
            }

            for (Map<String, Object> Jobject : Jbufferpoints) {
                Bufferpoint bufferpoint = new Bufferpoint(new JSONObject(Jobject));
                Location location = new Location((int) Jobject.get("x"), (int) Jobject.get("y"));
                GraphNode node = new GraphNode(bufferpoint, location);
                graph.addNode(node);
                nodeMap.put(node.getName(), node);
            }

            List<Vehicle> vehicles = new ArrayList<>();
            for (Map<String, Object> Jobject : Jvehicles) {
                vehicles.add(new Vehicle(new JSONObject(Jobject)));
            }

            List<Request> requests = new ArrayList<>();
            for (Map<String, Object> Jobject : Jrequests) {
                GraphNode pickupLocation, placeLocation;
                try{
                    pickupLocation = nodeMap.get(object.getJSONArray("pickupLocation").getString(0));
                    placeLocation = nodeMap.get(object.getJSONArray("placeLocation").getString(0));
                } catch (JSONException e){
                    pickupLocation = nodeMap.get(object.getString("pickupLocation"));
                    placeLocation = nodeMap.get(object.getString("placeLocation"));
                }

                int ID = object.getInt("ID");
                String boxID = object.getString("boxID");
                requests.add(new Request(pickupLocation, placeLocation, ID, boxID));
                //requests.add(new Request(new JSONObject(Jobject)));
            }

            return new Warehouse(graph, vehicles, requests, loadingDuration);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
