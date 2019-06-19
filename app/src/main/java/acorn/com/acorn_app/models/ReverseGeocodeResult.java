package acorn.com.acorn_app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ReverseGeocodeResult {
    @SerializedName("formatted_address")
    @Expose
    private String formattedAddress;
    @SerializedName("types")
    @Expose
    private List<String> types = null;

    public String getFormattedAddress() {
        return formattedAddress;
    }

    public void setFormattedAddress(String formattedAddress) {
        this.formattedAddress = formattedAddress;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }
}
