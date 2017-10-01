package com.credenceid.sdkapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.credenceid.biometrics.BiometricsActivity;
import com.credenceid.biometrics.BiometricsManager;
import com.credenceid.sdkapp.models.PageView;
import com.credenceid.sdkapp.pages.AboutPage;
import com.credenceid.sdkapp.pages.CardReaderPage;
import com.credenceid.sdkapp.pages.EncryptPage;
import com.credenceid.sdkapp.pages.FingerprintPage;
import com.credenceid.sdkapp.pages.IrisPage;
import com.credenceid.sdkapp.pages.MrzReaderPage;
import com.credenceid.sdkapp.pages.NfcPage;
import com.credenceid.sdkapp.pages.UsbAccessPage;

import java.io.File;

public class SampleActivity extends BiometricsActivity {
    private static final String TAG = SampleActivity.class.getName();

    private static final int MENU_EXIT = Menu.FIRST;

    public static BiometricsManager biometricsManager;

    /* ViewFlipper is used to switch between different pages in our activity. */
    private ViewFlipper viewFlipper;
    /* Every page's object. */
    private AboutPage aboutPage;
    private CardReaderPage cardReaderPage;
    private EncryptPage encryptPage;
    private FingerprintPage fingerprintPage;
    private IrisPage irisPage;
    private MrzReaderPage mrzReaderPage;
    private NfcPage nfcPage;
    private UsbAccessPage usbAccessPage;
    /* All image buttons corresponding to each PageView. */
    private ImageButton imageButtonAboutPage;
    private ImageButton imageButtonFingerprintPage;
    private ImageButton imageButtonIrisPage;
    private ImageButton imageButtonCardReaderPage;
    private ImageButton imageButtonEncryptPage;
    private ImageButton imageButtonNfcPage;
    private ImageButton imageButtonUsbAccessPage;
    private ImageButton imageButtonMrzPage;
    /* This object stores which current ImageButton is activated. */
    private ImageButton imageButtonCurrentPage;
    /* Stores which current PageView the user is on. */
    private PageView currentPageView;
    /* WebView displaying device information. */
    private WebView webView;    /* Var used to determine time different between onResume/onPause. */
    private long pauseTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* Start by initialize our screen resolution and size. */
        this.initializeDisplay();
        /* Set our content view. Essentially out layout file. */
        setContentView(R.layout.activity_sample);
        /* Now set up all components inside layout file. */
        this.initializeLayoutPages();
        this.initializeLayoutButtons();
        /* Disable allbuttons not  until Biometrics becomes initialized. This prevents users from
         * making API calls with null objects.
         */
        this.setGlobalButtonEnable(false);
    }

    /* Method used to set proper screen resolution. */
    private void initializeDisplay() {
        /* Setting up screen based on device screen size and resolution. */
        Point size = this.getDisplaySize();

        int orientation = (size.x > size.y) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        setRequestedOrientation(orientation);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        Log.d(TAG, "DisplayMetrics - density: " + metrics.density + ", dpi: " + metrics.densityDpi);

        if (metrics.densityDpi < DisplayMetrics.DENSITY_MEDIUM)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /* Method used to load different pages inside layout file. */
    private void initializeLayoutPages() {
        this.viewFlipper = (ViewFlipper) findViewById(R.id.view_flipper);
        this.webView = (WebView) findViewById(R.id.web_view);

        this.aboutPage = (AboutPage) findViewById(R.id.about_view);
        this.fingerprintPage = (FingerprintPage) findViewById(R.id.fingerprint_view);
        this.fingerprintPage.setActivity(this);
        this.irisPage = (IrisPage) findViewById(R.id.iris_view);
        this.irisPage.setActivity(this);
        this.cardReaderPage = (CardReaderPage) findViewById(R.id.card_reader_view);
        this.encryptPage = (EncryptPage) findViewById(R.id.encrypt_view);
        this.usbAccessPage = (UsbAccessPage) findViewById(R.id.usb_access_view);
        this.nfcPage = (NfcPage) findViewById(R.id.nfc_view);
        this.mrzReaderPage = (MrzReaderPage) findViewById(R.id.mrz_reader_view);
        this.mrzReaderPage.setActivity(this);
    }

    /* Method used to load all buttons from layout file. */
    private void initializeLayoutButtons() {
        this.imageButtonAboutPage = (ImageButton) findViewById(R.id.about_btn);
        this.imageButtonFingerprintPage = (ImageButton) findViewById(R.id.fingerprint_btn);
        this.imageButtonIrisPage = (ImageButton) findViewById(R.id.iris_btn);
        this.imageButtonCardReaderPage = (ImageButton) findViewById(R.id.card_reader_btn);
        this.imageButtonEncryptPage = (ImageButton) findViewById(R.id.encryption_btn);
        this.imageButtonUsbAccessPage = (ImageButton) findViewById(R.id.usb_access_btn);
        this.imageButtonNfcPage = (ImageButton) findViewById(R.id.nfc_btn);
        this.imageButtonMrzPage = (ImageButton) findViewById(R.id.mrz_reader_btn);
    }

    @Override
    public void onBiometricsInitialized(ResultCode result, String sdk_version, String required_version) {
        Log.d(TAG, "Sdkapp product name is " + getProductName());

        /* Once Biometrics initializes then set all of our buttons to be enabled. */
        this.setGlobalButtonEnable(result == ResultCode.OK);

        if (result == ResultCode.OK) {
            /* Biometrics may initalize/un-initialize multiple times due to binding/un-binding. In
             * order to prevent "re-initialization" we have a check to see if it was already init.
             */
            this.saveValidPages();
            this.showValidPages();
                /* Re-display AboutPage with correct device id and SDK version. */
            this.setCurrentPage(this.imageButtonAboutPage, aboutPage);
            // set MRZ page again so it can show ePassport option for starlight or credence tab.
            this.mrzReaderPage.setActivity(this);

            setPreferences("PREF_TIMEOUT", "60", new PreferencesListener() {
                @Override
                public void onPreferences(ResultCode result, String key, String value) {
                    Log.w(TAG, "onPreferences: " + key + ", " + value);
                    if (result != ResultCode.OK)
                        Toast.makeText(SampleActivity.this, "Error Setting Preferences",
                                Toast.LENGTH_LONG).show();
                }
            });

            biometricsManager = new BiometricsManager(this);
        }

        Toast.makeText(this, "Biometrics Initialized", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.getVisibility() == View.VISIBLE) {
                webView.setVisibility(View.GONE);
                return true;
            }
            if (imageButtonCurrentPage == imageButtonAboutPage) {
                Log.d(TAG, "force shutdown when exiting from About Page");
                onExit();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        pauseTime = SystemClock.elapsedRealtime();
        /*if (mCurrentPage != null) {
            mCurrentPage.deactivate();
		}*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_EXIT, Menu.NONE, R.string.menu_exit);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* if onResume happened right after onPause, do not pass it on to the pages
         * as the onPause / onResume sequence was likely triggered by USB device activation.
         */
        final long SHORT_PAUSE = 500;
        long timeSincePause = SystemClock.elapsedRealtime() - pauseTime;

        if (timeSincePause < SHORT_PAUSE) {
            Log.d(TAG, "onResume - ignoring short pause");
            return;
        }
        if (currentPageView != null)
            currentPageView.doResume();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_EXIT) {
            this.onExit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /* Give BiometricsManager a chance to process onActivityResult. */
        TheApp.getInstance().getBiometricsManager().onActivityResult(requestCode, resultCode, data);
    }

    /* Returns Point object containing screen dimensions. */
    private Point getDisplaySize() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        Log.d(TAG, "getDisplaySize - width: " + size.x + ", height: " + size.y);
        return size;
    }

    private void setGlobalButtonEnable(boolean enable) {
        setButtonEnable(imageButtonAboutPage, enable);
        setButtonEnable(imageButtonFingerprintPage, enable);
        setButtonEnable(imageButtonIrisPage, enable);
        setButtonEnable(imageButtonCardReaderPage, enable);
        setButtonEnable(imageButtonEncryptPage, enable);
        setButtonEnable(imageButtonNfcPage, enable);
        setButtonEnable(imageButtonUsbAccessPage, enable);
        setButtonEnable(imageButtonMrzPage, enable);
    }

    private void setButtonEnable(ImageButton button, boolean enabled) {
        if (button != null) button.setEnabled(enabled);
    }

    /* Set visibility of Biometric pages based on device type. */
    private void showValidPages() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean has_fingerprint_scanner = prefs.getBoolean("has_fingerprint_scanner", true);
        imageButtonFingerprintPage.setVisibility(has_fingerprint_scanner ? View.VISIBLE : View.GONE);

        boolean has_iris_scanner = prefs.getBoolean("has_iris_scanner", true);
        imageButtonIrisPage.setVisibility(has_iris_scanner ? View.VISIBLE : View.GONE);

        boolean has_card_reader = prefs.getBoolean("has_card_reader", true);
        imageButtonCardReaderPage.setVisibility(has_card_reader ? View.VISIBLE : View.GONE);

        boolean has_encryption = prefs.getBoolean("has_encryption", true);
        imageButtonEncryptPage.setVisibility(has_encryption ? View.VISIBLE : View.GONE);

        boolean has_usb_access = prefs.getBoolean("has_usb_access", true);
        imageButtonUsbAccessPage.setVisibility(has_usb_access ? View.VISIBLE : View.GONE);

        boolean has_mrz_reader = prefs.getBoolean("has_mrz_reader", true);
        imageButtonMrzPage.setVisibility(has_mrz_reader ? View.VISIBLE : View.GONE);

        boolean has_nfc_card = prefs.getBoolean("has_nfc_card", true);
        imageButtonNfcPage.setVisibility(has_nfc_card ? View.VISIBLE : View.GONE);
    }

    /* Based on what device CredenceService was initialized on, this will save what peripherals
     * pages are valid with SharesPreferences.
     */
    private void saveValidPages() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("has_fingerprint_scanner", hasFingerprintScanner());
        editor.putBoolean("has_iris_scanner", hasIrisScanner());
        editor.putBoolean("has_card_reader", hasCardReader());
        editor.putBoolean("has_encryption", true);
        editor.putBoolean("has_usb_access", hasUsbFileAccessEnabling());
        editor.putBoolean("has_nfc_card", hasNfcCard());
        editor.putBoolean("has_mrz_reader", hasMrzReader());
        editor.apply();
    }

    /* Called from menu or device back button to call finalize API and end application. */
    private void onExit() {
        this.finalizeBiometrics(true);
        this.finish();
    }

    /* Sets current page based on which corresponding PageView button was clicked. */
    private void setCurrentPage(View pageViewButton, View pageView) {
        /* If application was on Fingerprint/Iris page, we need to cancel any on going captures. */
        if (this.currentPageView == this.fingerprintPage || this.currentPageView == this.irisPage)
            this.cancelCapture();
        /* Deactivate current page application was on. */
        if (this.currentPageView != null) {
            this.currentPageView.deactivate();
            this.currentPageView = null;
        }
        /* Deactivates old current page's ImageButton. */
        if (this.imageButtonCurrentPage != null)
            this.imageButtonCurrentPage.setActivated(false);

        /* If new button is IN-valid or button is not an ImageButton then we return, since that
         * means a non-page view button was pressed.
         */
        if (pageViewButton == null || !(pageViewButton instanceof ImageButton)) {
            this.imageButtonCurrentPage = null;
            return;
        }
        /* Reaching here means all conditions were valid and met. Set current ImageButton to match
         * passed argument and set button as activated.
         */
        this.imageButtonCurrentPage = (ImageButton) pageViewButton;
        this.imageButtonCurrentPage.setActivated(true);

        /* Calculate index of page for which PageView we want to switch to. */
        int index = viewFlipper.indexOfChild(pageView);
        /* This would only be the case if we pass a custom PageView not belonging to any listed
         * inside layout file. Otherwise can set respective page.
         */
        if (index < 0) Log.w(TAG, "invalid view");
        else {
            viewFlipper.setDisplayedChild(index);
            if (pageView instanceof PageView) {
                /* Update our current page variables and activate new page. */
                currentPageView = (PageView) pageView;
                currentPageView.activate(this);
                /* Grab applications title, new Pagees title, and build fill title string. */
                String title = getResources().getString(R.string.app_name);
                String page_title = currentPageView.getTitle();
                title += (page_title != null) ? (" - " + page_title) : "";
                setTitle(title);
            }
        }
    }

    /* If user taps on scanned Fingerprint/Iris images, then application zooms in allowing user to
     * interact with images in full screen "mode".
     */
    public void showFullScreenScannedImage(String path_name) {
        if (path_name == null || path_name.isEmpty()) return;

        webView.setVisibility(View.VISIBLE);
        webView.getSettings().setBuiltInZoomControls(true);

        File file = new File(path_name);
        webView.loadUrl("file:///" + file.getAbsolutePath());
    }

    /* Button methods for onClick event. These have already been specified inside corresponding
     * xml, layout, file.
     * */
    public void onAbout(View v) {
        setCurrentPage(v, aboutPage);
        imageButtonAboutPage.setImageResource(R.drawable.ic_abouton);
    }

    public void onFingerprint(View v) {
        setCurrentPage(v, fingerprintPage);
        imageButtonAboutPage.setImageResource(R.drawable.ic_aboutoff);
    }

    public void onIris(View v) {
        setCurrentPage(v, irisPage);
        imageButtonAboutPage.setImageResource(R.drawable.ic_aboutoff);
    }

    public void onCardReader(View v) {
        setCurrentPage(v, cardReaderPage);
        imageButtonAboutPage.setImageResource(R.drawable.ic_aboutoff);
    }

    public void onMRZ(View v) {
        setCurrentPage(v, mrzReaderPage);
        imageButtonAboutPage.setImageResource(R.drawable.ic_aboutoff);
    }

    public void onEncrypt(View v) {
        setCurrentPage(v, encryptPage);
        imageButtonAboutPage.setImageResource(R.drawable.ic_aboutoff);
    }

    public void onUsbAccess(View v) {
        setCurrentPage(v, usbAccessPage);
        imageButtonAboutPage.setImageResource(R.drawable.ic_aboutoff);
    }

    public void onNfcView(View v) {
        setCurrentPage(v, nfcPage);
        imageButtonAboutPage.setImageResource(R.drawable.ic_aboutoff);
    }
}
