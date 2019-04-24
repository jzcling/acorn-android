package acorn.com.acorn_app.ui.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeStandalonePlayer;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.Date;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.UiUtils;

import static acorn.com.acorn_app.ui.AcornApplication.mFirebaseAnalytics;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_0;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_1;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_2;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_3;
import static acorn.com.acorn_app.ui.activities.AcornActivity.TARGET_POINTS_MULTIPLIER;
import static acorn.com.acorn_app.ui.activities.AcornActivity.isUserAuthenticated;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class VideoOnClickListener implements View.OnClickListener {
    private final static String TAG = "VideoOnClickListener";
    private final Handler handler = new Handler();
    private boolean isTransactionRunning = false;

    private final Context mContext;
    private final Video mVideo;
    private final String mCardAttribute;
    private final String mYoutubeApiKey;

    private final View mUpvoteView;
    private final View mDownvoteView;
    private final View mCommentView;
    private final View mFavView;
    private final View mShareView;

    private final Animation upvoteAnim;
    private final Animation downvoteAnim;
    private final Animation bounceAnim;

    //Data source
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    public VideoOnClickListener(Context context, String youtubeApiKey, Video video, String cardAttribute,
                                View upvoteView, View downvoteView, View commentView,
                                View favView, View shareView) {
        mContext = context;
        mVideo = video;
        mCardAttribute = cardAttribute;
        mYoutubeApiKey = youtubeApiKey;

        mDataSource = NetworkDataSource.getInstance(context, mExecutors);

        mUpvoteView = upvoteView;
        mDownvoteView = downvoteView;
        mCommentView = commentView;
        mFavView = favView;
        mShareView = shareView;

        upvoteAnim = AnimationUtils.loadAnimation(mContext, R.anim.upvote);
        downvoteAnim = AnimationUtils.loadAnimation(mContext, R.anim.downvote);
        bounceAnim = AnimationUtils.loadAnimation(mContext, R.anim.bounce);
        UiUtils.MyBounceInterpolator interpolator = new UiUtils.MyBounceInterpolator(0.2, 20);

        bounceAnim.setInterpolator(interpolator);
        upvoteAnim.setInterpolator(interpolator);
        downvoteAnim.setInterpolator(interpolator);
    }

    @Override
    public void onClick(View v) {
        switch (mCardAttribute) {
            case "title":
            case "videoThumbnail":
                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.ITEM_ID, mVideo.getObjectID());
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mVideo.getTitle());
                bundle.putString("item_source", mVideo.getSource());
                bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mVideo.getType());
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

                Intent intent = YouTubeStandalonePlayer
                        .createVideoIntent((Activity) mContext, mYoutubeApiKey, mVideo.youtubeVideoId);
                mExecutors.networkIO().execute(() -> mDataSource.recordVideoOpenDetails(mVideo));
                mContext.startActivity(intent);
                break;
            case "upvote":
                onUpvoteClicked();
                break;
            case "downvote":
                onDownvoteClicked();
                break;
            case "comment":
