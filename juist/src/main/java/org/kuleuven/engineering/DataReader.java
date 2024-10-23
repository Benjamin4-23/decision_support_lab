package org.kuleuven.engineering;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.kuleuven.engineering.graph.Graph;
import org.kuleuven.engineering.graph.GraphNode;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DataReader {
    public static Warehouse read(Path filePath){
        try(FileReader reader = new FileReader(filePath.toFile())){
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();

            int loadingDuration = object.get("loadingduration").getAsInt();
            int vehicleSpeed = object.get("vehiclespeed").getAsInt();
            int stackCapacity = object.get("stackcapacity").getAsInt();

            List<JsonElement> Jstacks = object.get("stacks").getAsJsonArray().asList();
            List<JsonElement> Jbufferpoints = object.get("bufferpoints").getAsJsonArray().asList();
            List<JsonElement> Jvehicles = object.get("vehicles").getAsJsonArray().asList();
            List<JsonElement> Jrequests = object.get("requests").getAsJsonArray().asList();

            Graph graph = new Graph(vehicleSpeed, loadingDuration);
            //read
            for (JsonElement element: Jstacks) {
                JsonObject Jobject = element.getAsJsonObject();
                Stack stack = new Stack(Jobject, stackCapacity);
                Location location = new Location(Jobject.get("x").getAsInt(), Jobject.get("y").getAsInt());
                graph.addNode(new GraphNode(stack, location));
            }

            for (JsonElement element: Jbufferpoints) {
                JsonObject Jobject = element.getAsJsonObject();
                Bufferpoint bufferpoint = new Bufferpoint(Jobject);
                Location location = new Location(Jobject.get("x").getAsInt(), Jobject.get("y").getAsInt());
                graph.addNode(new GraphNode(bufferpoint, location));
            }
            List<Vehicle> vehicles = Jvehicles.stream().map(e -> new Vehicle(e.getAsJsonObject())).toList();

            List<Request> requests = Jrequests.stream().map(e -> new Request(e.getAsJsonObject())).toList();

            return new Warehouse(graph, vehicles, requests);
        } catch (IOException ex){
            ex.printStackTrace();
        }
        return null;
    }

    private static List<Stack> parseStacks(List<JsonElement> Jstacks, int capacity){
        List<Stack> stacks = new ArrayList<>(Jstacks.size());
        for (JsonElement element: Jstacks) {
            JsonObject object = element.getAsJsonObject();
            Stack stack = new Stack(object, capacity);
            Location location = new Location(object.get("x").getAsInt(), object.get("y").getAsInt());

        }
        return stacks;
    }

}
