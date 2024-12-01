package org.kuleuven.engineering;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class TestWarehouse {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        for (File f : new File("./juist/data/").listFiles()) {
            System.out.printf("\033[33mRunning file %s!\033[0m %n", f.getName());
            Warehouse warehouse = DataReader.read(f.getPath());
            warehouse.scheduleRequests();
            warehouse.writeOperationLog();

            System.out.print("Press enter to run next file");
            String name = scanner.nextLine();
        }
    }
}
