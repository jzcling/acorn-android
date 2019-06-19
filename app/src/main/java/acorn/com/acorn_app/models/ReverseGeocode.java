package acorn.com.acorn_app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ReverseGeocode {
    @SerializedName("results")
    @Expose
    private List<ReverseGeocodeResult> results = null;
    @SerializedName("status")
    @Expose
    private String status;

    public List<ReverseGeocodeResult> getResults() {
        return results;
    }

    public void setResults(List<ReverseGeocodeResult> results) {
        this.results = results;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
