// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.errors.AssertionFailure;
import com.google.appinventor.components.runtime.errors.IllegalArgumentError;
import com.google.appinventor.components.runtime.util.BoundingBox;
import com.google.appinventor.components.runtime.util.TimerInternal;


import android.os.Handler;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * <p>Superclass of sprites able to move and interact with other sprites.</p>
 *
 * <p>While the Simple programmer sees the x- and y-coordinates as integers,
 * they are maintained internally as doubles so fractional changes (caused
 * by multiplying the speed by a cosine or sine value) have the chance to
 * add up.</p>
 *
 * @author spertus.google.com (Ellen Spertus)
 */
@SimpleObject
public abstract class Sprite extends VisibleComponent
    implements AlarmHandler, OnDestroyListener, Deleteable {
  private static final String LOG_TAG = "Sprite";

  protected final Canvas canvas;              // enclosing Canvas
  private final TimerInternal timerInternal;  // timer to control movement
  private final Handler androidUIHandler;     // for posting actions

  // Keeps track of which other sprites are currently colliding with this one.
  // That way, we don't raise CollidedWith() more than once for each collision.
  // Events are only raised when sprites are added to this collision set.  They
  // are removed when they no longer collide.
  private final Set<Sprite> registeredCollisions;

  // This variable prevents events from being raised before construction of
  // all components has taken place.  This was added to fix bug 2262218.
  protected boolean initialized = false;

  // Properties: These are protected, instead of private, both so they
  // can be used by subclasses and tests.
  protected int interval;      // number of milliseconds until next move
  protected boolean visible = true;
  // TODO(user): Convert to have co-ordinates be center, not upper left.
  // Note that this would simplify pointTowards to remove the adjustment
  // to the center points
  protected double xLeft;      // leftmost x-coordinate
  protected double yTop;       // uppermost y-coordinate
  protected double zLayer;     // z-coordinate, higher values go in front
  protected float speed;       // magnitude in pixels

  // Variables for custom origin postion
  protected boolean originCenter;                 //True force origin of coordinates to be center
  protected double originOffsetX;                 //origin x position coordinates calculated from top left
  protected double originOffsetY;                 //origin y position coordinates calculated from top left

  //Variable to make motion work
  protected Sprite targetObject;                  //Target of sprite to be locked on to
  protected double targetX;                       //X position of target sprite
  protected double targetY;                       //Y position of target sprite
  protected float targetDistance;                 //distance too target object so can be travelled round in a circle
  protected double degrees;                       //used to orbit target; This will be replaced and calculated on the fly
  protected boolean orbitClockwise;               //sets the direction as orbiting target
  protected float speedChangeIncrements;          //for smooth speed change to set the increments of speed change
  protected float speedChangeTo;                  //for smooth speed change
  protected double glideDistanceCounter;          //distance counter the sprite will move to using the Glide method
  
  //Variable to make gravity work
  protected boolean useCanvasGravity;             //Will cause the sprite to ignore gravityEnabled setting
  protected int weight;                           //The downward pull force caused by gravity (in pixels)
  protected boolean bounceEnabled;                //When sprite hits surface how much reverse distance will have (in percent)

  //enhancement 5 - canvas boundary
  protected boolean boundaryTopEnabled;           //Enable/Disable canvas top boundary
  protected boolean boundaryBottomEnabled;         //Enable/Disable canvas bottom boundary
  protected boolean boundaryLeftEnabled;          //Enable/Disable canvas left boundary
  protected boolean boundaryRightEnabled;         //Enable/Disable canvas right boundary
  protected boolean canvasWrapAroundEnabled;      //Enable/Disable sprite travelling from one side of canvas to the oppisite side

  //temp variables
  private float speedChange;

  /**
   * The angle, in degrees above the positive x-axis, specified by the user.
   * This is private in order to enforce that changing it also changes
   * {@link #heading}, {@link #headingRadians}, {@link #headingCos}, and
   * {@link #headingSin}.
   */
  protected double userHeading;

  /**
   * The angle, in degrees <em>below</em> the positive x-axis, specified by the
   * user.  We use this to compute new coordinates because, on Android, the
   * y-coordinate increases "below" the x-axis.
   */
  protected double heading;
  protected double headingRadians;  // heading in radians
  protected double headingCos;      // cosine(heading)
  protected double headingSin;      // sine(heading)

  /**
   * Creates a new Sprite component.  This version exists to allow injection
   * of a mock handler for testing.
   *
   * @param container where the component will be placed
   * @param handler a scheduler to which runnable events will be posted
   */
  protected Sprite(ComponentContainer container, Handler handler) {
    super();
    androidUIHandler = handler;

    // Add to containing Canvas.
    if (!(container instanceof Canvas)) {
      throw new IllegalArgumentError("Sprite constructor called with container " + container);
    }
    this.canvas = (Canvas) container;
    this.canvas.addSprite(this);

    // Maintain a list of collisions.
    registeredCollisions = new HashSet<Sprite>();

    // Set in motion.
    timerInternal = new TimerInternal(this, true, 1, handler);
    timerInternal.Interval(1);     //Gareth Haylings 15.07.2013 not sure if this is needed

    // Set default property values.
    Heading(0);  // Default initial heading
    Enabled(true);
    Speed(0);
    Visible(true);
    Z(1);

    //enhancement 3.1 - sprite motion using magnetism
    targetObject = null;
    targetX = 0;
    targetY = 0;
    targetDistance = 0;
    degrees = 0;
    orbitClockwise = true;
    speedChangeIncrements = 0;
    speedChangeTo = 0;
    glideDistanceCounter = -1;

    //enhancement 3.2 - sprite motion using Gravity
    weight = 0;
    bounceEnabled = false;
    useCanvasGravity = true;

    //enhancement 4 - custom origin
    originCenter = false;
    originOffsetX = 0;
    originOffsetY = 0;

    //enhancement 5 - canvas boundary
    boundaryTopEnabled = true;
    boundaryBottomEnabled = true;
    boundaryLeftEnabled = true;
    boundaryRightEnabled = true;
    canvasWrapAroundEnabled = true;


    container.$form().registerForOnDestroy(this);
  }

  /**
   * Creates a new Sprite component.  This is called by the constructors of
   * concrete subclasses, such as {@link Ball} and {@link ImageSprite}.
   *
   * @param container where the component will be placed
   */
  protected Sprite(ComponentContainer container) {
    // Note that although this is creating a new Handler, there is
    // only one UI thread in an Android app and posting to this
    // handler queues up a Runnable for execution on that thread.
    this(container, new Handler());
  }

  public void Initialize() {
    initialized = true;
    canvas.registerChange(this);
  }

  // Properties (Enabled, Heading, Interval, Speed, Visible, X, Y, Z)

  /**
   * Enabled property getter method.
   *
   * @return  {@code true} indicates a running timer, {@code false} a stopped
   *          timer
   */
  @SimpleProperty(
      description = "Controls whether the sprite moves when its speed is non-zero.",
      category = PropertyCategory.BEHAVIOR)
  public boolean Enabled() {
    return timerInternal.Enabled();
  }

  /**
   * Enabled property setter method: starts or stops the timer.
   *
   * @param enabled  {@code true} starts the timer, {@code false} stops it
   */
  @DesignerProperty(
      editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "True")
  @SimpleProperty
      public void Enabled(boolean enabled) {
    timerInternal.Enabled(enabled);
  }

  //--------------------------------------------------------------------------------------------------------------------------------------------------------
  //Enhancement 4 - Sprite origin
  /**
   * OriginCenter -
   * True - force coordinates to be taken from center of sprite
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "False")
  @SimpleProperty(
      description = "Set the origin position of the sprite to center")
  public void OriginCenter(boolean tempVal) {
    originCenter = tempVal;
    if (originCenter) {
      originOffsetX = Width() / 2;
      originOffsetY = Height() / 2;
      registerChange();
    }
  }

  @SimpleProperty(description = "True - origin center",
      category = PropertyCategory.BEHAVIOR)
  public boolean OriginCenter() {
    return originCenter;
  }

  /**
   * OriginOffsetX -
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
      defaultValue = "0")
  @SimpleProperty(
      description = "Set the origin position X",
      category = PropertyCategory.APPEARANCE)
  public void OriginOffsetX(double tempVal) {
    originOffsetX = tempVal;
    if (originOffsetX == Width() / 2 && originOffsetY == Height() / 2) {
      originCenter = true;
    } else {
      originCenter = false;
    }
    registerChange();
  }

  @SimpleProperty(
      description = "Get the origin position X")
  public double OriginOffsetX() {
    return originOffsetX;
  }


  /**
   * OriginOffsetY -
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
      defaultValue = "0")
  @SimpleProperty(
      description = "Set the origin position Y",
      category = PropertyCategory.APPEARANCE)
  public void OriginOffsetY(double tempVal) {
    originOffsetY = tempVal;
    if (originOffsetX == Width() / 2 && originOffsetY == Height() / 2) {
      originCenter = true;
    } else {
      originCenter = false;
    }
    registerChange();
  }

  @SimpleProperty(
      description = "Get the origin position Y")
  public double OriginOffsetY() {
    return originOffsetY;
  }
  //--------------------------------------------------------------------------------------------------------------------------------------------------------
  //Code for ImageSprite/Ball Motion Speed & Heading
 
  /**
   * Sets heading in which sprite should move.  In addition to changing the
   * local variables {@link #userHeading} and {@link #heading}, this
   * sets {@link #headingCos}, {@link #headingSin}, and {@link #headingRadians}.
   *
   * @param userHeading degrees above the positive x-axis
   */
  @SimpleProperty(
      description = "Set the sprite heading in degrees.",
      category = PropertyCategory.BEHAVIOR)
  @DesignerProperty(
      editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
      defaultValue = "0")
  public void Heading(double tempval) {
    targetDistance = 0;
    targetObject = null;
    SetHeading(tempval);
  }

  public void SetHeading(double tempval) {
    userHeading = tempval;
    //if heading is not between 0 - 359 recalculate so it's within these limits
    userHeading = userHeading - (Math.floor(userHeading / 360) * 360);
    userHeading = userHeading < 0 ? 360 + userHeading : userHeading;
    // Flip, because y increases in the downward direction on Android canvases
    heading = -userHeading;
    headingRadians = Math.toRadians(heading);
    headingCos = Math.cos(headingRadians);
    headingSin = Math.sin(headingRadians);
    // changing the heading needs to force a redraw for image sprites that rotate
    registerChange();
  }

  /**
   * Returns the heading of the sprite.
   *
   * @return degrees above the positive x-axis
   */
  @SimpleProperty(
    description = "Returns the sprite heading in degrees.")
  public double Heading() {
    return userHeading;
  }

  /**
   * Sets the speed with which this sprite should move.
   *
   * @param speed the magnitude (in pixels) to move every {@link #interval}
   * milliseconds
   */
  @SimpleProperty(
      description = "Set the travelling speed of the sprite (pixels/ms)",
      category = PropertyCategory.BEHAVIOR)
  @DesignerProperty(
      editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
      defaultValue = "0")
  public void Speed(float tempval) {
    glideDistanceCounter = -1;
    speedChangeIncrements = 0; //stop speed from incremental changing
    speed = tempval;
  }

  /**
   * Gets the speed with which this sprite moves.
   *
   * @return the magnitude (in pixels) the sprite moves every {@link #interval}
   *         milliseconds.
   */
  @SimpleProperty(
      description = "Get current travelling speed sprite (pixels/ms)")
  public float Speed() {
    return speed;
  }

  /**
   * SpeedChangeSmoothlyTo -
   * Will cause sprite to gradually reach the specified speed of a time period
   * set in milliseconds
   */
  @SimpleFunction()
  public void SpeedChangeSmoothlyTo(float targetSpeed, int timeToReachSpeed) {
    glideDistanceCounter = -1;
    speedChangeIncrements = (targetSpeed - speed) / timeToReachSpeed;
    speedChangeTo = targetSpeed;
  }


  /**
   * Glide -
   * Will cause the sprite to glide a specified distance and a speciefied speed
   * via the current heading set
   */
  @SimpleFunction()
  public void Glide(int distance, float targetSpeed) {
    speedChangeIncrements = 0;
    targetDistance = 0;
    targetObject = null;
    speed = (float) (distance/Math.floor(distance/targetSpeed));
    glideDistanceCounter = distance/speed;
  }

 
  /**
   * StopFollowingObject -
   * Will cause the sprite stop the following the target object
   */
  @SimpleFunction()
  public void StopFollowingObject() {
    targetObject = null;
  }
    
  /**
   * OrbitObject(Target, Radius, direction, follow) -
   * Will cause the sprite to orbit a target object at a set distance 
   * direction 0 = Clockwise else anticlockwise
   */
  @SimpleFunction()
  public void OrbitObject(Sprite target, float radius, int direction, boolean follow) {
    degrees = target.AngleToPoint(xLeft + (Width()/2), yTop + (Height()/2));
    targetObject = follow ? target : null;
    targetX = target.X() + (target.Width()/2);
    targetY = target.Y() + (target.Height()/2);
    targetDistance = radius;
    orbitClockwise = direction == 0 ? true : false;
  }
  
  /**
   * OrbitPointClockwise(X,Y,Radius) -
   * Will cause the sprite to orbit a x y position at a set distance
   * * direction 0 = Clockwise else anticlockwise
   */
  @SimpleFunction(description = "Orbit around point at a set distance<br> " +
      "direction 0 - travel clockwise<br>" +
      "direction 1 - travel anti-clockwise")
  public void OrbitPoint(float x, float y, float radius, int direction) {
    degrees = AngleToPoint(x,y) + 180;
    targetX = x;
    targetY = y;
    targetDistance = radius;
    orbitClockwise = direction == 0 ? true : false;
  }


  /**
   * Moves sprite directly to specified point.
   *
   * @param x the x-coordinate
   * @param y the y-coordinate
   */
  @SimpleFunction(
    description = "Moves the sprite to new X Y coordinates")
  public void MoveTo(double x, double y) {
    xLeft = x;
    yTop = y;
    registerChange();
  }

  /**
   * Turns this sprite to point towards a given other sprite.
   *
   * @param target the other sprite to point towards
   */
  @SimpleFunction(
    description = "Turns the sprite to point towards a target sprite. " +
    "Set follow to True will cause the sprite to keep changing heading " +
    "as the target sprite moves")
  public void PointTowards(Sprite target, boolean follow) {
    Heading(AngleTowards(target));
    targetObject = follow ? target : null;
  }

  /**
   * Turns this sprite to point towards a given point.
   *
   * @param x parameter of the point to turn to
   * @param y parameter of the point to turn to
   */
  @SimpleFunction(
    description = "Turns the sprite to point in the direction of the X, Y coordinates")
  public void PointInDirection(double x, double y) {
     Heading(AngleToPoint(x,y));
  } 

//--------------------------------------------------------------------------------------------------------------------------------------------------------
  //Enhancement 3.2 - Sprite Motion using Gravity

  /**
   * Weight -
   * (pixels) The downward pull force caused by gravity
   */
  @SimpleProperty(
      category = PropertyCategory.BEHAVIOR)
  @DesignerProperty(
      editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER,
      defaultValue = "0")
  public void Weight(int tempval) {
    weight = tempval;
  }

  @SimpleProperty(
    description = "Set the downward pull force")
  public int Weight() {
    return weight;
  }


  /**
   * BouncEnabled -
   * reverse distance the sprite will travel in the opposite direction when it hits a surface
   */
  @SimpleProperty(
      category = PropertyCategory.BEHAVIOR)
  @DesignerProperty(
      editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "False")
  public void BounceEnabled(boolean tempval) {
    bounceEnabled = tempval;
  }

  @SimpleProperty(
    description = "<p>True - when sprite hits surface will bounce</p>")
  public boolean BounceEnabled() {
    return bounceEnabled;
  }


  /**
   * UseCanvasGravity -
   * Disables/Enable use the global setting to switch on the gravity from the Canvas
   */
  @SimpleProperty(
      category = PropertyCategory.BEHAVIOR)
  @DesignerProperty(
      editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "True")
  public void UseCanvasGravity(boolean tempval) {
    useCanvasGravity = tempval;
  }

  @SimpleProperty(
    description = "<p>False - ignore gravity of canvas.</p>")
  public boolean UseCanvasGravity() {
    return useCanvasGravity;
  }
  
  //--------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Gets whether sprite is visible.
   *
   * @return  {@code true} if the sprite is visible, {@code false} otherwise
   */
  @SimpleProperty(
      description = "<p>True if the sprite is visible.</p>",
      category = PropertyCategory.APPEARANCE)
  public boolean Visible() {
    return visible;
  }

  /**
   * Sets whether sprite should be visible.
   *
   * @param visible  {@code true} if the sprite should be visible; {@code false}
   * otherwise.
   */
  @DesignerProperty(
      editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "True")
  @SimpleProperty
  public void Visible(boolean visible) {
    this.visible = visible;
    registerChange();
  }

  @SimpleProperty(
      description = "<p>The horizontal coordinate of the left edge of the sprite, " +
      "increasing as the sprite moves to the right.</p>")
  public double X() {
    return xLeft;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
      defaultValue = "0.0")
  @SimpleProperty(
      category = PropertyCategory.APPEARANCE)
  public void X(double x) {
    xLeft = x;
    registerChange();
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
      defaultValue = "0.0")
  @SimpleProperty(
      category = PropertyCategory.APPEARANCE)
  public void Y(double y) {
    yTop = y;
    registerChange();
  }

  @SimpleProperty(
      description = "<p>The vertical coordinate of the top of the sprite, " +
      "increasing as the sprite moves down.</p>")
  public double Y() {
    return yTop;
  }

  /**
   * Sets the layer of the sprite, indicating whether it will appear in
   * front of or behind other sprites.
   *
   * @param layer higher numbers indicate that this sprite should appear
   *        in front of ones with lower numbers; if values are equal for
   *        sprites, either can go in front of the other
   */
  @SimpleProperty(
      category = PropertyCategory.APPEARANCE)
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
                    defaultValue = "1")
  public void Z(double layer) {
    this.zLayer = layer;
    canvas.changeSpriteLayer(this);  // Tell canvas about change
  }

  @SimpleProperty(
      description = "<p>How the sprite should be layered relative to other sprits, " +
      "with higher-numbered layers in front of lower-numbered layers.</p>")
  public double Z() {
    return zLayer;
  }

  // Methods for event handling: general purpose method postEvent() and
  // Simple events: CollidedWith, Dragged, EdgeReached, Touched, NoLongeCollidingWith,
  // Flung, TouchUp, and TouchDown.

  /**
   * <p>Posts a dispatch for the specified event.  This guarantees that event
   * handlers run with serial semantics, e.g., appear atomic relative to
   * each other.</p>
   *
   * <p>This method is overridden in tests.</p>
   *
   * @param sprite the instance on which the event takes place
   * @param eventName the name of the event
   * @param args the arguments to the event handler
   */
  protected void postEvent(final Sprite sprite,
                           final String eventName,
                           final Object... args) {
    androidUIHandler.post(new Runnable() {
        public void run() {
          EventDispatcher.dispatchEvent(sprite, eventName, args);
        }});
  }

  // TODO(halabelson): Fix collision detection for rotated sprites.
  /**
   * Handler for CollidedWith events, called when two sprites collide.
   * Note that checking for collisions with a rotated ImageSprite currently
   * checks against the sprite's unrotated position.  Therefore, collision
   * checking will be inaccurate for tall narrow or short wide sprites that are
   * rotated.
   *
   * @param other the other sprite in the collision
   */
  @SimpleEvent
  public void CollidedWith(Sprite other) {
    if (registeredCollisions.contains(other)) {
      Log.e(LOG_TAG, "Collision between sprites " + this + " and "
          + other + " re-registered");
      return;
    }
    registeredCollisions.add(other);
    postEvent(this, "CollidedWith", other);
  }

  /**
   * Handler for Dragged events.  On all calls, the starting coordinates
   * are where the screen was first touched, and the "current" coordinates
   * describe the endpoint of the current line segment.  On the first call
   * within a given drag, the "previous" coordinates are the same as the
   * starting coordinates; subsequently, they are the "current" coordinates
   * from the prior call.  Note that the Sprite won't actually move
   * anywhere in response to the Dragged event unless MoveTo is
   * specifically called.
   *
   * @param startX the starting x-coordinate
   * @param startY the starting y-coordinate
   * @param prevX the previous x-coordinate (possibly equal to startX)
   * @param prevY the previous y-coordinate (possibly equal to startY)
   * @param currentX the current x-coordinate
   * @param currentY the current y-coordinate
   */
  @SimpleEvent
  public void Dragged(float startX, float startY,
                      float prevX, float prevY,
                      float currentX, float currentY) {
    postEvent(this, "Dragged", startX, startY, prevX, prevY, currentX, currentY);
  }

  
  /** EdgeReached(top,bottom,left,right)
   * Will return true or false to the edges reached
   */
  @SimpleEvent
  public void EdgeReached(boolean topEdge, boolean bottomEdge, boolean leftEdge, boolean rightEdge) {
    postEvent(this, "EdgeReached", topEdge, bottomEdge, leftEdge, rightEdge);
  }
    

  /**
   * Handler for NoLongerCollidingWith events, called when a pair of sprites
   * cease colliding.  This also registers the removal of the collision to a
   * private variable {@link #registeredCollisions} so that
   * {@link #CollidedWith(Sprite)} and this event are only raised once per
   * beginning and ending of a collision.
   *
   * @param other the sprite formerly colliding with this sprite
   */
  @SimpleEvent(
      description = "Event indicating that a pair of sprites are no longer " +
      "colliding.")
  public void NoLongerCollidingWith(Sprite other) {
    if (!registeredCollisions.contains(other)) {
      Log.e(LOG_TAG, "Collision between sprites " + this + " and "
          + other + " removed but not present");
    }
    registeredCollisions.remove(other);
    postEvent(this, "NoLongerCollidingWith", other);
  }

  /**
   * When the user touches the sprite and then immediately lifts finger: provides 
   * the (x,y) position of the touch, relative to the upper left of the canvas
   *
   * @param x  x-coordinate of touched point
   * @param y  y-coordinate of touched point
   */
  @SimpleEvent
  public void Touched(float x, float y) {
    postEvent(this, "Touched", x, y);
  }

  /**
   * When a fling gesture (quick swipe) is made on the sprite: provides
   * the (x,y) position of the start of the fling, relative to the upper
   * left of the canvas. Also provides the speed (pixels per millisecond) and heading
   * (0-360 degrees) of the fling, as well as the x velocity and y velocity
   * components of the fling's vector.
   *
   * @param x  x-coordinate of touched point
   * @param y  y-coordinate of touched point
   * * @param speed  the speed of the fling sqrt(xspeed^2 + yspeed^2)
   * @param heading  the heading of the fling 
   * @param xvel  the speed in x-direction of the fling
   * @param yvel  the speed in y-direction of the fling
   
   */
  @SimpleEvent
  public void Flung(float x, float y, float speed, float heading, float xvel, float yvel) {
    postEvent(this, "Flung", x, y, speed, heading, xvel, yvel);
  }

  /**
   * When the user stops touching the sprite (lifts finger after a
   * TouchDown event): provides the (x,y) position of the touch, relative
   * to the upper left of the canvas
   *
   * @param x  x-coordinate of touched point
   * @param y  y-coordinate of touched point
   */
  @SimpleEvent
  public void TouchUp(float x, float y) {
    postEvent(this, "TouchUp", x, y);
  }

  /**
   * When the user begins touching the sprite (places finger on sprite and
   * leaves it there): provides the (x,y) position of the touch, relative
   * to the upper left of the canvas
   *
   * @param x  x-coordinate of touched point
   * @param y  y-coordinate of touched point
   */
  @SimpleEvent
  public void TouchDown(float x, float y) {
    postEvent(this, "TouchDown", x, y);
  }

  // Methods providing Simple functions:
  // Bounce, CollidingWith, MoveIntoBounds, MoveTo, PointTowards.

  
  // This is primarily used to enforce raising only
  // one {@link #CollidedWith(Sprite)} event per collision but is also
  // made available to the Simple programmer.
  /**
   * Indicates whether a collision has been registered between this sprite
   * and the passed sprite.
   *
   * @param other the sprite to check for collision with this sprite
   * @return {@code true} if a collision event has been raised for the pair of
   *         sprites and they still are in collision, {@code false} otherwise.
   */
  @SimpleFunction
  public boolean CollidingWith(Sprite other) {
    return registeredCollisions.contains(other);
  }

  
    
  
  //-------------------------------------------------------------------------------------------------------------------
  // The following code sets up if the canvas boundary's will be use and what will happen when the boundary is reached
 
  
  // Internal methods supporting move-related functionality

  /**
   * <p>Responds to a move or change of this sprite by redrawing the
   * enclosing Canvas and checking for any consequences that need
   * handling.  Specifically, this (1) notifies the Canvas of a change
   * so it can detect any collisions, etc., and (2) raises the
   * {@link #EdgeReached(int)} event if the Sprite has reached the edge of the
   * Canvas.</p>
   */
  protected void registerChange() {
    // This was added to fix bug 2262218, where Ball.CollidedWith() was called
    // before all components had been constructed.
    if (!initialized) {
      // During REPL, components are not initalized, but we still want to repaint the canvas.
      canvas.getView().invalidate();
      return;
    }
   if (overWestEdge() || overNorthEdge() || overEastEdge() || overSouthEdge()) {
     EdgeReached(overNorthEdge(), overSouthEdge(), overWestEdge(), overEastEdge());
     moveIntoBounds();
   }
   canvas.registerChange(this);
    
  }

  
  

  /**
   * CanvasBoundaryEnabled(boolean top, boolean bottom, boolean left, boolean right, boolean canvasWrapRound) -
   * Switch on/off canvas boundary
   */
  @SimpleFunction(
      description = "Set canvas boundary on/off and allow sprite to auto wrap to oppisite side of canvas")
  public void CanvasBoundaryEnabled(boolean topEdge, boolean bottomEdge, boolean leftEdge, boolean rightEdge, boolean canvasWrapRound) {
    boundaryTopEnabled = topEdge;
    boundaryBottomEnabled = bottomEdge;
    boundaryLeftEnabled = leftEdge;
    boundaryRightEnabled = rightEdge;
    canvasWrapAroundEnabled = canvasWrapRound;
  }
  
  @SimpleProperty(
    description = "Top canvas boundary on/off")
  public boolean BoundaryTopEnabled() {
    return boundaryTopEnabled;
  }

  @SimpleProperty(
    description = "Top canvas boundary on/off")
  public boolean BoundaryBottomEnabled() {
    return boundaryBottomEnabled;
  }

  @SimpleProperty(
    description = "Left canvas boundary on/off")
  public boolean BoundaryLeftEnabled() {
    return boundaryLeftEnabled;
  }

  @SimpleProperty(
    description = "Right canvas boundary on/off")
  public boolean BoundaryRightEnabled() {
    return boundaryRightEnabled;
  }

  @SimpleProperty(
    description = "Sprite appears on oppisite canvas edge on/off")
  public boolean CanvasWrapAroundEnabled() {
    return canvasWrapAroundEnabled;
  }


  /**
   * Moves the sprite back in bounds if part of it extends out of bounds,
   * having no effect otherwise. If the sprite is too wide to fit on the
   * canvas, this aligns the left side of the sprite with the left side of the
   * canvas. If the sprite is too tall to fit on the canvas, this aligns the
   * top side of the sprite with the top side of the canvas.
   */
  protected final void moveIntoBounds() {
    boolean moved = false;
    boolean headInOppositeDirection = false;

    if (boundaryTopEnabled && yTop < 0) {
      //Top canvas boundary on and sprite hits top stop sprite move beyond top boundary
      yTop = 0;
      moved = true;
      if (bounceEnabled) {
        SetHeading(270+(90-Heading())); //make sprite bounce in opposite direction
        // headInOppositeDirection = true;
      }
    }
    if (!boundaryTopEnabled && yTop < 0 - Height()) {
      //Top canvas boundary off and sprite canvas wrap around on the sprite positioned on bottom of canvas
      //Top canvas boundary off and sprite canvas wrap around off the sprite positioned outside top of canvas
      yTop = canvasWrapAroundEnabled ? canvas.Height() + Height() : -Height();
      moved = true;
    }

    if (boundaryBottomEnabled && yTop > canvas.Height() - Height()) {
      //Bottom canvas boundary on and sprite hits bottom stop sprite move beyond bottom boundary
      yTop = canvas.Height() - Height();
      moved = true;
      if (bounceEnabled) {
        SetHeading(90+(270-Heading())); //make sprite bounce in opposite direction
        //headInOppositeDirection = true;
      }
    }
    if (!boundaryBottomEnabled && yTop > canvas.Height() + Height()) {
      //Bottom canvas boundary off and sprite canvas wrap around on the sprite positioned on top of canvas
      //Bottom canvas boundary off and sprite canvas wrap around off the sprite positioned outside bottom of canvas
      yTop = canvasWrapAroundEnabled ? -Height() : canvas.Height() + Height();
      moved = true;
    }

    if (boundaryLeftEnabled && xLeft < 0) {
      //Left canvas boundary on and sprite hits left stop sprite move beyond left boundary
      xLeft = 0;
      moved = true;
      if (bounceEnabled) {
        SetHeading(0+(180-Heading())); //make sprite bounce in opposite direction
        //headInOppositeDirection = true;
      }  
    }
    if (!boundaryLeftEnabled && xLeft < 0 - Width()){
      //Left canvas boundary off and sprite canvas wrap around on the sprite positioned on right of canvas
      //Left canvas boundary off and sprite canvas wrap around off the sprite positioned outside left of canvas
      xLeft = canvasWrapAroundEnabled ? canvas.Width() + Width() : -Width();
      moved = true;
    }

    if (boundaryRightEnabled && xLeft > canvas.Width() - Width()) {
      //Right canvas boundary on and sprite hits right stop sprite move beyond right boundary
      xLeft = canvas.Width() - Width();
      moved = true;
      if (bounceEnabled) {
        SetHeading(180+(0-Heading())); //make sprite bounce in opposite direction
        //headInOppositeDirection = true;
      }
    }
    if (!boundaryRightEnabled && xLeft > canvas.Width() + Width()) {
      //Right canvas boundary off and sprite canvas wrap around on the sprite positioned on left of canvas
      //Right canvas boundary off and sprite canvas wrap around off the sprite positioned outside right of canvas
      xLeft = canvasWrapAroundEnabled ? -Width() : canvas.Width() + Width();
      moved = true;
    }

    //Reverse heading when hits boundary
    if (bounceEnabled && headInOppositeDirection) {
    //  SetHeading(ChangeHeading());
    }
    
    // Then registerChange (just once!) if sprite has hit a boundary.
    if (moved) {
      canvas.registerChange(this);
    }
  }
  //-------------------------------------------------------------------------------------------------------------------


  /**
   * Updates the x- and y-coordinates based on the heading and speed.  The
   * caller is responsible for calling {@link #registerChange()}.
   */
  protected void updateCoordinates() {
    xLeft += speed * headingCos;
    yTop += speed * headingSin;
  }

  // Methods for determining collisions with other Sprites and the edge
  // of the Canvas.

  private final boolean overWestEdge() {
    return xLeft < 0;
  }

  private final boolean overEastEdge() {
    return xLeft + Width() > canvas.Width();
  }

  private final boolean overNorthEdge() {
    return yTop < 0;
  }

  private final boolean overSouthEdge() {
    return yTop + Height() > canvas.Height();
  }


  /**
   * Provides the bounding box for this sprite.  Modifying the returned value
   * does not affect the sprite.
   *
   * @param border the number of pixels outside the sprite to include in the
   *        bounding box
   * @return the bounding box for this sprite
   */
  public BoundingBox getBoundingBox(int border) {
    return new BoundingBox(X() - border, Y() - border,
        X() + Width() - 1 + border, Y() + Height() - 1 + border);
  }

  /**
   * Determines whether two sprites are in collision.  Note that we cannot
   * merely see whether the rectangular regions around each intersect, since
   * some types of sprite, such as BallSprite, are not rectangular.
   *
   * @param sprite1 one sprite
   * @param sprite2 another sprite
   * @return {@code true} if they are in collision, {@code false} otherwise
   */
  public static boolean colliding(Sprite sprite1, Sprite sprite2) {
    // If the bounding boxes don't intersect, there can be no collision.
    BoundingBox rect1 = sprite1.getBoundingBox(1);
    BoundingBox rect2 = sprite2.getBoundingBox(1);
    if (!rect1.intersectDestructively(rect2)) {
      return false;
    }

    // If we get here, rect1 has been mutated to hold the intersection of the
    // two bounding boxes.  Now check every point in the intersection to see if
    // both sprites contain that point.
    // TODO(user): Handling abutting sprites properly
    for (double x = rect1.getLeft(); x <= rect1.getRight(); x++) {
      for (double y = rect1.getTop(); y <= rect1.getBottom(); y++) {
        if (sprite1.containsPoint(x, y) && sprite2.containsPoint(x, y)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Determines whether this sprite intersects with the given rectangle.
   *
   * @param rect the rectangle
   * @return {@code true} if they intersect, {@code false} otherwise
   */
  public boolean intersectsWith(BoundingBox rect) {
    // If the bounding boxes don't intersect, there can be no intersection.
    BoundingBox rect1 = getBoundingBox(0);
    if (!rect1.intersectDestructively(rect)) {
      return false;
    }

    // If we get here, rect1 has been mutated to hold the intersection of the
    // two bounding boxes.  Now check every point in the intersection to see if
    // the sprite contains it.
    for (double x = rect1.getLeft(); x < rect1.getRight(); x++) {
      for (double y = rect1.getTop(); y < rect1.getBottom(); y++) {
        if (containsPoint(x, y)) {
            return true;
        }
      }
    }
    return false;
  }

  /**
   * Indicates whether the specified point is contained by this sprite.
   * Subclasses of Sprite that are not rectangular should override this method.
   *
   * @param qx the x-coordinate
   * @param qy the y-coordinate
   * @return whether (qx, qy) falls within this sprite
   */
  public boolean containsPoint(double qx, double qy) {
    return qx >= xLeft && qx < xLeft + Width() &&
        qy >= yTop && qy < yTop + Height();
  }

  // Convenience methods for dealing with hitting the screen edge and collisions

  // AlarmHandler implementation

  /**
   * Moves and redraws sprite, registering changes.
   */
  public void sharedAlarm() {
    // This check on initialized is currently redundant, since registerChange()
    // checks it too.
    if (initialized) {
      
      //Apply slide motion
      if (glideDistanceCounter >= 0) {
        glideDistanceCounter--;
      }
      if (glideDistanceCounter == 0) {
        speed = 0;
      }
      

      //Apply smooth speed change
      if (speedChangeIncrements != 0) {
        speed = speed + speedChangeIncrements;
        if ((speedChangeIncrements < 0 && speed <= speedChangeTo) || (speedChangeIncrements > 0 && speed >= speedChangeTo)) {
          speedChangeIncrements = 0;
          speed = speedChangeTo;
        }
      }

      //Sprite effected by gravity
      if (canvas.GravityEnabled() || !useCanvasGravity) {

        //Step 1b - If canvas.gravityInverted the up is down and down is up
        float dummyWeight = weight;
        if (UseCanvasGravity() && canvas.GravityInverted() && canvas.GravityEnabled()) {
          dummyWeight = -weight;
        }

            
        // If sprite reaches top/bottom edge of the canvas
        if (yTop<=0 || yTop + Height() >= canvas.Height()) { 
        
          //if light object
          if (dummyWeight<0) {
            if (yTop<=0 &&  boundaryTopEnabled) {
              if (bounceEnabled) {
                //if set to bounce when hits bottom edge then reduce the speed by quarter
                speed = (float) (speed * 0.75); 
                gravityEffect();
              } else {
                speed = 0;
              }
            }  
            if (yTop + Height() >= canvas.Height() &&  boundaryBottomEnabled) {
              SetHeading(270+(90-Heading())); //make sprite bounce in opposite direction
              gravityEffect();
            }
          }

          //if heavy object
          if (dummyWeight>0) {
            if (yTop + Height() >= canvas.Height() &&  boundaryBottomEnabled) {
              if (bounceEnabled) {
                //if set to bounce when hits bottom edge then reduce the speed by quarter
                speed = (float) (speed * 0.75); 
                gravityEffect();
              } else {
                speed = 0;
              }
            }
            if (yTop<=0 &&  boundaryTopEnabled) {
              //make sprite bounce in opposite direction
              SetHeading(90+(270-Heading())); 
              gravityEffect();
            }
          }

        } else {
          gravityEffect();
        }
      }


      //Make sprite point in detection of target sprite
      if (targetObject!=null) {
        if (targetDistance == 0) {
          PointTowards(targetObject, true);         
        } else {
          targetX = targetObject.X() + (targetObject.Width() / 2);
          targetY = targetObject.Y() + (targetObject.Height() / 2);
        }
      } 
      
      if (targetDistance != 0) {
        orbitTarget();     
      }

      if (speed != 0) {
        updateCoordinates();
        registerChange();
      } 
    }
    UserBlock();
  }

  /**
   * Change sprite to next position based or targetDistance and direction
  */
  public void orbitTarget() {
    if (orbitClockwise) {
      degrees = degrees - speed;
      SetHeading(degrees - 90);
    } else {
      degrees = degrees + speed;
      SetHeading(degrees + 90);
    }
    //if degrees is not between 0 - 359 recalculate so it's within these limits
    degrees = degrees - (Math.floor(degrees / 360) * 360); 
    degrees = degrees < 0 ? 360 + degrees : degrees;

    double opp = Math.sin(Math.toRadians(degrees)) * targetDistance;
    double adj = Math.sqrt((targetDistance * targetDistance) - (opp * opp));

    if (degrees > 90 && degrees < 270) {
      X((targetX - (Width()/2)) - adj);
    } else {
      X((targetX - (Width()/2)) + adj);
    }
    Y((targetY - (Height()/2)) - opp);
    

  }

  /**
   * Change speed and heading based on weight
  */
  public void gravityEffect() {
    // Step 1 - calculate current direction of sprite
    boolean pointDown = true;
    boolean facingLeft = true;
    if (Heading() >= 0 && Heading() < 90 ) {
      //facing ^>
      pointDown = false;
      facingLeft = false;
    } else if (Heading() >= 90 && Heading() < 180 ) {
      //facing <^
      pointDown = false;
      facingLeft = true;
    } else if (Heading() >= 180 && Heading() < 270 ) {
      //facing <v 
      pointDown = true;
      facingLeft = true;
    } else if (Heading() >= 270 && Heading() < 360 ) {
      //facing v>
      pointDown = true;
      facingLeft = false;
    }


    //Step 1b - If canvas.gravityInverted then up is down and down is up
    float dummyWeight = weight;
    if (UseCanvasGravity() && canvas.GravityInverted() && canvas.GravityEnabled()) {
      dummyWeight = -weight;
    }


    // Step 2 - calculate from weight whether the sprite should move up or down
    // also the weight effects the speed of acceleration
    float newSpeed = 0;
    //set direction and speed change flag so that sprite is heavy and will head down
    float spriteWeight = dummyWeight;


    boolean speedUp = pointDown ? true : false;
    boolean turnClockwise = facingLeft ? false : true;

    if (dummyWeight < 0) {
      //If sprite is light set direction and speed change flag up head down
      spriteWeight = 0 - dummyWeight;
      speedUp = pointDown ? false : true;
      turnClockwise = facingLeft ? true : false;
    }
    
    // Step 3 - calculate and adjust new speed 
    speedChange = (float) (spriteWeight * 0.001);
    newSpeed = speedUp ? speed + speedChange : speed - speedChange;  
    speed = newSpeed < 0 ? 0 : newSpeed;
    
    // Step 4 - calculate and adjust new heading 
    double newHeading = Heading();
    if (speed <= speedChange) {
      newHeading = 270 - (Heading() - 90); //if the sprite slows down too much make it reverse it's direction
      speed = speedChange;    
    } else {
      if (speed <= spriteWeight) {
        newHeading = turnClockwise ? Heading() - 1 : Heading() + 1;
      }
    }
    
    // step 5 - stop the heading for altering if the angle is near 90 or 270
    // as this causes the sprite to move across the canvas
    if (dummyWeight < 0 && Math.round(Heading())==90) {
      newHeading = 90;
    }
    if (dummyWeight > 0 && Math.round(Heading())==270) {
      newHeading = 270;
    }
    SetHeading(newHeading);
  }

  /**
   * Calculate the distance of the sprite from another sprite
  */
  @SimpleFunction(description = "Distance to target sprite")
  public double DistanceTo(Sprite target) {
    double centerXTarget = target.X() + (target.Width() / 2);
    double centerYTarget = target.Y() + (target.Height() / 2);
    return DistanceToPoint(centerXTarget, centerYTarget);
  }


  /**
   * Calculate the distance of sprite to xy position
   */
  @SimpleFunction(description = "Distance to point")
  public double DistanceToPoint(double x, double y) {
    double adj;
    double opp;
    double centerX = X() + (Width() / 2);
    double centerY = Y() + (Height() / 2);
    double centerXTarget = x;
    double centerYTarget = y;
        
    if (centerXTarget > centerX) {
      adj =  centerXTarget - centerX;
    } else {
      adj =  centerX - centerXTarget;
    }
    if (centerYTarget > centerY) {
      opp = centerYTarget - centerY;
    } else {
      opp = centerY - centerYTarget;
    }
    return Math.sqrt((adj * adj) + (opp * opp));
  }


  /**
   * Calculate the angle of sprite to target
   */
  @SimpleFunction(description = "Angle to target sprite")
  public double AngleTowards(Sprite target) {
    double angle = -Math.toDegrees(Math.atan2(
             // we adjust for the fact that the sprites' X() and Y()
             // are not the center points.
             target.Y() - Y() + (target.Height() - Height()) / 2,
             target.X() - X() + (target.Width() - Width()) / 2));
     return angle < 0 ? 360 + angle : angle;
  }


  /**
   * Calculate the angle of sprite to xy position
   */
  @SimpleFunction(description = "Angle to point")
  public double AngleToPoint(double x, double y) {
    double angle = -Math.toDegrees(Math.atan2(
        // we adjust for the fact that the sprite's X() and Y()
        // is not the center point.
        y - Y() - Height() / 2,
        x - X() - Width() / 2));
    return angle < 0 ? 360 + angle : angle;
  }



  //---------------------------------------------------------------------------------------------
  //Temp block for debugging
  @SimpleEvent
  public void UserBlock() {
    postEvent(this, "UserBlock");
  }

  
  //---------------------------------------------------------------------------------------------

  // Component implementation

  @Override
  public HandlesEventDispatching getDispatchDelegate() {
    return canvas.$form();
  }

  // OnDestroyListener implementation

  @Override
  public void onDestroy() {
    timerInternal.Enabled(false);
  }

  // Deleteable implementation

  @Override
  public void onDelete() {
    timerInternal.Enabled(false);
    canvas.removeSprite(this);
  }

  // Abstract methods that must be defined by subclasses

  /**
   * Draws the sprite on the given canvas
   *
   * @param canvas the canvas on which to draw
   */
  protected abstract void onDraw(android.graphics.Canvas canvas);
}