//                Intent commentIntent = new Intent(mContext, CommentActivity.class);
//                commentIntent.putExtra("id", mVideo.getObjectID());
//                bounceAnim.setAnimationListener(new MyAnimationListener(commentIntent));
//                if (mCommentView != null) {
//                    mCommentView.startAnimation(bounceAnim);
//                } else {
//                    mContext.startActivity(commentIntent);
//                }
                break;
            case "favourite":
                onSaveClicked();
                break;
            case "share":
                onShareClicked();
                break;
            default: break;
        }
    }

    private void onUpvoteClicked() {
        if (isTransactionRunning) return;
        if (!isUserAuthenticated) {
            createToast(mContext, "Please verify your email to vote", Toast.LENGTH_SHORT);
            return;
        }

        isTransactionRunning = true;
        disableVoteViews(isTransactionRunning);

        Long clickTime = new Date().getTime();
        String videoId = mVideo.getObjectID();

        // Update video with upvote data
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("video/"+videoId);

        videoRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Video video = mutableData.getValue(Video.class);
                if (video == null) {

                    return Transaction.success(mutableData);
                }

                int currentVoteCount = video.getVoteCount() == null ? 0 : video.getVoteCount();

                if (video.upvoters.containsKey(mUid)) {
                    video.upvoters.remove(mUid);
                    video.setVoteCount(currentVoteCount - 1);
                    mExecutors.mainThread().execute(()->mUpvoteView.startAnimation(downvoteAnim));
                } else if (video.downvoters.containsKey(mUid)) {
                    video.downvoters.remove(mUid);
                    video.upvoters.put(mUid, clickTime);
                    video.setVoteCount(currentVoteCount + 2);

                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.ITEM_ID, videoId);
                    bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mVideo.getTitle());
                    bundle.putString("item_source", mVideo.getSource());
                    bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mVideo.getType());
                    mFirebaseAnalytics.logEvent("upvote_video", bundle);

                    mExecutors.mainThread().execute(()->mUpvoteView.startAnimation(upvoteAnim));
                } else {
                    video.upvoters.put(mUid, clickTime);
                    video.setVoteCount(currentVoteCount + 1);

                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.ITEM_ID, videoId);
                    bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mVideo.getTitle());
                    bundle.putString("item_source", mVideo.getSource());
                    bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mVideo.getType());
                    mFirebaseAnalytics.logEvent("upvote_video", bundle);

                    mExecutors.mainThread().execute(()->mUpvoteView.startAnimation(upvoteAnim));
                }

                video.changedSinceLastJob = true;
                mutableData.setValue(video);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {

            }
        });

        // Update user with upvote data
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("user/"+mUid);

        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {

                    return Transaction.success(mutableData);
                }

                int currentUpvoteCount = user.getUpvotedItemsCount() == null ? 0 : user.getUpvotedItemsCount();
                int currentDownvoteCount = user.getDownvotedItemsCount() == null ? 0 : user.getDownvotedItemsCount();
                int currentPointsCount = user.getPoints();
                int targetPoints = user.getTargetPoints();

                if (user.upvotedItems.containsKey(videoId)) {
                    user.upvotedItems.remove(videoId);
                    user.setUpvotedItemsCount(currentUpvoteCount - 1);
                    user.setPoints(currentPointsCount - 1);
                } else if (user.downvotedItems.containsKey(videoId)) {
                    user.downvotedItems.remove(videoId);
                    user.upvotedItems.put(videoId, clickTime);
                    user.setUpvotedItemsCount(currentUpvoteCount + 1);
                    user.setDownvotedItemsCount(currentDownvoteCount - 1);
                } else {
                    user.upvotedItems.put(videoId, clickTime);
                    user.setUpvotedItemsCount(currentUpvoteCount + 1);
                    user.setPoints(currentPointsCount + 1);
                }
                if (targetPoints - user.getPoints() < 1) {
                    user.setTargetPoints(targetPoints * TARGET_POINTS_MULTIPLIER);
                    user.setStatus(user.getStatus() + 1);
                    String newStatus;
                    switch (user.getStatus()) {
                        default:
                            newStatus = LEVEL_0;
                            break;
                        case 1:
                            newStatus = LEVEL_1;
                            break;
                        case 2:
                            newStatus = LEVEL_2;
                            break;
                        case 3:
                            newStatus = LEVEL_3;
                            break;
                    }
                    mExecutors.mainThread().execute(()->createToast(mContext,
                            "Congratulations! You have grown into a " + newStatus, Toast.LENGTH_SHORT));
                }
                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                handler.postDelayed(() -> {
                    isTransactionRunning = false;
                    disableVoteViews(isTransactionRunning);

                }, 300);
            }
        });
    }

    private void onDownvoteClicked() {
        if (isTransactionRunning) return;
        if (!isUserAuthenticated) {
            createToast(mContext, "Please verify your email to vote", Toast.LENGTH_SHORT);
            return;
        }

        isTransactionRunning = true;
        disableVoteViews(isTransactionRunning);

        Long clickTime = new Date().getTime();
        String videoId = mVideo.getObjectID();

        // Update video with downvote data
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("video/"+videoId);

        videoRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Video video = mutableData.getValue(Video.class);
                if (video == null) {

                    return Transaction.success(mutableData);
                }

                int currentVoteCount = video.getVoteCount() == null ? 0 : video.getVoteCount();

                if (video.downvoters.containsKey(mUid)) {
                    video.downvoters.remove(mUid);
                    video.setVoteCount(currentVoteCount + 1);
                    mExecutors.mainThread().execute(()->mDownvoteView.startAnimation(upvoteAnim));
                } else if (video.upvoters.containsKey(mUid)) {
                    video.upvoters.remove(mUid);
                    video.downvoters.put(mUid, clickTime);
                    video.setVoteCount(currentVoteCount - 2);

                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.ITEM_ID, videoId);
                    bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mVideo.getTitle());
                    bundle.putString("item_source", mVideo.getSource());
                    bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mVideo.getType());
                    mFirebaseAnalytics.logEvent("downvote_video", bundle);

                    mExecutors.mainThread().execute(()->mDownvoteView.startAnimation(downvoteAnim));
                } else {
                    video.downvoters.put(mUid, clickTime);
                    video.setVoteCount(currentVoteCount - 1);

                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.ITEM_ID, videoId);
                    bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mVideo.getTitle());
                    bundle.putString("item_source", mVideo.getSource());
                    bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mVideo.getType());
                    mFirebaseAnalytics.logEvent("downvote_video", bundle);

                    mExecutors.mainThread().execute(()->mDownvoteView.startAnimation(downvoteAnim));
                }

                video.changedSinceLastJob = true;
                mutableData.setValue(video);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {

            }
        });

        // Update user with downvote data
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("user/"+mUid);

        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {

                    return Transaction.success(mutableData);
                }

                int currentUpvoteCount = user.getUpvotedItemsCount() == null ? 0 : user.getUpvotedItemsCount();
                int currentDownvoteCount = user.getDownvotedItemsCount() == null ? 0 : user.getDownvotedItemsCount();
                int currentPointsCount = user.getPoints();
                int targetPoints = user.getTargetPoints();

                if (user.downvotedItems.containsKey(videoId)) {
                    user.downvotedItems.remove(videoId);
                    user.setDownvotedItemsCount(currentDownvoteCount - 1);
                    user.setPoints(currentPointsCount - 1);
                } else if (user.upvotedItems.containsKey(videoId)) {
                    user.upvotedItems.remove(videoId);
                    user.downvotedItems.put(videoId, clickTime);
                    user.setDownvotedItemsCount(currentDownvoteCount + 1);
                    user.setUpvotedItemsCount(currentUpvoteCount - 1);
                } else {
                    user.downvotedItems.put(videoId, clickTime);
                    user.setDownvotedItemsCount(currentDownvoteCount + 1);
                    user.setPoints(currentPointsCount + 1);
                }
                if (targetPoints - user.getPoints() < 1) {
                    user.setTargetPoints(targetPoints * TARGET_POINTS_MULTIPLIER);
                    user.setStatus(user.getStatus() + 1);
                    String newStatus;
                    switch (user.getStatus()) {
                        default:
                            newStatus = LEVEL_0;
                            break;
                        case 1:
                            newStatus = LEVEL_1;
                            break;
                        case 2:
                            newStatus = LEVEL_2;
                            break;
                        case 3:
                            newStatus = LEVEL_3;
                            break;
                    }
                    mExecutors.mainThread().execute(()->createToast(mContext,
                            "Congratulations! You have grown into a " + newStatus, Toast.LENGTH_SHORT));

                }
                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                handler.postDelayed(() -> {
                    isTransactionRunning = false;
                    disableVoteViews(isTransactionRunning);

                }, 300);
            }
        });
    }

    private void onSaveClicked() {
//        Long clickTime = new Date().getTime();
//        String videoId = mVideo.getObjectID();
//
//        // Update video with save data
//        DatabaseReference videoRef = FirebaseDatabase.getInstance()
//                .getReference("video/"+videoId);
//
//        videoRef.runTransaction(new Transaction.Handler() {
//            @NonNull
//            @Override
//            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
//                Video video = mutableData.getValue(Video.class);
//                if (video == null) {
//
//                    return Transaction.success(mutableData);
//                }
//
//                int currentSaveCount = video.getSaveCount() == null ? 0 : video.getSaveCount();
//
//                if (video.savers.containsKey(mUid)) {
//                    video.savers.remove(mUid);
//                    video.setSaveCount(currentSaveCount - 1);
//                } else {
//                    video.savers.put(mUid, clickTime);
//                    video.setSaveCount(currentSaveCount + 1);
//                }
//                mExecutors.mainThread().execute(()->{
//                    bounceAnim.setAnimationListener(null);
//                    mFavView.startAnimation(bounceAnim);
//                });
//
//                video.changedSinceLastJob = true;
//                mutableData.setValue(video);
//                return Transaction.success(mutableData);
//            }
//
//            @Override
//            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
//
//            }
//        });
//
//        // Update user with save data
//        DatabaseReference userRef = FirebaseDatabase.getInstance()
//                .getReference("user/"+mUid);
//
//        userRef.runTransaction(new Transaction.Handler() {
//            @NonNull
//            @Override
//            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
//                User user = mutableData.getValue(User.class);
//                if (user == null) {
//
//                    return Transaction.success(mutableData);
//                }
//
//                int currentSaveCount = user.getSavedItemsCount() == null ? 0 : user.getSavedItemsCount();
//
//                if (user.savedItems.containsKey(videoId)) {
//                    user.savedItems.remove(videoId);
//                    user.setSavedItemsCount(currentSaveCount - 1);
//                } else {
//                    user.savedItems.put(videoId, clickTime);
//                    user.setSavedItemsCount(currentSaveCount + 1);
//                }
//                mutableData.setValue(user);
//                return Transaction.success(mutableData);
//            }
//
//            @Override
//            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
//
//            }
//        });
    }

    private void onShareClicked() {
        if (mVideo.youtubeVideoId != null && !mVideo.youtubeVideoId.equals("")) {
            Long clickTime = new Date().getTime();
            String videoId = mVideo.getObjectID();

            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, videoId);
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mVideo.getTitle());
            bundle.putString("item_source", mVideo.getSource());
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mVideo.getType());
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, bundle);

            // Update video with share data
            DatabaseReference videoRef = FirebaseDatabase.getInstance()
                    .getReference("video/" + videoId);

            videoRef.runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                    Video video = mutableData.getValue(Video.class);
                    if (video == null) {

                        return Transaction.success(mutableData);
                    }

                    int currentShareCount = video.getShareCount() == null ? 0 : video.getShareCount();

                    if (video.sharers.containsKey(mUid)) {
//                    video.sharers.remove(mUid);
//                    video.setShareCount(currentShareCount - 1);
                    } else {
                        video.sharers.put(mUid, clickTime);
                        video.setShareCount(currentShareCount + 1);
                    }

                    video.changedSinceLastJob = true;
                    mutableData.setValue(video);
                    return Transaction.success(mutableData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                    mExecutors.mainThread().execute(() -> {
                        Intent shareIntent = new Intent();
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, mVideo.getTitle());
                        shareIntent.putExtra(Intent.EXTRA_TEXT, mVideo.getVideoUrl() + "\n- shared using Acorn: Your favourite blogs in a nutshell");
                        shareIntent.setAction(Intent.ACTION_SEND);
                        bounceAnim.setAnimationListener(
                                new MyAnimationListener(Intent.createChooser(shareIntent, "Share video with")));
                        mShareView.startAnimation(bounceAnim);
                    });

                }
            });

            // Update user with share data
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("user/" + mUid);

            userRef.runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                    User user = mutableData.getValue(User.class);
                    if (user == null) {

                        return Transaction.success(mutableData);
                    }

                    int currentShareCount = user.getSharedItemsCount() == null ? 0 : user.getSharedItemsCount();

                    if (user.sharedItems.containsKey(videoId)) {
//                    user.sharedItems.remove(videoId);
//                    user.setSharedItemsCount(currentShareCount - 1);
                    } else {
                        user.sharedItems.put(videoId, clickTime);
                        user.setSharedItemsCount(currentShareCount + 1);
                    }
                    mutableData.setValue(user);
                    return Transaction.success(mutableData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {

                }
            });
        }
    }

    private void disableVoteViews(boolean b) {
        mUpvoteView.setEnabled(!b);
        mDownvoteView.setEnabled(!b);
    }

    private class MyAnimationListener implements Animation.AnimationListener {
        private final Intent intent;

        public MyAnimationListener(Intent intent) { this.intent = intent; }

        @Override
        public void onAnimationStart(Animation animation) {}

        @Override
        public void onAnimationEnd(Animation animation) {
            mExecutors.mainThread().execute(()->((Activity) mContext).startActivity(intent));
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
    }
}
