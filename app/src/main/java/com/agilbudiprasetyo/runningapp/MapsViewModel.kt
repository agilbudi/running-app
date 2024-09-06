package com.agilbudiprasetyo.runningapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.agilbudiprasetyo.runningapp.data.model.Direction
import com.google.android.gms.maps.model.LatLng

class MapsViewModel: ViewModel() {
    private val direction = MutableLiveData<Direction>()

    fun setLatLng(allLatLng: ArrayList<LatLng>) {
        val firstDirection = allLatLng.first()
        val lastDirection = allLatLng.last()
        direction.postValue(Direction(firstDirection, lastDirection))
    }

    fun getDirection(): LiveData<Direction> {
        return direction
    }
}