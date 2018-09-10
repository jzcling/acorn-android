package acorn.com.acorn_app.ui.adapters;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Date;
import java.util.concurrent.Executor;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.ui.activities.CommentActivity;
import acorn.com.acorn_app.ui.activities.WebViewActivity;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.UiUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_0;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_1;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_2;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_3;
import static acorn.com.acorn_app.ui.activities.AcornActivity.RC_SHARE;
import static acorn.com.acorn_app.ui.activities.AcornActivity.TARGET_POINTS_MULTIPLIER;
import static acorn.com.acorn_app.ui.activities.AcornActivity.isUserAuthenticated;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class ArticleOnClickListener implements View.OnClickListener {
    private final static String TAG = "ArticleOnClickListener";
    private final Handler handler = new Handler();
    private boolean isTransationRunning = false;

    private final Context mContext;
    private final Article mArticle;
    private final String mCardAttribute;

    private final View mUpvoteView;
    private final View mDownvoteView;
    private final View mCommentView;
    private final View mFavView;
    private final View mShareView;

    private final Animation upvoteAnim;
    private final Animation downvoteAnim;
    private final Animation bounceAnim;

    private final Executor mainThread = AppExecutors.getInstance().mainThread();

    public ArticleOnClickListener(Context context, Article article, String cardAttribute,
                                  View upvoteView, View downvoteView, View commentView,
                                  View favView, View shareView) {
        mContext = context;
        mArticle = article;
        mCardAttribute = cardAttribute;

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
            case "image":
                startWebViewActivity();
                break;
            case "upvote":
                onUpvoteClicked();
                break;
            case "downvote":
                onDownvoteClicked();
                break;
            case "comment":
                Intent commentIntent = new Intent(mContext, CommentActivity.class);
                commentIntent.putExtra("id", mArticle.getObjectID());
                bounceAnim.setAnimationListener(new MyAnimationListener(commentIntent));
                if (mCommentView != null) {
                    mCommentView.startAnimation(bounceAnim);
                } else {
                    mContext.startActivity(commentIntent);
                }
                break;
            case "favourite":
                onSaveClicked();
                break;
            case "share":
                onShareClicked();
                break;
            case "postImage":
                StorageReference storageReference;
                if (mArticle.getPostImageUrl() != null) {
                    storageReference = FirebaseStorage.getInstance()
                            .getReferenceFromUrl(mArticle.getPostImageUrl());
                } else {
                    storageReference = FirebaseStorage.getInstance()
                            .getReferenceFromUrl(mArticle.getImageUrl());
                }
                Dialog imagePopUp =
                        new UiUtils().configureImagePopUp(mContext, storageReference);
                imagePopUp.show();
                break;
        }
    }

    private void startWebViewActivity() {
//        if (mArticle.getType().equals("article")) {
            Intent intent = new Intent(mContext, WebViewActivity.class);
            intent.putExtra("id", mArticle.getObjectID());
            mContext.startActivity(intent);
//        } else {
//            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mArticle.getLink()));
//            mContext.startActivity(intent);
//        }
    }

    private void onUpvoteClicked() {
        if (isTransationRunning) return;
        if (!isUserAuthenticated) {
            createToast(mContext, "Please verify your email to vote", Toast.LENGTH_SHORT);
            return;
        }

        isTransationRunning = true;
        disableVoteViews(isTransationRunning);

        Long clickTime = new Date().getTime();
        String articleId = mArticle.getObjectID();

        // Update article with upvote data
        DatabaseReference articleRef = FirebaseDatabase.getInstance()
                .getReference("article/"+articleId);

        articleRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Article article = mutableData.getValue(Article.class);
                if (article == null) {
                    Log.d(TAG, "onUpvoteClicked: article: could not find article");
                    return Transaction.success(mutableData);
                }

                int currentVoteCount = article.getVoteCount();

                if (article.upvoters.containsKey(mUid)) {
                    article.upvoters.remove(mUid);
                    article.setVoteCount(currentVoteCount - 1);
                    mainThread.execute(()->mUpvoteView.startAnimation(downvoteAnim));
                } else if (article.downvoters.containsKey(mUid)) {
                    article.downvoters.remove(mUid);
                    article.upvoters.put(mUid, clickTime);
                    article.setVoteCount(currentVoteCount + 2);
                    mainThread.execute(()->mUpvoteView.startAnimation(upvoteAnim));
                } else {
                    article.upvoters.put(mUid, clickTime);
                    article.setVoteCount(currentVoteCount + 1);
                    mainThread.execute(()->mUpvoteView.startAnimation(upvoteAnim));
                }

                article.changedSinceLastJob = true;
                mutableData.setValue(article);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                Log.d(TAG, "onUpvoteClicked: article: " + databaseError);
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
                    Log.d(TAG, "onUpvoteClicked: user: could not find user");
                    return Transaction.success(mutableData);
                }

                int currentUpvoteCount = user.getUpvotedItemsCount();
                int currentDownvoteCount = user.getDownvotedItemsCount();
                int currentPointsCount = user.getPoints();
                int targetPoints = user.getTargetPoints();

                if (user.upvotedItems.containsKey(articleId)) {
                    user.upvotedItems.remove(articleId);
                    user.setUpvotedItemsCount(currentUpvoteCount - 1);
                    user.setPoints(currentPointsCount - 1);
                } else if (user.downvotedItems.containsKey(articleId)) {
                    user.downvotedItems.remove(articleId);
                    user.upvotedItems.put(articleId, clickTime);
                    user.setUpvotedItemsCount(currentUpvoteCount + 1);
                    user.setDownvotedItemsCount(currentDownvoteCount - 1);
                } else {
                    user.upvotedItems.put(articleId, clickTime);
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
                    mainThread.execute(()->createToast(mContext,
                            "Congratulations! You have grown into a " + newStatus, Toast.LENGTH_SHORT));
                }
                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                handler.postDelayed(() -> {
                    isTransationRunning = false;
                    disableVoteViews(isTransationRunning);
                    Log.d(TAG, "onUpvoteClicked: user: " + databaseError);
                }, 300);
            }
        });
    }

    private void onDownvoteClicked() {
        if (isTransationRunning) return;
        if (!isUserAuthenticated) {
            createToast(mContext, "Please verify your email to vote", Toast.LENGTH_SHORT);
            return;
        }

        isTransationRunning = true;
        disableVoteViews(isTransationRunning);

        Long clickTime = new Date().getTime();
        String articleId = mArticle.getObjectID();

        // Update article with downvote data
        DatabaseReference articleRef = FirebaseDatabase.getInstance()
                .getReference("article/"+articleId);

        articleRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Article article = mutableData.getValue(Article.class);
                if (article == null) {
                    Log.d(TAG, "onDownvoteClicked: article: could not find article");
                    return Transaction.success(mutableData);
                }

                int currentVoteCount = article.getVoteCount();

                if (article.downvoters.containsKey(mUid)) {
                    article.downvoters.remove(mUid);
                    article.setVoteCount(currentVoteCount + 1);
                    mainThread.execute(()->mDownvoteView.startAnimation(upvoteAnim));
                } else if (article.upvoters.containsKey(mUid)) {
                    article.upvoters.remove(mUid);
                    article.downvoters.put(mUid, clickTime);
                    article.setVoteCount(currentVoteCount - 2);
                    mainThread.execute(()->mDownvoteView.startAnimation(downvoteAnim));
                } else {
                    article.downvoters.put(mUid, clickTime);
                    article.setVoteCount(currentVoteCount - 1);
                    mainThread.execute(()->mDownvoteView.startAnimation(downvoteAnim));
                }

                article.changedSinceLastJob = true;
                mutableData.setValue(article);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDownvoteClicked: article: " + databaseError);
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
                    Log.d(TAG, "onDownvoteClicked: user: could not find user");
                    return Transaction.success(mutableData);
                }

                int currentUpvoteCount = user.getUpvotedItemsCount();
                int currentDownvoteCount = user.getDownvotedItemsCount();
                int currentPointsCount = user.getPoints();
                int targetPoints = user.getTargetPoints();

                if (user.downvotedItems.containsKey(articleId)) {
                    user.downvotedItems.remove(articleId);
                    user.setDownvotedItemsCount(currentDownvoteCount - 1);
                    user.setPoints(currentPointsCount - 1);
                } else if (user.upvotedItems.containsKey(articleId)) {
                    user.upvotedItems.remove(articleId);
                    user.downvotedItems.put(articleId, clickTime);
                    user.setDownvotedItemsCount(currentDownvoteCount + 1);
                    user.setUpvotedItemsCount(currentUpvoteCount - 1);
                } else {
                    user.downvotedItems.put(articleId, clickTime);
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
                    mainThread.execute(()->createToast(mContext,
                            "Congratulations! You have grown into a " + newStatus, Toast.LENGTH_SHORT));

                }
                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                handler.postDelayed(() -> {
                    isTransationRunning = false;
                    disableVoteViews(isTransationRunning);
                    Log.d(TAG, "onDownvoteClicked: user: " + databaseError);
                }, 300);
            }
        });
    }

    private void onSaveClicked() {
        Long clickTime = new Date().getTime();
        String articleId = mArticle.getObjectID();

        // Update article with save data
        DatabaseReference articleRef = FirebaseDatabase.getInstance()
                .getReference("article/"+articleId);

        articleRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Article article = mutableData.getValue(Article.class);
                if (article == null) {
                    Log.d(TAG, "onSaveClicked: article: could not find article");
                    return Transaction.success(mutableData);
                }

                int currentSaveCount = article.getSaveCount();

                if (article.savers.containsKey(mUid)) {
                    article.savers.remove(mUid);
                    article.setSaveCount(currentSaveCount - 1);
                } else {
                    article.savers.put(mUid, clickTime);
                    article.setSaveCount(currentSaveCount + 1);
                }
                mainThread.execute(()->{
                    bounceAnim.setAnimationListener(null);
                    mFavView.startAnimation(bounceAnim);
                });

                article.changedSinceLastJob = true;
                mutableData.setValue(article);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                Log.d(TAG, "onSaveClicked: article: " + databaseError);
            }
        });

        // Update user with save data
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("user/"+mUid);

        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {
                    Log.d(TAG, "onSaveClicked: user: could not find user");
                    return Transaction.success(mutableData);
                }

                int currentSaveCount = user.getSavedItemsCount();

                if (user.savedItems.containsKey(articleId)) {
                    user.savedItems.remove(articleId);
                    user.setSavedItemsCount(currentSaveCount - 1);
                } else {
                    user.savedItems.put(articleId, clickTime);
                    user.setSavedItemsCount(currentSaveCount + 1);
                }
                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                Log.d(TAG, "onSaveClicked: user: " + databaseError);
            }
        });
    }

    private void onShareClicked() {
        if (mArticle.getLink() != null && !mArticle.getLink().equals("")) {
            Long clickTime = new Date().getTime();
            String articleId = mArticle.getObjectID();

            // Update article with share data
            DatabaseReference articleRef = FirebaseDatabase.getInstance()
                    .getReference("article/" + articleId);

            articleRef.runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                    Article article = mutableData.getValue(Article.class);
                    if (article == null) {
                        Log.d(TAG, "onShareClicked: article: could not find article");
                        return Transaction.success(mutableData);
                    }

                    int currentShareCount = article.getShareCount();

                    if (article.sharers.containsKey(mUid)) {
//                    article.sharers.remove(mUid);
//                    article.setShareCount(currentShareCount - 1);
                    } else {
                        article.sharers.put(mUid, clickTime);
                        article.setShareCount(currentShareCount + 1);
                    }

                    article.changedSinceLastJob = true;
                    mutableData.setValue(article);
                    return Transaction.success(mutableData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                    mainThread.execute(() -> {
                        Intent shareIntent = new Intent();
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, mArticle.getTitle());
                        shareIntent.putExtra(Intent.EXTRA_TEXT, mArticle.getLink());
                        shareIntent.setAction(Intent.ACTION_SEND);
                        bounceAnim.setAnimationListener(
                                new MyAnimationListener(Intent.createChooser(shareIntent, "Share link with")));
                        mShareView.startAnimation(bounceAnim);
                    });
                    Log.d(TAG, "onShareClicked: article: " + databaseError);
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
                        Log.d(TAG, "onShareClicked: user: could not find user");
                        return Transaction.success(mutableData);
                    }

                    int currentShareCount = user.getSharedItemsCount();

                    if (user.sharedItems.containsKey(articleId)) {
//                    user.sharedItems.remove(articleId);
//                    user.setSharedItemsCount(currentShareCount - 1);
                    } else {
                        user.sharedItems.put(articleId, clickTime);
                        user.setSharedItemsCount(currentShareCount + 1);
                    }
                    mutableData.setValue(user);
                    return Transaction.success(mutableData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                    Log.d(TAG, "onShareClicked: user: " + databaseError);
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
            mainThread.execute(()->((Activity) mContext).startActivityForResult(intent, RC_SHARE));
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
    }
}
