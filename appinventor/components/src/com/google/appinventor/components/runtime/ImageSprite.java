// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.MediaUtil;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import java.io.IOException;

/**
 * Simple image-based Sprite.
 *
 */
@DesignerComponent(version = YaVersion.IMAGESPRITE_COMPONENT_VERSION,
    description = "<p>A 'sprite' that can be placed on a " +
    "<code>Canvas</code>, where it can react to touches and drags, " +
    "interact with other sprites (<code>Ball</code>s and other " +
    "<code>ImageSprite</code>s) and the edge of the Canvas, and move " +
    "according to its property values.  Its appearance is that of the " +
    "image specified in its <code>Picture</code> property (unless its " +
    "<code>Visible</code> property is <code>False</code>.</p> " +
    "<p>To have an <code>ImageSprite</code> move 10 pixels to the left " +
    "every 1000 milliseconds (one second), for example, " +
    "you would set the <code>Speed</code> property to 10 [pixels], the " +
    "<code>Interval</code> property to 1000 [milliseconds], the " +
    "<code>Heading</code> property to 180 [degrees], and the " +
    "<code>Enabled</code> property to <code>True</code>.  A sprite whose " +
    "<code>Rotates</code> property is <code>True</code> will rotate its " +
    "image as the sprite's <code>Heading</code> changes.  Checking for collisions " +
    "with a rotated sprite currently checks the sprite's unrotated position " +
    "so that collision checking will be inaccurate for tall narrow or short " +
    "wide sprites that are rotated.  Any of the sprite properties " +
    "can be changed at any time under program control.</p> ",
    category = ComponentCategory.ANIMATION)
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.INTERNET")
public class ImageSprite extends Sprite {
  private final Form form;
  private BitmapDrawable drawable;
  private int widthHint = LENGTH_PREFERRED;
  private int heightHint = LENGTH_PREFERRED;
  private String picturePath = "";  // Picture property
  private boolean rotates;

  private Matrix mat;

  private Bitmap unrotatedBitmap;
  private Bitmap rotatedBitmap;
  private Bitmap scaledBitmap;

  private BitmapDrawable rotatedDrawable;
  private double cachedRotationHeading;
  private boolean rotationCached;

  private boolean imageAspectRatioEnabled; //Stop the sprite image from being distorted from original image size
  private double imageScaleRatio;
  private double imageWidth;
  private double imageHeight;
  private boolean imageFillCanvas;
  private boolean imageAutoSize;
  

  //GH 03.09.2013 Enhancement 1
  //Variables to control Sprite animation
  private String frameImageListStr;   //List of image to be used in the image frame animation as a single string
  private String[] frameImageList;    //List of image to be used in the image frame animation
  private int frameQty;               //Total number of image frames in image list
  private int frameUse;               //Current image frame to be displayed
  private int frameStart;             //First image frame to be displayed in the FrameAnimation
  private int frameEnd;               //Last image frame to be displayed in the FrameAnimation
  private int frameSpeed;             //Interval before the animation image frame is chnaged
  private int frameSpeedDirection;    //Flag to play animation backwards
  private int frameSpeedCounter;      //This counts up and when is = to frameSpeed will display then next animation Image frame
  
    //Variables to control Sprite image heading
  private boolean imageHeadingSeperate;    // Seperate the image heading from travel heading
  private double imageHeading;             // heading of the image
  private double imageRotateSpeed;         // speed of image heading change in degrees
  private boolean imagePointTowardsLocked; // Lock image on and follow sprite set in ImagePointTowards
  private Sprite imageTargetObject;        //Target of sprite to be locked on to
  /**
   * Constructor for ImageSprite.
   *
   * @param container
   */
  public ImageSprite(ComponentContainer container) {
    super(container);
    form = container.$form();
    mat = new Matrix();
    rotates = true;
    rotationCached = false;

    //Variables to control Sprite animation
    frameImageListStr = "";
    frameQty = 0;
    frameUse = 1;
    frameStart = 1;
    frameEnd = 1;
    frameSpeed = 80;
    frameSpeedCounter = 0;
    frameSpeedDirection = 0;

    speed = 0;

    imageHeadingSeperate = false;
    imageHeading = 0;
    imageRotateSpeed = 0;
    imagePointTowardsLocked = false;
    imageTargetObject = null;

    imageScaleRatio = 1;
    imageAspectRatioEnabled = false;
    imageFillCanvas = false;
    imageAutoSize = false;
  }

