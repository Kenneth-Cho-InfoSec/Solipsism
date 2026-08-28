package com.krystelligence.solipsism.search

import com.krystelligence.solipsism.browser.di.SuggestionsClient
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.search.engine.AskSearch
import com.krystelligence.solipsism.search.engine.BaiduSearch
import com.krystelligence.solipsism.search.engine.BaseSearchEngine
import com.krystelligence.solipsism.search.engine.BingSearch
import com.krystelligence.solipsism.search.engine.CustomSearch
import com.krystelligence.solipsism.search.engine.DuckLiteSearch
import com.krystelligence.solipsism.search.engine.DuckSearch
import com.krystelligence.solipsism.search.engine.GoogleSearch
import com.krystelligence.solipsism.search.engine.NaverSearch
import com.krystelligence.solipsism.search.engine.StartPageMobileSearch
import com.krystelligence.solipsism.search.engine.StartPageSearch
import com.krystelligence.solipsism.search.engine.YahooSearch
import com.krystelligence.solipsism.search.engine.YandexSearch
import com.krystelligence.solipsism.search.suggestions.BaiduSuggestionsModel
import com.krystelligence.solipsism.search.suggestions.DuckSuggestionsModel
import com.krystelligence.solipsism.search.suggestions.GoogleSuggestionsModel
import com.krystelligence.solipsism.search.suggestions.NaverSuggestionsModel
import com.krystelligence.solipsism.search.suggestions.NoOpSuggestionsRepository
import com.krystelligence.solipsism.search.suggestions.RequestFactory
import com.krystelligence.solipsism.search.suggestions.SuggestionsRepository
import android.app.Application
import dagger.Reusable
import io.reactivex.rxjava3.core.Single
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * The model that provides the search engine based
 * on the user's preference.
 */
@Reusable
class SearchEngineProvider @Inject constructor(
    private val userPreferences: UserPreferences,
    @SuggestionsClient private val okHttpClient: Single<OkHttpClient>,
    private val requestFactory: RequestFactory,
    private val application: Application,
    private val logger: Logger
) {

    /**
     * Provide the [SuggestionsRepository] that maps to the user's current preference.
     */
    fun provideSearchSuggestions(): SuggestionsRepository =
        when (userPreferences.searchSuggestionChoice) {
            0 -> NoOpSuggestionsRepository()
            1 -> GoogleSuggestionsModel(okHttpClient, requestFactory, application, logger)
            2 -> DuckSuggestionsModel(okHttpClient, requestFactory, application, logger)
            3 -> BaiduSuggestionsModel(okHttpClient, requestFactory, application, logger)
            4 -> NaverSuggestionsModel(okHttpClient, requestFactory, application, logger)
            else -> DuckSuggestionsModel(okHttpClient, requestFactory, application, logger)
        }

    /**
     * Provide the [BaseSearchEngine] that maps to the user's current preference.
     */
    fun provideSearchEngine(): BaseSearchEngine =
        when (userPreferences.searchChoice) {
            0 -> CustomSearch(userPreferences.searchUrl)
            1 -> GoogleSearch()
            2 -> AskSearch()
            3 -> BingSearch()
            4 -> YahooSearch()
            5 -> StartPageSearch()
            6 -> StartPageMobileSearch()
            7 -> DuckSearch()
            8 -> DuckLiteSearch()
            9 -> BaiduSearch()
            10 -> YandexSearch()
            11 -> NaverSearch()
            else -> DuckSearch()
        }

    /**
     * Return the serializable index of of the provided [BaseSearchEngine].
     */
    fun mapSearchEngineToPreferenceIndex(searchEngine: BaseSearchEngine): Int =
        when (searchEngine) {
            is CustomSearch -> 0
            is GoogleSearch -> 1
            is AskSearch -> 2
            is BingSearch -> 3
            is YahooSearch -> 4
            is StartPageSearch -> 5
            is StartPageMobileSearch -> 6
            is DuckSearch -> 7
            is DuckLiteSearch -> 8
            is BaiduSearch -> 9
            is YandexSearch -> 10
            is NaverSearch -> 11
            else -> throw UnsupportedOperationException("Unknown search engine provided: " + searchEngine.javaClass)
        }

    /**
     * Provide a list of all supported search engines.
     */
    fun provideAllSearchEngines(): List<BaseSearchEngine> = listOf(
        CustomSearch(userPreferences.searchUrl),
        GoogleSearch(),
        AskSearch(),
        BingSearch(),
        YahooSearch(),
        StartPageSearch(),
        StartPageMobileSearch(),
        DuckSearch(),
        DuckLiteSearch(),
        BaiduSearch(),
        YandexSearch(),
        NaverSearch()
    )

    companion object {
        /** The persisted search-engine index used when no choice has been made. */
        const val DEFAULT_SEARCH_ENGINE_INDEX = 7
    }

}
