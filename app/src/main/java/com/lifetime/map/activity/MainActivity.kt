package com.lifetime.map.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.lifetime.map.R
import com.lifetime.map.utils.DirectionsParser
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null

    private var mLastKnownLocation: Location? = null

    private var mMap: GoogleMap? = null

    private var mSearchText: EditText? = null

    private var locations: MutableList<LatLng> = ArrayList()

    private lateinit var geocoder: Geocoder

    private var startPoint: EditText? = null

    //test
    private lateinit var listPoints: List<LatLng>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)

        mSearchText = findViewById(R.id.input_search)

        startPoint = findViewById(R.id.input_start_point)

        listPoints = ArrayList()

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        geocoder = Geocoder(this@MainActivity)

        mMap!!.uiSettings.isZoomControlsEnabled = true
        mMap!!.isMyLocationEnabled = false

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST)
            return
        }

        getDeviceLocation()

        current_location.setOnClickListener { getDeviceLocation() }

        init()

        clear_map.setOnClickListener {
            locations.clear()
            mMap!!.clear()
        }

        mMap!!.setOnMapLongClickListener { latLng ->
            mMap!!.clear()

            getLocationValueNoMoveCamera()

            var resultAddresses: List<Address>? = null

            for (latLng in locations) {
                try {
                    resultAddresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                mMap!!.addMarker(MarkerOptions().position(latLng).title(resultAddresses!![0].getAddressLine(0 ))).setIcon(bitmapDescriptorFromVector(applicationContext,
                        R.drawable.ic_indicator
                ))
            }

            showCurrentPlaceInformation(latLng)
        }
    }

    @SuppressLint("StaticFieldLeak")
    inner class TaskRequestDirections : AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg strings: String): String {
            return requestDirection(strings[0])
        }

        override fun onPostExecute(s: String) {
            super.onPostExecute(s)
            TaskParser().execute(s)
        }
    }

    @SuppressLint("StaticFieldLeak")
    inner class TaskParser : AsyncTask<String, Void, List<List<HashMap<String, String>>>>() {

        override fun doInBackground(vararg strings: String): List<List<HashMap<String, String>>>? {
            val jsonObject: JSONObject?
            val routes: List<List<HashMap<String, String>>>?
            jsonObject = JSONObject(strings[0])
                val directionsParser = DirectionsParser()
                routes = directionsParser.parse(jsonObject)
            return routes
        }

        override fun onPostExecute(lists: List<List<HashMap<String, String>>>) {
            val points= ArrayList<LatLng>()

            var polylineOptions: PolylineOptions? = null

            for (path in lists) {
                polylineOptions = PolylineOptions()

                for (point in path) {
                    val lat = java.lang.Double.parseDouble(point["lat"]!!)
                    val lon = java.lang.Double.parseDouble(point["long"]!!)

                    points.add(LatLng(lat, lon))
                }

                polylineOptions.addAll(points)
                polylineOptions.width(15f)
                polylineOptions.color(Color.BLUE)
                polylineOptions.geodesic(true)
            }
            if (polylineOptions != null) {
                mMap!!.addPolyline(polylineOptions)
            } else {
                Toast.makeText(applicationContext, "Direction not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestDirection(reqUrl: String): String {
        var responseString = ""
        var inputStream: InputStream? = null
        var httpURLConnection: HttpURLConnection? = null
        try {
            val url = URL(reqUrl)
            httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.connect()

            //Get the response result
            inputStream = httpURLConnection.inputStream
            val inputStreamReader = InputStreamReader(inputStream!!)
            val bufferedReader = BufferedReader(inputStreamReader)

            val stringBuffer = StringBuffer()
            val line: String = bufferedReader.readLine()
            while (true) {
                stringBuffer.append(line)
            }

            responseString = stringBuffer.toString()
            bufferedReader.close()
            inputStreamReader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
            httpURLConnection!!.disconnect()
        }
        return responseString
    }

    private fun getRequestUrl(origin: LatLng, dest: LatLng): String {
        //        https://route.api.here.com/routing/7.2/calculateroute.
        //        app_id=IHvLTkkCTu4oUixgJ4gR
        //        &
        //         app_code=E_CvlpVwmJtn3uVlJvqPlg
        //         &
        //         waypoint0=21.006323%2C105.843127
        //         &
        //         waypoint1=21.009153%2C105.828569
        //         &
        //         mode=fastest%3Bcar%3Btraffic%3Aenabled
        //         &
        //         departure=now

        val str_org = "waypoint0=" + origin.latitude + "%2C" + origin.longitude
        val str_dest = "waypoint1=" + dest.latitude + "%2C" + dest.longitude
        val app_id = "app_id=" + resources.getString(R.string.app_id)
        val app_code = "app_code=" + resources.getString(R.string.app_code)
        val mode = "mode=fastest%3Bcar%3Btraffic%3Aenabled"
        val departure = "departure=now"

        val output = "json"
        val param = "$app_id&$app_code&$str_org&$str_dest&$mode&$departure"

        return "https://route.api.here.com/routing/7.2/calculateroute.$output?$param"
    }

    private fun showCurrentPlaceInformation(latLng: LatLng) {
        var resultAddresses: List<Address>? = null
        try {
            resultAddresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val markerOptions = MarkerOptions()
        markerOptions.position(latLng)
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        markerOptions.title(resultAddresses!![0].getAddressLine(0))
        mMap!!.addMarker(markerOptions)

        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 13f)
        mMap!!.animateCamera(cameraUpdate)
    }

    private fun getDeviceLocation() {
        val locationResult = mFusedLocationProviderClient!!.lastLocation
        locationResult.addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                mLastKnownLocation = task.result
                val target = LatLng(mLastKnownLocation!!.latitude, mLastKnownLocation!!.longitude)
                val mark = MarkerOptions()
                        .title("Current Position.")
                        .position(target)
                        .icon(bitmapDescriptorFromVector(this,R.drawable.ic_indicator))
                mMap!!.addMarker(mark)
                val cameraUpdate = CameraUpdateFactory.newLatLngZoom(target, 13f)
                mMap!!.animateCamera(cameraUpdate)
            } else {
                Toast.makeText(this@MainActivity, "problem here", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getLocationValueNoMoveCamera() {
        val locationResult = mFusedLocationProviderClient!!.lastLocation
        locationResult.addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                mLastKnownLocation = task.result
                val target = LatLng(mLastKnownLocation!!.latitude, mLastKnownLocation!!.longitude)
                val mark = MarkerOptions()
                        .title("Current Position.")
                        .position(target)
                        .icon(bitmapDescriptorFromVector(this,R.drawable.ic_indicator))
                mMap!!.addMarker(mark)
            } else {
                Toast.makeText(this@MainActivity, "problem here", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun init() {
        Log.d("TAG", "init: initializing")

        findViewById<View>(R.id.direction).setOnClickListener { showDirectionResult() }

        mSearchText!!.setOnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || keyEvent.action == KeyEvent.ACTION_DOWN
                    || keyEvent.action == KeyEvent.KEYCODE_ENTER) {

                showResult()
            }
            false
        }
    }

    private fun showDirectionResult() {
        val sStartPoint = input_start_point.text.toString()
        val sEndPoint = input_search.text.toString()

        if (sStartPoint.isEmpty()) {
            input_start_point.error = "required"
            input_start_point.requestFocus()
            return
        }

        if (sEndPoint.isEmpty()) {
            input_search.error = "required"
            input_search.requestFocus()
            return
        }

        var addressesStart: List<Address>? = null
        var addressesEnd: List<Address>? = null
        try {
            addressesStart = geocoder.getFromLocationName(input_start_point.text.toString(), 1)
            addressesEnd = geocoder.getFromLocationName(input_search.text.toString(), 1)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (addressesStart!!.isNotEmpty() && addressesEnd!!.isNotEmpty()) {
            val latStart = addressesStart[0].latitude
            val lonStart = addressesStart[0].longitude
            val startPoint = LatLng(latStart, lonStart)

            val latEnd = addressesEnd[0].latitude
            val lonEnd = addressesEnd[0].longitude
            val endPoint = LatLng(latEnd, lonEnd)

            val url = getRequestUrl(startPoint, endPoint)
            TaskRequestDirections().execute(url)

            mMap!!.clear()

            mMap!!.addMarker(MarkerOptions()
                    .position(startPoint)
                    .title(addressesStart[0].getAddressLine(0)))

            mMap!!.addMarker(MarkerOptions()
                    .position(endPoint)
                    .title(addressesEnd[0].getAddressLine(0)))

            val bounds = LatLngBounds.Builder()
                    .include(startPoint)
                    .include(endPoint)
                    .build()
            val padding = 200
            val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            mMap!!.animateCamera(cu)

        }
    }

    private fun showResult() {
        CameraUpdateFactory.zoomTo(12f)

        //declare a EditText to get user informed address
        val etEndereco = findViewById<EditText>(R.id.input_search)

        var addresses: List<Address>? = null
        try {
            addresses = geocoder.getFromLocationName(etEndereco.text.toString(), 1)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (addresses!!.isNotEmpty()) {
            val latitude = addresses[0].latitude
            val longitude = addresses[0].longitude
            locations.add(LatLng(latitude, longitude))
            val builder = LatLngBounds.Builder()
            for (latLng in locations) {
                mMap!!.addMarker(MarkerOptions().position(latLng).title("Your Location")).setIcon(bitmapDescriptorFromVector(applicationContext,
                        R.drawable.ic_indicator
                ))
                builder.include(latLng)
            }

            val bounds = builder.build()
            val padding = 200
            val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            mMap!!.animateCamera(cu)
        }
    }

    companion object {
        private const val LOCATION_REQUEST = 500
    }
    private fun bitmapDescriptorFromVector(context: Context, @DrawableRes vectorDrawableResourceId: Int): BitmapDescriptor {
        val background = ContextCompat.getDrawable(context,
                R.drawable.ic_been_here_marker
        )
        background!!.setBounds(0, 0, background.intrinsicWidth, background.intrinsicHeight)
        val vectorDrawable = ContextCompat.getDrawable(context, vectorDrawableResourceId)
        vectorDrawable!!.setBounds(0, 0
                , vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(background.intrinsicWidth, background.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        background.draw(canvas)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

}
