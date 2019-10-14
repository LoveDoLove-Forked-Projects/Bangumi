package soko.ekibun.bangumi.ui.topic

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.appbar.AppBarLayout
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.activity_topic.*
import soko.ekibun.bangumi.R
import soko.ekibun.bangumi.api.bangumi.bean.Images
import soko.ekibun.bangumi.api.bangumi.bean.Topic
import soko.ekibun.bangumi.api.bangumi.bean.TopicPost
import soko.ekibun.bangumi.ui.web.WebActivity
import soko.ekibun.bangumi.util.GlideUtil

class TopicView(private val context: TopicActivity) {
    val adapter by lazy { PostAdapter() }

    private var appBarOffset = 0

    init {
        context.item_list.adapter = adapter
        context.item_list.layoutManager = object : androidx.recyclerview.widget.LinearLayoutManager(context) {
            override fun requestChildRectangleOnScreen(parent: androidx.recyclerview.widget.RecyclerView, child: View, rect: Rect, immediate: Boolean): Boolean {
                return false
            }

            override fun requestChildRectangleOnScreen(parent: androidx.recyclerview.widget.RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean): Boolean {
                return false
            }
        }
        adapter.emptyView = LayoutInflater.from(context).inflate(R.layout.view_empty, context.item_list, false)
        adapter.isUseEmpty(false)
        adapter.setEnableLoadMore(true)

        var canScroll = false
        context.app_bar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val ratio = Math.abs(verticalOffset.toFloat() / appBarLayout.totalScrollRange)
            context.title_collapse.alpha = 1 - (1 - ratio) * (1 - ratio) * (1 - ratio)
            context.title_expand.alpha = 1 - ratio
            context.title_collapse.translationY = -context.title_slice.height / 2 * ratio
            context.title_expand.translationY = context.title_collapse.translationY
            context.title_slice.translationY = (context.title_collapse.height - context.title_expand.height - (context.title_slice.layoutParams as ConstraintLayout.LayoutParams).topMargin - context.title_slice.height / 2) * ratio

            appBarOffset = verticalOffset
            canScroll = canScroll || appBarOffset != 0

            context.item_list.invalidate()
        })
        context.item_list.nestedScrollRange = {
            context.app_bar.totalScrollRange
        }
        context.item_list.nestedScrollDistance = {
            -appBarOffset
        }

        context.item_list.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                canScroll = context.item_list.canScrollVertically(-1) || appBarOffset != 0
                context.item_swipe.setOnChildScrollUpCallback { _, _ -> canScroll }
            }
        })
    }

    fun processTopicBefore(title: String, links: Map<String, String>, images: Images) {
        context.title_collapse.text = title
        context.title_expand.text = context.title_collapse.text

        val scroll2Top = {
            if (context.item_list.canScrollVertically(-1) || appBarOffset != 0) {
                context.app_bar.setExpanded(true, true)
                (context.item_list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).scrollToPositionWithOffset(0, 0)
                true
            } else false
        }
        context.title_collapse.setOnClickListener {
            if (scroll2Top()) return@setOnClickListener
            WebActivity.launchUrl(context, context.openUrl)
        }
        context.title_expand.setOnClickListener {
            if (scroll2Top()) return@setOnClickListener
            WebActivity.launchUrl(context, context.openUrl)
        }
        links.toList().getOrNull(0)?.let { link ->
            context.title_slice_0.text = link.first
            context.title_slice_0.setOnClickListener {
                if (scroll2Top()) return@setOnClickListener
                WebActivity.launchUrl(context, link.second, context.openUrl)
            }
        }
        links.toList().getOrNull(1)?.let { link ->
            context.title_slice_1.text = link.first
            context.title_slice_1.setOnClickListener {
                if (scroll2Top()) return@setOnClickListener
                WebActivity.launchUrl(context, link.second, context.openUrl)
            }
        }
        context.title_slice_divider.visibility = if (context.title_slice_1.text.isNotEmpty()) View.VISIBLE else View.GONE
        context.title_slice_1.visibility = context.title_slice_divider.visibility
        context.title_slice_0.post {
            context.title_slice_0.maxWidth = context.title_expand.width - if (context.title_slice_divider.visibility == View.VISIBLE) 2 * context.title_slice_divider.width + context.title_slice_1.width else 0
        }

        GlideUtil.with(context.item_cover_blur)
                ?.load(images.getImage(context))
                ?.apply(RequestOptions.bitmapTransform(BlurTransformation(25, 8)).placeholder(context.item_cover_blur.drawable))
                ?.into(context.item_cover_blur)
    }

    fun processTopic(topic: Topic, scrollPost: String, onItemClick: (View, Int) -> Unit) {
        processTopicBefore(topic.title, topic.links, topic.images)
        adapter.isUseEmpty(true)
        topic.replies.forEach { it.isExpanded = true }
        setNewData(topic.replies)
        (context.item_list?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.let { layoutManager ->
            val scrollIndex = adapter.data.indexOfFirst { it.pst_id == scrollPost }
            if (scrollIndex < 0) return@let
            layoutManager.scrollToPositionWithOffset(scrollIndex, 0)
        }

        adapter.loadMoreEnd()
        context.btn_reply.text = when {
            !topic.lastview.isNullOrEmpty() -> context.getString(R.string.hint_reply)
            !topic.error.isNullOrEmpty() -> topic.error
            topic.replies.isEmpty() -> context.getString(R.string.hint_empty_topic)
            else -> context.getString(R.string.hint_login_topic)
        }

        adapter.setOnItemChildClickListener { _, v, position ->
            onItemClick(v, position)
        }
    }


    fun setNewData(data: List<TopicPost>) {
        var floor = 0
        var subFloor = 0
        var referPost: TopicPost? = null

        adapter.setNewData(data.filter {
            if (it.isSub) {
                subFloor++
            } else {
                floor++
                subFloor = 0
            }
            it.floor = floor
            it.sub_floor = subFloor
            it.editable = it.is_self
            if (subFloor == 0) {
                referPost = it
                referPost?.subItems?.clear()
                true
            } else {
                referPost?.editable = false
                referPost?.addSubItem(it)
                false
            }
        })
        var i = 0
        while (i < adapter.data.size) {
            val topicPost = adapter.data[i]
            if (topicPost.isExpanded) {
                topicPost.isExpanded = false
                adapter.expand(i, false, false)
            }
            i++
        }
        //adapter.expandAll()
    }
}