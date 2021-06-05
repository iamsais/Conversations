package eu.siacs.conversations.mingling.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

import eu.siacs.conversations.databinding.ActivityGetstartedBinding;
import eu.siacs.conversations.mingling.utils.GeneralUtils;

import static eu.siacs.conversations.mingling.utils.Constants.KEY_GET_STARTED;


public class GetStartedActivity extends AppCompatActivity {

    private static final String TAG = GetStartedActivity.class.getSimpleName();
    ActivityGetstartedBinding binding;

    Context context;
    Activity activity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityGetstartedBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        context = this;
        activity = this;

        binding.signIn.setOnClickListener(v -> new GeneralUtils(this)
                .openActivity(this, StartGetWorkspaceActivity.class, null, true));

        binding.getstarted.setOnClickListener(v -> {
            Map<String, String> values = new HashMap<>();
            values.put(KEY_GET_STARTED, KEY_GET_STARTED);
            new GeneralUtils(context)
                    .openActivity(activity, StartGetEmailActivity.class, values, true);
        });

    }
}
