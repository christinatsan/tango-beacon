# BLE-Tango Localization Manager #

This app requires at least Android 4.4 (Kit-Kat), and a device that supports Bluetooth.

A Tango-equipped device is required to access Tango-related features in the app. Just to note, this app can still be run on a non-Tango device, but the Tango-related features will be disabled.

**Notes:**

* During BLE fingerprinting, BLE localization, and BLE/Tango localization, the application will attempt to connect to the localization server, whose base URL is defined in Globals.java.
* BLE Fingerprinting is done in either FingerprinterActivity.java or RawFingerprinterActivity.java.
* BLE Localization and Navigation are done in LocatorActivity.java and NavigatorActivity.java, respectively.
* BLE Localization and Navigation are done in TangoLocatorActivity.java and TangoNavigatorActivity.java, respectively.
* The map view (MapView) extends the SubsamplingScaleImageView class. Any changes to the map view should be made to MapView.java.
* The floor plan(s) is/are located in res/drawable-nodpi as .jpg files.