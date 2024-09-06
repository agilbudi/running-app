package com.agilbudiprasetyo.runningapp

import android.Manifest
import android.content.IntentSender
import android.content.res.Resources
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.agilbudiprasetyo.runningapp.data.model.Direction
import com.agilbudiprasetyo.runningapp.databinding.ActivityMapsBinding
import com.agilbudiprasetyo.runningapp.util.checkingPermission
import com.agilbudiprasetyo.runningapp.util.createLocationRequest
import com.agilbudiprasetyo.runningapp.util.vectorToBipmap
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.util.concurrent.TimeUnit

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private var boundsBuilder = LatLngBounds.Builder()
    private val permissionFine = Manifest.permission.ACCESS_FINE_LOCATION
    private val permissionCoarse = Manifest.permission.ACCESS_FINE_LOCATION
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var isTracking = false
    private lateinit var locationCallback: LocationCallback
    private val allLatLng = ArrayList<LatLng>()
    private val viewModel: MapsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val priority = Priority.PRIORITY_HIGH_ACCURACY
        val interval = TimeUnit.SECONDS.toMillis(1)
        val maxWaitTime = TimeUnit.SECONDS.toMillis(1)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(priority, interval).apply {
            setMaxUpdateDelayMillis(maxWaitTime)
        }.build()
        createLocationCallback()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        uiSettings()
        setMapStyle()
        setUpPermissions()

        mMap.setOnMyLocationButtonClickListener {
            getMyLocation()
            false
        }
        mMap.setOnMapLongClickListener { latLng ->
            mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("New Marker")
                    .snippet("Lat: ${latLng.latitude} Long: ${latLng.longitude}")
                    .icon(
                        vectorToBipmap(
                            resources,
                            R.drawable.baseline_person_pin_circle_24,
                            Color.parseColor("#3DDC84")
                        )
                    )
            )
        }
        mMap.setOnPoiClickListener { pointOfInterest ->
            val poiMarker = mMap.addMarker(
                MarkerOptions()
                    .position(pointOfInterest.latLng)
                    .title(pointOfInterest.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
            )
            poiMarker?.showInfoWindow()
        }
        binding.btnMain.setOnClickListener {
            if (!isTracking) {
                clearMaps()
                startLocationUpdates()
            } else {
                updateTrackingStatus(false)
                viewModel.setLatLng(allLatLng)
                stopLocationUpdates()
                viewModel.getDirection().observe(this) { direction ->
                    showMarkRoute(direction)
                }
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.map_option, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.normal_type -> {
                mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                true
            }

            R.id.satellite_type -> {
                mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
                true
            }

            R.id.terrain_type -> {
                mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
                true
            }

            R.id.hybrid_type -> {
                mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isTracking) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun uiSettings() {
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isIndoorLevelPickerEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permission ->
            when {
                permission[permissionFine] ?: false -> {
                    setUpPermissions()
                }

                permission[permissionCoarse] ?: false -> {
                    setUpPermissions()
                }

                else -> {
                    Toast.makeText(this, "Permission Granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun setMapStyle() {
        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            )
            if (!success) {
                Log.e(TAG, "Style parsing failed.")
            }
        } catch (exception: Resources.NotFoundException) {
            Log.e(TAG, "Can't find style. Error: ", exception)
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    Log.i(TAG, "onLocationCallback: ${location.latitude}, ${location.longitude}")

                    val lastLatLng = LatLng(location.latitude, location.longitude)
                    allLatLng.add(lastLatLng)

                    mMap.addPolyline(
                        PolylineOptions()
                            .color(Color.MAGENTA)
                            .width(20f)
                            .addAll(allLatLng)
                    )

                    boundsBuilder.include(lastLatLng)
                    val bounds: LatLngBounds = boundsBuilder.build()
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 10))
                }
            }
        }
    }

    private val resolutionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_OK -> {
                Log.d(TAG, "onActivityResult: All location setting are satisfied.")
            }

            RESULT_CANCELED -> {
                Toast.makeText(
                    this@MapsActivity,
                    "Anda harus mengaktifkan GPS untuk menggunakan fitur ini!",
                    Toast.LENGTH_SHORT
                ).show()
                updateTrackingStatus(false)
            }
        }
    }

    private fun setUpPermissions() {
        if (
            checkingPermission(this, permissionFine) &&
            checkingPermission(this, permissionCoarse)
        ) {
            mMap.isMyLocationEnabled = true
        } else {
            requestPermissionLauncher.launch(
                arrayOf(permissionFine, permissionCoarse)
            )
        }
    }

    private fun getMyLocation() {
        createLocationRequest(this, locationRequest)
            .addOnSuccessListener {
                if (
                    checkingPermission(this, permissionFine) &&
                    checkingPermission(this, permissionCoarse)
                ) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                        if (location == null) {
                            Toast.makeText(
                                this@MapsActivity,
                                "Location is not found. Try Again",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    requestPermissionLauncher.launch(
                        arrayOf(permissionFine, permissionCoarse)
                    )
                }
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(exception.resolution).build()
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        Toast.makeText(this@MapsActivity, sendEx.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun showMarker(
        latLng: LatLng,
        title: String,
        parseColor: Int = Color.parseColor("#045A5C")
    ) {
        mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet("Lat: ${latLng.latitude} Long: ${latLng.longitude}")
                .icon(
                    vectorToBipmap(
                        resources,
                        R.drawable.baseline_person_pin_circle_24,
                        parseColor
                    )
                )
        )
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
    }

    private fun updateTrackingStatus(newStatus: Boolean) {
        isTracking = newStatus
        if (isTracking) {
            binding.btnMain.text = getString(R.string.stop_running)
        } else {
            binding.btnMain.text = getString(R.string.start_running)
        }
    }

    private fun startLocationUpdates() {
        createLocationRequest(this, locationRequest)
            .addOnFailureListener { exception ->
                updateTrackingStatus(false)
                if (exception is ResolvableApiException) {
                    try {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(exception.resolution).build()
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        Toast.makeText(this@MapsActivity, sendEx.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnSuccessListener {
                updateTrackingStatus(true)
                try {
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest, locationCallback, Looper.getMainLooper()
                    )
                    updateTrackingStatus(true)
                } catch (exception: SecurityException) {
                    Log.e(TAG, "startLocationUpdates: ${exception.message}")
                }
            }

    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun showMarkRoute(direction: Direction){
        val locationStart = direction.locationStart
        val locationEnd = direction.locationEnd
        showMarker(locationStart, "Start", Color.parseColor("#30B12B"))
        showMarker(locationEnd, "Finish", Color.parseColor("#B22B41"))
    }

    private fun clearMaps() {
        mMap.clear()
        allLatLng.clear()
        boundsBuilder = LatLngBounds.builder()
    }

    companion object {
        const val TAG = "MapsActivity"
    }
}