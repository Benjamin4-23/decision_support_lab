package org.kuleuven.engineering;

public class Event {

    public EventType type;
    public String boxId;
    public String vehicleName;
    public String startLocation;
    public String endLocation;
    public boolean isHandled;
    private double startTime;
    private double endTime;

    public Event(String vehicleName, String boxId, String startLocation, String endLocation, EventType type){
        this.boxId = boxId;
        this.type = type;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.vehicleName = vehicleName;
        this.isHandled = false;
    }
    public enum EventType{
        PU,
        PL,
        PU_RELOCATE, 
        PL_RELOCATE
    }


    public boolean isHandled(double time){
        return isHandled;
    }

    public String log(){
        String s = vehicleName + ";" + startLocation + ";" + startTime  + ";" + endLocation + ";" + endTime + ";"+ boxId + ";" + type.toString();

        System.out.print(s);
        return s;
    }
}


