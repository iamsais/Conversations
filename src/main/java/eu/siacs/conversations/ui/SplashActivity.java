package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

	Runnable r = new Runnable() {
		@Override
		public void run() {
			startActivity(new Intent(SplashActivity.this, ConversationsActivity.class));
			finish();
		}
	};

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Handler handler = new Handler(Looper.getMainLooper());
		handler.postDelayed(r, 2000);


	}
}
