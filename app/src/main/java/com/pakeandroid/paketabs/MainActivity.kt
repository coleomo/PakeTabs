package com.pakeandroid.paketabs

import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        // 为根视图注册长按菜单（右键菜单）
        registerForContextMenu(findViewById(R.id.main))
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        tabsAdapter = BrowserTabsAdapter(this)
        viewPager.adapter = tabsAdapter
        // 不允许左右滑动切换tab
        viewPager.isUserInputEnabled = false

        // 设置主页按钮和刷新按钮的点击事件
        setupToolbarButtons()

        tabMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.customView = createTabView(tab, position)
        }
        tabMediator.attach()

        if (tabs.isEmpty()) {
            addNewTab(DEFAULT_HOME_URL)
        }

        //使用刘海屏也需要这一句进行全屏处理
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            //隐藏状态栏和导航栏
            //用于WindowInsetsCompat.Type.systemBars()隐藏两个系统栏
            //用于WindowInsetsCompat.Type.statusBars()仅隐藏状态栏
            //用于WindowInsetsCompat.Type.navigationBars()仅隐藏导航栏
            it.hide(WindowInsetsCompat.Type.systemBars())
            //交互效果
            //BEHAVIOR_SHOW_BARS_BY_SWIPE 下拉状态栏操作可能会导致activity画面变形
            //BEHAVIOR_SHOW_BARS_BY_TOUCH 未测试到与BEHAVIOR_SHOW_BARS_BY_SWIPE的明显差异
            //BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 下拉或上拉的屏幕交互操作会显示状态栏和导航栏
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        //允许window 的内容可以上移到刘海屏状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menu.add(0, MENU_EXIT_APP, 0, "退出")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_EXIT_APP -> {
                showExitPasswordDialog()
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    private fun showExitPasswordDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("请输入密码")
            .setView(input)
            .setPositiveButton("确定") { dialog, _ ->
                val password = input.text.toString()
                if (password == EXIT_PASSWORD) {
                    dialog.dismiss()
                    // 关闭当前应用所有 Activity
                    finishAffinity()
                } else {
                    Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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

    /**
     * 设置工具栏按钮（主页和刷新）的点击事件
     */
    private fun setupToolbarButtons() {
        val homeBtn = findViewById<TextView>(R.id.homeBtn)
        val reloadBtn = findViewById<TextView>(R.id.reload)

        // 主页按钮：加载默认URL
        homeBtn.setOnClickListener {
            getCurrentFragment()?.loadUrl(DEFAULT_HOME_URL)
        }

        // 刷新按钮：刷新当前页面
        reloadBtn.setOnClickListener {
            getCurrentFragment()?.reload()
        }
    }

    /**
     * 获取当前激活的Fragment
     */
    private fun getCurrentFragment(): BrowserTabFragment? {
        val currentItem = viewPager.currentItem
        if (currentItem !in tabs.indices) return null
        return tabsAdapter.getFragmentAt(currentItem)
    }

    private fun createTabView(tab: TabLayout.Tab, position: Int): View {
        val view = LayoutInflater.from(this).inflate(R.layout.view_tab, null)
        val titleView = view.findViewById<TextView>(R.id.tabTitle)
        val closeButton = view.findViewById<ImageButton>(R.id.tabClose)
        titleView.text = tabs[position].title
        // 关闭标签页
        if(position == 0) {
            closeButton.visibility = View.GONE
        }else{
            closeButton.visibility = View.VISIBLE
            closeButton.setOnClickListener {
                val currentPos = tab.position
                if (currentPos != TabLayout.Tab.INVALID_POSITION) {
                    closeTab(currentPos)
                }
            }
        }
        return view
    }

    private inner class BrowserTabsAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {
        
        // 保存Fragment引用的Map，key为tabId
        private val fragmentMap = mutableMapOf<Long, BrowserTabFragment>()
        
        override fun getItemCount(): Int = tabs.size

        override fun createFragment(position: Int): BrowserTabFragment {
            val tabId = tabs[position].id
            val fragment = BrowserTabFragment.newInstance(tabId, tabs[position].initialUrl)
            fragmentMap[tabId] = fragment
            return fragment
        }

        override fun getItemId(position: Int): Long = tabs[position].id

        override fun containsItem(itemId: Long): Boolean = tabs.any { it.id == itemId }
        
        /**
         * 获取指定位置的Fragment
         */
        fun getFragmentAt(position: Int): BrowserTabFragment? {
            if (position !in tabs.indices) return null
            val tabId = tabs[position].id
            return fragmentMap[tabId]
        }
    }

    private data class TabEntry(
        val id: Long,
        val title: String,
        val initialUrl: String
    )

    companion object {
        private const val DEFAULT_HOME_URL = "https://juejin.cn/"
        private const val MENU_EXIT_APP = 1001
        private const val EXIT_PASSWORD = "123456"
    }
}