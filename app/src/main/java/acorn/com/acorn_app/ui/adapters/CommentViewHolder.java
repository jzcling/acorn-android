package acorn.com.acorn_app.ui.adapters;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Comment;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;
import acorn.com.acorn_app.utils.IOUtils;
import acorn.com.acorn_app.utils.UiUtils;

import static android.net.ConnectivityManager.TYPE_WIFI;

public class CommentViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "CommentViewHolder";
    private final Context mContext;
    private final Executor networkIO = AppExecutors.getInstance().networkIO();
    private final Executor mainThread = AppExecutors.getInstance().mainThread();

    public final TextView commentTextView;
    private final ImageView commentImageView;
    private final TextView commenterTextView;
    private final TextView commentDateTextView;
    private final ImageView commentDownloadImageView;
    private final ViewGroup urlLayout;
    private final TextView urlTitleView;
    private final TextView urlSourceView;
    private final ImageView urlImageView;

    private final DisplayMetrics displayMetrics;
    private final int maxWidth;
    private final int maxUrlWidth;

    public CommentViewHolder(Context context, View view) {
        super(view);
        mContext = context;
        commentTextView = (TextView) itemView.findViewById(R.id.commentTextView);
        commentTextView.setMovementMethod(LinkMovementMethod.getInstance());
        commentImageView = (ImageView) itemView.findViewById(R.id.commentImageView);
        commenterTextView = (TextView) itemView.findViewById(R.id.commenterTextView);
        commentDateTextView = (TextView) itemView.findViewById(R.id.commentDateTextView);
        commentDownloadImageView = (ImageView) itemView.findViewById(R.id.commentDownloadImageView);
        urlLayout = (ConstraintLayout) itemView.findViewById(R.id.urlLayout);
        urlTitleView = (TextView) itemView.findViewById(R.id.urlTitle);
        urlSourceView = (TextView) itemView.findViewById(R.id.urlSource);
        urlImageView = (ImageView) itemView.findViewById(R.id.urlImage);

        displayMetrics = mContext.getResources().getDisplayMetrics();
        maxWidth = (int) Math.floor(0.75f * displayMetrics.widthPixels);
        maxUrlWidth = (int) Math.floor(0.8f * maxWidth);
    }

    public void bind(Comment comment) {
        ConnectivityManager cm = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isWifi = cm.getNetworkInfo(TYPE_WIFI).isConnected();

        if (comment.getIsUrl()) {
            commentTextView.setVisibility(View.GONE);
            commentImageView.setVisibility(View.GONE);
            commentDownloadImageView.setVisibility(View.GONE);
            if (commenterTextView != null) commenterTextView.setVisibility(View.GONE);
            commentDateTextView.setVisibility(View.GONE);

            urlLayout.setVisibility(View.VISIBLE);
            urlTitleView.setText(comment.getCommentText());
            urlTitleView.setMaxWidth(maxUrlWidth);
            if (comment.geturlSource() != null) {
                urlSourceView.setText(comment.geturlSource());
                urlSourceView.setMaxWidth(maxUrlWidth);
            } else {
                urlSourceView.setVisibility(View.GONE);
            }
            if (comment.getImageUrl() != null) {
                Glide.with(mContext)
                        .load(comment.getImageUrl())
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.loading_spinner))
                        .into(urlImageView);
            } else {
                urlImageView.setVisibility(View.GONE);
            }
            if (comment.getUrlLink() != null) {
                urlLayout.setOnClickListener(v -> mContext.startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(comment.getUrlLink()))));
            }
        } else {
            urlLayout.setVisibility(View.GONE);
            if (commenterTextView != null) commenterTextView.setVisibility(View.VISIBLE);
            commentDateTextView.setVisibility(View.VISIBLE);

            if (comment.getCommentText() != null && !comment.getCommentText().equals("")) {
                String commentText = comment.getCommentText();
                commentTextView.setText(commentText);
                commentTextView.setVisibility(View.VISIBLE);
                String tempCommentText = commentTextView.getText().toString();
                Log.d(TAG, tempCommentText);
                Pattern urlPattern = Pattern.compile("((?:https?://|www\\.)[a-zA-Z0-9+&@#/%=~_|$?!:,.-]*\\b)");
                Matcher m = urlPattern.matcher(tempCommentText);
                if (m.find()) {
                    String truncatedLink = m.group(1).length() > 40 ?
                            m.group(1).substring(0, 37) + "..." : m.group(1);
                    tempCommentText = m.replaceAll("<a href=\"$1\">" + truncatedLink + "</a>");
                    Log.d(TAG, tempCommentText);
                    commentTextView.setText(Html.fromHtml(tempCommentText));
                }
                commentTextView.setMaxWidth(maxWidth);
            } else {
                commentTextView.setVisibility(View.GONE);
            }
            if (comment.getImageUrl() != null) {
                loadImage(comment, isWifi);
            } else {
                commentImageView.setVisibility(View.GONE);
                commentDownloadImageView.setVisibility(View.GONE);
            }
            if (commenterTextView != null) commenterTextView.setText(comment.getUserDisplayName());
            commentDateTextView.setText(DateUtils.parseCommentDate(comment.getPubDate()));
        }

