package android.edu.rss.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.rssclient.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class ImageLoader implements Serializable {
    
private static final long serialVersionUID = 1L;

	MemoryCache memoryCache=new MemoryCache();
	FileCache fileCache;
	 
	private Map<ImageView, String> imageViews=Collections.synchronizedMap(new WeakHashMap<ImageView, String>());
	ExecutorService executorService; 
	  
	public ImageLoader(Context context){
	    fileCache=new FileCache(context);
	    executorService=Executors.newFixedThreadPool(5);
	}
	  
	final int stub_id = R.drawable.ic_launcher;
	//  final int stub_id = R.anim.progress_rotation;
	
	private ScaleType imageScaleType;
	
	public File getImageFile(String url) {
		return fileCache.getFile(url);
	}

    public void displayImage(String url, ImageView imageView) {
        imageViews.put(imageView, url);
        Bitmap bitmap = memoryCache.get(url);
        
        final Animation in = new AlphaAnimation(0.0f, 1.0f);
	    in.setDuration(100);
	    AnimationSet as = new AnimationSet(true);
	    as.addAnimation(in);
    
        imageScaleType = imageView.getScaleType();
        if(bitmap!=null) {
            imageView.setImageBitmap(bitmap);
            imageView.startAnimation(as);
		} else {
	        queuePhoto(url, imageView);
		}
    }
        
    private void queuePhoto(String url, ImageView imageView)
    {
        PhotoToLoad p=new PhotoToLoad(url, imageView);
        executorService.submit(new PhotosLoader(p));
    }
    
    private Bitmap getBitmap(String url) 
    {
        File f=fileCache.getFile(url);
        
        //from SD cache
        Bitmap b = decodeFile(f);
        if(b!=null)
            return b;
        
        //from web
        try {
            Bitmap bitmap = null;
            url = url.replaceAll(" ", "%20");
            URL imageUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection)imageUrl.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            InputStream is=conn.getInputStream();
            OutputStream os = new FileOutputStream(f);
            Utils.CopyStream(is, os);
            os.close();
            bitmap = decodeFile(f);
            return bitmap;
        } catch (Throwable ex){
           ex.printStackTrace();
           if(ex instanceof OutOfMemoryError)
               memoryCache.clear();
           return null;
        }
    }

    //decodes image and scales it to reduce memory consumption
    private Bitmap decodeFile(File f){
        try {
            //decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(f),null,o);
            
            //Find the correct scale value. It should be the power of 2.
            final int REQUIRED_SIZE=70;
            int width_tmp=o.outWidth, height_tmp=o.outHeight;
            while(true){
                if(width_tmp/2<REQUIRED_SIZE || height_tmp/2<REQUIRED_SIZE)
                    break;
                width_tmp/=2;
                height_tmp/=2;
            }
            
            //decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
//            o2.inSampleSize=scale;
            return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
        } catch (FileNotFoundException e) {}
        return null;
    }
    
    //Task for the queue
    private class PhotoToLoad
    {
        public String url;
        public ImageView imageView;
        public PhotoToLoad(String u, ImageView i){
            url=u; 
            imageView=i;
        }
    }
    
    class PhotosLoader implements Runnable {
        PhotoToLoad photoToLoad;
        PhotosLoader(PhotoToLoad photoToLoad){
            this.photoToLoad=photoToLoad;
        }
        
        public void run() {
            if(imageViewReused(photoToLoad))
                return;
            Bitmap bmp=getBitmap(photoToLoad.url);
            memoryCache.put(photoToLoad.url, bmp);
            if(imageViewReused(photoToLoad))
                return;
            BitmapDisplayer bd=new BitmapDisplayer(bmp, photoToLoad);
            Activity a=(Activity)photoToLoad.imageView.getContext();
            a.runOnUiThread(bd);
        }
    }
    
    boolean imageViewReused(PhotoToLoad photoToLoad){
        String tag=imageViews.get(photoToLoad.imageView);
        if(tag==null || !tag.equals(photoToLoad.url))
            return true;
        return false;
    }
    
    //Used to display bitmap in the UI thread
    class BitmapDisplayer implements Runnable
    {
        Bitmap bitmap;
        PhotoToLoad photoToLoad;
        public BitmapDisplayer(Bitmap b, PhotoToLoad p){bitmap=b;photoToLoad=p;}
        public void run()
        {
            if(imageViewReused(photoToLoad))
                return;
            if(bitmap!=null) {
//                final Animation in = new AlphaAnimation(0.0f, 1.0f);
//            in.setDuration(500);
//            AnimationSet as = new AnimationSet(true);
//            as.addAnimation(in);
            
                photoToLoad.imageView.setScaleType(imageScaleType);
                if (photoToLoad.imageView.getContentDescription().equals("should_be_resized")) {
                final double viewWidthToBitmapWidthRatio = (double)photoToLoad.imageView.getWidth() / (double)bitmap.getWidth();
                photoToLoad.imageView.getLayoutParams().height = (int) (bitmap.getHeight() * viewWidthToBitmapWidthRatio);
              }
                photoToLoad.imageView.setImageBitmap(bitmap);
//                photoToLoad.imageView.startAnimation(as);
                
            } else {
//                photoToLoad.imageView.setImageResource(stub_id);
            }
        }
    }

    public void clearCache() {
        memoryCache.clear();
        fileCache.clear();
    }

    class AnimationRunner implements Runnable {

      private final AnimationDrawable m_Animation;

      public AnimationRunner(AnimationDrawable animation) {
        m_Animation = animation;
      }

      @Override
      public void run() {
        m_Animation.start();
      }

    }

}