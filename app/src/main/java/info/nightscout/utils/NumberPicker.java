package info.nightscout.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 28.06.2016.
 */
public class NumberPicker extends LinearLayout implements View.OnKeyListener,
        View.OnTouchListener, View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(NumberPicker.class);

    TextView editText;
    Button minusButton;
    Button plusButton;

    Double value;
    Double minValue = 0d;
    Double maxValue = 1d;
    Double step = 1d;
    NumberFormat formater;
    boolean allowZero = false;

    private Handler mHandler;
    private ScheduledExecutorService mUpdater;

    private class UpdateCounterTask implements Runnable {
        private boolean mInc;
        private int repeated = 0;
        private int multiplier = 1;

        private final int doubleLimit = 5;

        public UpdateCounterTask(boolean inc) {
            mInc = inc;
        }

        public void run() {
            Message msg = new Message();
            if (repeated % doubleLimit == 0) multiplier *= 2;
            repeated++;
            msg.arg1 = multiplier;
            msg.arg2 = repeated;
            if (mInc) {
                msg.what = MSG_INC;
            } else {
                msg.what = MSG_DEC;
            }
            mHandler.sendMessage(msg);
        }
    }

    private static final int MSG_INC = 0;
    private static final int MSG_DEC = 1;

    public NumberPicker(Context context) {
        super(context, null);
    }

    public NumberPicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.initialize(context, attrs);
    }

    public NumberPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void initialize(Context context, AttributeSet attrs) {
        // set layout view
        LayoutInflater.from(context).inflate(R.layout.number_picker_layout, this, true);

        // init ui components
        this.minusButton = (Button) findViewById(R.id.decrement);
        this.plusButton = (Button) findViewById(R.id.increment);
        this.editText = (EditText) findViewById(R.id.display);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INC:
                        inc(msg.arg1);
                        return;
                    case MSG_DEC:
                        dec(msg.arg1);
                        return;
                }
                super.handleMessage(msg);
            }
        };

        minusButton.setOnTouchListener(this);
        minusButton.setOnKeyListener(this);
        minusButton.setOnClickListener(this);
        plusButton.setOnTouchListener(this);
        plusButton.setOnKeyListener(this);
        plusButton.setOnClickListener(this);
    }

    public void removeTextChangedListener(TextWatcher textWatcher) {
        editText.removeTextChangedListener(textWatcher);
    }

    public void addTextChangedListener(TextWatcher textWatcher) {
        editText.addTextChangedListener(textWatcher);
    }

    public void setParams(Double initValue, Double minValue, Double maxValue, Double step, NumberFormat formater, boolean allowZero, TextWatcher textWatcher) {
        setParams(initValue, minValue, maxValue, step, formater, allowZero);
        editText.addTextChangedListener(textWatcher);
    }

    public void setParams(Double initValue, Double minValue, Double maxValue, Double step, NumberFormat formater, boolean allowZero) {
        this.value = initValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.formater = formater;
        this.allowZero = allowZero;

        updateEditText();
    }

    public void setValue(Double value) {
        this.value = value;
        updateEditText();
    }

    public Double getValue() {
        return value;
    }

    public String getText() {
        return editText.getText().toString();
    }

    public void setStep(Double step) {
        this.step = step;
    }

    private void inc(int multiplier) {
        value += step * multiplier;
        if (value > maxValue) {
            value = maxValue;
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.youareonallowedlimit));
            stopUpdating();
        }
        updateEditText();
    }

    private void dec( int multiplier) {
        value -= step * multiplier;
        if (value < minValue) {
            value = minValue;
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.youareonallowedlimit));
            stopUpdating();
        }
        updateEditText();
    }

    private void updateEditText() {
        if (value == 0d && !allowZero)
            editText.setText("");
        else
            editText.setText(formater.format(value));
    }

    private void startUpdating(boolean inc) {
        if (mUpdater != null) {
            log.debug("Another executor is still active");
            return;
        }
        mUpdater = Executors.newSingleThreadScheduledExecutor();
        mUpdater.scheduleAtFixedRate(new UpdateCounterTask(inc), 200, 200,
                TimeUnit.MILLISECONDS);
    }

    private void stopUpdating() {
        if (mUpdater != null) {
            mUpdater.shutdownNow();
            mUpdater = null;
        }
    }

    @Override
    public void onClick(View v) {
        if (mUpdater == null) {
            if (v == plusButton) {
                inc(1);
            } else {
                dec(1);
            }
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        boolean isKeyOfInterest = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
        boolean isReleased = event.getAction() == KeyEvent.ACTION_UP;
        boolean isPressed = event.getAction() == KeyEvent.ACTION_DOWN
                && event.getAction() != KeyEvent.ACTION_MULTIPLE;

        if (isKeyOfInterest && isReleased) {
            stopUpdating();
        } else if (isKeyOfInterest && isPressed) {
            startUpdating(v == plusButton);
        }
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean isReleased = event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL;
        boolean isPressed = event.getAction() == MotionEvent.ACTION_DOWN;

        if (isReleased) {
            stopUpdating();
        } else if (isPressed) {
            startUpdating(v == plusButton);
        }
        return false;
    }

}
