package acorn.com.acorn_app.models;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.HashMap;
import java.util.Map;

@Entity(tableName = "address_table")
public class dbAddress {
    @PrimaryKey(autoGenerate = true)
    public Integer id;
    public String objectID;
    public String articleId;
    public String address;
    public String formattedAddress;
    public Double latitude;
    public Double longitude;

    public dbAddress(Address address, String articleId) {
        this.objectID = address.objectID;
        this.articleId = articleId;
        this.address = address.address;
        this.formattedAddress = address.formattedAddress;
        if (address.location != null) {
            this.latitude = address.location.get("lat");
            this.longitude = address.location.get("lng");
        }
    }

    public dbAddress(String objectID, String articleId, String address,
                     String formattedAddress, Double latitude, Double longitude) {
        this.objectID = objectID;
        this.articleId = articleId;
        this.address = address;
        this.formattedAddress = formattedAddress;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}