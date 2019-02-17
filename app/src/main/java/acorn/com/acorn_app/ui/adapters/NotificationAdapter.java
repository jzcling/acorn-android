package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Notif;

import static android.content.Context.MODE_PRIVATE;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationViewHolder> {
    private static final String TAG = "NotificationAdapter";
    private final Context mContext;
    private List<Notif> mNotifList = new ArrayList<>();
    
    public NotificationAdapter(final Context context) {
        mContext = context;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        LayoutInflater inflater = LayoutInflater.from(mContext);
        view = inflater.inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(mContext, view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notif notif = mNotifList.get(position);
        holder.bind(notif);
    }

    @Override
    public int getItemCount() {
        return mNotifList.size();
    }

    public void setList(List<Notif> newList) {
        mNotifList = newList;
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        Notif notif = mNotifList.get(position);
        SharedPreferences sharedPrefs = mContext.getSharedPreferences(
                mContext.getString(R.string.notif_pref_id), MODE_PRIVATE);
        String key;
        if (notif.type.equals("comment")) {
            key = "c_" + notif.articleId;
        } else if (notif.type.equals("article")) {
            key = "a_" + notif.articleId;
        } else {
            key = "d_" + notif.articleId;
        }

        String keys = sharedPrefs.getString(mContext.getString(R.string.notif_pref_key), "");

        List<String> keyList = new ArrayList<>(Arrays.asList(keys.split("·")));

        keyList.remove(key);
        String newKeys = null;
        for (String k : keyList) {
            if (newKeys == null) {
                newKeys = k;
            } else {
                newKeys += "·" + k;
            }
        }

        sharedPrefs.edit().putString(mContext.getString(R.string.notif_pref_key), newKeys)
                .remove(key)
                .apply();

        mNotifList.remove(position);
        notifyItemRemoved(position);
    }
}
