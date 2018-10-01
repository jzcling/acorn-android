package acorn.com.acorn_app.ui.activities;

import android.app.AlertDialog;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.ui.viewModels.UserThemeViewModel;
import acorn.com.acorn_app.models.User;

import static acorn.com.acorn_app.ui.activities.AcornActivity.isFirstTimeLogin;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.utils.UiUtils.createToast;


public class ThemeSelectionActivity extends AppCompatActivity {
    private static final String TAG = "ThemeSelectionActivity";

    // All themes
    private static List<String> mThemeList;

    // Firebase
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;

    // User Theme
    private static ArrayList<String> mUserThemeList = new ArrayList<>();
    private ArrayList<String> mLastSavedState = new ArrayList<>();
    private UserThemeViewModel mUserThemeViewModel;

    // Recyclerview
    private RecyclerView mListView;
    private LinearLayoutManager mLayoutManager;
    private ThemeAdapter mAdapter;

    // Views
    private CheckBox mAllThemesCb;
    private Button mSaveButton;

    // Result
    private Intent result = null;
    private boolean isBackPressed = false;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_selection);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();

        // Set up views
        mAllThemesCb = (CheckBox) findViewById(R.id.theme_all_checkBox);
        mListView = (RecyclerView) findViewById(R.id.theme_recyclerView);
        mSaveButton = (Button) findViewById(R.id.theme_saveButton);

        // Set up theme list
        mThemeList = new ArrayList<>();
        String[] themeArray = getResources().getStringArray(R.array.theme_array);
        Collections.addAll(mThemeList, themeArray);

        // Set up recycler view
        mAdapter = new ThemeAdapter(mThemeList);
        mLayoutManager = new GridLayoutManager(this, 2);
        mListView.setAdapter(mAdapter);
        mListView.setLayoutManager(mLayoutManager);

        // Set up view model
        mUserThemeViewModel = ViewModelProviders
                .of(ThemeSelectionActivity.this).get(UserThemeViewModel.class);
        mUserThemeViewModel.getThemes().observe(ThemeSelectionActivity.this, list -> {

            mAdapter.notifyDataSetChanged();
        });

        // Get user theme data
        mUserThemeList = new ArrayList<>();
        mLastSavedState = new ArrayList<>();
        mLastSavedState.addAll(intent.getStringArrayListExtra("themePrefs"));
        if (mLastSavedState.size() > 0) {
            mUserThemeList.addAll(mLastSavedState);
        } else {

//            mUserThemeList.addAll(mThemeList);
        }

        if (mUserThemeList.containsAll(mThemeList)) {
            mAllThemesCb.setChecked(true);
        } else {
            mAllThemesCb.setChecked(false);
        }


        mUserThemeViewModel.setValue(mUserThemeList);

        // Set up all themes checkbox
        mAllThemesCb.setOnClickListener(v -> {
            if (!mAllThemesCb.isChecked()) {
                mUserThemeList.clear();
            } else {
                mUserThemeList = new ArrayList<>();
                mUserThemeList.addAll(mThemeList);
            }
            mUserThemeViewModel.setValue(mUserThemeList);
        });


        // Set up save button
        mSaveButton.setOnClickListener(v -> {
            mSaveButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveThemePrefs();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (isFirstTimeLogin) {
            if (mUserThemeList.size() < 1) {
                createToast(this, "Please select at least one theme", Toast.LENGTH_SHORT);
                return;
            }
        }
        isBackPressed = true;
        if (!(mUserThemeList.containsAll(mLastSavedState)
                && mLastSavedState.containsAll(mUserThemeList))) {
            openSaveAlertDialog();
            return;
        }
        finish();
    }

    @Override
    public void finish() {
        if (result == null) {
            setResult(RESULT_CANCELED, new Intent());
        }
        super.finish();
    }

    private void openSaveAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Would you like to save your theme subscriptions?")
                .setCancelable(true)
                .setPositiveButton("Yes", (dialog, which) -> saveThemePrefs())
                .setNegativeButton("No", (dialog, which) -> {
                    dialog.cancel();
                    finish();
                });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void saveThemePrefs() {
        if (mUserThemeList.size() < 1) {
            createToast(this, "Please select at least one theme", Toast.LENGTH_SHORT);
            return;
        }
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("user/" + mUid);
        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {

                    return Transaction.success(mutableData);
                }
                user.setSubscriptions(mUserThemeList);
                user.setSubscriptionsCount(mUserThemeList.size());
                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {

                if (databaseError == null) {
                    createToast(ThemeSelectionActivity.this,
                            "Theme subscriptions saved", Toast.LENGTH_SHORT);
                    mLastSavedState = new ArrayList<>();
                    mLastSavedState.addAll(mUserThemeList);
                    result = new Intent();
                    result.putStringArrayListExtra("themePrefs", mLastSavedState);
                    setResult(RESULT_OK, result);
                } else {
                    createToast(ThemeSelectionActivity.this,
                            "Failed to save theme subscriptions", Toast.LENGTH_SHORT);
                }
                if (isBackPressed) finish();
                if (isFirstTimeLogin) finish();
            }
        });
    }

    private class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder> {
        private final List<String> themeList = new ArrayList<>();

        public ThemeAdapter(List<String> themeList) {
            this.themeList.addAll(themeList);
        }

        @NonNull
        @Override
        public ThemeAdapter.ThemeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_theme, parent, false);
            return new ThemeViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ThemeAdapter.ThemeViewHolder holder, int position) {
            String theme = themeList.get(position);
            int[] grayTone = getResources().getIntArray(R.array.gray_tone);
            int[][] states = new int[][] {
                    new int[] {android.R.attr.state_checked},
                    new int[] {-android.R.attr.state_checked}
            };
            int[] colors = new int[] {
                    grayTone[position],
                    grayTone[position]
            };
            ColorStateList grayToneStateList = new ColorStateList(states, colors);
            holder.checkBox.setText(theme);
            holder.checkBox.setButtonTintList(grayToneStateList);

            if (mUserThemeList.contains(theme)) {
                holder.checkBox.setChecked(true);
            } else {
                holder.checkBox.setChecked(false);
            }

            // setOnCheckedChangeListener appears to bind later:
            // adapter theme moves on to several iterations after but this code chunk's theme does not
            // use buttonView text to alter user theme list instead
            holder.checkBox.setOnCheckedChangeListener(((buttonView, isChecked) -> {
                String tempTheme = buttonView.getText().toString();
                if (isChecked) {

                    if (!mUserThemeList.contains(tempTheme)) mUserThemeList.add(tempTheme);
                    if (mUserThemeList.containsAll(themeList)) mAllThemesCb.setChecked(true);
                } else {

                    if (mUserThemeList.contains(tempTheme)) {
                        mUserThemeList.remove(tempTheme);
                        mAllThemesCb.setChecked(false);
                    }
                }

            }));
        }

        @Override
        public int getItemCount() {
            return themeList.size();
        }

        public class ThemeViewHolder extends RecyclerView.ViewHolder {
            final CheckBox checkBox;
            public ThemeViewHolder(View v) {
                super(v);
                this.checkBox = v.findViewById(R.id.theme_checkBox);
            }
        }
    }
}
