package acorn.com.acorn_app.services;

import acorn.com.acorn_app.models.ReverseGeocode;
import io.reactivex.Single;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface MapsApiService {
    @GET("geocode/json")
    Single<ReverseGeocode> getReverseGeocode(@Query("key") String apiKey,
                                             @Query("latlng") String latlng,
                                             @Query("result_type") String resultType,
                                             @Query("location_type") String locationType);
}
