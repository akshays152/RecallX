package com.recallx
import android.app.Application
import com.recallx.data.repository.DefaultRecallXRepository
class RecallXApplication : Application() { val repository by lazy { DefaultRecallXRepository.create() } }
