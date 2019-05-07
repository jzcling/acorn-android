package acorn.com.acorn_app.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Notif;
import acorn.com.acorn_app.ui.adapters.NotificationAdapter;
import acorn.com.acorn_app.ui.adapters.NotificationViewHolder;

public class UiUtils implements NotificationItemTouchHelper.RecyclerItemTouchHelperListener {
    private static final String TAG = "UiUtils";
    private NotificationAdapter mAdapter;

    public static void createToast(Context context, String toastText, int timeLength) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View toastLayout = inflater.inflate(R.layout.layout_toast, null);
        TextView toastTextView = (TextView) toastLayout.findViewById(R.id.toast_text);
        toastTextView.setText(toastText);

        Toast toast = new Toast(context.getApplicationContext());
        toast.setView(toastLayout);
        if (timeLength == Toast.LENGTH_SHORT) {
            toast.setDuration(Toast.LENGTH_SHORT);
        } else if (timeLength == Toast.LENGTH_LONG) {
            toast.setDuration(Toast.LENGTH_LONG);
        } else {
            toast.setDuration(Toast.LENGTH_LONG);
            Handler handler = new Handler();
            handler.postDelayed(toast::cancel, timeLength);
        }
        toast.show();
    }

    public static void increaseTouchArea(View child) {
        View parent = (View) child.getParent();
        parent.post(() -> {
            final Rect r = new Rect();
            child.getHitRect(r);
            r.left -= 120;
            r.right += 120;
            r.top -= 120;
            r.bottom += 120;
            parent.setTouchDelegate( new TouchDelegate(r, child));
        });
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, int position) {
        if (viewHolder instanceof NotificationViewHolder) {
            // remove the item from recycler view
            mAdapter.removeItem(position);
        }
    }

    public static class MyBounceInterpolator implements android.view.animation.Interpolator {
        private double mAmplitude = 1;
        private double mFrequency = 10;

        public MyBounceInterpolator(double amplitude, double frequency) {
            mAmplitude = amplitude;
            mFrequency = frequency;
        }

        public float getInterpolation(float time) {
            return (float) (-1 * Math.pow(Math.E, -time/ mAmplitude) *
                    Math.cos(mFrequency * time) + 1);
        }
    }

    public Dialog configureImagePopUp(Context context, Object imageUri) {
        Dialog builder = new Dialog(context);
        LayoutInflater factory = LayoutInflater.from(context);
        final View view = factory.inflate(R.layout.dialog_image, null);

        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int width = (int) Math.floor(0.9f * displayMetrics.widthPixels);

        PhotoView imageView = view.findViewById(R.id.dialog_inflated_image);
        imageView.getLayoutParams().width = width;
        Glide.with(context.getApplicationContext())
                .load(imageUri)
                .apply(new RequestOptions()
                        .placeholder(R.drawable.loading_spinner))
                .into(imageView);
        builder.setContentView(view);

        return builder;
    }

    public Dialog configureNotificationDialog(Context context, SharedPreferences sharedPreferences) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater factory = LayoutInflater.from(context);
        final View view = factory.inflate(R.layout.dialog_notification, null);
        builder.setView(view);

        List<Notif> notificationList = getNotificationList(context, sharedPreferences);

        // Set up clear button
        builder.setPositiveButton("CLEAR ALL", (dialog, which) -> sharedPreferences.edit().clear().apply());

        // Set up recycler view
        RecyclerView mRecyclerView = (RecyclerView) view.findViewById(R.id.notification_rv);
        LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(context);
        mLinearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new NotificationAdapter(context);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setList(notificationList);

        ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                new NotificationItemTouchHelper(0, ItemTouchHelper.LEFT|ItemTouchHelper.RIGHT, this);
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(mRecyclerView);

        // Set up empty textview
        TextView mEmptyTextView = (TextView) view.findViewById(R.id.notification_empty_text);
        if (notificationList.size() > 0) mEmptyTextView.setVisibility(View.GONE);

        return builder.create();
    }

    public static List<Notif> getNotificationList(Context context, SharedPreferences sharedPrefs) {
        List<Notif> mNotifList = new ArrayList<>();

        String keys = sharedPrefs.getString(context.getString(R.string.notif_pref_key), "");
        if (!keys.equals("")) {
            String[] keyList = keys.split("~·~");
            for (String key : keyList) {
                String value = sharedPrefs.getString(key, "");
                List<String> valueList = Arrays.asList(value.split("~·~"));

                // new as of 1.4.2 to clear all notifications of previous versions
                // due to addition of link (index position 9)
                if (valueList.size() < 10) {
                    sharedPrefs.edit().clear().apply();
                    return new ArrayList<>();
                }

                try {
                    Notif notification = new Notif(valueList.get(0), valueList.get(1), valueList.get(2),
                            valueList.get(3), valueList.get(4), valueList.get(5), valueList.get(6),
                            valueList.get(7), Long.parseLong(valueList.get(8)), valueList.get(9));
                    mNotifList.add(notification);
                } catch (Exception e) {
                    Log.d(TAG, e.getLocalizedMessage());
                }
            }
        }

        mNotifList.sort((o1, o2) -> o1.timestamp.compareTo(o2.timestamp));
        Collections.reverse(mNotifList);
        return mNotifList;
    }

    public static Rect locateView(View v) {
        int[] loc_int = new int[2];
        if (v != null) {
            try {
                v.getLocationOnScreen(loc_int);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        Rect location = new Rect();
        location.left = loc_int[0];
        location.top = loc_int[1];
        location.right = location.left + v.getWidth();
        location.bottom = location.top + v.getHeight();
        return location;
    }
}
