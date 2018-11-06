package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Video;

public class VideoFeedAdapter extends RecyclerView.Adapter<VideoFeedViewHolder> {
    private static final String TAG = "VideoFeedAdapter";
    private final Context mContext;
    private String mYoutubeApiKey;
    private String mVideoType;
    private List<Video> mVideoList = new ArrayList<>();

    public VideoFeedAdapter(final Context context, NetworkDataSource dataSource) {
        mContext = context;
        dataSource.getYoutubeApiKey((apiKey) -> mYoutubeApiKey = apiKey);
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
}
