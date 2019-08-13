package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.utils.AppExecutors;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;

public class VideoFeedAdapter extends RecyclerView.Adapter<VideoFeedViewHolder> {
    private static final String TAG = "VideoFeedAdapter";
    private final Context mContext;
    private String mYoutubeApiKey;
    private String mVideoType;
    private List<Video> mVideoList = new ArrayList<>();
    private List<String> mSeenList = new ArrayList<>();

    private NetworkDataSource mDataSource;

    public VideoFeedAdapter(final Context context, NetworkDataSource dataSource) {
        mContext = context;
        mDataSource = dataSource;
//        dataSource.getYoutubeApiKey((apiKey) -> mYoutubeApiKey = apiKey);
    }

    @Override
    public int getItemViewType(int position) {
        Video video = mVideoList.get(position);
        if (video.getType() == null || video.getType().equals("video")) {
            return 0;
        } else {
            return 1;
        }
    }

    @NonNull
    @Override
    public VideoFeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        mVideoType = viewType == 0 ? "video" : "userGenerated";
        LayoutInflater inflater = LayoutInflater.from(mContext);
        view = inflater.inflate(R.layout.item_video_card, parent, false);
        return new VideoFeedViewHolder(mContext, view, mVideoType, mYoutubeApiKey);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoFeedViewHolder holder, int position) {
        Video video = mVideoList.get(position);
        holder.bind(video);
    }

    @Override
    public int getItemCount() {
        return mVideoList.size();
    }

    public void setList(List<Video> newList, Runnable onComplete) {
        mVideoList = new ArrayList<>(newList);
        notifyDataSetChanged();
        onComplete.run();
    }

    public void setList(List<Video> newList) {
        mVideoList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public List<Video> getList() {
        return mVideoList;
    }

    public Video getLastItem() {
        return mVideoList.get(mVideoList.size()-1);
    }

    public void removeItem(int position) {
        Video video = mVideoList.get(position);
        mVideoList.remove(video);
        notifyDataSetChanged();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull VideoFeedViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        int position = holder.getAdapterPosition();
        Video video = mVideoList.get(position);
        if (!mSeenList.contains(video.getObjectID())) {
            mSeenList.add(video.getObjectID());
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull VideoFeedViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        int position = holder.getAdapterPosition();
        if (position >= 0 && position < mVideoList.size()) {
            Video article = mVideoList.get(position);
            if (mSeenList.contains(article.getObjectID())) {
                mDataSource.logSeenItemEvent(mUid, article.getObjectID(), article.getType());
            }
        }
    }
}
