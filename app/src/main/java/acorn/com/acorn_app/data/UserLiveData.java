package acorn.com.acorn_app.data;

import android.os.Handler;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.utils.DateUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mSharedPreferences;

public class UserLiveData extends LiveData<User> {
    private static final String TAG = "UserLiveData";

    private Query query;

    private final UserValueEventListener valueListener = new UserValueEventListener();

    private boolean listenerRemovePending = false;
    private final Handler handler = new Handler();
    private final Runnable removeListener = () -> {
            query.removeEventListener(valueListener);
            listenerRemovePending = false;
    };

    public UserLiveData(String uid) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
        this.query = dbRef.child("user/" + uid);
    }

    @Override
    protected void onActive() {
        Log.d(TAG, "onActive");
        if (listenerRemovePending) {
            handler.removeCallbacks(removeListener);
        } else {
            query.addValueEventListener(valueListener);
        }
        listenerRemovePending = false;
    }

    @Override
    protected void onInactive() {
        Log.d(TAG, "onInactive");
        handler.postDelayed(removeListener, 500);
        listenerRemovePending = true;
    }

    private class UserValueEventListener implements ValueEventListener {

        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            Log.d(TAG, "user data changed");
            if (dataSnapshot.exists()) {
                User user = dataSnapshot.getValue(User.class);
                setValue(user);
            } else {
                setValue(null);
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            // Do nothing
        }
    }

}

