package acorn.com.acorn_app.utils;

import android.databinding.BindingAdapter;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

public class BindingAdapterUtils {
    @BindingAdapter({"imageUrl"})
    public static void loadImage(ImageView view, @Nullable String imageUrl) {
        if (imageUrl != null) {
            view.setVisibility(View.VISIBLE);
            Glide.with(view.getContext())
                    .load(imageUrl)
                    .into(view);
        } else {
            view.setVisibility(View.GONE);
        }
    }
    @BindingAdapter({"isVisible"})
    public static void setIsVisible(View view, @Nullable String isVisible) {
        if (isVisible != null) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }
}
