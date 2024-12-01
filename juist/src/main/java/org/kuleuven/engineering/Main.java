package org.kuleuven.engineering;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        String fileName = "I100_800_3_1_20b2.json";
        Path currentPath = Paths.get(System.getProperty("user.dir")); // current location
        Path filePath = Paths.get(currentPath.toString(), "juist", "data", fileName);
        Warehouse warehouse = DataReader.read(filePath);
        warehouse.scheduleRequests();
        warehouse.writeOperationLog();
    }
}

// bij ophalen van stack kijken of het needed box is, of box van andere request

