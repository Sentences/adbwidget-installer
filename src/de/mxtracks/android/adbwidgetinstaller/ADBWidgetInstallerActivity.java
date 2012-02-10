package de.mxtracks.android.adbwidgetinstaller;

/**
 * 
 * 
 * For RootTools see https://code.google.com/p/roottools/
 * For ADBWidget see http://code.google.com/p/secure-settings-widget/
 */

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.stericson.RootTools.RootTools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class ADBWidgetInstallerActivity extends Activity implements OnClickListener {
	private EditText tvLog;
	private Button btnInstall, btnUninstall;
	private Boolean hasSu = false;
	private String widgetFilename = "ADBWidget.apk";
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        tvLog = (EditText) this.findViewById(R.id.tvLogDisplay);
        btnInstall = (Button) this.findViewById(R.id.btnInstall);
        btnUninstall = (Button) this.findViewById(R.id.btnUninstall);
        
        btnInstall.setOnClickListener(this);
        btnUninstall.setOnClickListener(this);
        
        btnInstall.setEnabled(false);
        btnUninstall.setEnabled(false);
        
        RootTools.debugMode = true;
        
        try {
	        if (RootTools.isAccessGiven() && RootTools.isBusyboxAvailable()){
	        	RootTools.log(getString(R.string.su_granted));
	        	tvLog.setText(tvLog.getText() + "\n" + getString(R.string.su_granted));
	        	tvLog.setText(tvLog.getText() + "\nBusybox found. Version: " + RootTools.getBusyBoxVersion());
	        	this.hasSu = true;
	        	btnInstall.setEnabled(true);
	            btnUninstall.setEnabled(true);
	        }
        
        }
        catch (Exception e){
        	RootTools.log(getString(R.string.su_denied));
        	tvLog.setText(tvLog.getText() + "\n" + getString(R.string.su_denied));
        	Toast.makeText(this, getString(R.string.su_denied_message), Toast.LENGTH_LONG).show();
        }
    }

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.btnInstall:
			//Only if su is given
			if (this.hasSu) {
				try {
					InputStream in = null;
			        OutputStream out = null;
			        
					AssetManager assetManager = getAssets();
					in = assetManager.open(widgetFilename);
					String outFile = this.getFilesDir().toString() + "/" + widgetFilename;
					out = new FileOutputStream(outFile);
					RootTools.log("Copy " + widgetFilename + " to " + outFile);
					copyFile(in, out);
					in.close();
			        in = null;
			        out.flush();
			        out.close();
			        out = null;

					RootTools.remount("/system", "rw");
					tvLog.append("\nMounting /system/ rw");
					// copy file from temp dir to /system/app/
					tvLog.append("\ncp "+outFile+" to /system/app/");
					for (String line : RootTools.sendShell("busybox cp "+outFile+" /system/app/",10000))
	                {
						tvLog.append("\n" + line);
						RootTools.log("Line " + line);
	                }
					tvLog.append("\nchmod 644 to /system/app/" + widgetFilename);
					// chmod file to 644 rw r r
					for (String line : RootTools.sendShell("busybox chmod 644 /system/app/" + widgetFilename,10000))
	                {
						tvLog.append("\n" + line);
						RootTools.log("Line " + line);
	                }
					tvLog.append("\nMounting /system/ ro");
					RootTools.remount("/system", "ro");

					tvLog.append("\nRemove temporary " + widgetFilename);
					// remove temp file
					for (String line : RootTools.sendShell("busybox rm "+outFile,10000))
	                {
						tvLog.append("\n" + line);
						RootTools.log("Line " + line);
	                }
					tvLog.append("\n" + getString(R.string.install_success));
					tvLog.append("\n" + getString(R.string.reboot_after_install_needed));
					
					// Tell the User that i can try to restart
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setMessage(getString(R.string.success_dialog))
							.setCancelable(false)
							.setPositiveButton(getString(R.string.yes),
									new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog,
												int id) {
											// Try to restart
											try {
												RootTools.restartAndroid();
											}
											catch (Exception e) {
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
				catch (Exception e) {
					tvLog.append("\n" + getString(R.string.install_failed) + e.getMessage());
				}
			}
			break;
		case R.id.btnUninstall:
			//Only if su is given
			if (this.hasSu) {
				try {
					tvLog.append("\nMounting /system/ rw");
					RootTools.remount("/system", "rw");
					for (String line : RootTools.sendShell("busybox rm /system/app/"+ widgetFilename,10000))
	                {
						tvLog.append("\n" + line);
						RootTools.log("Line " + line);
	                }
					tvLog.append("\nMounting /system/ ro");
					RootTools.remount("/system", "ro");
					
					tvLog.append("\n" + getString(R.string.uninstall_success));
					tvLog.append("\n" + getString(R.string.reboot_after_uninstall_needed));
				}
				catch (Exception e){
					tvLog.append("\n" + getString(R.string.uninstall_failed) + e.getMessage());
				}
			}
			break;
		
		} 
		
	}
	
	private void copyFile(InputStream in, OutputStream out) throws IOException {
	    byte[] buffer = new byte[1024];
	    int read;
	    while((read = in.read(buffer)) != -1){
	      out.write(buffer, 0, read);
	    }
	}

}