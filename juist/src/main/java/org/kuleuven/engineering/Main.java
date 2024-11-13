package org.kuleuven.engineering;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        String fileName = "I3_3_1.json";
        Path currentPath = Paths.get(System.getProperty("user.dir")); // current location
        Path filePath = Paths.get(currentPath.toString(), "juist", "data", fileName);
        Warehouse warehouse = DataReader.read(filePath);
        warehouse.scheduleRequests();
        // warehouse.printOperationLog();
    }
}
