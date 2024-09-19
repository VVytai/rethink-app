/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.celzero.bravedns.database.ConnectionTrackerDAO
import com.celzero.bravedns.util.Constants

class DomainConnectionsViewModel(private val connectionTrackerDAO: ConnectionTrackerDAO) : ViewModel() {
    private var domains: MutableLiveData<String> = MutableLiveData()
    private var flag: MutableLiveData<String> = MutableLiveData()
    private var timeCategory: TimeCategory = TimeCategory.ONE_HOUR
    private var startTime: MutableLiveData<Long> = MutableLiveData()

    companion object {
        private const val ONE_HOUR_MILLIS = 1 * 60 * 60 * 1000L
        private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        private const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }

    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    init {
        // set from and to time to current and 1 hr before
        startTime.value = System.currentTimeMillis() - ONE_HOUR_MILLIS
        domains.postValue("")
    }

    fun setDomain(domain: String) {
        domains.postValue(domain)
    }

    fun setFlag(flag: String) {
        this.flag.postValue(flag)
    }

    fun timeCategoryChanged(tc: TimeCategory) {
        timeCategory = tc
        when (tc) {
            TimeCategory.ONE_HOUR -> {
                startTime.value = System.currentTimeMillis() - ONE_HOUR_MILLIS
            }
            TimeCategory.TWENTY_FOUR_HOUR -> {
                startTime.value = System.currentTimeMillis() - ONE_DAY_MILLIS
            }
            TimeCategory.SEVEN_DAYS -> {
                startTime.value = System.currentTimeMillis() - ONE_WEEK_MILLIS
            }
        }
    }

    val domainConnectionList = domains.switchMap { input ->
        fetchDomainConnections(input)
    }

    val flagConnectionList = flag.switchMap { input ->
        fetchFlagConnections(input)
    }

    private fun fetchDomainConnections(input: String) =
        Pager(PagingConfig(pageSize = Constants.LIVEDATA_PAGE_SIZE)) {
            connectionTrackerDAO.getDomainConnections(input, startTime.value!!)
        }.liveData.cachedIn(viewModelScope)

    private fun fetchFlagConnections(input: String) =
        Pager(PagingConfig(pageSize = Constants.LIVEDATA_PAGE_SIZE)) {
            connectionTrackerDAO.getFlagConnections(input, startTime.value!!)
        }.liveData.cachedIn(viewModelScope)
}
