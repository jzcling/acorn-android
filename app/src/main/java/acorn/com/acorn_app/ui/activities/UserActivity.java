package acorn.com.acorn_app.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.ui.viewModels.UserViewModel;
import acorn.com.acorn_app.ui.viewModels.UserViewModelFactory;
import acorn.com.acorn_app.utils.InjectorUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_0;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_1;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_2;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_3;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;

public class UserActivity extends AppCompatActivity {
    private static final String TAG = "UserActivity";
    private static final float TARGET_POINTS_MULTIPLIER = 3f;

    private User mUser;

    // Main user views
    private TextView mDisplayNameView;
    private TextView mStatusView;
    private TextView mPointsView;
    private ImageView mStatusImageView;
    private TextView mUpvoteCountView;
    private TextView mDownvoteCountView;
    private TextView mCommentCountView;
    private TextView mPostCountView;
    private ConstraintLayout mUpvoteCountLayout;
    private ConstraintLayout mDownvoteCountLayout;
    private ConstraintLayout mCommentCountLayout;
    private ConstraintLayout mPostCountLayout;

    // Progress views
    private ViewGroup mProgressGroup;
    private TextView mProgressRequirementView;
    private ProgressBar mProgressBar;

    // Themes views
    private ViewGroup mThemesGroup;
    private TextView mTheme1View;
    private TextView mTheme2View;
    private TextView mTheme3View;

    // View Model
    private UserViewModel mUserViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up views
        mDisplayNameView = findViewById(R.id.user_displayNameView);
        mStatusView = findViewById(R.id.user_statusTextView);
        mPointsView = findViewById(R.id.user_pointsTextView);
        mStatusImageView = findViewById(R.id.user_statusImageView);

        mUpvoteCountView = findViewById(R.id.user_upvoteCountView);
        mDownvoteCountView = findViewById(R.id.user_downvoteCountView);
        mCommentCountView = findViewById(R.id.user_commentCountView);
        mPostCountView = findViewById(R.id.user_postCountView);

        mUpvoteCountLayout = findViewById(R.id.user_upvoteViewLayout);
        mDownvoteCountLayout = findViewById(R.id.user_downvoteViewLayout);
        mCommentCountLayout = findViewById(R.id.user_commentViewLayout);
        mPostCountLayout = findViewById(R.id.user_postViewLayout);

        mProgressGroup = findViewById(R.id.user_progressBarLayout);
        mProgressRequirementView = findViewById(R.id.user_pointsToNextLevel);
        mProgressBar = findViewById(R.id.user_progressBar);
        mThemesGroup = findViewById(R.id.user_themesLayout);
        mTheme1View = findViewById(R.id.user_themes1);
        mTheme2View = findViewById(R.id.user_themes2);
        mTheme3View = findViewById(R.id.user_themes3);

        // Set up user view model and get user data
        setupUser();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void openItemListActivityFor(UserViewModel.UserAction type) {
        Intent intent = new Intent(this, ItemListActivity.class);
        switch (type) {
            case UPVOTE:
                intent.putExtra("userAction", "upvote");
                break;
            case DOWNVOTE:
                intent.putExtra("userAction", "downvote");
                break;
            case COMMENT:
                intent.putExtra("userAction", "comment");
                break;
            case POST:
                intent.putExtra("userAction", "post");
                break;
        }
        startActivity(intent);
    }

    private void setupUser() {
        Log.d(TAG, "setupUser");

        UserViewModelFactory factory = InjectorUtils.provideUserViewModelFactory(this, getApplicationContext());
        mUserViewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);
        mUserViewModel.getUser(mUid).observe(this, user -> {
            Log.d(TAG, "user: " + user);
            if (user != null) {
                mUser = user;
                mDisplayNameView.setText(mUser.getDisplayName());
                mPointsView.setText(mUser.getPoints() + " Pts");
                if (mUser.getStatus() == 0) {
                    mStatusImageView.setBackground(getDrawable(R.drawable.user_acorn));
                    mStatusView.setText(LEVEL_0);
                } else if (mUser.getStatus() == 1) {
                    mStatusImageView.setBackground(getDrawable(R.drawable.user_sprout));
                    mStatusView.setText(LEVEL_1);
                } else if (mUser.getStatus() == 2) {
                    mStatusImageView.setBackground(getDrawable(R.drawable.user_sapling));
                    mStatusView.setText(LEVEL_2);
                } else if (mUser.getStatus() >= 3) {
                    mStatusImageView.setBackground(getDrawable(R.drawable.user_oak));
                    mStatusView.setText(LEVEL_3);
                }

                mUpvoteCountView.setText(String.valueOf(mUser.getUpvotedItemsCount()));
                mDownvoteCountView.setText(String.valueOf(mUser.getDownvotedItemsCount()));
                mCommentCountView.setText(String.valueOf(mUser.getCommentedItemsCount()));
                mPostCountView.setText(String.valueOf(mUser.getCreatedPostsCount()));

                mUpvoteCountLayout.setOnClickListener(view -> {
                    openItemListActivityFor(UserViewModel.UserAction.UPVOTE);
                });
                mDownvoteCountLayout.setOnClickListener(view -> {
                    openItemListActivityFor(UserViewModel.UserAction.DOWNVOTE);
                });
                mCommentCountLayout.setOnClickListener(view -> {
                    openItemListActivityFor(UserViewModel.UserAction.COMMENT);
                });
                mPostCountLayout.setOnClickListener(view -> {
                    openItemListActivityFor(UserViewModel.UserAction.POST);
                });

                double x = mUser.getPoints();
                double y = mUser.getTargetPoints();
                double z = mUser.getStatus() == 0 ? 0 : mUser.getTargetPoints() / TARGET_POINTS_MULTIPLIER;
                mProgressRequirementView.setText(String.valueOf((int) (y - x)));
                int progressLevel = (int) Math.max(Math.floor((x - z) / (y - z) * 100D), 0);

                mProgressBar.setProgress(progressLevel);

                if (mUser.openedThemes.size() < 1) {
                    mThemesGroup.setVisibility(View.GONE);
                } else {
                    LinkedHashMap<String, Integer> sortedOpenedThemes = mUser.openedThemes.entrySet().stream()
                            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
                    List<String> themes = new ArrayList<>(sortedOpenedThemes.keySet());
                    Log.d(TAG, "premiumStatus: " + mUser.premiumStatus.toString());

                    if (themes.size() < 2) {
                        mTheme2View.setVisibility(View.GONE);
                        mTheme3View.setVisibility(View.GONE);
                        mTheme1View.setText(themes.get(0));
                    } else if (themes.size() < 3) {
                        mTheme3View.setVisibility(View.GONE);
                        mTheme1View.setText(themes.get(0));
                        mTheme2View.setText(themes.get(1));
                    } else {
                        mTheme1View.setText(themes.get(0));
                        mTheme2View.setText(themes.get(1));
                        mTheme3View.setText(themes.get(2));
                    }
                }
            }
        });
    }
}