  /**
   * If sprite is set to fill canvas or autosize use the
   * follow code to rescale the sprite
   */
  public void resizeSprite() {
    if (widthHint == LENGTH_PREFERRED || heightHint == LENGTH_PREFERRED) {
      //set width/height to size of original image size
      imageAutoSize = true;
      imageFillCanvas = false;
    } else if (widthHint == LENGTH_FILL_PARENT || heightHint == LENGTH_FILL_PARENT){
      //set width/height to fill canvas
      imageAutoSize = false;
      imageFillCanvas = true;
    }
    
    if (imageFillCanvas) {
      if (imageAspectRatioEnabled) {
        //If aspect ratio ON then calculate the best fit for the image on the canvas
        if (canvas.Height() - (canvas.Width() * imageScaleRatio) > 0 ) {
          widthHint = canvas.Width();
          heightHint =(int) (canvas.Width() * imageScaleRatio);
        } else {
          widthHint =  (int) (canvas.Height() / imageScaleRatio);
          heightHint = canvas.Height();
        }
        
      } else {
        //If aspect ratio OFF then make the image fill the canvas
        widthHint = canvas.Width();
        heightHint = canvas.Height();
      }
    } else if (imageAutoSize) {
      //If autosize then reset image with to size of original
      widthHint = (int) imageWidth;
      heightHint = (int) imageHeight;
    }
  }
  
  
  public void onDraw(android.graphics.Canvas mcanvas) {
    if (unrotatedBitmap != null && visible) {
      
      int xinit;
      int yinit;
      resizeSprite();
      if (imageFillCanvas) {
        //force sprite to center of canvas
        xinit = (canvas.Width() / 2 ) - (widthHint / 2);
        yinit = (canvas.Height() / 2 ) - (heightHint / 2);
      } else {
        //offset sprite to orgin point
        xinit =  (int) Math.round(xLeft - OriginOffsetX());
        yinit =  (int) Math.round(yTop - OriginOffsetY());
      }
        
      int w = widthHint;
      int h = heightHint;
      

      //set the image heading to seperate from the travel heading is user sets
      //ImageImageHeadingSeperate = true otherwise use the travel heading for the
      //Image heading
      double headingUse = imageHeadingSeperate ?  ImageHeading() : Heading();

      // If the sprite doesn't rotate,  use the original drawable
      // otherwise use the bitmapDrawable
      if (!rotates) {
        drawable.setBounds(xinit, yinit, xinit + w, yinit + h);
        drawable.draw(mcanvas);
      } else {
        // compute the new rotated image if the heading has changed
        if (!rotationCached || (cachedRotationHeading != headingUse)) {
          // Set up the matrix for the rotation transformation
          // Rotate around the center of the sprite image (w/2, h/2)
          // TODO(halabelson): Add a way for the user to specify the center of rotation.
          mat.setRotate((float) -headingUse, w / 2, h / 2);
          // We must scale the unrotated Bitmap to be the user specified size before
          // rotating.
          if (w != unrotatedBitmap.getWidth() || h != unrotatedBitmap.getHeight()) {
            scaledBitmap = Bitmap.createScaledBitmap(unrotatedBitmap, w, h, true);
          }
          else {
            scaledBitmap = unrotatedBitmap;
          }
          // Next create the rotated bitmap
          // Careful: We use getWidth and getHeight of the unrotated bitmap, rather than the
          // Width and Height of the sprite.  Doing the latter produces an illegal argument
          // exception in creating the bitmap, if the user sets the Width or Height of the
          // sprite to be larger than the image size.
          rotatedBitmap = Bitmap.createBitmap(
              scaledBitmap,
              0, 0,
              scaledBitmap.getWidth(), scaledBitmap.getHeight(),
              mat, true);
          // make a drawable for the rotated image and cache the heading
          rotatedDrawable = new BitmapDrawable(rotatedBitmap);
          cachedRotationHeading = headingUse;
        }
        // Position the drawable:
        // We want the center of the image to remain fixed under the rotation.
        // To do this, we have to take account of the fact that, since the original
        // and the rotated bitmaps are rectangular, the offset of the center point from (0,0)
        // in the rotated bitmap will in general be different from the offset
        // in the unrotated bitmap.  Namely, rather than being 1/2 the width and height of the
        // unrotated bitmap, the offset is 1/2 the width and height of the rotated bitmap.
        // So when we display on the canvas, we  need to displace the upper left away
        // from (xinit, yinit) to take account of the difference in the offsets.
        rotatedDrawable.setBounds(
            xinit + w / 2 - rotatedBitmap.getWidth() / 2,
            yinit + h / 2 - rotatedBitmap.getHeight() / 2 ,
            // add in the width and height of the rotated bitmap
            // to get the other right and bottom edges
            xinit + w / 2 + rotatedBitmap.getWidth() / 2,
            yinit + h / 2 + rotatedBitmap.getHeight() / 2);
        rotatedDrawable.draw(mcanvas);
      }
    }
  }

