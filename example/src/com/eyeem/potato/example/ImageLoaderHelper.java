package com.eyeem.potato.example;

import java.io.File;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.os.Environment;

import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiscCache;
import com.nostra13.universalimageloader.cache.memory.impl.UsingFreqLimitedMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

public class ImageLoaderHelper {

   private static boolean use_mem_cache = true;
   public static boolean blink;

   public static String getCacheDir(Context ctx) {

      if (Environment.getExternalStorageState().equals(Environment.MEDIA_REMOVED)) {
         return ctx.getFileStreamPath("img_cache").getAbsolutePath();
      } else {
         return ctx.getExternalCacheDir() + "/eyeem/potato_img_cache";
      }
   }

   public static ImageLoaderConfiguration getConfiguration(Context ctx) {
      File cache_dir = new File(getCacheDir(ctx));

      if (!cache_dir.exists())
         cache_dir.mkdirs();
      MemoryInfo mi = new MemoryInfo();
      ActivityManager activityManager = (ActivityManager) ctx.getSystemService(Activity.ACTIVITY_SERVICE);
      activityManager.getMemoryInfo(mi);
      long availableMegs = mi.availMem / 1048576L;

      if (availableMegs < 100) // if we are running so low
         use_mem_cache = false;
      ImageLoaderConfiguration.Builder cfg_builder = new ImageLoaderConfiguration.Builder(ctx);
      cfg_builder.threadPoolSize(5);
      cfg_builder.discCache(new UnlimitedDiscCache(cache_dir));
      cfg_builder.defaultDisplayImageOptions(DisplayImageOptions.createSimple());
      if (use_mem_cache)
         cfg_builder.memoryCache(new UsingFreqLimitedMemoryCache(1000000));  // use 1/3 of the mem for img caching
      cfg_builder.defaultDisplayImageOptions(options);
      return cfg_builder.build();

   }

   static final DisplayImageOptions options;

   static {
      options = new DisplayImageOptions.Builder()
              .cacheInMemory()
              .cacheOnDisc()
                      //.fadeIn()
              .build();
   }
}
