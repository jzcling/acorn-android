package acorn.com.acorn_app.data;

import android.arch.lifecycle.LiveData;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;

import java.util.ArrayList;
import java.util.List;

import acorn.com.acorn_app.models.Video;

public class VideoListLiveData extends LiveData<List<Video>> {
    private static final String TAG = "VideoListLiveData";

    private final Query query;

    private final MyChildEventListener childListener = new MyChildEventListener();

    private final List<String> mVideoIds = new ArrayList<>();
    private final List<Video> mVideoList = new ArrayList<>();

    private boolean listenerRemovePending = false;
    private final Handler handler = new Handler();
    private final Runnable removeListener = new Runnable() {
        @Override
        public void run() {
            query.removeEventListener(childListener);
            listenerRemovePending = false;
        }
    };

    public VideoListLiveData(Query query) {
        this.query = query;
    }

    @Override
    protected void onActive() {
        if (listenerRemovePending) {
            handler.removeCallbacks(removeListener);
        }
        else {
            query.addChildEventListener(childListener);
        }
        listenerRemovePending = false;
    }

    @Override
    protected void onInactive() {
        handler.postDelayed(removeListener, 500);
        listenerRemovePending = true;
    }

    private class MyChildEventListener implements ChildEventListener {

        @Override
        public void onChildAdded(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildAdded");
            Video video = dataSnapshot.getValue(Video.class);

            if (!mVideoIds.contains(dataSnapshot.getKey())) {
                if (video != null && !video.isReported) {
                    mVideoIds.add(dataSnapshot.getKey());
                    mVideoList.add(video);
                }
            }
            setValue(mVideoList);
        }

        @Override
        public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildChanged");
            Video newVideo = dataSnapshot.getValue(Video.class);
            String videoKey = dataSnapshot.getKey();

            int videoIndex = mVideoIds.indexOf(videoKey);
            if (videoIndex > -1) {
                if (newVideo != null) {
                    if (newVideo.isReported) {
                        mVideoIds.remove(videoIndex);
                        mVideoList.remove(videoIndex);
                    } else {
                        // Replace with the new data
                        mVideoList.set(videoIndex, newVideo);
                    }
                }
            }

            setValue(mVideoList);
        }

        @Override
        public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
            Log.d(TAG, "onChildRemoved");
            String videoKey = dataSnapshot.getKey();

            int videoIndex = mVideoIds.indexOf(videoKey);
            if (videoIndex > -1) {
                // Remove data from the list
                mVideoIds.remove(videoIndex);
                mVideoList.remove(videoIndex);
            }

            setValue(mVideoList);
        }

        @Override
        public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildMoved");
            Video movedVideo = dataSnapshot.getValue(Video.class);
            String videoKey = dataSnapshot.getKey();

            int oldIndex = mVideoIds.indexOf(videoKey);
            if (oldIndex > -1) {
                // Remove data from old position
                mVideoIds.remove(oldIndex);
                mVideoList.remove(oldIndex);

                // Add data in new position
                int newIndex = previousChildKey == null ? 0 : mVideoIds.indexOf(previousChildKey) + 1;
                mVideoIds.add(videoKey);
                mVideoList.add(newIndex, movedVideo);
            }

            setValue(mVideoList);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {

        }

    }
}
