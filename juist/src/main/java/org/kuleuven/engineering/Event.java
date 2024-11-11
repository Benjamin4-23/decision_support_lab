package org.kuleuven.engineering;

public class Event {

    public EventType type;
    public String boxId;
    public String vehicleName;
    public String location;
    private double timer = -1;
    private double startTime;
    public Event(String vehicleName, String boxId, String location, EventType type){
        this.boxId = boxId;
        this.type = type;
        this.location = location;
        this.vehicleName = vehicleName;
    }
    public enum EventType{
        PICK_UP,
        PLACE,
        RELOCATE, // got to place to relocate
        RELOCATE_RETURN // return to original stack
    }

    public void setStartTime(double time){
        this.startTime = time;
    }
    public void setTimer(double time){
        this.timer = time;
    }

    public boolean isHandled(double time){
        if(timer == -1) return false;
        return time > timer;
    }

    public String log(){
        String s = "";
        /*if(type == EventType.PICK_UP || type == EventType.PLACE){
            s += String.format("%s, %.0f, %.0f, %s, %s, %s%n", vehicleName,startTime, timer, boxId, location,type.toString());
        }*/
        s += String.format("%s, %.0f, %.0f, %s, %s, %s%n", vehicleName,startTime, timer, boxId, location,type.toString());

        System.out.print(s);
        return s;
    }
}


