package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;

public class VideoFeedViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "VideoFeedViewHolder";
    private final Context mContext;
    private final String mVideoType;

    private final ImageView banner;
    private final TextView theme;
    private final TextView top_separator;
    private final TextView youtubeViewCount;
    private final TextView title;
    private final TextView contributor;
    private TextView separator;
    private TextView pubDate;
    private final ImageView netVote;
    private final TextView voteCount;
    private final TextView commentCount;
    private final ImageView videoThumbnail;

    private CheckBox upVoteView;
    private CheckBox downVoteView;
    private CheckBox commentView;
    private CheckBox favView;
    private CheckBox shareView;

    private final AppExecutors mExecutors = AppExecutors.getInstance();

    public VideoFeedViewHolder(Context context, View view, String videoType) {
        super(view);
        mContext = context;
        mVideoType = videoType;

        banner = (ImageView) view.findViewById(R.id.card_banner_new);
        theme = (TextView) view.findViewById(R.id.card_theme);
        top_separator = (TextView) view.findViewById(R.id.card_top_separator);
        youtubeViewCount = (TextView) view.findViewById(R.id.card_youtube_view_count);
        title = (TextView) view.findViewById(R.id.card_title);
        contributor = (TextView) view.findViewById(R.id.card_contributor);
        separator = (TextView) view.findViewById(R.id.card_sub_separator);
        pubDate = (TextView) view.findViewById(R.id.card_date);
        netVote = (ImageView) view.findViewById(R.id.card_image_net_vote);
        voteCount = (TextView) view.findViewById(R.id.card_vote_count);
        commentCount = (TextView) view.findViewById(R.id.card_comment_count);
        videoThumbnail = (ImageView) view.findViewById(R.id.card_image);

        upVoteView = (CheckBox) view.findViewById(R.id.card_button_upvote);
        downVoteView = (CheckBox) view.findViewById(R.id.card_button_downvote);
        commentView = (CheckBox) view.findViewById(R.id.card_button_comment);
        favView = (CheckBox) view.findViewById(R.id.card_button_favourite);
        shareView = (CheckBox) view.findViewById(R.id.card_button_share);
    }

    private VideoOnClickListener onClickListener(Video video, String cardAttribute) {
        return new VideoOnClickListener(mContext, video, cardAttribute,
                upVoteView, downVoteView, commentView, favView, shareView);
    }

    public void bind(Video video) {
        if (video.seenBy.keySet().contains(mUid)) {
            banner.setVisibility(View.GONE);
        } else {
            banner.setVisibility(View.VISIBLE);
        }
        if (video.getMainTheme() != null && !video.getMainTheme().equals("")) {
            theme.setText(video.getMainTheme());
        } else {
            theme.setVisibility(View.GONE);
            top_separator.setVisibility(View.GONE);
        }
        String viewCount = String.format("%,d",video.youtubeViewCount) + " YouTube views";
        youtubeViewCount.setText(viewCount);
        title.setText(video.getTitle());
        if (video.getSource() != null && !video.getSource().equals(""))
            contributor.setText(video.getSource().length() > 20 ?
                    video.getSource().substring(0, 17) + "..." : video.getSource());
        if (!mVideoType.equals("userGenerated")) {
            pubDate.setText(DateUtils.parseDate(video.getPubDate()));
        } else if (pubDate != null) {
            pubDate.setText(DateUtils.parseDate(video.getPostDate()));
        }
        if (video.getVoteCount() < 0) {
            netVote.setImageResource(R.drawable.ic_arrow_down);
            netVote.setColorFilter(mContext.getColor(R.color.card_down_arrow_tint));
        } else {
            netVote.setImageResource(R.drawable.ic_arrow_up);
            netVote.setColorFilter(mContext.getColor(R.color.card_up_arrow_tint));
        }
        voteCount.setText(String.valueOf(video.getVoteCount() == null ? 0 : video.getVoteCount()));
        commentCount.setText(String.valueOf(video.getCommentCount() == null ? 0 : video.getCommentCount()));

        title.setOnClickListener(onClickListener(video, "title"));
        videoThumbnail.setOnClickListener(onClickListener(video, "videoThumbnail"));
        upVoteView.setOnClickListener(onClickListener(video, "upvote"));
        downVoteView.setOnClickListener(onClickListener(video, "downvote"));
        commentView.setEnabled(false);
        shareView.setEnabled(false);
        shareView.setOnClickListener(onClickListener(video, "share"));

        if (video.upvoters.containsKey(mUid)) {
            upVoteView.setChecked(true);
        } else {
            upVoteView.setChecked(false);
        }
        if (video.downvoters.containsKey(mUid)) {
            downVoteView.setChecked(true);
        } else {
            downVoteView.setChecked(false);
        }
        if (video.commenters.containsKey(mUid)) {
            commentView.setChecked(true);
        } else {
            commentView.setChecked(false);
        }
        if (video.savers.containsKey(mUid)) {
            favView.setChecked(true);
        } else {
            favView.setChecked(false);
        }

        if (!(video.youtubeVideoId == null || video.youtubeVideoId.equals(""))) {
            String thumbnailUrl = "https://img.youtube.com/vi/" + video.youtubeVideoId + "/hqdefault.jpg";
            Glide.with(mContext.getApplicationContext())
                .load(thumbnailUrl)
                .into(videoThumbnail);

            if (mVideoType.equals("userGenerated")) {
                if (video.getSource() == null || video.getSource().equals("")) {
                    contributor.setText(video.getPostAuthor());
                    title.setText(video.getPostText().length() > 200 ?
                            video.getPostText().substring(0, 197) + "..." :
                            video.getPostText());
                } else if (video.getTitle() != null && !video.getTitle().equals("")) {
                    separator.setVisibility(View.GONE);
                    pubDate.setVisibility(View.GONE);
                }
            }
        }
    }
}
