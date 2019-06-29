package acorn.com.acorn_app.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.view.CollapsibleActionView;

public class CollapsibleMenuItemView extends LinearLayout implements CollapsibleActionView {


    public CollapsibleMenuItemView(Context context) {
        super(context);
    }

    public CollapsibleMenuItemView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CollapsibleMenuItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CollapsibleMenuItemView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onActionViewExpanded() {
        ViewGroup.LayoutParams params = getLayoutParams();
        params.height = 100;
        params.width = 200;
        Log.d("AcornActivity", "onActionViewExpanded: width: " + params.height + ", height: " + params.width);
        setLayoutParams(params);
        requestLayout();
    }

    @Override
    public void onActionViewCollapsed() { }
}
