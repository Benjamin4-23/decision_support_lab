package org.kuleuven.engineering;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        String fileName = "I100_800_3_1_20b2.json";
        Warehouse warehouse = DataReader.read("./juist/data/"+fileName);
        warehouse.scheduleRequests();
        warehouse.writeOperationLog();
    }
}

// bij ophalen van stack kijken of het needed box is, of box van andere request

