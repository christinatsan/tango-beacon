# BLE Fingerprinter #

This app requires at least Android 4.3 (Gingerbread), and a device that supports Bluetooth.

**Notes:**

* During fingerprinting and localization, the application will attempt to connect to the localization server, whose base URL is defined in Globals.java.
* Fingerprinting and localization have been divided into two separate activities, MainActivity.java and LocatorActivity.java, respectively.
* The map view (MapView) extends the SubsamplingScaleImageView class. Any changes to the map view should be made to MapView.java.
* The floor plan is a .jpg located in res/drawable-nodpi.