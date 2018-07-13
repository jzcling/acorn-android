package acorn.com.acorn_app.ui.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.Comment;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.ui.adapters.CommentAdapter;
import acorn.com.acorn_app.ui.adapters.CommentViewHolder;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;
import acorn.com.acorn_app.utils.OnSwipeListener;
import acorn.com.acorn_app.utils.UiUtils;

import static acorn.com.acorn_app.data.NetworkDataSource.ARTICLE_REF;
import static acorn.com.acorn_app.data.NetworkDataSource.COMMENT_REF;
import static acorn.com.acorn_app.data.NetworkDataSource.NOTIFICATION_TOKENS;
import static acorn.com.acorn_app.data.NetworkDataSource.USER_REF;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_0;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_1;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_2;
import static acorn.com.acorn_app.ui.activities.AcornActivity.LEVEL_3;
import static acorn.com.acorn_app.ui.activities.AcornActivity.isUserAuthenticated;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserToken;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUsername;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static acorn.com.acorn_app.utils.UiUtils.locateView;
import static android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class CommentActivity extends AppCompatActivity implements View.OnTouchListener {
    private static final String TAG = "CommentActivity";

    private static final AppExecutors mExecutors = AppExecutors.getInstance();

    private static final int RC_GALLERY = 1;
    private static final int RC_CAMERA = 2;
    private static final int RC_PREVIEW = 3;
    public static final int DEFAULT_COMMENT_LENGTH_LIMIT = 1000;
    private static final String COMMENT_SENT_EVENT = "comment_sent";

    private static final String LLM_STATE = "llmState";

    private static final int TARGET_POINTS_MULTIPLIER = 3;

    private DatabaseReference mArticleRef;
    private ValueEventListener mArticleListener;
    private ChildEventListener mFollowListener;

    private Button mSendButton;
    private Menu mOptionsMenu;
    private RecyclerView mCommentRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private FirebaseRecyclerAdapter<Comment, CommentViewHolder> mAdapter;

    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;

    private ViewGroup mArticleView;
    private TextView mArticleTitleView;
    private TextView mArticleSourceView;
    private TextView mArticlePubDateView;
    private TextView mArticleVoteCountView;
    private TextView mArticleCommentCountView;
    private ImageView mArticleImageView;

    private static String mArticleId;
    private static Article mArticle;

    public static List<Integer> mSearchPos;
    public static String mSearchText;
    private static int mCurrentIndex;

    private EditText mCommentEditText;
    private String mCurrentImagePath;
    private Uri mImageUri;

    private TextView mPreviewTitleView;
    private ImageView mPreviewImageView;
    private TextView mPreviewSourceView;
    private CardView mPreviewCardView;
    private ImageButton mPreviewCancelView;
    private String mPreviewLink;
    private String mPreviewTitle;
    private String mPreviewSource;
    private String mPreviewImageUrl;
    private boolean addPreviewPending;
    private final Runnable addPreview = () -> {
        addPreview();
        addPreviewPending = false;
    };
    private Handler handler = new Handler();

    private Animation slideDownAnim;
    private Animation slideUpAnim;
    private GestureDetector gestureDetector;

    public static String mCommentOpenObjectID;

//    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mArticleId = getIntent().getStringExtra("id");
        mCommentOpenObjectID = mArticleId;

        // Set up Firebase Database
        if (mDatabase == null) mDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mDatabase.getReference();
        mArticleRef = mDatabaseReference.child(ARTICLE_REF).child(mArticleId);
        DatabaseReference mCommentRef = mDatabaseReference.child(COMMENT_REF).child(mArticleId);

        // Set up article views
        mArticleView = (ViewGroup) findViewById(R.id.comment_item_card);
        mArticleTitleView = (TextView) findViewById(R.id.card_title);
        mArticleSourceView = (TextView) findViewById(R.id.card_contributor);
        mArticlePubDateView = (TextView) findViewById(R.id.card_date);
        mArticleVoteCountView = (TextView) findViewById(R.id.card_vote_count);
        mArticleCommentCountView = (TextView) findViewById(R.id.card_comment_count);
        mArticleImageView = (ImageView) findViewById(R.id.card_image);

        // Set up preview views
        mPreviewCardView = (CardView) findViewById(R.id.preview_article_card);
        mPreviewTitleView = (TextView) findViewById(R.id.preview_article_title);
        mPreviewSourceView = (TextView) findViewById(R.id.preview_article_contributor);
        mPreviewImageView = (ImageView) findViewById(R.id.preview_article_image);
        mPreviewCancelView = (ImageButton) findViewById(R.id.preview_article_cancel);

        mPreviewCancelView.setOnClickListener(v -> clearPreview());

        // Initiate listener on article data
        mArticleListener = mArticleRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mArticle = dataSnapshot.getValue(Article.class);
                if (mArticle.getType().equals("article")) {
                    mArticleTitleView.setText(mArticle.getTitle());
                } else {
                    if (mArticle.getSource() != null && !mArticle.getSource().equals("")) {
                        mArticleTitleView.setText(mArticle.getTitle());
                    } else {
                        mArticleTitleView.setText(mArticle.getPostText());
                    }
                }
                if (mArticle.getSource() != null && !mArticle.getSource().equals("")) {
                    mArticleSourceView.setText(mArticle.getSource());
                } else {
                    mArticleSourceView.setText(mArticle.getPostAuthor());
                }
                mArticlePubDateView.setText(DateUtils.parseDate(mArticle.getPubDate()));
                mArticleVoteCountView.setText(String.valueOf(mArticle.getVoteCount()));
                mArticleCommentCountView.setText(String.valueOf(mArticle.getCommentCount()));
                if (mArticle.getImageUrl() != null && !mArticle.getImageUrl().equals("")) {
                    Object imageUrl;
                    if (mArticle.getImageUrl().startsWith("gs://")) {
                        imageUrl = FirebaseStorage.getInstance()
                                .getReferenceFromUrl(mArticle.getImageUrl());
                    } else {
                        imageUrl = mArticle.getImageUrl();
                    }
                    Glide.with(CommentActivity.this)
                            .load(imageUrl)
                            .into(mArticleImageView);
                } else if (mArticle.getPostImageUrl() != null && !mArticle.getPostImageUrl().equals("")) {
                    StorageReference imageUri = FirebaseStorage.getInstance()
                            .getReferenceFromUrl(mArticle.getPostImageUrl());
                    Glide.with(CommentActivity.this)
                            .load(imageUri)
                            .into(mArticleImageView);
                } else {
                    mArticleImageView.setVisibility(View.GONE);
                }

                // Set up on click interface for article
                if (mArticle.getLink() != null && !mArticle.getLink().equals("")) {
                    mArticleTitleView.setOnClickListener(v ->
                        mExecutors.mainThread().execute(() -> startWebViewActivity())
                    );
                    mArticleImageView.setOnClickListener(v ->
                            mExecutors.mainThread().execute(() -> startWebViewActivity())
                    );
                } else {
                    mArticleTitleView.setOnClickListener(v -> {
                        StorageReference imageUri;
                        if (mArticle.getPostImageUrl() != null) {
                            imageUri = FirebaseStorage.getInstance()
                                    .getReferenceFromUrl(mArticle.getPostImageUrl());
                        } else {
                            imageUri = FirebaseStorage.getInstance()
                                    .getReferenceFromUrl(mArticle.getImageUrl());
                        }
                        mExecutors.mainThread().execute(() -> {
                            Dialog imagePopUp =
                                    new UiUtils().configureImagePopUp(CommentActivity.this, imageUri);
                            imagePopUp.show();
                        });
                    });
                    mArticleImageView.setOnClickListener(v -> {
                        StorageReference imageUri;
                        if (mArticle.getPostImageUrl() != null) {
                            imageUri = FirebaseStorage.getInstance()
                                    .getReferenceFromUrl(mArticle.getPostImageUrl());
                        } else {
                            imageUri = FirebaseStorage.getInstance()
                                    .getReferenceFromUrl(mArticle.getImageUrl());
                        }
                        mExecutors.mainThread().execute(() -> {
                            Dialog imagePopUp =
                                    new UiUtils().configureImagePopUp(CommentActivity.this, imageUri);
                            imagePopUp.show();
                        });
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                createToast(CommentActivity.this, databaseError.toString(), Toast.LENGTH_SHORT);
            }
        });

        // Set up recycler view
        mCommentRecyclerView = (RecyclerView) findViewById(R.id.comment_recycler_view);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setStackFromEnd(true);
        mCommentRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = (CommentAdapter) new CommentAdapter(this, getOptions(mCommentRef));
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                setScrollPosition(savedInstanceState, positionStart);
            }
        });
        mCommentRecyclerView.setAdapter(mAdapter);

        FloatingActionButton fab = findViewById(R.id.comment_fab);
        fab.setOnClickListener(v -> {
            mCommentRecyclerView.scrollToPosition(mAdapter.getItemCount()-1);
            fab.hide();
        });
        mCommentRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    fab.show();
                } else {
                    fab.hide();
                }
            }
        });

        // Set up comment posting mechanism
        mCommentEditText = (EditText) findViewById(R.id.comment_editText);
        mCommentEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(DEFAULT_COMMENT_LENGTH_LIMIT)
        });
        mCommentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {
                if (addPreviewPending) {
                    handler.removeCallbacks(addPreview);
                    Log.d(TAG, "callback removed");
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                if (s.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                Pattern urlPattern = Pattern.compile("((?:https?://|www\\.)[a-zA-Z0-9+&@#/%=~_|$?!:,.-]*\\b)");
                Matcher m = urlPattern.matcher(s);
                if (m.find()) {
                    clearPreview();
                    mPreviewLink = m.group(1);
                    Log.d(TAG, "callback added");
                    handler.postDelayed(addPreview, 500);
                    addPreviewPending = true;
                }
            }
        });

        ImageView mAddCommentImageView = (ImageView) findViewById(R.id.comment_addImageView);
        mAddCommentImageView.setOnClickListener(v -> {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.layout_popup_add_image, null);
            PopupWindow popupWindow = new PopupWindow(popupView, WRAP_CONTENT, WRAP_CONTENT);
            popupWindow.setElevation(10);
            popupWindow.setBackgroundDrawable(new ColorDrawable());
            popupWindow.setOutsideTouchable(true);

            Log.d(TAG, "height: " + v.getHeight());
            Rect addImageLoc = locateView(v);
            popupWindow.showAsDropDown(v, -addImageLoc.left, -(3*v.getHeight()+20));

            ImageView mGalleryImageView = (ImageView) popupView.findViewById(R.id.popup_gallery);
            ImageView mCameraImageView = (ImageView) popupView.findViewById(R.id.popup_camera);

            mGalleryImageView.setOnClickListener(view -> {
                Intent galleryIntent = new Intent();
                galleryIntent.setType("image/*");
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(galleryIntent, "Select Image"), RC_GALLERY);
            });

            mCameraImageView.setOnClickListener(view -> {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (photoFile != null) {
                        Uri photoURI = FileProvider.getUriForFile(this,
                                "com.example.android.fileprovider",
                                photoFile);
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        startActivityForResult(cameraIntent, RC_CAMERA);
                    }
                }
            });
        });

        mSendButton = (Button) findViewById(R.id.comment_sendButton);
        mSendButton.setOnClickListener(view -> {
            String commentText = mCommentEditText.getText().toString();
            sendComment(mCommentRef, commentText, null);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_bar_comment, menu);

        // Set up follow/unfollow options
        MenuItem followOption = (MenuItem) menu.findItem(R.id.action_follow);
        MenuItem unfollowOption = (MenuItem) menu.findItem(R.id.action_unfollow);
        if (mArticleRef != null) {
            mFollowListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    if (dataSnapshot.getValue() != null) {
                        if (dataSnapshot.getKey().equals(mUid)) {
                            followOption.setVisible(false);
                            unfollowOption.setVisible(true);
//                            Log.d(TAG, dataSnapshot.getKey() + ": " + dataSnapshot.getValue(String.class));
                        }
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        if (dataSnapshot.getKey().equals(mUid)) {
                            followOption.setVisible(true);
                            unfollowOption.setVisible(false);
//                            Log.d(TAG, dataSnapshot.getKey() + ": " + dataSnapshot.getValue(String.class));
                        }
                    }
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            };
            mArticleRef.child(NOTIFICATION_TOKENS).addChildEventListener(mFollowListener);
        }

        // Set up search
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        EditText searchEditText = (EditText) searchView.findViewById(R.id.search_src_text);
        MenuItem searchChevronUp = mOptionsMenu.findItem(R.id.action_search_up);
        MenuItem searchChevronDown = mOptionsMenu.findItem(R.id.action_search_down);
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                mSearchText = searchEditText.getText().toString();
                mSearchPos = ((CommentAdapter) mAdapter).findText(mSearchText);
                mCurrentIndex = 0;
                if (mSearchPos.size() > 0) {
                    mCommentRecyclerView.scrollToPosition(
                            Math.min(mAdapter.getItemCount() - 1, mSearchPos.get(mCurrentIndex) + 5));
                    for (Integer position : mSearchPos) {
                        CommentViewHolder vh = (CommentViewHolder) mCommentRecyclerView
                                .findViewHolderForAdapterPosition(position);
                        if (vh != null) {
                            String originalText = vh.commentTextView.getText().toString();
                            SpannableString highlightedText = new SpannableString(originalText);
                            Pattern p = Pattern.compile(mSearchText, Pattern.CASE_INSENSITIVE);
                            Matcher m = p.matcher(originalText);
                            while (m.find()) {
                                highlightedText.setSpan(new BackgroundColorSpan(
                                                getResources().getColor(R.color.search_comment_highlight)),
                                        m.start(), m.end(), SPAN_INCLUSIVE_INCLUSIVE);
                            }
                            vh.commentTextView.setText(highlightedText);
                        }
                    }

                    Log.d(TAG, "positions: " + mSearchPos);
                    View view = getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }

                    mAdapter.notifyDataSetChanged();

                    searchChevronUp.setVisible(true);
                    searchChevronDown.setVisible(true);
                } else {
                    createToast(this, "No results returned for " + mSearchText, Toast.LENGTH_SHORT);
                }
            }
            return false;
        });
        MenuItem searchItem = (MenuItem) mOptionsMenu.findItem(R.id.action_search);
        MenuItem articleChevron = mOptionsMenu.findItem(R.id.action_expand);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                if (mArticleView.getVisibility() == View.VISIBLE) {
                    mArticleView.startAnimation(slideUpAnim);
                    articleChevron.setIcon(getDrawable(R.drawable.chevron_down));
                }
                articleChevron.setVisible(false);

                mArticleRef.child(NOTIFICATION_TOKENS).removeEventListener(mFollowListener);
                followOption.setVisible(false);
                unfollowOption.setVisible(false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                articleChevron.setVisible(true);
                mArticleRef.child(NOTIFICATION_TOKENS).addChildEventListener(mFollowListener);

                searchChevronUp.setVisible(false);
                searchChevronDown.setVisible(false);

                if (mSearchPos.size() > 0) {
                    for (Integer position : mSearchPos) {
                        CommentViewHolder vh = (CommentViewHolder) mCommentRecyclerView
                                .findViewHolderForAdapterPosition(position);
                        if (vh != null) {
                            String originalText = vh.commentTextView.getText().toString();
                            vh.commentTextView.setText(originalText);
                        }
                    }
                    mAdapter.notifyDataSetChanged();
                }
                mSearchText = null;
                mSearchPos = null;
                mCurrentIndex = -1;
                return true;
            }
        });

        // Set up swipe effects for card
        setUpAnimations();
        setUpGestureDetector();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_expand:
                if (mArticleView.getVisibility() == View.VISIBLE) {
                    mArticleView.startAnimation(slideUpAnim);
                    item.setIcon(getDrawable(R.drawable.chevron_down));
                } else {
                    mArticleView.startAnimation(slideDownAnim);
                    item.setIcon(getDrawable(R.drawable.chevron_up));
                }
                return true;
            case R.id.action_search:
                return true;
            case R.id.action_follow:
                mArticleRef.child(NOTIFICATION_TOKENS).child(mUid).setValue(mUserToken);
                return true;
            case R.id.action_unfollow:
                mArticleRef.child(NOTIFICATION_TOKENS).child(mUid).removeValue();
                return true;
            case R.id.action_search_up:
                if (mCurrentIndex < mSearchPos.size() - 1) {
                    mCurrentIndex++;
                    mCommentRecyclerView.scrollToPosition(Math.max(0, mSearchPos.get(mCurrentIndex)-5));
                } else {
                    createToast(this, "You have reached the beginning!", 1000);
                }
                return true;
            case R.id.action_search_down:
                if (mCurrentIndex > 0) {
                    mCurrentIndex--;
                    mCommentRecyclerView.scrollToPosition(
                            Math.min(mAdapter.getItemCount() - 1, mSearchPos.get(mCurrentIndex) + 5));
                } else {
                    createToast(this, "You have reached the end!", 1000);
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

//    private void sendInvitation() {
//        Intent intent = new AppInviteInvitation.IntentBuilder(getString(R.string.invitation_title))
//                .setComment(getString(R.string.invitation_comment))
//                .setCallToActionText(getString(R.string.invitation_cta))
//                .build();
//        startActivityForResult(intent, REQUEST_INVITE);
//    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == RC_GALLERY) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    Uri imageUri = data.getData();
                    Intent previewImageIntent = new Intent(this, PreviewImageActivity.class);
                    previewImageIntent.putExtra("imageUri", imageUri.toString());
                    startActivityForResult(previewImageIntent, RC_PREVIEW);
                }
            }
        } else if (requestCode == RC_CAMERA) {
            if (resultCode == RESULT_OK) {
                Intent previewImageIntent = new Intent(this, PreviewImageActivity.class);
                previewImageIntent.putExtra("imageUri", mImageUri.toString());
                startActivityForResult(previewImageIntent, RC_PREVIEW);
            }
        } else if (requestCode == RC_PREVIEW) {
            if (resultCode == RESULT_OK) {
                Uri imageUri = Uri.parse(data.getStringExtra("imageUri"));
                String commentText = data.getStringExtra("caption");
                DatabaseReference mCommentRef = mDatabaseReference.child(COMMENT_REF).child(mArticleId);
                sendComment(mCommentRef, commentText, imageUri);
            }
        }
        //else if (requestCode == REQUEST_INVITE) {
