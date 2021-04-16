package com.mrtan.devicejsontest.service

import android.content.Intent
import android.os.Looper
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object MessageBus : LiveData<Intent>() {
  fun send(intent: Intent) {
    if (Thread.currentThread() == Looper.getMainLooper().thread){
      value = intent
    } else {
      GlobalScope.launch(Dispatchers.Main) {
        value = intent
      }
    }
  }
}