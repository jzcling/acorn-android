package acorn.com.acorn_app.ui.activities;

import android.app.AppOpsManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerUtils;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import acorn.com.acorn_app.R;

import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class YouTubeActivity extends AppCompatActivity {
    private static final String TAG = "YoutubeActivity";

    private String mApiKey;
    private String mVideoId;

    private YouTubePlayerView mYouTubePlayerView;
    private int width;
    private int height;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_youtube);

        mApiKey = getIntent().getStringExtra("apiKey");
        mVideoId = getIntent().getStringExtra("videoId");

        mYouTubePlayerView = findViewById(R.id.player);
        getLifecycle().addObserver(mYouTubePlayerView);
        initPictureInPicture(mYouTubePlayerView);
        initialisePlayer();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();

        finishAndRemoveTask();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent");
        super.onNewIntent(intent);

        mApiKey = intent.getStringExtra("apiKey");
        mVideoId = intent.getStringExtra("videoId");

        initialisePlayer();
    }

    private void initialisePlayer() {
        mYouTubePlayerView.getYouTubePlayerWhenReady(youTubePlayer -> {
            youTubePlayer.loadVideo(mVideoId, 0f);
            width = mYouTubePlayerView.getWidth();
            height = mYouTubePlayerView.getHeight();
            mYouTubePlayerView.enterFullScreen();
        });
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            mYouTubePlayerView.getPlayerUiController().showUi(false);
        } else {
            mYouTubePlayerView.getPlayerUiController().showUi(true);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPipMode() {
        Rational rational = new Rational(width, height);

        PictureInPictureParams mParams =
                new PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                        .build();

        enterPictureInPictureMode(mParams);
    }

    private void initPictureInPicture(YouTubePlayerView youTubePlayerView) {
        ImageView pictureInPictureIcon = new ImageView(this);
        pictureInPictureIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_picture_in_picture_24dp));

        pictureInPictureIcon.setOnClickListener( view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                boolean supportsPIP = getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
                if(supportsPIP)
                    enterPipMode();
            } else {
                createToast(this, "You need at least Android Oreo for this feature", Toast.LENGTH_LONG);
            }
        });

        youTubePlayerView.getPlayerUiController().addView(pictureInPictureIcon);
    }
}