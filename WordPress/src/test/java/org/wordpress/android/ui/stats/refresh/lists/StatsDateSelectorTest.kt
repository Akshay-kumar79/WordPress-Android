package org.wordpress.android.ui.stats.refresh.lists

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.fluxc.network.utils.StatsGranularity
import org.wordpress.android.ui.stats.refresh.StatsViewModel.DateSelectorUiModel
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection
import org.wordpress.android.ui.stats.refresh.lists.sections.granular.SelectedDateProvider
import org.wordpress.android.ui.stats.refresh.lists.sections.granular.SelectedDateProvider.SectionChange
import org.wordpress.android.ui.stats.refresh.utils.StatsDateFormatter
import org.wordpress.android.ui.stats.refresh.utils.StatsDateSelector
import org.wordpress.android.ui.stats.refresh.utils.StatsSiteProvider
import org.wordpress.android.util.config.StatsTrafficTabFeatureConfig
import java.util.Date

@ExperimentalCoroutinesApi
class StatsDateSelectorTest : BaseUnitTest() {
    @Mock
    lateinit var selectedDateProvider: SelectedDateProvider

    @Mock
    lateinit var statsDateFormatter: StatsDateFormatter

    @Mock
    lateinit var siteProvider: StatsSiteProvider

    @Mock
    lateinit var statsTrafficTabFeatureConfig: StatsTrafficTabFeatureConfig
    private val selectedDate = Date(0)
    private val selectedDateLabel = "Jan 1"
    private val statsSection = StatsSection.DAYS
    private val statsGranularity = StatsGranularity.DAYS
    private val updatedDate = Date(10)
    private val updatedLabel = "Jan 2"

    private val dateProviderSelectedDate = MutableLiveData<SectionChange>()

    private lateinit var dateSelector: StatsDateSelector

    @Before
    fun setUp() {
        dateProviderSelectedDate.value = SectionChange(statsSection)
        whenever(selectedDateProvider.granularSelectedDateChanged(statsSection)).thenReturn(dateProviderSelectedDate)

        dateSelector = StatsDateSelector(
            selectedDateProvider,
            statsDateFormatter,
            siteProvider,
            statsTrafficTabFeatureConfig,
            statsSection
        )
        whenever(selectedDateProvider.getSelectedDate(statsSection)).thenReturn(selectedDate)
        whenever(statsDateFormatter.printGranularDate(selectedDate, statsGranularity)).thenReturn(selectedDateLabel)
        whenever(statsDateFormatter.printGranularDate(updatedDate, statsGranularity)).thenReturn(updatedLabel)
        whenever(statsTrafficTabFeatureConfig.isEnabled()).thenReturn(true)
    }

    @Test
    fun `does not reemit hidden date selector`() {
        val models = mutableListOf<DateSelectorUiModel>()
        dateSelector.dateSelectorData.observeForever { model -> model?.let { models.add(it) } }

        dateSelector.updateDateSelector()

        Assertions.assertThat(models).hasSize(1)

        dateSelector.updateDateSelector()

        Assertions.assertThat(models).hasSize(1)
    }

    @Test
    fun `shows date selector on days screen`() {
        whenever(selectedDateProvider.getSelectedDate(statsSection)).thenReturn(selectedDate)
        whenever(selectedDateProvider.hasPreviousDate(statsSection)).thenReturn(true)
        whenever(selectedDateProvider.hasNextDate(statsSection)).thenReturn(true)
        var model: DateSelectorUiModel? = null

        dateSelector.dateSelectorData.observeForever { model = it }

        dateSelector.updateDateSelector()

        Assertions.assertThat(model).isNotNull
        Assertions.assertThat(model?.isVisible).isTrue()
        Assertions.assertThat(model?.enableSelectPrevious).isTrue()
        Assertions.assertThat(model?.enableSelectNext).isTrue()
        Assertions.assertThat(model?.date).isEqualTo(selectedDateLabel)
    }

    @Test
    fun `updates date selector on date change`() {
        whenever(selectedDateProvider.hasPreviousDate(statsSection)).thenReturn(true)
        whenever(selectedDateProvider.hasNextDate(statsSection)).thenReturn(true)
        var model: DateSelectorUiModel? = null
        dateSelector.dateSelectorData.observeForever { model = it }

        dateSelector.updateDateSelector()

        Assertions.assertThat(model?.date).isEqualTo(selectedDateLabel)

        whenever(selectedDateProvider.getSelectedDate(statsSection)).thenReturn(updatedDate)

        dateSelector.updateDateSelector()

        Assertions.assertThat(model?.date).isEqualTo(updatedLabel)
    }

    @Test
    fun `verify date selector hidden for insights`() {
        whenever(selectedDateProvider.granularSelectedDateChanged(StatsSection.INSIGHTS)).thenReturn(
            dateProviderSelectedDate
        )
        dateSelector = StatsDateSelector(
            selectedDateProvider,
            statsDateFormatter,
            siteProvider,
            statsTrafficTabFeatureConfig,
            StatsSection.INSIGHTS
        )
        var model: DateSelectorUiModel? = null
        dateSelector.dateSelectorData.observeForever { model = it }

        dateSelector.updateDateSelector()

        Assertions.assertThat(model).isNotNull
        Assertions.assertThat(model?.isVisible).isFalse()
    }
}
