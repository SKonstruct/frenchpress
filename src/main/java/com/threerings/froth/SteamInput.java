package com.threerings.froth;

/**
 * Shim — SK references these enums and data classes as types but never
 * invokes any method here, so the bodies all throw rather than pretend to
 * succeed.
 */
public class SteamInput
{
  public static final int MAX_COUNT = 16;
  public static final int MAX_ACTIVE_LAYERS = 16;
  public static final int MAX_ORIGINS = 8;
  public static final long HANDLE_ALL_CONTROLLERS = -1L;

  public enum InputSourceMode {
    NONE, DPAD, BUTTONS, FOUR_BUTTONS, ABSOLUTE_MOUSE, RELATIVE_MOUSE,
    JOYSTICK_MOVE, JOYSTICK_MOUSE, JOYSTICK_CAMERA, SCROLL_WHEEL,
    TRIGGER, TOUCH_MENU, MOUSE_JOYSTICK, MOUSE_REGION, RADIAL_MENU,
    SINGLE_BUTTON, SWITCHES,
    ;
  }

  public enum InputType {
    UNKNOWN,
    STEAM_CONTROLLER,
    XBOX_360_CONTROLLER,
    XBOX_ONE_CONTROLLER,
    GENERIC_GAMEPAD,
    PS4_CONTROLLER,
    APPLE_MFI_CONTROLLER,
    ANDROID_CONTROLLER,
    SWITCH_JOYCON_PAIR,
    SWITCH_JOYCON_SINGLE,
    SWITCH_PRO_CONTROLLER,
    MOBILE_TOUCH,
    PS3_CONTROLLER,
    PS5_CONTROLLER,
    STEAM_DECK_CONTROLLER,
    ;
  }

  public enum GlyphSize { SMALL, MEDIUM, LARGE, ; }

  public enum LEDFlag { SET_COLOR, RESTORE_USER_DEFAULT, ; }

  @Deprecated
  public enum ControllerPad { LEFT, RIGHT, ; }

  public static final class DigitalActionData {
    public boolean state;
    public boolean active;
    @Override public String toString () {
      return "DigitalActionData{state=" + state + ", active=" + active + "}";
    }
  }

  public static final class AnalogActionData {
    public InputSourceMode mode;
    public float x;
    public float y;
    public boolean active;
    @Override public String toString () {
      return "AnalogActionData{mode=" + mode + ", x=" + x + ", y=" + y
        + ", active=" + active + "}";
    }
  }

  public static final class MotionData {
    public float rotQuatX, rotQuatY, rotQuatZ, rotQuatW;
    public float posAccelX, posAccelY, posAccelZ;
    public float rotVelX, rotVelY, rotVelZ;
  }

  private static void warn () {
    System.err.println("[frenchpress] WARNING: SteamInput method called but stubbed/unused");
  }

  public static boolean init (boolean explicitlyCallRunFrame) { warn(); return false; }
  public static boolean shutdown () { warn(); return false; }
  public static boolean setInputActionManifestFilePath (String path) { warn(); return false; }
  public static void runFrame () { warn(); }
  public static boolean waitForData (boolean waitForever, int timeout) { warn(); return false; }
  public static boolean newDataAvailable () { warn(); return false; }
  public static int getConnectedControllers (long[] handlesOut) { warn(); return 0; }
  public static void enableDeviceCallbacks () { warn(); }
  public static long getActionSetHandle (String actionSetName) { warn(); return 0L; }
  public static void activateActionSet (long inputHandle, long actionSetHandle) { warn(); }
  public static long getCurrentActionSet (long inputHandle) { warn(); return 0L; }
  public static void activateActionSetLayer (long inputHandle, long actionSetLayerHandle) { warn(); }
  public static void deactivateActionSetLayer (long inputHandle, long actionSetLayerHandle) { warn(); }
  public static void deactivateAllActionSetLayers (long inputHandle) { warn(); }
  public static int getActiveActionSetLayers (long inputHandle, long[] handlesOut) { warn(); return 0; }
  public static long getDigitalActionHandle (String actionName) { warn(); return 0L; }
  public static boolean getDigitalActionData (
      long inputHandle, long digitalActionHandle, DigitalActionData data) { warn(); return false; }
  public static int getDigitalActionOrigins (
      long inputHandle, long actionSetHandle, long digitalActionHandle, int[] originsOut) {
    warn(); return 0;
  }
  public static String getStringForDigitalActionName (long digitalActionHandle) { warn(); return ""; }
  public static long getAnalogActionHandle (String actionName) { warn(); return 0L; }
  public static boolean getAnalogActionData (
      long inputHandle, long analogActionHandle, AnalogActionData data) { warn(); return false; }
  public static int getAnalogActionOrigins (
      long inputHandle, long actionSetHandle, long analogActionHandle, int[] originsOut) {
    warn(); return 0;
  }
  public static String getStringForAnalogActionName (long analogActionHandle) { warn(); return ""; }
  public static void stopAnalogActionMomentum (long inputHandle, long analogActionHandle) { warn(); }
  public static String getGlyphPNGForActionOrigin (int origin, GlyphSize size, int flags) { warn(); return ""; }
  public static String getGlyphSVGForActionOrigin (int origin, int flags) { warn(); return ""; }
  @Deprecated
  public static String getGlyphForActionOriginLegacy (int origin) { warn(); return ""; }
  public static String getStringForActionOrigin (int origin) { warn(); return ""; }
  public static String getStringForXboxOrigin (int origin) { warn(); return ""; }
  public static String getGlyphForXboxOrigin (int origin) { warn(); return ""; }
  public static int getActionOriginFromXboxOrigin (long inputHandle, int xboxOrigin) { warn(); return 0; }
  public static int translateActionOrigin (InputType destinationType, int sourceOrigin) { warn(); return 0; }
  public static boolean getMotionData (long inputHandle, MotionData data) { warn(); return false; }
  public static void triggerVibration (long inputHandle, int leftSpeed, int rightSpeed) { warn(); }
  public static void triggerVibrationExtended (
      long inputHandle, int leftSpeed, int rightSpeed,
      int leftTriggerSpeed, int rightTriggerSpeed) {
    warn();
  }
  public static void triggerSimpleHapticEvent (
      long inputHandle, int hapticLocation,
      int intensity, int gainDB, int otherIntensity, int otherGainDB) {
    warn();
  }
  public static void setLEDColor (long inputHandle, int colorR, int colorG, int colorB, LEDFlag flag) {
    warn();
  }
  @Deprecated
  public static void legacyTriggerHapticPulse (
      long inputHandle, ControllerPad targetPad, int durationMicroSec) {
    warn();
  }
  @Deprecated
  public static void legacyTriggerRepeatedHapticPulse (
      long inputHandle, ControllerPad targetPad,
      int durationMicroSec, int offMicroSec, int repeat, int flags) {
    warn();
  }
  public static boolean showBindingPanel (long inputHandle) { warn(); return false; }
  public static InputType getInputTypeForHandle (long inputHandle) { warn(); return InputType.UNKNOWN; }
  public static long getControllerForGamepadIndex (int index) { warn(); return 0L; }
  public static int getGamepadIndexForController (long inputHandle) { warn(); return 0; }
  public static boolean getDeviceBindingRevision (long inputHandle, int[] revisionOut) { warn(); return false; }
  public static int getRemotePlaySessionID (long inputHandle) { warn(); return 0; }
  public static int getSessionInputConfigurationSettings () { warn(); return 0; }
}