  /**
   * Returns the path of the sprite's picture
   *
   * @return  the path of the sprite's picture
   */
  @SimpleProperty(
      description = "The picture that determines the sprite's appearence",
      category = PropertyCategory.APPEARANCE)
  public String Picture() {
    return picturePath;
  }

  /**
   * Specifies the path of the sprite's picture
   *
   * <p/>See {@link MediaUtil#determineMediaSource} for information about what
   * a path can be.
   *
   * @param path  the path of the sprite's picture
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_ASSET,
      defaultValue = "")
  @SimpleProperty
  public void Picture(String path) {
    PictureBeforeChanges();    //Call event to allow user to assign their own blocks before the picture changes
    picturePath = (path == null) ? "" : path;
    try {
      drawable = MediaUtil.getBitmapDrawable(form, picturePath);
    } catch (IOException ioe) {
      Log.e("ImageSprite", "Unable to load " + picturePath);
      drawable = null;
    }
    // NOTE(lizlooney) - drawable can be null!
    if (drawable != null) {
      // we'll need the bitmap for the drawable in order to rotate it
      unrotatedBitmap = drawable.getBitmap();
      imageWidth = drawable.getBitmap().getWidth();
      imageHeight = drawable.getBitmap().getHeight();
      imageScaleRatio = imageHeight / imageWidth;
    } else {
      unrotatedBitmap = null;
    }
    registerChange();
    frameSpeedCounter = 0;  //when animation frame changed reset count next frame
    PictureAfterChanges();  //Call event to allow user to assign their own blocks after the picture changes
  }

  // The actual width/height of an ImageSprite whose Width/Height property is set to Automatic or
  // Fill Parent will be the width/height of the image.

  @Override
  @SimpleProperty
  public int Height() {
    return heightHint;
  }

  @Override
  @SimpleProperty
  public void Height(int height) {
    imageFillCanvas = false;
    imageAutoSize = false;
    heightHint = height;
    if (imageAspectRatioEnabled) {
      //Scale width to ratio to original height
      widthHint = (int) (heightHint / imageScaleRatio);
    }
    registerChange();
  }

  @Override
  @SimpleProperty
  public int Width() {
    return widthHint;
  }

  @Override
  @SimpleProperty
  public void Width(int width) {
    imageFillCanvas = false;
    imageAutoSize = false;
    widthHint = width;
    if (imageAspectRatioEnabled) {
      //Scale height to ratio to original width
      heightHint = (int) (widthHint * imageScaleRatio);
    }
    registerChange();
  }

  @SimpleFunction
  public void FillCanvas() {
    imageFillCanvas = true;
    imageAutoSize = false;
    registerChange();
  }

  @SimpleFunction
  public void AutoSize() {
    imageFillCanvas = false;
    imageAutoSize = true;
    registerChange();
  }


  /**
   * ImageAspectRatioEnabled -
   * True  - sprite will not distort the image when width or height is changed the image will be scales up/down automatically
   * False - Sprite width and height will be treat seperately
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "False")
  @SimpleProperty
  public void KeepAspectRatio(boolean tempVal) {
    imageAspectRatioEnabled = tempVal;
    registerChange();
  }

  @SimpleProperty(
      description = "keep the image aspect ratio inline with the ordginal image size to stop image distortion.",
      category = PropertyCategory.APPEARANCE)
  public boolean KeepAspectRatioEnabled() {
    return imageAspectRatioEnabled;
  }


  /**
   * Rotates property getter method.
   *
   * @return  {@code true} indicates that the image rotates to match the sprite's heading
   * {@code false} indicates that the sprite image doesn't rotate.
   */
  @SimpleProperty(
      description = "If true, the sprite image rotates to match the sprite's heading. " +
      "If false, the sprite image does not rotate when the sprite changes heading. " +
      "The sprite rotates around its centerpoint.",
      category = PropertyCategory.BEHAVIOR)
  public boolean Rotates() {
    return rotates;
  }

