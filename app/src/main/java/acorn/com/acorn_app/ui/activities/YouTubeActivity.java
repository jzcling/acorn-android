package acorn.com.acorn_app.ui.activities;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import java.util.Date;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.TimeLog;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.ui.adapters.VideoOnClickListener;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.Logger;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static acorn.com.acorn_app.utils.UiUtils.increaseTouchArea;

public class YouTubeActivity extends AppCompatActivity {
    private static final String TAG = "YoutubeActivity";

    private String mApiKey;
    private String mVideoId;

    private YouTubePlayerView mYouTubePlayerView;
    private int width;
    private int height;

    private ConstraintLayout mButtonLayout;
    private ConstraintLayout mButtonLayoutRight;

    // Action Buttons
    private CheckBox upVoteView;
    private CheckBox downVoteView;
    private CheckBox commentView;
    private CheckBox favView;
    private CheckBox shareView;
    private CheckBox upVoteViewRight;
    private CheckBox downVoteViewRight;
    private CheckBox commentViewRight;
    private CheckBox favViewRight;
    private CheckBox shareViewRight;

    private Logger mLogger;

    private TimeLog mTimeLog;

    private AppExecutors mExecutors = AppExecutors.getInstance();
    private NetworkDataSource mDataSource = NetworkDataSource.getInstance(this, mExecutors);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_youtube);

        mLogger = new Logger(this);

        mYouTubePlayerView = findViewById(R.id.player);
        getLifecycle().addObserver(mYouTubePlayerView);
        initPictureInPicture(mYouTubePlayerView);

        mButtonLayout = findViewById(R.id.button_layout_bottom);
        mButtonLayoutRight = findViewById(R.id.button_layout_right);

        upVoteView = (CheckBox) findViewById(R.id.button_upvote);
        downVoteView = (CheckBox) findViewById(R.id.button_downvote);
        commentView = (CheckBox) findViewById(R.id.button_comment);
        favView = (CheckBox) findViewById(R.id.button_favourite);
        shareView = (CheckBox) findViewById(R.id.button_share);
        upVoteViewRight = (CheckBox) findViewById(R.id.button_upvote_right);
        downVoteViewRight = (CheckBox) findViewById(R.id.button_downvote_right);
        commentViewRight = (CheckBox) findViewById(R.id.button_comment_right);
        favViewRight = (CheckBox) findViewById(R.id.button_favourite_right);
        shareViewRight = (CheckBox) findViewById(R.id.button_share_right);

        increaseTouchArea(upVoteView);
        increaseTouchArea(downVoteView);
        increaseTouchArea(commentView);
        increaseTouchArea(favView);
        increaseTouchArea(shareView);
        increaseTouchArea(upVoteViewRight);
        increaseTouchArea(downVoteViewRight);
        increaseTouchArea(commentViewRight);
        increaseTouchArea(favViewRight);
        increaseTouchArea(shareViewRight);

        Intent intent = getIntent();
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        mVideoId = intent.getStringExtra("videoId");
        mApiKey = intent.getStringExtra("apiKey");
        boolean fromNotif = intent.getBooleanExtra("fromNotif", false);
        String notifType = intent.getStringExtra("notifType");
        if (mUid == null) mUid = FirebaseAuth.getInstance().getUid();
        if (mUid != null) {
            mLogger.logNotificationClicked(fromNotif, notifType, mUid, mVideoId);
        } else {
            mLogger.logNotificationError(fromNotif, notifType, "unknown", mVideoId);
        }

        initialisePlayer();
        setListeners();

        mTimeLog = new TimeLog();
        mTimeLog.openTime = (new Date()).getTime();
        mTimeLog.userId = mUid;
        mTimeLog.itemId = "yt:" + mVideoId;
        mTimeLog.type = "video";
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

        mTimeLog.closeTime = (new Date()).getTime();
        mTimeLog.activeTime = mTimeLog.closeTime - mTimeLog.openTime;
        mDataSource.logItemTimeLog(mTimeLog);

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

        handleIntent(intent);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!isInPictureInPictureMode()) {
                mButtonLayoutRight.setVisibility(View.VISIBLE);
                mButtonLayout.setVisibility(View.GONE);
            }
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (!isInPictureInPictureMode()) {
                mButtonLayoutRight.setVisibility(View.GONE);
                mButtonLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    private void initialisePlayer() {
        mYouTubePlayerView.getYouTubePlayerWhenReady(youTubePlayer -> {
            youTubePlayer.loadVideo(mVideoId, 0f);
        });
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            mYouTubePlayerView.enterFullScreen();
            mYouTubePlayerView.getPlayerUiController().showUi(false);
        } else {
            mYouTubePlayerView.exitFullScreen();
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                mButtonLayout.setVisibility(View.VISIBLE);
            } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mButtonLayoutRight.setVisibility(View.VISIBLE);
            }

            mYouTubePlayerView.getPlayerUiController().showUi(true);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPipMode() {
        width = 16;
        height = 9;
        Rational rational = new Rational(width, height);
        mButtonLayout.setVisibility(View.GONE);
        mButtonLayoutRight.setVisibility(View.GONE);

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

    private VideoOnClickListener onClickListener(Video video, String cardAttribute) {
        return new VideoOnClickListener(this, video, cardAttribute,
                upVoteView, downVoteView, commentView, favView, shareView);
    }

    private VideoOnClickListener onClickListenerRight(Video video, String cardAttribute) {
        return new VideoOnClickListener(this, video, cardAttribute,
                upVoteViewRight, downVoteViewRight, commentViewRight, favViewRight, shareViewRight);
    }

    private void setListeners() {
        String videoId = "yt:" + mVideoId;
        mDataSource.getSingleVideo(videoId, video -> {
            upVoteView.setOnClickListener(onClickListener(video, "upvote"));
            downVoteView.setOnClickListener(onClickListener(video, "downvote"));
            commentView.setOnClickListener(onClickListener(video, "comment"));
            favView.setOnClickListener(onClickListener(video, "favourite"));
            shareView.setOnClickListener(onClickListener(video, "share"));
            upVoteViewRight.setOnClickListener(onClickListener(video, "upvote"));
            downVoteViewRight.setOnClickListener(onClickListener(video, "downvote"));
            commentViewRight.setOnClickListener(onClickListener(video, "comment"));
            favViewRight.setOnClickListener(onClickListener(video, "favourite"));
            shareViewRight.setOnClickListener(onClickListener(video, "share"));
        });
    }
}