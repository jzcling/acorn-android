package acorn.com.acorn_app.ui.activities;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.User;

import static acorn.com.acorn_app.data.NetworkDataSource.USER_REF;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_0;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_1;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_2;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_3;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class UserActivity extends AppCompatActivity {
    private static final String TAG = "UserActivity";
    private static final int TARGET_POINTS_MULTIPLIER = 3;

    private static User mUser;

    // Firebase
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;
    private static DatabaseReference mUserRef;

    // Main user views
    private TextView mDisplayNameView;
    private TextView mStatusView;
    private ImageView mStatusImageView;
    private TextView mUpvoteCountView;
    private TextView mDownvoteCountView;
    private TextView mCommentCountView;

    // Progress views
    private ViewGroup mProgressGroup;
    private TextView mProgressRequirementView;
    private ProgressBar mProgressBar;

    // Categories views
    private ViewGroup mCategoriesGroup;
    private TextView mCategory1View;
    private TextView mCategery2View;
    private TextView mCategory3View;

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
        mStatusImageView = findViewById(R.id.user_statusImageView);
        mUpvoteCountView = findViewById(R.id.user_upvoteCountView);
        mDownvoteCountView = findViewById(R.id.user_downvoteCountView);
        mCommentCountView = findViewById(R.id.user_commentCountView);
        mProgressGroup = findViewById(R.id.user_progressBarLayout);
        mProgressRequirementView = findViewById(R.id.user_pointsToNextLevel);
        mProgressBar = findViewById(R.id.user_progressBar);
        mCategoriesGroup = findViewById(R.id.user_categoriesLayout);
        mCategory1View = findViewById(R.id.user_categories1);
        mCategery2View = findViewById(R.id.user_categories2);
        mCategory3View = findViewById(R.id.user_categories3);

        // Get user data
        mUserRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mUser = dataSnapshot.getValue(User.class);
                mDisplayNameView.setText(mUser.getDisplayName());
                switch (mUser.getStatus()) {
                    case 0:
                        mStatusImageView.setBackground(getDrawable(R.drawable.user_acorn));
                        mStatusView.setText(LEVEL_0);
                        break;
                    case 1:
                        mStatusImageView.setBackground(getDrawable(R.drawable.user_sprout));
                        mStatusView.setText(LEVEL_1);
                        break;
                    case 2:
                        mStatusImageView.setBackground(getDrawable(R.drawable.user_sapling));
                        mStatusView.setText(LEVEL_2);
                        break;
                    case 3:
                        mStatusImageView.setBackground(getDrawable(R.drawable.user_oak));
                        mStatusView.setText(LEVEL_3);
                        break;
                }
                mUpvoteCountView.setText(String.valueOf(mUser.getUpvotedItemsCount()));
                mDownvoteCountView.setText(String.valueOf(mUser.getDownvotedItemsCount()));
                mCommentCountView.setText(String.valueOf(mUser.getCommentedItemsCount()));

                double x = mUser.getPoints();
                double y = mUser.getTargetPoints();
                double z = mUser.getStatus() == 0 ? 0 : mUser.getTargetPoints()/TARGET_POINTS_MULTIPLIER;
                mProgressRequirementView.setText(String.valueOf((int) (y - x)));
                int progressLevel = (int) Math.max(Math.floor((x - z) / (y - z) * 100D), 0);
                Log.d(TAG, "x: " + x + ", y: " + y + ", z: " + z + ", progressLevel: " + progressLevel);
                mProgressBar.setProgress(progressLevel);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                createToast(UserActivity.this, databaseError.toString(), Toast.LENGTH_SHORT);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