//        if (comment.getText() != null) {
//            // write this comment to the on-device index
//            FirebaseAppIndex.getInstance().update(getMessageIndexable(comment));
//        }
//
//        // log a view action on it
//        FirebaseUserActions.getInstance().end(getMessageViewAction(comment));
    }

    private void loadImage(Comment comment, boolean isWifi) {
        String key = comment.getCommentId();
        String imageUrl = comment.getImageUrl();
        Uri localUri;
        if (comment.getLocalImageUri() != null) {
            localUri = Uri.parse(comment.getLocalImageUri());
            File dir = mContext.getDir("images", Context.MODE_PRIVATE);
            File storedImage = new File(dir, key + ".jpg");
            if (storedImage.exists()) {
                commentImageView.setVisibility(View.VISIBLE);
                commentDownloadImageView.setVisibility(View.GONE);

                commentImageView.getLayoutParams().width = maxWidth;
                commentTextView.getLayoutParams().width = maxWidth;

                Glide.with(mContext)
                        .load(localUri)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.loading_spinner))
                        .into(commentImageView);
                commentImageView.setOnClickListener(v -> {
                    Dialog imagePopUp = new UiUtils().configureImagePopUp(mContext, localUri);
                    imagePopUp.show();
                });
                commentImageView.invalidate();
                return;
            }
        }

        if (isWifi) {
            if (imageUrl.startsWith("gs://")) {
                StorageReference storageReference = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(imageUrl);
                File dir = mContext.getDir("images", Context.MODE_PRIVATE);
                File storedImage = new File(dir, key + ".jpg");
                storageReference.getFile(storedImage).addOnSuccessListener(taskSnapshot -> {
                    Log.d("imageSaved", comment.getCommentId());
                    comment.setLocalImageUri(Uri.fromFile(storedImage).toString());
                    loadImage(comment, isWifi);
                });
            } else {
                networkIO.execute(() -> {
                    try {
                        Bitmap bitmap = IOUtils.getBitmapFromUrl(imageUrl);
                        File dir = mContext.getDir("images", Context.MODE_PRIVATE);
                        File storedImage = new File(dir, key + ".jpg");
                        FileOutputStream outStream = new FileOutputStream(storedImage);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outStream);
                        mainThread.execute(() -> {
                            comment.setLocalImageUri(Uri.fromFile(storedImage).toString());
                            loadImage(comment, isWifi);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } else {
            commentImageView.setVisibility(View.GONE);
            commentDownloadImageView.setVisibility(View.VISIBLE);
            commentDownloadImageView.setOnClickListener(v -> loadImage(comment, true));
        }
    }
}