//            if (resultCode == RESULT_OK) {
//                // Use Firebase Measurement to log that invitation was sent.
//                Bundle payload = new Bundle();
//                payload.putString(FirebaseAnalytics.Param.VALUE, "inv_sent");
//
//                // Check how many invitations were sent and log.
//                String[] ids = AppInviteInvitation.getInvitationIds(resultCode, data);
//                Log.d(TAG, "Invitations sent: " + ids.length);
//            } else {
//                // Use Firebase Measurement to log that invitation was not sent
//                Bundle payload = new Bundle();
//                payload.putString(FirebaseAnalytics.Param.VALUE, "inv_not_sent");
//                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, payload);
//
//                // Sending failed or it was canceled, show failure comment to the user
//                Log.d(TAG, "Failed to send invitation.");
//            }
//        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mAdapter.stopListening();

    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mArticleRef.removeEventListener(mArticleListener);
        if (mFollowListener != null)
            mArticleRef.child(NOTIFICATION_TOKENS).removeEventListener(mFollowListener);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCommentOpenObjectID = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(LLM_STATE, mLinearLayoutManager.findLastCompletelyVisibleItemPosition());
        Log.d(TAG, "onSaveInstanceState: llm state saved");
    }

    private FirebaseRecyclerOptions<Comment> getOptions(DatabaseReference ref) {
        SnapshotParser<Comment> parser = dataSnapshot -> {
            Comment comment = dataSnapshot.getValue(Comment.class);
            if (comment != null) {
                comment.setCommentId(dataSnapshot.getKey());
            }
            return comment;
        };
        FirebaseRecyclerOptions<Comment> options =
                new FirebaseRecyclerOptions.Builder<Comment>()
                        .setQuery(ref, parser)
                        .build();
        return options;
    }

    private void startWebViewActivity() {
//        if (mArticle.getType().equals("article")) {
            Intent intent = new Intent(this, WebViewActivity.class);
            intent.putExtra("id", mArticle.getObjectID());
            startActivity(intent);
//        } else {
//            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mArticle.getLink()));
//            startActivity(intent);
//        }
    }

    private void updateCommentCount() {
        mArticleRef.child("commentCount").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer commentCount = mutableData.getValue(Integer.class);
                if (commentCount == null) {
                    Log.d(TAG, "updateCommentCount: Could not find comment count");
                    return Transaction.success(mutableData);
                }
                mArticleRef.child("commentCount").setValue(commentCount + 1);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                Log.d(TAG, "updateCommentCount: " + databaseError);
                if (databaseError != null) {
                    updateCommentCount();
                }
            }
        });
    }

    private void updateUserCommentCount() {
        mArticleRef.child("commenters/" + mUid).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer userCommentCount = mutableData.getValue(Integer.class);
                Log.d(TAG, "userCommentCount: " + userCommentCount);
                if (userCommentCount == null) {
                    Log.d(TAG, "updateUserCommentCount: Could not find user comment data");
                    mArticleRef.child("commenters/" + mUid).setValue(1);
                    return Transaction.success(mutableData);
                }
                mArticleRef.child("commenters/" + mUid).setValue(userCommentCount + 1);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                Log.d(TAG, "updateUserCommentCount: " + databaseError);
            }
        });
    }

    private void updateNotificationTokens() {
        mArticleRef.child("notificationTokens/" + mUid).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                String notificationToken = mutableData.getValue(String.class);
                Log.d(TAG, "notificationToken: " + notificationToken);
                if (notificationToken == null) {
                    Log.d(TAG, "updateNotificationTokens: Could not find notification tokens");
                    mArticleRef.child("notificationTokens/" + mUid).setValue(mUserToken);
                    return Transaction.success(mutableData);
                }
                mArticleRef.child("notificationTokens/" + mUid).setValue(mUserToken);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                Log.d(TAG, "updateNotificationTokens: " + databaseError);
            }
        });
    }

    private void updateUserData(Runnable onComplete) {
        DatabaseReference userRef = mDatabaseReference.child(USER_REF).child(mUid);
        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {
                    Log.d(TAG, "updateUser: Could not find user");
                    return Transaction.success(mutableData);
                }

                int currentCommentCount = user.getCommentedItemsCount();
                int currentArticleCommentCount = user.commentedItems.containsKey(mArticleId) ? user.commentedItems.get(mArticleId) : 0;

                user.setCommentedItemsCount(currentCommentCount+1);
                user.commentedItems.put(mArticleId, currentArticleCommentCount + 1);
                user.setPoints(user.getPoints()+1);
                if (user.getTargetPoints() - user.getPoints() < 1) {
                    user.setTargetPoints(user.getTargetPoints() * TARGET_POINTS_MULTIPLIER);
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
                    mExecutors.mainThread().execute(()->createToast(CommentActivity.this,
                            "Congratulations! You have grown into a " + newStatus, Toast.LENGTH_SHORT));
                }
                userRef.setValue(user);

                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                Log.d(TAG, "updateUserData: " + databaseError);
                onComplete.run();
            }
        });
    }

    private void pushUrlComment(DatabaseReference ref, Long pubDate) {
        if (mPreviewLink != null) {
            mExecutors.networkIO().execute(() -> {
                String key = ref.push().getKey();
                Comment urlComment = new Comment(mUid, mUsername, mPreviewTitle, mPreviewImageUrl,
                        null, pubDate, true, mPreviewSource, mPreviewLink);
                ref.child(key).setValue(urlComment);
                mExecutors.mainThread().execute(this::clearPreview);
            });
        }
    }

    private void sendComment(DatabaseReference ref, String commentText, Uri imageUri) {
        if (!isUserAuthenticated) {
            createToast(this, "Please verify your email to comment", Toast.LENGTH_SHORT);
            return;
        }
        if (commentText.length() > 1000) {
            createToast(this, "Your comment should be less than 1000 characters", Toast.LENGTH_SHORT);
            return;
        }

        Long pubDate = -1L * (new Date().getTime());
        String key = ref.push().getKey();
        if (imageUri == null) {
            Comment tempComment = new Comment(mUid, mUsername,
                    commentText, null, null,
                    pubDate, false, null, null);
            ref.child(key).setValue(tempComment);
            updateCommentCount();
            updateUserCommentCount();
            updateNotificationTokens();
            updateUserData(()->pushUrlComment(ref, pubDate));
        } else {
            Uri tempUri = imageUri;
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), tempUri);
                File dir = getDir("images", Context.MODE_PRIVATE);
                File storedImage = new File(dir, key + ".jpg");
                FileOutputStream outStream = new FileOutputStream(storedImage);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outStream);
                tempUri = Uri.fromFile(storedImage);
                String imagePath = tempUri.toString();

                StorageReference storageReference = FirebaseStorage.getInstance()
                        .getReference(mUid)
                        .child(tempUri.getLastPathSegment());

                storageReference.putFile(tempUri).addOnCompleteListener(CommentActivity.this,
                        task -> {
                            if (task.isSuccessful()) {
                                String imageUrl = storageReference.getRoot().toString() + storageReference.getPath();
                                Comment comment =
                                        new Comment(mUid, mUsername, commentText,
                                                imageUrl, imagePath, pubDate,
                                                false, null, null);
                                ref.child(key).setValue(comment);
                                updateCommentCount();
                                updateUserCommentCount();
                                updateNotificationTokens();
                                updateUserData(()->pushUrlComment(ref, pubDate));
                            } else {
                                Log.w(TAG, "Image comment upload was not successful.",
                                        task.getException());
                            }
                        });
                outStream.flush();
                outStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mCommentEditText.setText("");
        mCommentRecyclerView.scrollToPosition(mAdapter.getItemCount()-1);
        mPreviewCardView.setVisibility(View.GONE);