  /**
   * Rotates property setter method
   *
   * @param rotates  {@code true} indicates that the image rotates to match the sprite's heading
   * {@code false} indicates that the sprite image doesn't rotate.
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "True")
  @SimpleProperty
    public void Rotates(boolean rotates) {
    this.rotates = rotates;
    registerChange();
  }
  //-------------------------------------------------------------------------------------------------
  //Code to enable imageheading separate from heading
  //Enhancement 3.3 - Image heading settings

  //ImageHeadingSeperate          - added 30/09/2013
  //ImageHeading                  - added 30/09/2013
  //ImageHeadingRotateSpeed       - added 30/09/2013
  //ImagePointTowards
  //ImagePointTowardsLockEnabled


  //Image rotation and display angle

  /**
   * ImageHeadingSeperate -
   * True  - image of sprite using ImageHeading to set image orientation
   * False - image of sprite using Heading to set image orientation
   */
  //@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
 //     defaultValue = "False")
 // @SimpleProperty
 // public void ImageHeadingSeperate(boolean tempVal) {
 //   imageHeadingSeperate = tempVal;
 //   registerChange();
 // }

  @SimpleFunction
  public void ImageHeadingUsesHeading() {
     imageHeadingSeperate = false;
     registerChange();
  }

  
  //@SimpleProperty(
  //    description = "If true, the sprite image rotates to match the sprite's heading. " +
  //    "If false, the sprite image does not rotate when the sprite changes heading. " +
  //    "The sprite rotates around its centerpoint.",
  //    category = PropertyCategory.BEHAVIOR)
  //public boolean ImageHeadingSeperate() {
  //  return imageHeadingSeperate;
  //}


