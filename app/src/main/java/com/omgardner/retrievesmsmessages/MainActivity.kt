package com.omgardner.retrievesmsmessages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.util.JsonWriter
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileWriter
import java.io.IOException
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {
    // a unique integer code to identify the specific permission being requested
    //    this is necessary because permission requests are handled asynchronously
    val PERMISSION_REQUEST_CODE = 1

    // prevent access to data if app has insufficient permissions
    var allPermissionsGranted : Boolean by Delegates.observable(false) { _, _, newValue ->
        btnReadSmsInbox.isClickable = newValue
        if (newValue) {
            txtPermissionStatus.text = "YES"
            txtPermissionStatus.setTextColor(Color.GREEN)

            btnReadSmsInbox.setBackgroundColor(
                ContextCompat.getColor(applicationContext, R.color.colorPrimaryDark)
            )
        } else {
            txtPermissionStatus.text = "NO"
            txtPermissionStatus.setTextColor(Color.RED)

            btnReadSmsInbox.setBackgroundColor(Color.GRAY)
        }
    }

    // disable the button if the file hasn't been prepared yet
    var isFileReadyForDownload : Boolean by Delegates.observable(false) { _, _, newValue ->

            var color = if (newValue) {
                ContextCompat.getColor(applicationContext, R.color.colorPrimaryDark)}
            else {
                Color.GRAY
            }

            btnShareFile.isClickable = newValue
            btnShareFile.setBackgroundColor(color)
    }


    // first code that gets run when an activity is called through the AndroidManifest.xml
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val resultJsonFilepath = File(filesDir, "smsData.json")

        // trigger the observable variable to update the UI based on it's state.
        allPermissionsGranted = false
        isFileReadyForDownload = false

        // request permissions
        btnRequestPermission.setOnClickListener {
            // https://developer.android.com/training/permissions/requesting

            // instantiate list of desired permissions for this App
            var permissionList = arrayOf(Manifest.permission.READ_SMS)
            // filter the list to contain only permissions that have NOT been granted
            permissionList.filter{
                ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
            }

            if (permissionList.isNotEmpty()){
                // request all permissions that haven't been granted yet.
                ActivityCompat.requestPermissions(this@MainActivity, permissionList, PERMISSION_REQUEST_CODE)
            } else {
                // all permissions within the permissionList have already been granted.
                // update the variable tracking this condition
                allPermissionsGranted = true
            }
        }

        // read the SMS Inbox and save it to RESULT_JSON_FILEPATH
        btnReadSmsInbox.setOnClickListener {
            // double check that the permission has already been granted
            if (!allPermissionsGranted) {
                println("Cannot read SMS due to insufficient permissions.")
                Toast.makeText(applicationContext, "Cannot read SMS due to insufficient permissions.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(applicationContext, "Retrieving the SMS data from the content provider.", Toast.LENGTH_LONG).show()
            // https://developer.android.com/guide/topics/providers/content-provider-basics

            // nullable string
            // https://kotlinlang.org/docs/reference/java-interop.html#null-safety-and-platform-types

            class SmsTypes(val type: String, val uri : Uri, val projection: Array<String>)
            val smsTypesList = arrayOf(
                // inbox: received SMS messages
                SmsTypes("Inbox",
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms.Inbox.DATE,
                        Telephony.Sms.Inbox.BODY,
                        Telephony.Sms.Inbox.ADDRESS
                    )
                ),
                // Outbox: sent SENT messages
                SmsTypes("Outbox",
                    Telephony.Sms.Outbox.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms.Outbox.DATE,
                        Telephony.Sms.Outbox.BODY,
                        Telephony.Sms.Outbox.ADDRESS)
                )
            )

            // open json file in write mode
            val fw = FileWriter(resultJsonFilepath)
            val jw = JsonWriter(fw)
            jw.beginArray()

            for (smsType in smsTypesList){
                // query the CONTENT_URI for the current SMS type e.g. Inbox
                var cursor = contentResolver.query(
                    smsType.uri,
                    smsType.projection, // DEBUG: null means all columns returned
                    null,
                    null,
                    null
                )
                // https://kotlinlang.org/docs/reference/scope-functions.html#apply
                cursor?.apply{
                    while(moveToNext()) {
                        // create an object for each record in the cursor result
                        jw.beginObject()
                        for (i in 0 until columnCount) {
                            jw.name(getColumnName(i)).value(getString(i))
                            Log.i("msg", "${getColumnName(i)},${getString(i)}")
                        }
                        // indicates whether the message is from the Inbox or Outbox
                        jw.name("sms_type").value(smsType.type)
                        Log.i("msg", smsType.type)
                        jw.endObject()
                    }
                    Log.i("msg", "")
                }?.close()
                Log.i("debug", "${smsType.type} processed.")

            }
            jw.endArray()
            fw.close()

            // once the file has been successfully written
            isFileReadyForDownload = true
            println("finished saving of file")
            Toast.makeText(applicationContext, "Saved file to $ $resultJsonFilepath ", Toast.LENGTH_SHORT).show()
        }

        // send the file to an Intent
        btnShareFile.setOnClickListener {
            // https://developer.android.com/training/secure-file-sharing/share-file
            Toast.makeText(applicationContext, "Starting Intent.", Toast.LENGTH_SHORT).show()

            // get the URI from the RESULT_JSON_FILEPATH : File object
            val fileUri = FileProvider.getUriForFile(this@MainActivity, "com.omgardner.fileprovider", resultJsonFilepath)

            if (fileUri != null) {
                // https://developer.android.com/training/sharing/send#send-binary-content
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    type = contentResolver.getType(fileUri)
                }
                startActivity(Intent.createChooser(shareIntent, "SHARE YOUR SOUL TO:"))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        // overriding the onRequestPermissionResult
        // The callback is passed the same request code you passed to requestPermissions()
        // below is a switch / case/ when statement.
        when (requestCode) {
            // this is the request that we are interested in when the requestCode matches our request
            PERMISSION_REQUEST_CODE -> {
                // check to see if the permission is granted.
                allPermissionsGranted = (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

}