//                mFirebaseAnalytics.logEvent(COMMENT_SENT_EVENT, null);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private void setUpAnimations() {
        // Set up animations for card
        slideDownAnim = AnimationUtils.loadAnimation(this, R.anim.slide_down);
            slideDownAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) { mArticleView.setVisibility(View.VISIBLE); }

            @Override
            public void onAnimationEnd(Animation animation) { mArticleTitleView.invalidate(); }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        slideUpAnim = AnimationUtils.loadAnimation(this, R.anim.slide_up);
            slideUpAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) { mArticleView.setVisibility(View.GONE); }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

    private void setUpGestureDetector() {
        // Set up swipe gesture listener for card
        gestureDetector = new GestureDetector(this, new OnSwipeListener() {
            @Override
            public boolean onSwipe(Direction direction) {
                if (direction == Direction.up) {
                    mArticleView.startAnimation(slideUpAnim);
                    mOptionsMenu.findItem(R.id.action_expand)
                            .setIcon(getDrawable(R.drawable.chevron_down));
                }
                return true;
            }
        });
        mArticleView.setOnTouchListener(this);
    }

    private void setScrollPosition(Bundle savedState, int positionStart) {
        int commentCount = mAdapter.getItemCount();
        int lastVisiblePosition;
        if (savedState != null) {
            lastVisiblePosition = savedState.getInt(LLM_STATE) - 1;
        } else {
            lastVisiblePosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
        }

        if (lastVisiblePosition == -1 ||
                (positionStart >= (commentCount - 1) && lastVisiblePosition == (positionStart - 1))) {
            mCommentRecyclerView.scrollToPosition(positionStart);
            Log.d(TAG, "scrolling to: " + positionStart);
        }
    }

    private void addPreview() {
        mExecutors.networkIO().execute(() -> {
            final String TITLE;
            final String SOURCE;
            final String IMAGE_URL;
            try {
                if (!mPreviewLink.startsWith("http://") && !mPreviewLink.startsWith("https://")) {
                    mPreviewLink = "https://" + mPreviewLink;
                }
                Document parsedHtml = Jsoup.connect(mPreviewLink).get();
                TITLE = parsedHtml.title();
                mPreviewTitle = TITLE;
                Elements source = parsedHtml.select("meta[property~=og:site_name]");
                SOURCE = source.size() > 0 ? source.first().attr("content") : null;
                mPreviewSource = SOURCE;
                Elements image = parsedHtml.select("meta[property~=og:image]");
                IMAGE_URL = image.size() > 0 ? image.first().attr("content") : null;
                mPreviewImageUrl = IMAGE_URL;
                Log.d(TAG, "urlTitle: " + TITLE + ", urlSource: " + SOURCE + ", mImageUrl: " + IMAGE_URL);

                mExecutors.mainThread().execute(() -> {
                    mPreviewTitleView.setText(TITLE);
                    if (SOURCE != null) {
                        mPreviewSourceView.setText(SOURCE);
                    } else {
                        mPreviewSourceView.setVisibility(View.GONE);
                    }
                    if (IMAGE_URL != null) {
                        Glide.with(CommentActivity.this)
                                .load(IMAGE_URL)
                                .into(mPreviewImageView);
                    } else {
                        mPreviewImageView.setVisibility(View.GONE);
                    }
                    mPreviewCardView.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void clearPreview() {
        mPreviewLink = null;
        mPreviewTitle = null;
        mPreviewSource = null;
        mPreviewImageUrl = null;
        mPreviewTitleView.setText(null);
        mPreviewSourceView.setText(null);
        mPreviewImageView.setImageBitmap(null);
        mPreviewTitleView.setVisibility(View.VISIBLE);
        mPreviewImageView.setVisibility(View.VISIBLE);
        mPreviewSourceView.setVisibility(View.VISIBLE);
        mPreviewCardView.setVisibility(View.GONE);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentImagePath = image.getAbsolutePath();
        Log.d(TAG, "path: " + mCurrentImagePath);
        mImageUri = FileProvider.getUriForFile(this,
                "com.example.android.fileprovider",
                image);
        galleryAddPic();
        return image;
    }

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(mCurrentImagePath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }
}
