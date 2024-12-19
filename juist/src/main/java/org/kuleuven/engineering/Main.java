package org.kuleuven.engineering;

import org.kuleuven.engineering.dataReading.DataReader;

public class Main {
    public static void main(String[] args) {
        String fileName = "I3_3_1_2_2.json";
        Warehouse warehouse = DataReader.read("./juist/data/"+fileName);
        warehouse.scheduleRequests();
        // warehouse.writeOperationLog();
    }
}

// bij ophalen van stack kijken of het needed box is, of box van andere request

