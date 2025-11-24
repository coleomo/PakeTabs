package com.pakeandroid.paketabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity(), BrowserTabFragment.TabHost {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var tabsAdapter: BrowserTabsAdapter
    private lateinit var tabMediator: TabLayoutMediator
    private val tabs = mutableListOf<TabEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        tabsAdapter = BrowserTabsAdapter(this)
        viewPager.adapter = tabsAdapter

        tabMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.customView = createTabView(tab, position)
        }
        tabMediator.attach()

        if (tabs.isEmpty()) {
            addNewTab(DEFAULT_HOME_URL)
        }
    }

    override fun onDestroy() {
        if (::tabMediator.isInitialized) {
            tabMediator.detach()
        }
        super.onDestroy()
    }

    override fun openNewTab(url: String) {
        addNewTab(url)
    }

    override fun updateTabTitle(tabId: Long, title: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return
        val displayTitle = title.ifBlank { tabs[index].initialUrl }
        if (tabs[index].title == displayTitle) return
        tabs[index] = tabs[index].copy(title = displayTitle)
        refreshTabTitle(index)
    }

    private fun addNewTab(url: String) {
        val newTab = TabEntry(
            id = System.nanoTime(),
            title = url,
            initialUrl = url
        )
        tabs.add(newTab)
        tabsAdapter.notifyItemInserted(tabs.lastIndex)
        viewPager.setCurrentItem(tabs.lastIndex, true)
    }

    private fun closeTab(position: Int) {
        if (position !in tabs.indices) return
        tabs.removeAt(position)
        tabsAdapter.notifyItemRemoved(position)
        if (tabs.isEmpty()) {
            addNewTab(DEFAULT_HOME_URL)
        } else {
            val nextIndex = position.coerceAtMost(tabs.lastIndex)
            viewPager.setCurrentItem(nextIndex, true)
        }
    }

    private fun refreshTabTitle(position: Int) {
        val tab = tabLayout.getTabAt(position) ?: return
        val titleView = tab.customView?.findViewById<TextView>(R.id.tabTitle) ?: return
        titleView.text = tabs[position].title
    }

    private fun createTabView(tab: TabLayout.Tab, position: Int): View {
        val view = LayoutInflater.from(this).inflate(R.layout.view_tab, null)
        val titleView = view.findViewById<TextView>(R.id.tabTitle)
        val closeButton = view.findViewById<ImageButton>(R.id.tabClose)
        titleView.text = tabs[position].title
        closeButton.setOnClickListener {
            val currentPos = tab.position
            if (currentPos != TabLayout.Tab.INVALID_POSITION) {
                closeTab(currentPos)
            }
        }
        return view
    }

    private inner class BrowserTabsAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = tabs.size

        override fun createFragment(position: Int) =
            BrowserTabFragment.newInstance(tabs[position].id, tabs[position].initialUrl)

        override fun getItemId(position: Int): Long = tabs[position].id

        override fun containsItem(itemId: Long): Boolean = tabs.any { it.id == itemId }
    }

    private data class TabEntry(
        val id: Long,
        val title: String,
        val initialUrl: String
    )

    companion object {
        private const val DEFAULT_HOME_URL = "https://juejin.cn/"
    }
}