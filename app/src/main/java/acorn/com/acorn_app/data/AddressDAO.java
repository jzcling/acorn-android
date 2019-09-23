package acorn.com.acorn_app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import acorn.com.acorn_app.models.dbStation;
import acorn.com.acorn_app.models.dbAddress;

@Dao
public interface AddressDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(dbAddress address);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAddresses(List<dbAddress> addresses);

    @Update
    void update(dbAddress address);

    @Update
    void update(dbAddress... addresses);

    @Query("DELETE FROM address_table")
    void deleteAllAddresses();

    @Query("DELETE FROM address_table " +
            "WHERE articleId = :articleId")
    void deleteForArticle(String articleId);

    @Query("SELECT * FROM address_table " +
            "WHERE objectID = :objectID")
    dbAddress getDbAddress(String objectID);

    @Query("SELECT * FROM address_table")
    List<dbAddress> getAllAddresses();

    @Query("SELECT COUNT(objectID) FROM address_table")
    int size();

    @Query("SELECT COUNT(DISTINCT articleId) FROM address_table")
    int articleCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(dbStation station);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStations(List<dbStation> stations);

    @Update
    void update(dbStation station);

    @Update
    void update(dbStation... stations);

    @Query("DELETE FROM station_table")
    void deleteAllStations();

    @Query("SELECT * FROM station_table " +
            "WHERE stationLocale = :locale")
    dbStation getStation(String locale);

    @Query("SELECT * FROM station_table")
    List<dbStation> getAllStations();

    @Query("SELECT COUNT(stationLocale) FROM station_table")
    int stationSize();

    @Query("SELECT stationLocale FROM station_table")
    List<String> getAllStationLocales();
}
