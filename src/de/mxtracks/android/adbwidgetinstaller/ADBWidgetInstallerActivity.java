package de.mxtracks.android.adbwidgetinstaller;

/**
 * 
 * 
 * For RootTools see https://code.google.com/p/roottools/
 * For ADBWidget see http://code.google.com/p/secure-settings-widget/
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.stericson.RootTools.RootTools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ADBWidgetInstallerActivity extends Activity implements
		OnClickListener {
	private static final String TAG = "ADBInstaller";
	private TextView tvLog;
	private Button btnInstall, btnUninstall;
	private Boolean hasSu = false;
	private String widgetDownload = "http://secure-settings-widget.googlecode.com/files/ADBWidget-0.2-alpha.apk";
	private String widgetFilename = "ADBWidget.apk";
	private Context mContext = this;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		tvLog = (TextView) this.findViewById(R.id.tvLogDisplay);
		btnInstall = (Button) this.findViewById(R.id.btnInstall);
		btnUninstall = (Button) this.findViewById(R.id.btnUninstall);

		btnInstall.setOnClickListener(this);
		btnUninstall.setOnClickListener(this);

		btnInstall.setEnabled(false);
		btnUninstall.setEnabled(false);

		RootTools.debugMode = false;

		try {
			if (RootTools.isAccessGiven() && RootTools.isBusyboxAvailable()) {
				RootTools.log(getString(R.string.su_granted));
				tvLog.append("\n" + getString(R.string.su_granted));
				tvLog.append("\nBusybox found. Version: "
						+ RootTools.getBusyBoxVersion());
				this.hasSu = true;
				btnInstall.setEnabled(true);
				btnUninstall.setEnabled(true);
			}

		} catch (Exception e) {
			RootTools.log(getString(R.string.su_denied));
			tvLog.append("\n" + getString(R.string.su_denied));
			Toast.makeText(this, getString(R.string.su_denied_message),
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnInstall:
			tvLog.append("\nTrying to install");
			installADBWidget inst = new installADBWidget();
			inst.execute(new String[] { widgetDownload });
			break;
		case R.id.btnUninstall:
			// Only if su is given
			if (this.hasSu) {
				try {
					tvLog.append("\nMounting /system/ rw");
					RootTools.remount("/system", "rw");
					for (String line : RootTools.sendShell(
							"busybox rm /system/app/" + widgetFilename, 10000)) {
						tvLog.append("\n" + line);
					}
					tvLog.append("\nMounting /system/ ro");
					RootTools.remount("/system", "ro");

					tvLog.append("\n" + getString(R.string.uninstall_success));
					tvLog.append("\n"
							+ getString(R.string.reboot_after_uninstall_needed));
				} catch (Exception e) {
					tvLog.append("\n" + getString(R.string.uninstall_failed)
							+ e.getMessage());
				}
			}
			break;

		}

	}

	// AsyncTask to download the Widget and install it
	// as System App
	private class installADBWidget extends AsyncTask<String, String, String> {
		protected String doInBackground(String... urls) {
			publishProgress("downloading " + widgetFilename);
			if (downloadFile(urls[0], widgetFilename)) {
				publishProgress("download ok");
				if (hasSu) {
					try {
						RootTools.remount("/system", "rw");
						publishProgress("mounting /system/ rw");
						// copy file from temp dir to /system/app/
						String outFile = getApplicationContext().getFilesDir()
								.toString() + "/" + widgetFilename;
						publishProgress("cp " + outFile + " to /system/app/");
						for (String line : RootTools.sendShell("busybox cp "
								+ outFile + " /system/app/", 10000)) {
							publishProgress(line);
						}
						publishProgress("chmod 644 to /system/app/"
								+ widgetFilename);
						// chmod file to 644 rw r r
						for (String line : RootTools.sendShell(
								"busybox chmod 644 /system/app/"
										+ widgetFilename, 10000)) {
							publishProgress(line);
						}
						publishProgress("mounting /system/ ro");
						RootTools.remount("/system", "ro");
						// Remove the downloaded file
						publishProgress("removing temporary " + widgetFilename);
						// remove temp file
						for (String line : RootTools.sendShell("busybox rm "
								+ outFile, 10000)) {
							publishProgress(line);
						}
					} catch (Exception e) {
						return "SUFAILED";
					}
				}
				return "DONE";
			} else {
				return "DOWNLOADFAILED";
			}
		}

		@Override
		protected void onProgressUpdate(String... values) {
			super.onProgressUpdate(values);
			if (isCancelled()) {
				onCancelled();
				return;
			}
			tvLog.append("\n" + values[0]);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			btnInstall.setEnabled(false);
			btnUninstall.setEnabled(false);
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			btnInstall.setEnabled(true);
			btnUninstall.setEnabled(true);
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			btnInstall.setEnabled(true);
			btnUninstall.setEnabled(true);
			if (result == "DONE") {
				tvLog.append("\n" + getString(R.string.install_success));
				tvLog.append("\n" + getString(R.string.reboot_after_install_needed));
				showSuccessDialog();
			} else if (result == "SUFAILED") {
				tvLog.append("\n" + getString(R.string.install_failed));
			} else {
				tvLog.append("\n" + getString(R.string.download_failed, widgetDownload));
			}
		}

		private void showSuccessDialog() {
			// Tell the User that i can try to restart
			AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
			builder.setMessage(getString(R.string.success_dialog))
					.setCancelable(false)
					.setPositiveButton(getString(R.string.yes),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									// Try to restart
									try {
										RootTools.restartAndroid();
									} catch (Exception e) {
										RootTools.log(e.getMessage());
									}
								}
							})
					.setNegativeButton(getString(R.string.no),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			AlertDialog alert = builder.create();
			alert.show();
		}
	}

	// Downloader
	public Boolean downloadFile(String downloadUrl, String Filename) {
		try {
			Log.d(TAG, "Downloading " + Filename + " from " + downloadUrl);
			URL url = new URL(downloadUrl);
			File dir = new File(getApplicationContext().getFilesDir() + "/");
			HttpURLConnection c = (HttpURLConnection) url.openConnection();
		    c.setRequestMethod("GET");
		    c.setDoOutput(true);
		    c.connect();
		    FileOutputStream fos = new FileOutputStream(new File(dir,Filename));

		    InputStream in = c.getInputStream();

		    byte[] buffer = new byte[1024];
		    int len = 0;
		    while ( (len = in.read(buffer)) != -1 ) {
		         fos.write(buffer,0,len);
		    }
		    fos.close();
		    in.close();

			
//			URLConnection ucon = url.openConnection();
//
//			/*
//			 * Define InputStreams to read from the URLConnection.
//			 */
//			InputStream is = ucon.getInputStream();
//			BufferedInputStream bis = new BufferedInputStream(is);
//
//			/*
//			 * Read bytes to the Buffer until there is nothing more to read(-1).
//			 */
//			ByteArrayBuffer baf = new ByteArrayBuffer(50);
//			int current = 0;
//			while ((current = bis.read()) != -1) {
//				baf.append((byte) current);
//			}
//
//			FileOutputStream fos = new FileOutputStream(file);
//			fos.flush();
//			fos.close();
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

}