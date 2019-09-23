package acorn.com.acorn_app.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "station_table")
public class dbStation {
    public double latitude;
    public double longitude;
    @PrimaryKey
    @NonNull
    public String stationLocale;
    public String type;

    @Ignore
    public dbStation() {}

    public dbStation(double latitude, double longitude, @NonNull String stationLocale, String type) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.stationLocale = stationLocale;
        this.type = type;
    }
}