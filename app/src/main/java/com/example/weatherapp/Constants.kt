package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants{

    const val app_id: String = "a5893be58f561469805b6b69d9e8329e"
    const val base_url: String = "http://api.openweathermap.org/data/"
    const val metric_unit: String = "metric"


    fun isNetworkAvailable(context: Context): Boolean{
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){ //sprawdzamy wersje telefonu
            val network = connectivityManager.activeNetwork ?: return false //jesli jest to nowa wersja
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }else{ //dla starych wersji androida
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }

    }
}