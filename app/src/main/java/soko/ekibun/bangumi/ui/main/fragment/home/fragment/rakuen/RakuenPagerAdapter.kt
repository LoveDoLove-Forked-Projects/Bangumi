package soko.ekibun.bangumi.ui.main.fragment.home.fragment.rakuen

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.rxjava3.disposables.Disposable
import soko.ekibun.bangumi.R
import soko.ekibun.bangumi.api.ApiHelper.subscribeOnUiThread
import soko.ekibun.bangumi.api.bangumi.bean.Topic
import soko.ekibun.bangumi.ui.topic.TopicActivity
import soko.ekibun.bangumi.ui.view.FixSwipeRefreshLayout
import soko.ekibun.bangumi.ui.view.ShadowDecoration

/**
 * 超展开PagerAdapter
 * @property fragment RakuenFragment
 * @property pager ViewPager
 * @property tabList (kotlin.Array<(kotlin.String..kotlin.String?)>..kotlin.Array<out (kotlin.String..kotlin.String?)>?)
 * @property items HashMap<Int, Pair<RakuenAdapter, FixSwipeRefreshLayout>>
 * @property topicCall HashMap<Int, Call<List<Topic>>>
 * @constructor
 */
class RakuenPagerAdapter(
    context: Context,
    val fragment: RakuenFragment,
    private val pager: androidx.viewpager.widget.ViewPager
) : androidx.viewpager.widget.PagerAdapter() {
    private val tabList = context.resources.getStringArray(R.array.topic_list)

    init {
        pager.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) { /* no-op */
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) { /* no-op */
            }

            override fun onPageSelected(position: Int) {
                loadTopicList(position)
            }
        })
    }

    @SuppressLint("UseSparseArrays")
    private val items = HashMap<Int, Pair<RakuenAdapter, FixSwipeRefreshLayout>>()

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = items.getOrPut(position) {
            val swipeRefreshLayout = FixSwipeRefreshLayout(container.context)
            val recyclerView = androidx.recyclerview.widget.RecyclerView(container.context)
            ShadowDecoration.set(recyclerView)
            val adapter = RakuenAdapter()
            adapter.setEmptyView(LayoutInflater.from(container.context).inflate(R.layout.view_empty, container, false))
            adapter.isUseEmpty = false
            adapter.setOnItemClickListener { _, v, position ->
                TopicActivity.startActivity(v.context, adapter.data[position])
                //WebActivity.launchUrl(v.context, adapter.data[position].url)
            }
            recyclerView.adapter = adapter
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(container.context)
            recyclerView.isNestedScrollingEnabled = false
            swipeRefreshLayout.addView(recyclerView)
            swipeRefreshLayout.tag = recyclerView
            swipeRefreshLayout.setOnRefreshListener { loadTopicList(position) }
            Pair(adapter, swipeRefreshLayout)
        }
        container.addView(item.second)
        if ((item.second.tag as? androidx.recyclerview.widget.RecyclerView)?.tag == null)
            loadTopicList(position)
        return item.second
    }

    /**
     * 重置
     * @param position Int
     */
    fun reset(position: Int) {
        val item = items[position] ?: return
        topicCall[position]?.dispose()
        item.first.isUseEmpty = false
        item.first.setNewInstance(null)
    }

    @SuppressLint("UseSparseArrays")
    private var topicCall = HashMap<Int, Disposable>()

    /**
     * 加载帖子列表
     * @param position Int
     */
    fun loadTopicList(position: Int = pager.currentItem) {
        val item = items[position] ?: return
        item.first.isUseEmpty = false
        topicCall[position]?.dispose()
        item.second.isRefreshing = true
        topicCall[position] = Topic.getList(
            if (position == 1) when (fragment.selectedFilter) {
                R.id.topic_filter_join -> "my_group"
                R.id.topic_filter_post -> "my_group&filter=topic"
                R.id.topic_filter_reply -> "my_group&filter=reply"
                else -> "group"
            } else listOf("", "group", "subject", "ep", "mono")[position]
        ).subscribeOnUiThread({
            item.first.isUseEmpty = true
            item.first.setNewInstance(it.toMutableList())
            (item.second.tag as? androidx.recyclerview.widget.RecyclerView)?.tag = true
        }, onComplete = {
            item.second.isRefreshing = false
        })
    }

    override fun getPageTitle(pos: Int): CharSequence {
        return tabList[pos]
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun getCount(): Int {
        return tabList.size
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

}