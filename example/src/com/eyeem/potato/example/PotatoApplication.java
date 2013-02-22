package com.eyeem.potato.example;

import com.nostra13.universalimageloader.core.ImageLoader;

import android.app.Application;

public class PotatoApplication extends Application {

   private ImageLoader img_loader;

   public ImageLoader getImageLoader() {
      if (img_loader == null) {
         img_loader = ImageLoader.getInstance();
         // Initialize ImageLoader with configuration. Do it once.
         img_loader.init(ImageLoaderHelper.getConfiguration(this));
      }
      return img_loader;
   }

}
