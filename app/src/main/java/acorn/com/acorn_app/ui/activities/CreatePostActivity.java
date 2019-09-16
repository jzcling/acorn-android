package acorn.com.acorn_app.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.utils.AppExecutors;

import static acorn.com.acorn_app.ui.AcornApplication.mFirebaseAnalytics;
import static acorn.com.acorn_app.ui.activities.AcornActivity.ID_OFFSET;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserToken;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUsername;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class CreatePostActivity extends AppCompatActivity {
    private static final String TAG = "CreatePostActivity";
    private static final int RC_GALLERY = 1101;
    private static final int RC_CAMERA = 1102;
    private static final int RC_PREVIEW = 1103;

    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    private ProgressBar mProgressBar;
    private ConstraintLayout mPostRoot;

    private Spinner mDropListView;
    private ImageButton mPostCancelView;
    private EditText mPostTextView;

    private boolean mToUploadImage = false;
    private ImageView mGalleryView;
    private ImageView mCameraView;
    private TextView mPostView;

    private boolean mHasTitle = false;
    private boolean mHasSource = false;
    private CardView mArticleCardView;
    private TextView mArticleTitleView;
    private ImageView mArticleImageView;
    private TextView mArticleSourceView;
    private ImageButton mArticleCancelView;

    private ImageView mPostImageView;
    private ImageButton mPostImageCancelView;

    private boolean addPreviewPending;
    private final Runnable addPreview = () -> {
        addPreview();
        addPreviewPending = false;
    };
    private final Handler handler = new Handler();

    private String mCurrentImagePath;
    private Uri mImageUri;
    private String mImageUrl;
    private String mStorageImagePath;
    private String mLink;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        mProgressBar = findViewById(R.id.post_progressBar);
        mPostRoot = findViewById(R.id.post_root);

        mDropListView = findViewById(R.id.post_theme_spinner);
        mPostCancelView = findViewById(R.id.post_cancel);
        mPostTextView = findViewById(R.id.post_text);

        mGalleryView = findViewById(R.id.post_gallery);
        mCameraView = findViewById(R.id.post_camera);
        mPostView = findViewById(R.id.post_send);

        mArticleCardView = findViewById(R.id.post_article_card);
        mArticleTitleView = findViewById(R.id.post_article_title);
        mArticleSourceView = findViewById(R.id.post_article_contributor);
        mArticleImageView = findViewById(R.id.post_article_image);
        mArticleCancelView = findViewById(R.id.post_article_cancel);

        mPostImageView = findViewById(R.id.post_image);
        mPostImageCancelView = findViewById(R.id.post_image_cancel);

        mDataSource = NetworkDataSource.getInstance(this, mExecutors);

        Intent intent = getIntent();
        String shareAction = intent.getAction();
        String shareType = intent.getType();

        if (Intent.ACTION_SEND.equals(shareAction) && shareType != null) {
            if ("text/plain".equals(shareType)) {
                handleSendText(intent);
            } else if (shareType.startsWith("image/")) {
                handleSendImage(intent);
            }
        }

        mPostCancelView.setOnClickListener(v -> finish());

        mPostView.setOnClickListener(v -> {
            if (mPostTextView.getText().toString().trim().length() < 1) {
                createToast(this, "Please write something before posting", Toast.LENGTH_SHORT);
                mPostTextView.requestFocus();
                return;
            }
            if (mDropListView.getSelectedItem().toString().equals("Please select")) {
                createToast(this, "Please select a theme", Toast.LENGTH_SHORT);
                mDropListView.requestFocus();
                return;
            }

            mPostRoot.setVisibility(View.INVISIBLE);
            mProgressBar.setVisibility(View.VISIBLE);
            DatabaseReference postRef = FirebaseDatabase.getInstance().getReference("article");

            Integer entityId = -1;
            Long postDate = -(new Date()).getTime();
            String objectID = (ID_OFFSET + postDate) + "_" + entityId;
            String type = "post";
            String postAuthor = mUsername;
            String postText = mPostTextView.getText().toString();
            String title = mHasTitle ? mArticleTitleView.getText().toString() : null;
            String source = mHasSource ? mArticleSourceView.getText().toString() : null;
            String trendingIndex = String.valueOf(((ID_OFFSET + postDate)/10000000L));
            String link = mLink;
            String mainTheme = mDropListView.getSelectedItem().toString();

            mExecutors.networkIO().execute(() -> {
                if (mToUploadImage) {
                    saveImageToFirebaseStorage(objectID, () -> {
                        String postImageUrl = mHasTitle ? null : mStorageImagePath;
                        String imageUrl = mHasTitle ? mStorageImagePath : null;
                        Article article = new Article(entityId, objectID, type, mUid, postAuthor, postText,
                                postImageUrl, postDate, title, source, postDate, trendingIndex, imageUrl,
                                link, null, mainTheme, null, false);
                        article.notificationTokens.put(mUid, mUserToken);
                        article.theme.add(mainTheme);
                        postRef.child(objectID).setValue(article).addOnSuccessListener(aVoid -> {
                            mExecutors.networkIO().execute(() -> {
                                updateUserData(objectID, postDate);
                                mDataSource.createAlgoliaPost(article.toJson());
//                                FirebaseMessaging.getInstance().subscribeToTopic(objectID);
                            });
                            mExecutors.mainThread().execute(() -> {
                                Bundle bundle = new Bundle();
                                bundle.putString("commenter", mUid);
                                bundle.putString("post", article.getObjectID());
                                mFirebaseAnalytics.logEvent("create_post", bundle);

                                createToast(this, "Post created!", Toast.LENGTH_SHORT);
                                finish();
                            });
                        }).addOnFailureListener(e -> mExecutors.mainThread().execute(() -> {
                            Log.d(TAG, "failed to create post: " + e.getLocalizedMessage());
                            createToast(this, "Post creation failed!", Toast.LENGTH_SHORT);
                            finish();
                        }));
                    });
                } else {
                    String postImageUrl = null;
                    String imageUrl = mImageUrl;
                    Article article = new Article(entityId, objectID, type, mUid, postAuthor, postText,
                            postImageUrl, postDate, title, source, postDate, trendingIndex, imageUrl,
                            link, null, mainTheme, null, false);
                    article.notificationTokens.put(mUid, mUserToken);
                    postRef.child(objectID).setValue(article).addOnSuccessListener(aVoid -> {
                        mExecutors.networkIO().execute(() -> {
                            updateUserData(objectID, postDate);
                            mDataSource.createAlgoliaPost(article.toJson());
//                            FirebaseMessaging.getInstance().subscribeToTopic(objectID);
                        });
                        mExecutors.mainThread().execute(() -> {
                            createToast(this, "Post created!", Toast.LENGTH_SHORT);
                            finish();
                        });
                    }).addOnFailureListener(e -> mExecutors.mainThread().execute(() -> {
                        e.printStackTrace();
                        createToast(this, "Post creation failed!", Toast.LENGTH_SHORT);
                        finish();
                    }));
                }
            });
        });

        mPostTextView.setOnClickListener(v -> mPostTextView.postDelayed(() -> {
            InputMethodManager keyboard = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(mPostTextView, 0);
        },200));
        mPostTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (addPreviewPending) {
                    handler.removeCallbacks(addPreview);

                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length() > 0) {
                    mPostView.setEnabled(true);
                } else {
                    mPostView.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                Pattern urlPattern = Pattern.compile("((?:https?://|www\\.)[a-zA-Z0-9+&@#/%=~_|$?!:,.-]*\\b)");
                Matcher m = urlPattern.matcher(s);
                if (m.find()) {
                    clearArticle();
                    clearPostImage();
                    mLink = m.group(1);

                    handler.postDelayed(addPreview, 500);
                    addPreviewPending = true;
                }
            }
        });

        mArticleCancelView.setOnClickListener(v -> clearArticle());

        mPostImageCancelView.setOnClickListener(v -> clearPostImage());

        mGalleryView.setOnClickListener(v -> {
            Intent galleryIntent = new Intent();
            galleryIntent.setType("image/*");
            galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(galleryIntent, "Select Image"), RC_GALLERY);
        });

        mCameraView.setOnClickListener(v -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException e) {
                    Log.d(TAG, "failed to get camera: " + e.getLocalizedMessage());
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
                clearArticle();
                clearPostImage();
                mImageUri = Uri.parse(data.getStringExtra("imageUri"));
                String caption = data.getStringExtra("caption");
                if (!caption.equals("")) {
                    mHasTitle = true;
                    mHasSource = false;
                    mArticleCardView.setVisibility(View.VISIBLE);
                    mArticleTitleView.setText(caption);
                    Glide.with(CreatePostActivity.this)
                            .load(mImageUri.toString())
                            .into(mArticleImageView);
                    mArticleSourceView.setVisibility(View.GONE);
                    mArticleSourceView.setText(null);
                } else {
                    Glide.with(CreatePostActivity.this)
                            .load(mImageUri.toString())
                            .into(mPostImageView);
                    mPostImageView.setVisibility(View.VISIBLE);
                    mPostImageCancelView.setVisibility(View.VISIBLE);
                    mHasTitle = false;
                    mHasSource = false;
                }
                mToUploadImage = true;
            }
        }
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

    private void saveImageToFirebaseStorage(String key, Runnable onUploadSuccess) {
        Uri tempUri = mImageUri;
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), tempUri);
            File dir = getDir("images", Context.MODE_PRIVATE);
            File storedImage = new File(dir, key + ".jpg");
            FileOutputStream outStream = new FileOutputStream(storedImage);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outStream);
            tempUri = Uri.fromFile(storedImage);
            Log.d(TAG, "filename: " + tempUri.getLastPathSegment());

            StorageReference storageReference = FirebaseStorage.getInstance()
                    .getReference(mUid)
                    .child(tempUri.getLastPathSegment());
            Log.d(TAG, "storageRef: " + storageReference.toString());

            storageReference.putFile(tempUri).addOnCompleteListener(CreatePostActivity.this,
                    task -> {
                        if (task.isSuccessful()) {
                            mStorageImagePath = storageReference.getRoot().toString()
                                    + storageReference.getPath();
                            onUploadSuccess.run();
                        } else {
                            Log.d(TAG, "failed to save file");
                        }
                    });
            outStream.flush();
            outStream.close();
        } catch (Exception e) {
            Log.d(TAG, "error uploading image: " + e.getLocalizedMessage());
        }
    }

    private void addPreview() {
        mExecutors.networkIO().execute(() -> {
            try {
                if (!mLink.startsWith("http://") && !mLink.startsWith("https://")) {
                    mLink = "https://" + mLink;
                }
                Document parsedHtml = Jsoup.connect(mLink).get();
                String urlTitle = parsedHtml.title();
                Elements source = parsedHtml.select("meta[property~=og:site_name]");
                String urlSource = source.size() > 0 ? source.first().attr("content") : null;
                Elements image = parsedHtml.select("meta[property~=og:image]");
                mImageUrl = image.size() > 0 ? image.first().attr("content") : null;


                mExecutors.mainThread().execute(() -> {
                    mArticleTitleView.setText(urlTitle);
                    if (urlSource != null) {
                        mArticleSourceView.setText(urlSource);
                        mHasSource = true;
                    } else {
                        mArticleSourceView.setVisibility(View.GONE);
                    }
                    if (mImageUrl != null) {
                        Glide.with(CreatePostActivity.this)
                                .load(mImageUrl)
                                .into(mArticleImageView);
                    } else {
                        mArticleImageView.setVisibility(View.GONE);
                    }
                    mArticleCardView.setVisibility(View.VISIBLE);
                    mHasTitle = true;
                });
            } catch (Exception e) {
                Log.d(TAG, "failed to add preview: " + e.getLocalizedMessage());
            }
        });
    }

    private void clearArticle() {
        mHasTitle = false;
        mHasSource = false;
        mToUploadImage = false;
        mLink = null;
        mCurrentImagePath = null;
        mImageUri = null;
        mArticleTitleView.setText(null);
        mArticleSourceView.setText(null);
        mArticleImageView.setImageBitmap(null);
        mArticleTitleView.setVisibility(View.VISIBLE);
        mArticleImageView.setVisibility(View.VISIBLE);
        mArticleSourceView.setVisibility(View.VISIBLE);
        mArticleCardView.setVisibility(View.GONE);
    }

    private void clearPostImage() {
        mHasTitle = false;
        mHasSource = false;
        mToUploadImage = false;
        mLink = null;
        mCurrentImagePath = null;
        mImageUri = null;
        mPostImageView.setImageBitmap(null);
        mPostImageView.setVisibility(View.GONE);
        mPostImageCancelView.setVisibility(View.GONE);
    }

    private void updateUserData(String objectID, Long postDate) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference();

        Query userQuery = ref.child("user").orderByKey().equalTo(mUid);
        DatabaseReference userRef = ref.child("user/" + mUid);
        userQuery.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                User user = dataSnapshot.getValue(User.class);
                userQuery.removeEventListener(this);
                int postCount = user.getCreatedPostsCount() == null ? 0 : user.getCreatedPostsCount();
                user.createdPosts.put(objectID, postDate);
                user.setCreatedPostsCount(postCount + 1);

                userRef.setValue(user);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) { }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void handleSendText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {
            mPostTextView.setText(sharedText);
        }
    }

    private void handleSendImage(Intent intent) {
        Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {

            Glide.with(CreatePostActivity.this)
                    .load(imageUri.toString())
                    .into(mPostImageView);
        }
    }
}