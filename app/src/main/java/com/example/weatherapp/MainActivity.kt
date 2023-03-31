package com.example.weatherapp


import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService

import retrofit.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {


    private var binding: ActivityMainBinding? = null

    // A fused location client variable which is further user to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    /*
    private var tv_main = binding?.tvMain?.text
    private var tv_description = binding?.tvMainDescription?.toString()
    private var tvTemp = binding?.tvDegree?.text.toString()
    */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        // Initialize the Fused location variable
        mFusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this) //aktualna lokalizacja uzytkownika

        if (!isLocationEnabled()) { //sprawdzenie usługi lokalizacji
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            // przejscie do ustawien lokalizacji
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }

    private fun showProgressDialog() {
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {

        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    //czy usługi lokalizacyjne są włączone na urządzeniu
    private fun isLocationEnabled(): Boolean {

        val locationManager: LocationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    //dialog
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }


    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    //obiekt klasy anonimowej, reagujacy na wyniki lokalizacji
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation!!.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation!!.longitude
            Log.i("Current Longitude", "$longitude")



            getLocationWeatherDetails(latitude, longitude)


        }
    }


    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {

        //sprawdzamy dostep do internetu
        if (Constants.isNetworkAvailable(this@MainActivity)) {

            val retrofit: Retrofit = Retrofit.Builder() //tworzymy obiekt retrofit
                .baseUrl(Constants.base_url) //za pomoca base_url
                .addConverterFactory(GsonConverterFactory.create()) //format json
                .build()

            val service: WeatherService =
                retrofit.create(WeatherService::class.java) //prepare service

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,
                longitude,
                Constants.metric_unit,
                Constants.app_id
            ) //prepare listCall

            showProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                @SuppressLint("SetTextI18n")
                override fun onResponse(
                    response: Response<WeatherResponse>,
                    retrofit: Retrofit
                ) {

                    // Check weather the response is success or not.
                    if (response.isSuccess) {

                        hideProgressDialog()

                        val weatherList: WeatherResponse = response.body()
                        setupUI(weatherList)
                        Log.i("Response Result", "$weatherList")
                    } else {
                        // If the response is not success then we check the response code.
                        val sc = response.code()
                        when (sc) {
                            400 -> {
                                Log.e("Error 400", "Bad Request")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable) {
                    hideProgressDialog()
                    Log.e("Errorrrrr", t.message.toString())
                }
            })
            // END

        } else {
            Toast.makeText(
                this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()
        }

    }


    private fun setupUI(weatherList: WeatherResponse) {

        for (i in weatherList.weather.indices) {
            Log.i("Weather name", weatherList.weather.toString())

            binding?.tvMain?.text = weatherList.weather[i].main
            binding?.tvMainDescription?.text = weatherList.weather[i].description
            binding?.tvDegree?.text =
                weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            binding?.tvDegreeDesc?.text =
                weatherList.main.feels_like.toString() + getUnit(application.resources.configuration.locales.toString())
            binding?.tvSpeed?.text = weatherList.wind.speed.toString()
            binding?.tvMin?.text =
                weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.locales.toString())
            binding?.tvMax?.text =
                weatherList.main.temp_max.toString() + getUnit(application.resources.configuration.locales.toString())
            binding?.tvCountry?.text = weatherList.sys.country
            binding?.tvName?.text = weatherList.name
            binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise.toLong())
            binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset.toLong())

            when (weatherList.weather[i].icon) {
                "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                "01n" -> binding?.ivMain?.setImageResource(R.drawable.moon)
                "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloudy)
                "03d" -> binding?.ivMain?.setImageResource(R.drawable.clouds)
                "09d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "10d" -> binding?.ivMain?.setImageResource(R.drawable.heavyrain)
                "11d" -> binding?.ivMain?.setImageResource(R.drawable.thunderstorm)
                "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "50d" -> binding?.ivMain?.setImageResource(R.drawable.mist)
            }
        }
    }

    private fun getUnit(value: String): String? {

        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)

        }

    }
}
private fun <T> Call<T>.enqueue(callback: Callback<T>) {

}




