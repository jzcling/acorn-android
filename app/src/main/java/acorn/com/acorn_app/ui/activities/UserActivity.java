package acorn.com.acorn_app.ui.activities;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.User;

import static acorn.com.acorn_app.data.NetworkDataSource.USER_REF;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_0;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_1;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_2;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_3;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static java.util.Map.Entry.comparingByValue;

public class UserActivity extends AppCompatActivity {
    private static final String TAG = "UserActivity";
    private static final int TARGET_POINTS_MULTIPLIER = 3;

    private User mUser;

    // Firebase
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;
    private DatabaseReference mUserRef;
    private ValueEventListener mUserRefListener;

    // Main user views
    private TextView mDisplayNameView;
    private TextView mStatusView;
    private TextView mPointsView;
    private ImageView mStatusImageView;
    private TextView mUpvoteCountView;
    private TextView mDownvoteCountView;
    private TextView mCommentCountView;
    private TextView mPostCountView;

    // Progress views
    private ViewGroup mProgressGroup;
    private TextView mProgressRequirementView;
    private ProgressBar mProgressBar;

    // Themes views
    private ViewGroup mThemesGroup;
    private TextView mTheme1View;
    private TextView mTheme2View;
    private TextView mTheme3View;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up Firebase Database
        if (mDatabase == null) mDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mDatabase.getReference();
        mUserRef = mDatabaseReference.child(USER_REF).child(mUid);

        // Set up views
        mDisplayNameView = findViewById(R.id.user_displayNameView);
        mStatusView = findViewById(R.id.user_statusTextView);
        mPointsView = findViewById(R.id.user_pointsTextView);
        mStatusImageView = findViewById(R.id.user_statusImageView);
        mUpvoteCountView = findViewById(R.id.user_upvoteCountView);
        mDownvoteCountView = findViewById(R.id.user_downvoteCountView);
        mCommentCountView = findViewById(R.id.user_commentCountView);
        mPostCountView = findViewById(R.id.user_postCountView);
        mProgressGroup = findViewById(R.id.user_progressBarLayout);
        mProgressRequirementView = findViewById(R.id.user_pointsToNextLevel);
        mProgressBar = findViewById(R.id.user_progressBar);
        mThemesGroup = findViewById(R.id.user_themesLayout);
        mTheme1View = findViewById(R.id.user_themes1);
        mTheme2View = findViewById(R.id.user_themes2);
        mTheme3View = findViewById(R.id.user_themes3);

        // Get user data
        mUserRefListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mUser = dataSnapshot.getValue(User.class);
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

                Log.d(TAG, "name: " + mUser.getDisplayName());

                mUpvoteCountView.setText(String.valueOf(mUser.getUpvotedItemsCount()));
                mDownvoteCountView.setText(String.valueOf(mUser.getDownvotedItemsCount()));
                mCommentCountView.setText(String.valueOf(mUser.getCommentedItemsCount()));
                mPostCountView.setText(String.valueOf(mUser.getCreatedPostsCount()));

                double x = mUser.getPoints();
                double y = mUser.getTargetPoints();
                double z = mUser.getStatus() == 0 ? 0 : mUser.getTargetPoints()/TARGET_POINTS_MULTIPLIER;
                mProgressRequirementView.setText(String.valueOf((int) (y - x)));
                int progressLevel = (int) Math.max(Math.floor((x - z) / (y - z) * 100D), 0);

                mProgressBar.setProgress(progressLevel);

                if (mUser.openedThemes.size() < 1) {
                    mThemesGroup.setVisibility(View.GONE);
                } else {
                    Map<String, Integer> sortedOpenedThemes = mUser.openedThemes.entrySet().stream()
                            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
                    List<String> themes = new ArrayList<>(sortedOpenedThemes.keySet());
                    Log.d(TAG, "premiumStatus: " + mUser.premiumStatus.toString());
//                    Log.d(TAG, sortedOpenedThemes.toString());
                    
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

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                createToast(UserActivity.this, databaseError.toString(), Toast.LENGTH_SHORT);
            }
        };
        mUserRef.addValueEventListener(mUserRefListener);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUserRef.removeEventListener(mUserRefListener);
    }
}
