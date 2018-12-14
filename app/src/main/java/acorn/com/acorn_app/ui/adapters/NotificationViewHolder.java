package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.common.util.NumberUtils;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Notif;
import acorn.com.acorn_app.ui.activities.CommentActivity;
import acorn.com.acorn_app.ui.activities.WebViewActivity;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;

public class NotificationViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "NotificationViewHolder";
    private final Context mContext;

    private final ConstraintLayout parentView;
    private final TextView notificationText;
    private final TextView title;
    private final TextView contributor;
    private final ImageView mainImage;
    private final TextView extra;
    private final ConstraintLayout viewBackground;
    public final ConstraintLayout viewForeground;

    private NetworkDataSource mDataSource;
    private AppExecutors mExecutors = AppExecutors.getInstance();

    public NotificationViewHolder(Context context, View view) {
        super(view);
        mContext = context;

        parentView = (ConstraintLayout) view.findViewById(R.id.notification_parent);
        notificationText = (TextView) view.findViewById(R.id.notification_text);
        title = (TextView) view.findViewById(R.id.notification_title);
        contributor = (TextView) view.findViewById(R.id.notification_contributor);
        mainImage = (ImageView) view.findViewById(R.id.notification_image);
        extra = (TextView) view.findViewById(R.id.notification_extra);
        viewBackground = (ConstraintLayout) view.findViewById(R.id.view_background);
        viewForeground = (ConstraintLayout) view.findViewById(R.id.view_foreground);

        mDataSource = NetworkDataSource.getInstance(context, mExecutors);
    }

    public void bind(Notif notif) {
        parentView.setOnClickListener(v -> {
            if (NumberUtils.isNumeric(notif.extra)) {
                if (notif.link.equals("")) {
                    Intent intent = new Intent(mContext, CommentActivity.class);
                    intent.putExtra("id", notif.articleId);
                    mContext.startActivity(intent);
                } else {
                    Intent intent = new Intent(mContext, WebViewActivity.class);
                    intent.putExtra("id", notif.articleId);
                    mContext.startActivity(intent);
                }
            } else {
                Intent intent = new Intent(mContext, CommentActivity.class);
                intent.putExtra("id", notif.articleId);
                mContext.startActivity(intent);
            }
        });
        notificationText.setText(notif.text);
        title.setText(notif.title);
        contributor.setText(notif.source);
        if (!(notif.imageUrl == null || notif.imageUrl.equals(""))) {
            mainImage.setVisibility(View.VISIBLE);
            Glide.with(mContext)
                    .load(notif.imageUrl)
                    .into(mainImage);
        } else {
            mainImage.setVisibility(View.GONE);
        }
        String extraText;
        if (notif.type.equals("article") || notif.type.equals("deal")) {
            extraText = DateUtils.parseDate(Long.parseLong(notif.extra));
        } else {
            extraText = notif.extra;
            extra.setTypeface(null, Typeface.ITALIC);
        }
        extra.setText(extraText);
    }
}