  /**
   * ImageHeading -
   * Set the image heading
   **/
   @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
       defaultValue = "0")
   @SimpleProperty
   public void ImageHeading(double tempVal) {
     imageHeadingSeperate = true;
     imageRotateSpeed = 0;
     imageTargetObject = null;
     setImageHeading(tempVal);
   }

   public void setImageHeading(double tempVal) {
     imageHeadingSeperate = true;
     imageHeading = tempVal;
     //if heading is not between 0 - 359 recalculate so it's within these limits
     imageHeading = (float) (imageHeading - (Math.floor(imageHeading / 360) * 360));
     imageHeading = imageHeading < 0 ? 360 + imageHeading : imageHeading;
     registerChange();
   }

   @SimpleProperty(description = "Current angle of imagesprite",
       category = PropertyCategory.APPEARANCE)
   public double ImageHeading() {
     return imageHeading;
   }


  /**
   * ImageRotateSpeed -
   * negative value  - rotates image anticlockwise set degree every 1ms
   * 0 - not image rotation
   * positive value  - rotates image clockwise set degree every 1ms
   **/
   @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
       defaultValue = "0")
   @SimpleProperty
   public void ImageRotateSpeed(double tempVal) {
     imageRotateSpeed = tempVal;
   }

   @SimpleProperty(description = "The number of sprites in the spritesheet width",
       category = PropertyCategory.APPEARANCE)
   public double ImageRotateDelay() {
     return imageRotateSpeed;
   }



   /**
    * Turns this sprite to point towards a given other sprite.
    *
    * @param target the other sprite to point towards
    */
   @SimpleFunction(
     description = "<p>Turns the sprite to point towards a designated " +
     "target sprite. The new heading will be parallel to the line joining " +
     "the centerpoints of the two sprites.</p>")
   public void ImagePointTowards(Sprite target) {
     imageHeadingSeperate = true;
     ImageHeading(-Math.toDegrees(Math.atan2(
        // we adjust for the fact that the sprites' X() and Y()
        // are not the center points.
        target.Y() - Y() + (target.Height() - Height()) / 2,
        target.X() - X() + (target.Width() - Width()) / 2)));
     imageTargetObject = target;
   }

   /**
    * Turns this sprite to point towards a given point.
    *
    * @param x parameter of the point to turn to
    * @param y parameter of the point to turn to
    */
   @SimpleFunction(
     description = "<p>Turns the sprite to point towards the point " +
     "with coordinates as (x, y).</p>")
   public void ImagePointInDirection(double x, double y) {
     imageHeadingSeperate = true;
     ImageHeading(-Math.toDegrees(Math.atan2(
         // we adjust for the fact that the sprite's X() and Y()
         // is not the center point.
         y - Y() - Height() / 2,
         x - X() - Width() / 2)));
   }


   /**
    * TargetObjectLockEnabled -
    * This property working in conjuction with PointTowards when set to true will
    * cause the sprite to always head in the direction of the target object.
    */
   @SimpleProperty(
       category = PropertyCategory.BEHAVIOR)
   @DesignerProperty(
       editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
       defaultValue = "False")
   public void ImagePointTowardsLocked(boolean tempval) {
     imagePointTowardsLocked = tempval;
   }

   @SimpleProperty(
     description = "<p>Sprite will lock and track target object.</p>")
   public boolean ImagePointTowardsLocked() {
     return imagePointTowardsLocked;
   }


  //-------------------------------------------------------------------------------------------------
  //Code to enable Sprite animation

  /**
  *Event so user can define there own blocks before the picture changes
  */
  @SimpleEvent
  public void PictureBeforeChanges(){
    EventDispatcher.dispatchEvent(this, "PictureBeforeChanges");
  }

  /**
  *Event so user can define there own blocks after the picture changes
  */
  @SimpleEvent
  public void PictureAfterChanges(){
    EventDispatcher.dispatchEvent(this, "PictureAfterChanges");
  }

  /**
  *Set FrameImageList
  *Used to store all images that will be use as frame for the frame animation
  **/
  @DesignerProperty(
    editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
    defaultValue = "")
  @SimpleProperty(description = "List of images.",
    category = PropertyCategory.APPEARANCE)
  public void FrameImageList(String images){
    frameImageListStr = images.replace("(","").replace(")","");
    frameImageList = frameImageListStr.split(" "); //frameImageList = split imageList by | and put into arra
    frameQty = frameImageList.length;      //set the total number for images in the imagelist
    frameStart = 1;
    frameEnd = frameQty;
    FrameToUse(frameUse); //select the current image frame to be displayed
  }

  @SimpleProperty(description = "Total number image frame in FrameImageList.",
    category = PropertyCategory.APPEARANCE)
  public int FrameQty(){
    return frameQty;
  }

  @SimpleProperty(description = "The list of frameimages being used",
    category = PropertyCategory.APPEARANCE)
  public String FrameImageList(){
    return "(" + frameImageListStr + ")";  // Return the FrameImageList as a string so the user can interigate it from a list
  }

  /**
  *Set the image to be used for the image list
  *As default the value is 1 allow the imagesprite to be used as previous
  **/
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER,
     defaultValue = "1")
  @SimpleProperty
  //public void FrameUse(int frame){
  public void FrameToUse(int frame){
    //If the frameIndex is outside the limit of the frameImageList then set frameIndex to a value within the limits
    if (frameQty > 0){
      if (frame > frameQty) {
        frameUse = 1;
      } else if (frame < 1) {
        frameUse = frameQty;
      } else {
        frameUse = frame;
      }
       Picture(frameImageList[frameUse - 1]); //select the current image frame to be displayed
    }
  }

  //-------------------------------------------------------------------------------------------------------------------------
  //delete this code
  //@SimpleProperty
  //public void FrameUse(int frame){}
  //@SimpleProperty(description = "The current image frame index being used",
  //     category = PropertyCategory.APPEARANCE)
  // public int FrameUse(){return 1;}
  //-------------------------------------------------------------------------------------------------------------------------

  @SimpleProperty(description = "The current image frame index being used",
    category = PropertyCategory.APPEARANCE)
  //public int FrameUse(){
  public int FrameToUse(){
    return frameUse;
  }

  /**
  *Set FramesPerSecond
  *Used to control the speed of the FrameAnimation
  **/
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER,
    defaultValue = "0")
  @SimpleProperty
  public void AnimationSpeed(int frameRate){
    if (frameRate < -100){
      frameSpeedDirection = -100;
    }else if(frameRate > 100){
      frameSpeedDirection = 100;
    }else{
      frameSpeedDirection = frameRate;
    }
    frameSpeed = frameSpeedDirection < 0 ? 0 - frameSpeedDirection : frameSpeedDirection;
  }

  @SimpleProperty(description = "Number of image frames displayed per second",
    category = PropertyCategory.APPEARANCE)
  public int AnimationSpeed(){
    return frameSpeedDirection;
  }

 // /**
 // *Set StartFrame
 // *
 // **/
 // @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER,
 //    defaultValue = "1")
 // @SimpleProperty
 // public void AnimationStartFrame(int frame){
 //   frameStart = frame < 1 ? 1 : frame;
 // }

 // @SimpleProperty(description = "Fisrt frame index in the animation",
 //   category = PropertyCategory.APPEARANCE)
 // public int  AnimationStartFrame(){
 //   return frameStart;
 // }

