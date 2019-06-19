package acorn.com.acorn_app.models;

import java.util.HashMap;
import java.util.Map;

public class Address {
    public String objectID;
    public Map<String, Integer> article = new HashMap<>();
    public String address;
    public String formattedAddress;
    public Map<String, Double> location;

    public Address() {}
}