package com.krystelligence.solipsism.browser.view

import com.krystelligence.solipsism.icon.TabCountView
import com.krystelligence.solipsism.search.SearchView
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

interface ViewDelegate {

    val root: CoordinatorLayout

    val toolbar: MaterialToolbar

    val contentFrame: FrameLayout

    val uiLayout: LinearLayout

    val browserLayoutContainer: FrameLayout?

    val toolbarLayout: ConstraintLayout

    val drawerLayout: DrawerLayout

    val tabDrawer: LinearLayout

    val bookmarkDrawer: LinearLayout

    val homeImageView: ImageView

    val tabCountView: TabCountView

    val drawerTabsList: RecyclerView

    val desktopTabsList: RecyclerView

    val bookmarkListView: RecyclerView

    val searchContainer: ConstraintLayout

    val search: SearchView

    val findBar: LinearLayout

    val findQuery: EditText

    val findPrevious: ImageButton

    val findNext: ImageButton

    val findQuit: ImageButton

    val homeButton: FrameLayout

    val actionBack: ImageView

    val actionForward: ImageView

    val actionHome: ImageView

    val newTabButton: ImageView

    val searchRefresh: ImageView

    val searchQr: ImageView?

    val actionAddBookmark: ImageView

    val actionPageTools: ImageView

    val tabHeaderButton: ImageView

    val bookmarkBackButton: ImageView

    val searchSslStatus: ImageView

    val progressView: ProgressBar

    val verticalUrlText: TextView?
        get() = null

    val addressOverlay: View?
        get() = null

    val settingsButton: ImageView?
        get() = null

    val addressRail: LinearLayout?
        get() = null

    val railNav: LinearLayout?
        get() = null

}