//  /**
//  *Set EndFrame
//  *
//  **/
//  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER,
//    defaultValue = "1")
//  @SimpleProperty
// public void AnimationEndFrame(int frame){
//    frameEnd = frame > frameQty ? frameQty : frame;
//  }

//  @SimpleProperty(description = "Last frame index in the animation",
//    category = PropertyCategory.APPEARANCE)
//  public int AnimationEndFrame(){
//    return frameEnd;
//  }


  //-------------------------------------------------------------------------------------------------------------------------
  //delete this code
 // @SimpleFunction
 // public void AnimationUseNextFrame(){}
  //-------------------------------------------------------------------------------------------------------------------------

 // /**
 // *AnimationUseNextFrame
 // *Used to show the next frame in animation sequence
 // */
 // @SimpleFunction
 // //public void AnimationUseNextFrame(){
 // public void NextFrame(){
 //       frameUse++;
 //   if (frameUse > frameEnd){
 //     frameUse = frameStart;
 //   }
 //   FrameToUse(frameUse);
 // }

  /**
  * Moves and redraws sprite, registering changes.
  */
  @Override
  public void alarm(){
    if (frameSpeed > 0){
      frameSpeedCounter++;
      if (frameSpeedCounter > (100 - frameSpeed)){
      //  NextFrame();
        if (frameSpeedDirection > 0) {
          FrameToUse(frameUse + 1);
        } else {
          FrameToUse(frameUse - 1);
        }
        
      }
    }

    //Below for imagsprite rotate at time interval
    if (imageHeadingSeperate) {
      if (imageRotateSpeed != 0) {
        setImageHeading(imageHeading + imageRotateSpeed);
      }
      if (imagePointTowardsLocked && imageTargetObject!=null) {
        ImagePointTowards(imageTargetObject);
      }
    }

    //Make sprite point in detection of target sprite
    //Below for imagsprite movement at time interval
    sharedAlarm();
  }
}
