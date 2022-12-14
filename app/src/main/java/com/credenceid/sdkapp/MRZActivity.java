package com.credenceid.sdkapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.credenceid.biometrics.Biometrics;
import com.credenceid.biometrics.Biometrics.CloseReasonCode;
import com.credenceid.biometrics.Biometrics.ResultCode;
import com.credenceid.biometrics.DeviceFamily;
import com.credenceid.icao.ICAODocumentData;
import com.credenceid.icao.ICAOReadIntermediateCode;

import static com.credenceid.biometrics.Biometrics.ResultCode.FAIL;
import static com.credenceid.biometrics.Biometrics.ResultCode.INTERMEDIATE;
import static com.credenceid.biometrics.Biometrics.ResultCode.OK;


@SuppressWarnings({"unused", "StatementWithEmptyBody"})
public class MRZActivity
		extends Activity {

	private final static String TAG = MRZActivity.class.getSimpleName();

	/* If a document is present on either MRZ/EPassport sensor then C-Service returns this code in
	 * sensors respective callback.
	 */
	private static final int DOCUMENT_PRESENT_CODE = 2;

	/* Once MRZ data is received and split, there are ten different sections. Each sections
	 * corresponds with an index in split array.
	 */
	private static final int DATE_OF_BIRTH = 0;
	private static final int EXPIRATION = 1;
	private static final int ISSUER = 2;
	private static final int DOCUMENT_TYPE = 3;
	private static final int LAST_NAME = 4;
	private static final int FIRST_NAME = 5;
	private static final int NATIONALITY = 6;
	private static final int DISCRETIONARY = 7;
	private static final int DISCRETIONARY_TWO = 8;
	private static final int DOCUMENT_NUMBER = 9;
	private static final int GENDER = 10;
	/* MRZ reader returns one giant string of data back. Once user splits this string by space
	 * delimiter they are supposed to have ten elements. This constant can be used to confirm
	 * that appropriate data was read.
	 */
	private final int mMRZ_DATA_COUNT = 10;

	/* --------------------------------------------------------------------------------------------
	 *
	 * Components in layout file.
	 *
	 * --------------------------------------------------------------------------------------------
	 */
	private TextView mStatusTextView;
	private ImageView mICAOFaceImageView;
	private ImageView mICAOFingerImageView;
	private TextView mICAOTextView;
	private Button mOpenMRZButton;
	private Button mOpenRFButton;
    private Button mLoadCertificatesButton;
	private Switch mSwPaceKey;
	private CheckBox mCbChipAuthentication;
	private CheckBox mCbTerminalAuthentication;
	/* This button should only be enabled if three conditions are all met.
	 * 1. EPassport is open.
	 * 2. MRZ has been read and document number, D.O.B., and D.O.E. have been captured
	 * 3. A document is present on EPassport sensor.
	 */
	private Button mReadICAOButton;

	/* These keep track of MRZ/EPassport sensor states. These are used to regulate button enables
	 * and handle branches in functionality.
	 */
	private boolean mIsMRZOpen = false;
	private boolean mIsEPassportOpen = false;
	private boolean mHasMRZData = false;
	private boolean mIsDocPresentOnEPassport = false;
	private boolean mIsMrzByCameraEngineInitialized = false;

	private String mDocNumber = "";
	private String mDateOfBirth = "";
	private String mDateOfExpiry = "";
	private String mIdDocumentKey = "";

	/* Callback invoked each time MRZ reader is able to read MRZ text from document. */
	private Biometrics.OnMRZReaderListener mOnMRZReadListener = (ResultCode resultCode,
																 String hint,
																 byte[] rawData,
																 String data,
																 String parsedData) -> {

		if (OK == resultCode) {
			/* Once data is read, it is auto parsed and returned as one big string of data. */
			if (null == parsedData || parsedData.isEmpty()) {
				mStatusTextView.setText(getString(R.string.mrz_failed_reswipe));
				return;
			}

			/* Each section of data is separated by a "\r\n" character. If we split this data up, we
			 * should have TEN elements of data. Please see the constants defined at the top of this
			 * class to see the different pieces of information MRZ contains and their respective
			 * indexes.
			 */
			mIdDocumentKey = data;
			final String[] splitData = parsedData.split("\r\n");
			if (splitData.length < mMRZ_DATA_COUNT) {
				mStatusTextView.setText(getString(R.string.mrz_failed_reswipe));
				return;
			}

			mDateOfBirth = splitData[DATE_OF_BIRTH]
					.substring(splitData[DATE_OF_BIRTH].indexOf(":") + 1);

			mDateOfExpiry = splitData[EXPIRATION]
					.substring(splitData[EXPIRATION].indexOf(":") + 1);

			String issuer = splitData[ISSUER].substring(splitData[ISSUER].indexOf(":") + 1);

			String docType = splitData[DOCUMENT_TYPE]
					.substring(splitData[DOCUMENT_TYPE].indexOf(":") + 1).replaceAll("\\s+", "");

			String discretionary = splitData[DISCRETIONARY]
					.substring(splitData[DISCRETIONARY].indexOf(":") + 1);

			mDocNumber = splitData[DOCUMENT_NUMBER]
					.substring(splitData[DOCUMENT_NUMBER].indexOf(":") + 1);

			/* Only for Senegal Identity cards is document number split into discretionary. */
			if (issuer.equals("SEN") && docType.equals("I") && discretionary.matches(".*\\d+.*")) {
				String tmp = discretionary.replaceAll("<", "");
				if (tmp.length() >= 8)
					tmp = tmp.substring(0, 8);
				mDocNumber += tmp;
			}

			mStatusTextView.setText(getString(R.string.mrz_read_success));
			mICAOTextView.setText(parsedData);

			mHasMRZData = true;

		} else if (INTERMEDIATE == resultCode) {
			mStatusTextView.setText(getString(R.string.mrz_reading_wait));

		} else if (FAIL == resultCode) {
			mStatusTextView.setText(getString(R.string.mrz_failed_reswipe));
			mHasMRZData = false;
		}
	};

	/* Callback invoked each time C-Service detects a document change from MRZ reader. */
	private Biometrics.OnMRZDocumentStatusListener mOnMrzDocumentStatusListener
			= (int previousState, int currentState) -> {

		/* If currentState is not 2, then no document is present. */
		if (DOCUMENT_PRESENT_CODE != currentState)
			return;

		mStatusTextView.setText(getString(R.string.mrz_reading_wait));

		/* If current state is 2, then a document is present on MRZ reader. If a document
		 * is present we must read it to obtain MRZ field data. Call "readMRZ" to read the document.
		 *
		 * When MRZ is read this callback is invoked "mOnMRZReadListener".
		 */
		App.BioManager.readMRZ(mOnMRZReadListener);
	};

	/* Callback invoked each time sensor detects a document change from EPassport reader. */
	private Biometrics.OnEPassportStatusListener mOnEPassportCardStatusListener
			= (int previousState, int currentState) -> {

		/* If currentState is not 2, then no document is present. */
		if (DOCUMENT_PRESENT_CODE != currentState) {
			mIsDocPresentOnEPassport = false;
		} else {
			mIsDocPresentOnEPassport = true;

			/* Only if remaining other conditions (1 & 2) are met should button be enabled. */
			mReadICAOButton.setEnabled(mHasMRZData && mIsEPassportOpen);
		}
	};

	/* --------------------------------------------------------------------------------------------
	 *
	 * Android activity lifecycle event methods.
	 *
	 * --------------------------------------------------------------------------------------------
	 */

	@Override
	protected void
	onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_mrz);

		this.initializeLayoutComponents();
		this.configureLayoutComponents();
	}

	/* Invoked when user pressed back menu button. */
	@Override
	public void
	onBackPressed() {

		super.onBackPressed();
		App.BioManager.ePassportCloseCommand();
		App.BioManager.closeMRZ();
	}

	/* Invoked when application is killed, either by user or system. */
	@Override
	protected void
	onDestroy() {

		super.onDestroy();

		/* If user presses back button then close all open peripherals. */
		App.BioManager.ePassportCloseCommand();
		App.BioManager.closeMRZ();

		/* If user presses back button then they are exiting application. If this is the case then
		 * tell C-Service to unbind from this application.
		 */
		App.BioManager.finalizeBiometrics(false);
	}

	/* --------------------------------------------------------------------------------------------
	 *
	 * Initialize and configure components.
	 *
	 * --------------------------------------------------------------------------------------------
	 */

	/* Initializes all objects inside layout file. */
	private void
	initializeLayoutComponents() {

		mStatusTextView = findViewById(R.id.status_textview);

		mICAOFaceImageView = findViewById(R.id.icao_dg2_imageview);
		mICAOTextView = findViewById(R.id.icao_textview);

		mOpenMRZButton = findViewById(R.id.open_mrz_button);
		mOpenRFButton = findViewById(R.id.open_epassport_buton);
		mReadICAOButton = findViewById(R.id.read_icao_button);
		mCbChipAuthentication = findViewById(R.id.checkBoxChipAuthnetication);
		mCbTerminalAuthentication = findViewById(R.id.checkBoxTerminalAuthentication);
		mSwPaceKey = findViewById(R.id.switchPaceKey);
        mLoadCertificatesButton = findViewById(R.id.load_certificates);
	}

	/* Configure all objects in layout file, set up listeners, views, etc. */
	private void
	configureLayoutComponents() {

		mOpenMRZButton.setEnabled(true);
		mOpenMRZButton.setText(getString(R.string.open_mrz));
		mOpenMRZButton.setOnClickListener((View v) -> {
			if(DeviceFamily.CredenceTwo == App.BioManager.getDeviceFamily()){
				//TODO add C-CAMERA here

			} else {
				/* Based on current state of MRZ reader take appropriate action. */
				if (!mIsMRZOpen) {
					openMRZReader();
					mICAOTextView.setText("");
					mICAOFaceImageView.setImageBitmap(null);
					mICAOFingerImageView.setImageBitmap(null);
				} else {
					App.BioManager.closeMRZ();
					App.BioManager.ePassportCloseCommand();
				}
			}
		});

		mOpenRFButton.setText(getString(R.string.open_epassport));
		mOpenRFButton.setOnClickListener((View v) -> {
			if(App.BioManager.getDeviceFamily() == DeviceFamily.CredenceTwo){
				if (mIsEPassportOpen)
					App.BioManager.cardCloseCommand();
				else this.openCtwoCardReader();
			} else {
				/* Based on current state of EPassport reader take appropriate action. */
				if (!mIsEPassportOpen)
					openEPassportReader();
				else App.BioManager.ePassportCloseCommand();
			}
		});

		mReadICAOButton.setEnabled(mIsDocPresentOnEPassport);
		mReadICAOButton.setOnClickListener((View v) -> {
			if(mSwPaceKey.isChecked()) {
				displayCanCodedialogBox();
			} else {
				if(App.BioManager.getDeviceFamily() == DeviceFamily.CredenceTAB){
					readICAODocument(mIdDocumentKey);
				}else{
					mIdDocumentKey = "";
					readICAODocument(mIdDocumentKey);
				}
			}

		});

		mLoadCertificatesButton.setOnClickListener((View v) -> {
			displayCertificatesdialogBox();
        });

	}

	/* --------------------------------------------------------------------------------------------
	 *
	 * Private Helpers.
	 *
	 * --------------------------------------------------------------------------------------------
	 */

	/* Calls Credence APIs to open MRZ reader. */
	private void
	openMRZReader() {

		mStatusTextView.setText(getString(R.string.mrz_opening));

		/* Register a listener that will be invoked each time MRZ reader's status changes. Meaning
		 * that anytime a document is placed/removed invoke this callback.
		 */
		App.BioManager.registerMRZDocumentStatusListener(mOnMrzDocumentStatusListener);

		/* Once our callback is registered we may now open the reader. */
		App.BioManager.openMRZ(new Biometrics.MRZStatusListener() {
			@Override
			public void
			onMRZOpen(ResultCode resultCode) {

				/* This code is returned once sensor has fully finished opening. */
				if (OK == resultCode) {
					/* Now that sensor is open, if user presses "mOpenMRZButton" sensor should now
					 * close. To achieve this we change flag which controls what action button takes.
					 */
					mIsMRZOpen = true;

					mStatusTextView.setText(getString(R.string.mrz_opened));
					mOpenMRZButton.setText(getString(R.string.close_mrz));
					mOpenRFButton.setEnabled(true);
				}
				/* This code is returned while sensor is in the middle of opening. */
				else if (INTERMEDIATE == resultCode) {
					/* Do nothing while operation is still on-going. */

				}
				/* This code is returned if sensor fails to open. */
				else if (FAIL == resultCode) {
					mStatusTextView.setText(getString(R.string.mrz_open_failed));
				}
			}

			@Override
			public void
			onMRZClose(ResultCode resultCode,
					   CloseReasonCode closeReasonCode) {

				if (OK == resultCode) {
					/* Now that sensor is open, if user presses "mOpenMRZButton" sensor should now
					 * open. To achieve this we change flag which controls what action button takes.
					 */
					mIsMRZOpen = false;

					mStatusTextView.setText(getString(R.string.mrz_closed));
					mOpenMRZButton.setText(getString(R.string.open_mrz));

					mOpenRFButton.setText(getString(R.string.open_epassport));

				} else if (INTERMEDIATE == resultCode) {
					/* This code is never returned here. */

				} else if (FAIL == resultCode) {
					mStatusTextView.setText(getString(R.string.mrz_failed_close));
				}
			}
		});
	}

	/* Calls Credence APIs to open EPassport reader. */
	private void
	openEPassportReader() {

		mStatusTextView.setText(getString(R.string.epassport_opening));

		/* Register a listener will be invoked each time EPassport reader's status changes. Meaning
		 * that anytime a document is placed/removed invoke this callback.
		 */
		App.BioManager.registerEPassportStatusListener(mOnEPassportCardStatusListener);

		/* Once our callback is registered we may now open the reader. */
		App.BioManager.ePassportOpenCommand(new Biometrics.EPassportReaderStatusListener() {
			@Override
			public void
			onEPassportReaderOpen(ResultCode resultCode) {

				/* This code is returned once sensor has fully finished opening. */
				if (OK == resultCode) {
					/* Now that sensor is open, if user presses "mOpenRFButton" sensor should now
					 * close. To achieve this we change flag which controls what action button takes.
					 */
					mIsEPassportOpen = true;

					mReadICAOButton.setEnabled(true);

					mOpenRFButton.setText(getString(R.string.close_epassport));
					mStatusTextView.setText(getString(R.string.epassport_opened));

				}
				/* This code is returned while sensor is in the middle of opening. */
				else if (INTERMEDIATE == resultCode) {
					/* Do nothing while operation is still on-going. */
				}
				/* This code is returned if sensor fails to open. */
				else if (FAIL == resultCode) {
					mStatusTextView.setText(getString(R.string.epassport_open_failed));
				}

			}

			@Override
			public void
			onEPassportReaderClosed(ResultCode resultCode,
									CloseReasonCode closeReasonCode) {

				if (OK == resultCode) {
					/* Now that sensor is open, if user presses "mOpenRFButton" sensor should now
					 * close. To achieve this we change flag which controls what action button takes.
					 */
					mIsEPassportOpen = false;

					mReadICAOButton.setEnabled(false);
					mOpenRFButton.setEnabled(true);
					mOpenRFButton.setText(getString(R.string.open_epassport));
					mStatusTextView.setText(getString(R.string.epassport_closed));

				} else if (INTERMEDIATE == resultCode) {
					/* This code is never returned here. */

				} else if (FAIL == resultCode) {
					mStatusTextView.setText(getString(R.string.mrz_failed_close));
				}
			}

		});
	}

	/* Calls Credence APIs to open card reader. */
	private void
	openCtwoCardReader() {

		/* Let user know card reader will now try to be opened. */
		mStatusTextView.setText(getString(R.string.opening_card_reader));

		App.BioManager.cardOpenCommand(new Biometrics.CardReaderStatusListener() {
			@Override
			public void
			onCardReaderOpen(ResultCode resultCode) {

				if (OK == resultCode) {
					mIsEPassportOpen = true;
					mReadICAOButton.setEnabled(true);
					mOpenRFButton.setText(getString(R.string.close_epassport));
					mStatusTextView.setText(getString(R.string.epassport_opened));

				} else if (INTERMEDIATE == resultCode) {
					/* This code is never returned here. */

				} else if (FAIL == resultCode) {
					mStatusTextView.setText("Fail to open RFID reader");
				}
			}

			@SuppressLint("SetTextI18n")
			@Override
			public void
			onCardReaderClosed(ResultCode resultCode,
							   CloseReasonCode closeReasonCode) {

				if (OK == resultCode) {
					/* Now that sensor is open, if user presses "mOpenRFButton" sensor should now
					 * close. To achieve this we change flag which controls what action button takes.
					 */
					mIsEPassportOpen = false;

					mReadICAOButton.setEnabled(false);
					mOpenRFButton.setEnabled(true);
					mOpenRFButton.setText(getString(R.string.open_epassport));
					mStatusTextView.setText(getString(R.string.epassport_closed));

				} else if (INTERMEDIATE == resultCode) {
					/* This code is never returned here. */

				} else if (FAIL == resultCode) {
					mStatusTextView.setText("Fail to close RFID reader");
				}
			}
		});
	}

	/* Calls Credence APIs to read an ICAO document.
	 *
	 * @param dateOfBirth Date of birth on ICAO document (YYMMDD format).
	 * @param documentNumber Document number of ICAO document.
	 * @param dateOfExpiry Date of expiry on ICAO document (YYMMDD format).
	 */
	@SuppressLint("SetTextI18n")
	@SuppressWarnings("SpellCheckingInspection")
	private void
	readICAODocument(String mrz) {

		mICAOTextView.setText("");
		mICAOFaceImageView.setImageBitmap(null);
		mICAOFingerImageView.setImageBitmap(null);

		if (null == mrz || mrz.isEmpty()) {
			Log.w(TAG, "MRZ parameter INVALID, will not read ICAO document.");
			Toast.makeText(this,
					"MRZ parameter INVALID, will not read ICAO document.",
					Toast.LENGTH_LONG).show();
			return;
		}

		Log.d(TAG, "ID Doc Key = "+mIdDocumentKey);

		/* Disable button so user does not initialize another readICAO document API call. */
		mReadICAOButton.setEnabled(false);
		mStatusTextView.setText(getString(R.string.reading));

	}

	void displayCanCodedialogBox(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("ENTER KEY");

		// Set up the input
		final EditText input = new EditText(this);

		// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
		input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		builder.setView(input);

		// Set up the buttons
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mIdDocumentKey = input.getText().toString();
				readICAODocument(mIdDocumentKey);
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});

		builder.show();
	}

	private void setTACredenetials(String cvcaCertificates,
                                   String dvCertificates,
                                   String isCertificate,
                                   String isKey){
//	    App.BioManager.setLocalTACredentials(cvcaCertificates,
//                dvCertificates,
//                isCertificate,
//                isKey);
    }

    void displayCertificatesdialogBox(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Here is he certificates location :");

        String EAC_PATH = Environment.getExternalStorageDirectory().toString() + "/MRTD/EAC_Files/EAC_CREDENCE_01/";
        String CERT_CVCA_PATH = EAC_PATH + "certificates/CVCA/";
        String CERT_DV_PATH = EAC_PATH + "certificates/DV/";
        String CERT_IS_PATH = EAC_PATH + "certificates/IS/";
        String KEYS_EAC_PATH = EAC_PATH + "keys/";

        final TextView certificatesLocations = new TextView(this);
        certificatesLocations.setText("CVCA certificate: " + CERT_CVCA_PATH + "\n"
                +"DV certificate: " + CERT_DV_PATH + "\n"
                +"IS certificate: " + CERT_IS_PATH + "\n"
                +"IS key: " + KEYS_EAC_PATH);
        builder.setView(certificatesLocations);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setTACredenetials(CERT_CVCA_PATH,
                        CERT_DV_PATH,
                        CERT_IS_PATH,
                        KEYS_EAC_PATH);
            }
        });
        builder.show();
    }
}