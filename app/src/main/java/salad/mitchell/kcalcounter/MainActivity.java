package salad.mitchell.kcalcounter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.MathContext;

public class MainActivity extends AppCompatActivity {
    private final String KEY_DEFICIT = "a";
    private final String KEY_RATE = "b";
    private final String KEY_TIMESTAMP = "c";

    private final BigDecimal NANOSECONDS_PER_DAY = new BigDecimal("86400000000000");

    private TextView mDeficitTextView;
    private TextView mRateTextView;
    private EditText mConsumedEditText;

    private Handler mHandler;

    private float mDeficit;  // Original calorie deficit
    private int mRate;       // Calories per day
    private long mTimestamp; // Monotonically increasing timestamp (nanoseconds) that original calorie deficit was set

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mHandler = new Handler();

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Calorie deficit

        mDeficitTextView = (TextView) findViewById(R.id.deficit);

        mDeficitTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final View dialogView = getLayoutInflater().inflate(R.layout.set_deficit_dialog, null);

                new AlertDialog.Builder(MainActivity.this)
                        .setView(dialogView)
                        .setTitle("Set calorie deficit")
                        .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    EditText setDeficitEditText = (EditText) dialogView.findViewById(R.id.set_deficit);
                                    mDeficit = Float.parseFloat(setDeficitEditText.getText().toString());
                                    mTimestamp = System.nanoTime();
                                    updateDeficitTextView();
                                } catch (NumberFormatException e) {
                                    // This only fires when EditText is empty, because you can only input
                                    // numbers
                                }
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                        .create()
                        .show();
            }
        });

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Calories per day

        mRateTextView = (TextView) findViewById(R.id.rate);

        mRateTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final View dialogView = getLayoutInflater().inflate(R.layout.set_rate_dialog, null);

                new AlertDialog.Builder(MainActivity.this)
                        .setView(dialogView)
                        .setTitle("Set calories per day")
                        .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    EditText setRateEditText = (EditText) dialogView.findViewById(R.id.set_rate);

                                    int oldRate = mRate;
                                    long oldTimestamp = mTimestamp;

                                    mRate = Integer.parseInt(setRateEditText.getText().toString());

                                    // Bump the base deficit up to the current deficit (so it's
                                    // basically equal to what's being shown), and bump the
                                    // timestamp to now.
                                    mTimestamp = System.nanoTime();

                                    mDeficit += nanosecondsToDays(mTimestamp - oldTimestamp)*oldRate;

                                    updateRateTextView();
                                } catch (NumberFormatException e) {
                                    // This only fires when EditText is empty, because you can only input
                                    // numbers
                                }
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                        .create()
                        .show();
            }
        });


        ////////////////////////////////////////////////////////////////////////////////////////////
        // Consume calories

        mConsumedEditText = (EditText) findViewById(R.id.consumed);

        mConsumedEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConsumedEditText.getText().clear();
            }
        });

        mConsumedEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                switch (actionId) {
                    case EditorInfo.IME_ACTION_DONE:
                        try {
                            Editable text = mConsumedEditText.getText();

                            mDeficit -= Integer.parseInt(text.toString());
                            updateDeficitTextView();

                            Toast.makeText(
                                    MainActivity.this,
                                    "Consumed " + text + " calories",
                                    Toast.LENGTH_SHORT)
                                    .show();
                        } catch (NumberFormatException e) {
                            // This only fires when EditText is empty, because you can only input
                            // numbers
                        } finally {
                            mConsumedEditText.setText("");
                        }

                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Initialize persisted data and member variables

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);

        mDeficit = prefs.getFloat(KEY_DEFICIT, 0);
        mRate = prefs.getInt(KEY_RATE, 0);
        mTimestamp = prefs.getLong(KEY_TIMESTAMP, System.nanoTime());

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Update rate view with member variables (deficit gets updated just below, repeatedly)

        updateRateTextView();

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Update calorie deficit every minute

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mHandler.postDelayed(this, 60000);
                updateDeficitTextView();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Empty the pending message queue

        mHandler.removeCallbacksAndMessages(null);

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Store persistent data

        getPreferences(MODE_PRIVATE)
                .edit()
                .putFloat(KEY_DEFICIT, mDeficit)
                .putInt(KEY_RATE, mRate)
                .putLong(KEY_TIMESTAMP, mTimestamp)
                .apply();
    }

    private void updateDeficitTextView() {
        double deficit = mDeficit + nanosecondsToDays(System.nanoTime() - mTimestamp)*mRate;

        if (deficit >= 0) {
            mDeficitTextView.setText(String.format("%d calorie deficit", Math.round(deficit)));
        } else {
            mDeficitTextView.setText(String.format("%d calorie surplus", -Math.round(deficit)));
        }
    }

    private void updateRateTextView() {
        mRateTextView.setText(String.format("%d calories per day", mRate));
    }

    // Convert nanoseconds to days.
    private double nanosecondsToDays(long nanoseconds) {
        return BigDecimal.valueOf(nanoseconds)
                .divide(NANOSECONDS_PER_DAY, MathContext.DECIMAL64)
                .doubleValue();
    }
}