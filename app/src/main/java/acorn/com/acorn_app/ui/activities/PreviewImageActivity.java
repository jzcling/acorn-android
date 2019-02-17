package acorn.com.acorn_app.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import acorn.com.acorn_app.R;

public class PreviewImageActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private Intent intent;
    private ImageView imageView;
    private ImageButton cancelImageView;
    private EditText captionTextView;
    private ImageButton sendButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_image);

        intent = getIntent();
        Uri imageUri = Uri.parse(intent.getStringExtra("imageUri"));

        progressBar = (ProgressBar) findViewById(R.id.preview_progress_bar);
        cancelImageView = (ImageButton) findViewById(R.id.preview_cancel);
        imageView = (ImageView) findViewById(R.id.preview_image);
        captionTextView = (EditText) findViewById(R.id.preview_caption_text);
        sendButton = (ImageButton) findViewById(R.id.preview_send_button);

        Glide.with(this)
                .load(imageUri)
                .apply(new RequestOptions()
                        .placeholder(R.drawable.loading_spinner))
                .into(imageView);
        cancelImageView.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
        sendButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("imageUri", imageUri.toString());
            resultIntent.putExtra("caption", captionTextView.getText().toString());
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });
    }
}