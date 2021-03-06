package com.example.tripcaptainkotlin.view.ui.activity

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.tripcaptainkotlin.R
import com.example.tripcaptainkotlin.ar.PlaceNode
import com.example.tripcaptainkotlin.ar.PlacesArFragment
import com.example.tripcaptainkotlin.databinding.LayoutSafetyWarningBinding
import com.example.tripcaptainkotlin.model.DirectionsResponse
import com.example.tripcaptainkotlin.model.Place
import com.example.tripcaptainkotlin.model.getPositionVector
import com.example.tripcaptainkotlin.service.DirectionsService
import com.example.tripcaptainkotlin.viewModel.LocationViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.ar.sceneform.AnchorNode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class ArPlaceActivity : AppCompatActivity(), SensorEventListener {

    private val REQUEST_CHECK_SETTINGS = 0x1;
    private val TAG = "ArPlaceActivity"

    private lateinit var place: Place

    private lateinit var arFragment: PlacesArFragment
    private lateinit var mapFragment: SupportMapFragment

    private lateinit var layoutSafetyWarningBinding: LayoutSafetyWarningBinding
    private lateinit var dialog: AlertDialog

    // Location
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Sensor
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var anchorNode: AnchorNode? = null
    private var markers: MutableList<Marker> = emptyList<Marker>().toMutableList()
    private lateinit var currentLocation: Location
    private var map: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isSupportedDevice()) {
            return
        }
        setContentView(R.layout.activity_ar_place)

        locationViewModel = ViewModelProvider(this).get(LocationViewModel::class.java)
        place = intent.getParcelableExtra("Place")!!

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as PlacesArFragment
        mapFragment =
            supportFragmentManager.findFragmentById(R.id.maps_fragment) as SupportMapFragment

        sensorManager = getSystemService()!!
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val permissions = listOf(
            android.Manifest.permission.CAMERA
        )

        Dexter.withActivity(this@ArPlaceActivity)
            .withPermissions(
                permissions
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let {
                        if (report.areAllPermissionsGranted()) {
                            Toast.makeText(this@ArPlaceActivity, "OK", Toast.LENGTH_SHORT).show()

                            showSafetyWarning()
                            locationViewModel.getLocationData()
                                .observe(this@ArPlaceActivity, Observer {
                                    currentLocation = it
                                })
                            setUpAr()
                            setUpMaps()

                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    // Remember to invoke this method when the custom rationale is closed
                    // or just by default if you don't want to use any custom rationale.
                    token?.continuePermissionRequest()
                }
            })
            .withErrorListener {
                Toast.makeText(this@ArPlaceActivity, it.name, Toast.LENGTH_SHORT).show()
            }
            .check()
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun showSafetyWarning() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        // set the custom layout
        layoutSafetyWarningBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.layout_safety_warning,
            null,
            false
        )
        layoutSafetyWarningBinding.apply {
            mActivity = this@ArPlaceActivity
        }

        builder.setView(layoutSafetyWarningBinding.getRoot())

        // create and show the alert dialog
        dialog = builder.create()
        dialog.show()
    }

    fun closeDialog() {
        dialog.dismiss()
    }

    private fun setUpAr() {
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            val anchor = hitResult.createAnchor()
            anchorNode = AnchorNode(anchor)
            anchorNode?.setParent(arFragment.arSceneView.scene)
            addPlace(anchorNode!!)
        }
    }

    private fun addPlace(anchorNode: AnchorNode) {
        val currentLocation = currentLocation

        // Add the place in AR
        val placeNode =
            PlaceNode(this, arFragment.transformationSystem, true, place, currentLocation)
        placeNode.setParent(anchorNode)
        placeNode.localPosition =
            place.getPositionVector(orientationAngles[0], currentLocation.latLng)

        placeNode.setOnTapListener { _, _ ->
            showInfoWindow(place)
        }

        // Add the place in maps
        map?.let {
            val marker = it.addMarker(
                MarkerOptions()
                    .position(place.geometry.location.latLng)
                    .title(place.name)
            )
            marker.tag = place
            markers.add(marker)
        }

    }

    private fun showInfoWindow(place: Place) {
        // Show in AR
        val matchingPlaceNode = anchorNode?.children?.filter {
            it is PlaceNode
        }?.first {
            val otherPlace = (it as PlaceNode).place ?: return@first false
            return@first otherPlace == place
        } as? PlaceNode
        matchingPlaceNode?.showInfoWindow()

        // Show as marker
        val matchingMarker = markers.firstOrNull {
            val placeTag = (it.tag as? Place) ?: return@firstOrNull false
            return@firstOrNull placeTag == place
        }
        matchingMarker?.showInfoWindow()
    }

    @SuppressLint("MissingPermission")
    private fun setUpMaps() {
        mapFragment.getMapAsync { googleMap ->

            googleMap.isMyLocationEnabled = true

            locationViewModel.getLocationData().observe(this@ArPlaceActivity, Observer {
                val pos = CameraPosition.fromLatLngZoom(it.latLng, 16f)
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                getDirections(it, googleMap)
            })

            googleMap.setOnMarkerClickListener { marker ->
                val tag = marker.tag
                if (tag !is Place) {
                    return@setOnMarkerClickListener false
                }
                showInfoWindow(tag)
                return@setOnMarkerClickListener true
            }
            map = googleMap
        }
    }

    private fun getDirections(currentLocation: Location, googleMap: GoogleMap) {
        val directionsService = DirectionsService.create()
        directionsService.getDirections(
            getString(R.string.google_maps_key),
            "${currentLocation.latitude},${currentLocation.longitude}",
            "place_id:${place.place_id}",
            "walking"
        ).enqueue(object : Callback<DirectionsResponse> {
            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                Log.e(TAG + "Directions", "Failed to get directions", t)
            }

            override fun onResponse(
                call: Call<DirectionsResponse>,
                response: Response<DirectionsResponse>
            ) {
                if (!response.isSuccessful) {
                    Log.e(TAG + "Directions", "Failed to get directions")
                    return
                }

                val points = response.body()!!.results[0].overviewPolyline.points
                val decodedPoints = decodePoly(points)

                val POLYLINE_STROKE_WIDTH_PX = 12
                val PATTERN_GAP_LENGTH_PX = 20
                val DOT: PatternItem = Dot()
                val GAP: PatternItem = Gap(PATTERN_GAP_LENGTH_PX.toFloat())
                val PATTERN_POLYLINE_DOTTED = listOf(GAP, DOT)

                //Create PolylineOptions
                val polylineOptions =
                    PolylineOptions()
                        .width(POLYLINE_STROKE_WIDTH_PX.toFloat())
                        .pattern(PATTERN_POLYLINE_DOTTED)

                //Add points to polylineOption
                if (decodedPoints != null) {
                    for (point in decodedPoints) {
                        polylineOptions.add(point)
                    }
                }

                googleMap.addPolyline(polylineOptions)
            }
        })
    }

    private fun decodePoly(encoded: String): List<LatLng>? {
        val poly: MutableList<LatLng> = ArrayList()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }
        return poly
    }

    private fun isSupportedDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val openGlVersionString = activityManager.deviceConfigurationInfo.glEsVersion
        if (openGlVersionString.toDouble() < 3.0) {
            Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
            finish()
            return false
        }
        return true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) {
            return
        }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
    }

}

val Location.latLng: LatLng
    get() = LatLng(this.latitude, this.longitude)
