package com.example.scopestorageandroid.ui

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.scopestorageandroid.adapters.ImageAdapter
import com.example.scopestorageandroid.databinding.ActivityMainBinding
import com.example.scopestorageandroid.model.SharedStoragePhoto
import com.example.scopestorageandroid.utils.hasReadPremission
import com.example.scopestorageandroid.utils.hasWritePremission
import com.example.scopestorageandroid.utils.MinSdk29
import com.example.scopestorageandroid.utils.sdk29AndUp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.util.*

const val TAG="MainActivity"
class MainActivity : AppCompatActivity() {

    lateinit var permisssionLauncher: ActivityResultLauncher<Array<String>>
    lateinit var imageAdapter: ImageAdapter
    lateinit var contentObserver: ContentObserver
    lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var deletedImageUri: Uri? = null
    lateinit var activityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
        imageAdapter= ImageAdapter(){   photo->
            MaterialAlertDialogBuilder(this )
                .setTitle("Alert Dialog")
                .setMessage("Do want to delete")
                .setNegativeButton("Delete"){dialog,where->
                    lifecycleScope.launch {
                        deleteImageFromExternalStorage(photo.contentUri)
                    }
                    dialog.dismiss()
                }
                .setNeutralButton("Cancel"){dialog,where->
                    dialog.dismiss()
                }
                .show()
        }


        permisssionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions->
            Log.d(TAG,permissions.toString())
            if (permissions[Manifest.permission.READ_EXTERNAL_STORAGE]==false){
                Log.d(TAG,permissions.toString())
            }


        }

        intentSenderLauncher=registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){
            results->
            if(results.resultCode== RESULT_OK){
                if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    lifecycleScope.launch {
                        deleteImageFromExternalStorage(deletedImageUri ?: return@launch)
                    }
                }
                Toast.makeText(this@MainActivity, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Photo couldn't be deleted", Toast.LENGTH_SHORT).show()
            }
        }


        initContentObserver()
        loadImagesIntoRecyclerView()
        activityMainBinding.rvImages.apply {
            adapter=imageAdapter
            layoutManager=StaggeredGridLayoutManager(3,RecyclerView.VERTICAL)
        }



        val takePhoto=registerForActivityResult(ActivityResultContracts.TakePicturePreview()){
            bitmap->
            lifecycleScope.launch {
                Log.d(TAG,Thread.currentThread().name)
                val savedSucces= saveToExternalStorage(UUID.randomUUID().toString(),bitmap)
                if (savedSucces)
                    Toast.makeText(this@MainActivity,"Saved",Toast.LENGTH_LONG).show()
                else
                    Toast.makeText(this@MainActivity,"Failed",Toast.LENGTH_LONG).show()
            }
        }


        activityMainBinding.tabkePicBtn.setOnClickListener{
            requestPermission()
            if((hasWritePremission(this)|| MinSdk29()) && hasReadPremission(this))
                takePhoto.launch()
        }

    }

    private fun loadImagesIntoRecyclerView(){
        lifecycleScope.launch {
            val photos=loadFromExternalStorage()
            imageAdapter.submitList(photos)
        }
    }

    private fun initContentObserver(){
        contentObserver=object : ContentObserver(null){
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if((hasWritePremission(this@MainActivity)|| MinSdk29()) && hasReadPremission(this@MainActivity))
                    loadImagesIntoRecyclerView()

            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }


    private suspend fun saveToExternalStorage(filename: String, bitmap: Bitmap) :Boolean{

       return withContext(Dispatchers.IO){
           Log.d(TAG,Thread.currentThread().name)
           val imageCollection= sdk29AndUp {
               MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
           } ?:MediaStore.Images.Media.EXTERNAL_CONTENT_URI

           val contentValues=ContentValues().apply {
               put(MediaStore.Images.Media.DISPLAY_NAME,"$filename.jpg")
               put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
               put(MediaStore.Images.Media.WIDTH,bitmap.width)
               put(MediaStore.Images.Media.HEIGHT,bitmap.height)
           }

           try {
               contentResolver.insert(imageCollection,contentValues)?.also { uri->
                   contentResolver.openOutputStream(uri).use { outputStream->
                       if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,outputStream))
                           throw IOException("Could not save Bitmap")
                   }
               }?:throw IOException("Could not create MediaStore Entry")
               true
           }catch (e:IOException){
               e.printStackTrace()
               false
           }
       }
    }

    private suspend fun loadFromExternalStorage():List<SharedStoragePhoto>{
        return withContext(Dispatchers.IO) {

            val collection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )
            val photos = mutableListOf<SharedStoragePhoto>()
            contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while(cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    photos.add(SharedStoragePhoto(id, displayName, width, height, contentUri))
                }
                photos.toList()
            } ?: listOf()
        }
    }

    private suspend fun deleteImageFromExternalStorage(photoUri : Uri){

        withContext(Dispatchers.IO){
            try {
                contentResolver.delete(photoUri,null,null)
            }catch (e:SecurityException){
                e.printStackTrace()
                val intentSender= when {
                    Build.VERSION.SDK_INT>=Build.VERSION_CODES.R -> {
                        MediaStore.createDeleteRequest(contentResolver, listOf(photoUri)).intentSender
                    }
                    Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q ->{
                        val recoverableSecurityException=  e as? RecoverableSecurityException
                        recoverableSecurityException?.userAction?.actionIntent?.intentSender
                    }
                    else -> null
                }

                intentSender?.let { intentSender->
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }
        }

    }

    private fun requestPermission() {
        var permissionList= mutableListOf<String>()

        if (!(hasWritePremission(this) || MinSdk29()))
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (!hasReadPremission(this))
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE)

        Log.d(TAG,permissionList.toString())

        if (permissionList.isNotEmpty())
            permisssionLauncher.launch(permissionList.toTypedArray())

        permissionList.clear()
    }




}