package com.tori.safety.ui.monitoring

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.tori.safety.ml.DrowsinessDetector
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class MonitoringViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MonitoringViewModel
    private lateinit var drowsinessDetector: DrowsinessDetector
    private val earFlow = MutableSharedFlow<Float>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        drowsinessDetector = mockk()
        coEvery { drowsinessDetector.earFlow } returns earFlow
        viewModel = MonitoringViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startMonitoring should update earValue`() = runTest {
        var observedValue: Float? = null
        val observer: (Float) -> Unit = { observedValue = it }
        viewModel.earValue.observeForever(observer)
        
        viewModel.startMonitoring(drowsinessDetector)
        earFlow.emit(0.25f)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Check that the value was observed
        assert(observedValue != null)
        assertEquals(0.25f, observedValue!!, 0.001f)
        
        viewModel.earValue.removeObserver(observer)
    }
}