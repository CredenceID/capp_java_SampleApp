package com.credenceid.sdkapp.pages;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.credenceid.biometrics.Biometrics;
import com.credenceid.biometrics.Biometrics.CardReaderStatusListner;
import com.credenceid.biometrics.Biometrics.CloseReasonCode;
import com.credenceid.biometrics.Biometrics.OnCardStatusListener;
import com.credenceid.biometrics.Biometrics.ResultCode;
import com.credenceid.sdkapp.R;
import com.credenceid.sdkapp.models.PageView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class NfcPage extends LinearLayout implements PageView {
    private static final String TAG = NfcPage.class.getName();

    private Biometrics mBiometrics;
    private String cardReadDetailText;

    private TextView mCardDetailsTextView;
    private Button mOpenBtn;
    private Button mCloseBtn;
    private ImageView mPhotoView;
    private Bitmap bm = null;

    public NfcPage(Context context) {
        super(context);
        initialize();
    }

    public NfcPage(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public NfcPage(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    private void initialize() {
        Log.d(TAG, "initialize");
        LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        li.inflate(R.layout.page_nfc_reader, this, true);
        mOpenBtn = (Button) findViewById(R.id.open_id_btn);
        mOpenBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onCapture(v);
            }
        });

        mCloseBtn = (Button) findViewById(R.id.close_id_btn);
        mCloseBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("Close Button Listener", "Close");
                mBiometrics.cardCloseCommand();
            }
        });

        mCardDetailsTextView = (TextView) findViewById(R.id.card_details);
        if (mCardDetailsTextView != null) {
            mCardDetailsTextView.setHorizontallyScrolling(false);
            mCardDetailsTextView.setMaxLines(Integer.MAX_VALUE);
        }
        mPhotoView = (ImageView) findViewById(R.id.photo_view);
    }

    @Override
    public String getTitle() {
        return getContext().getResources().getString(R.string.title_nfc_card_reader);
    }

    @Override
    public void activate(Biometrics biometrics) {
        mBiometrics = biometrics;
        doResume();
    }

    @Override
    public void doResume() {
        enableOpenButton();
        disableCloseButton();
        mBiometrics.registerCardStatusListener(new OnCardStatusListener() {
            @Override
            public void onCardStatusChange(String ATR, int prevState, int currentState) {
                if (currentState == 1) {
                    Log.d("doResume", "Call doRead");
                    mCardDetailsTextView.setText("");
                    mCardDetailsTextView.setText("Card Present");
                    cardReadDetailText = "";
                    doCardRead();

                } else if (currentState == 0) {
                    mCardDetailsTextView.setText("");
                    mCardDetailsTextView.setText("Card Absent");
                }
            }
        });
    }

    @Override
    public void deactivate() {

    }

    // Calls the cardOpenCommand API call
    private void onCapture(View v) {
        Log.d(TAG, "OnCapture in NfcPage");

        mCardDetailsTextView.setText("");
        if (bm != null) {
            Log.d(TAG, "Bitmap is non null recycle it");
            bm.recycle(); // This should release bitmap.
        } else {
            Log.d(TAG, "Bitmap is null no need to recycle");
        }
        bm = null;

        disableOpenButton();
        mPhotoView.setImageResource(android.R.color.transparent);
        cardReadDetailText = "";

        mCardDetailsTextView.setText("Requesting Card Open");

        mBiometrics.cardOpenCommand(new CardReaderStatusListner() {
            @Override
            public void onCardReaderOpen(ResultCode arg0) {
                Log.d(TAG, "OnCardOpen:" + arg0.toString());
                if (arg0 == ResultCode.OK) {
                    mCardDetailsTextView.setText("Card Open Success.");
                    disableOpenButton();
                    enableCloseButton();
                } else {
                    mCardDetailsTextView.setText("Card Open: FAILED");
                    enableOpenButton();
                    disableCloseButton();
                }
            }

            @Override
            public void onCardReaderClosed(ResultCode resultCode, CloseReasonCode arg0) {
                if (resultCode == ResultCode.OK) {
                    mCardDetailsTextView.setText("Card Closed: " + arg0.toString());
                    disableCloseButton();
                    enableOpenButton();
                } else if (resultCode == ResultCode.FAIL) {
                    Log.d(TAG, "onCardReaderClosed: FAILED");
                    mCardDetailsTextView.setText("Card Closed: FAILED");
                }
            }
        });
    }

    // Calls the ektpCardReadCommand API call to read in data.
    private void doCardRead() {
        Log.d("doRead", "Reading card");

        // Gets SAM Pin to unlock card read
        byte[] pin;
        pin = readSamPinFromFile();

        if (pin == null) {
            cardReadDetailText += "SAM PIN ERROR, using Default \n";
            mCardDetailsTextView.setText(cardReadDetailText);
        }

        mBiometrics.ektpCardReadCommand(1, pin, new Biometrics.OnEktpCardReadListener() {

            @Override
            public void OnEktpCardRead(ResultCode result, String hint, byte[] data) {
                Log.d(TAG, "OnEktpCardRead: Result Code=" + result);

                if (result == ResultCode.OK) {
                    cardReadDetailText += "\n Read Completed";
                } else if (result == ResultCode.INTERMEDIATE) {
                    cardReadDetailText += "\n " + hint;
                    if (data != null) {
                        cardReadDetailText += " Data Length=" + data.length;
                    } else {
                        cardReadDetailText += " Data Null";
                    }
                } else {
                    cardReadDetailText += "\n" + hint;
                }

                mCardDetailsTextView.setText(cardReadDetailText);
            }
        });
    }

    // Reads in a SAM Pin or uses hardcoded one for the Credence One eKTP device
    private byte[] readSamPinFromFile() {
        String SAM_PIN_FILE = Environment.getExternalStorageDirectory().getPath() + "/ektp/config.properties";

        byte[] pin = null;
        if (mBiometrics.getProductName().equalsIgnoreCase("Credence One eKTP")) {
            pin = "2015BB1218080000000000000000219661CFF281E0F1F921FE375C8C8D64FECA759173761A7859B52B3B4DEC036F41F6".getBytes();
        }

        String samPinFile;
        String samPin = null;
        String line;
        File f = new File(SAM_PIN_FILE);

        if (f.exists()) {
            samPinFile = SAM_PIN_FILE;
            Log.d(TAG, " SAM PIN - file found: " + samPinFile);
            try {
                InputStream fis = new FileInputStream(samPinFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
                while ((line = br.readLine()) != null) {
                    if (line != null) {
                        samPin = line;
                        break;
                    }
                }

                if (samPin != null) {
                    samPin = samPin.substring(7);//remove the "samPin=" prefix to the actual data
                    Log.d(TAG, " readSamPinFromFile: Using Sam Pin =" + samPin);
                    pin = samPin.getBytes();
                }
            } catch (Exception ex) {
                Log.d(TAG, " readSamPinFromFile exception " + ex.getLocalizedMessage());
                ex.printStackTrace();
                pin = null;
            }
        } else {
            Log.d(TAG, " SAM PIN - not found");
        }
        return pin;
    }

    private void enableOpenButton() {
        mOpenBtn.setEnabled(true);
    }

    private void disableOpenButton() {
        mOpenBtn.setEnabled(false);
    }

    private void enableCloseButton() {
        mCloseBtn.setEnabled(true);
    }

    private void disableCloseButton() {
        mCloseBtn.setEnabled(false);
    }

    public void DisplayBm() {
        if (mPhotoView != null) {
            mPhotoView.clearAnimation();
            mPhotoView.invalidate();
            if (bm != null)
                mPhotoView.setImageBitmap(bm);
            else
                mPhotoView.setImageResource(R.drawable.ic_photoid);
        } else
            Log.d(TAG, "mPhotoView is null so not drawing bitmap");
    }

    public String dumpBytes(byte[] buffer) {
        byte[] HEX_CHAR = new byte[]{'0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        if (buffer == null) {
            return "";
        }

        StringBuffer sb = new StringBuffer();
        sb.append("\nLength ");
        sb.append(Integer.toString(buffer.length));
        sb.append("\n");
        for (int i = 0; i < buffer.length; i++) {
            if (i != 1 && i % 16 == 1)
                sb.append("\n");
            sb.append("0x")
                    .append((char) (HEX_CHAR[(buffer[i] & 0x00F0) >> 4]))
                    .append((char) (HEX_CHAR[buffer[i] & 0x000F])).append(" ");
        }

        return sb.toString();
    }

    private String fposToString(int pos) {
        switch (pos) {
            case 1:
                return "Right Thumb";
            case 2:
                return "Right Index";
            case 3:
                return "Right Middle";
            case 4:
                return "Right Ring";
            case 5:
                return "Right Little";
            case 6:
                return "Left Thumb";
            case 7:
                return "Left Index";
            case 8:
                return "left Middle";
            case 9:
                return "Left Ring";
            case 10:
                return "Left Little";
            default:
                return "";
        }
    }
}