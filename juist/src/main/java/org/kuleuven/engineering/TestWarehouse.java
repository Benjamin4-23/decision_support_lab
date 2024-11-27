package org.kuleuven.engineering;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestWarehouse {
    public static void main(String[] args) {
        // Define the path to the JSON file you want to test
        String fileName = "I30_100_3_3_10.json"; // You can change this to test different files
        Path currentPath = Paths.get(System.getProperty("user.dir")); // current location
        Path filePath = Paths.get(currentPath.toString(), "juist", "data", fileName);

        // Read the warehouse configuration from the JSON file
        Warehouse warehouse = DataReader.read(filePath);

        // Schedule the requests
        warehouse.scheduleRequests();

        // Print the operation log
        warehouse.writeOperationLog();
    }
}
