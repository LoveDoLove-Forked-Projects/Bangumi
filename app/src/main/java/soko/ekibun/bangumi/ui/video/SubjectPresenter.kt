package soko.ekibun.bangumi.ui.video

import android.content.DialogInterface
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import android.support.v4.widget.NestedScrollView
import android.support.v7.app.AlertDialog
import android.view.View
import kotlinx.android.synthetic.main.activity_video.*
import kotlinx.android.synthetic.main.dialog_edit_lines.view.*
import kotlinx.android.synthetic.main.dialog_edit_subject.view.*
import kotlinx.android.synthetic.main.subject_detail.*
import kotlinx.android.synthetic.main.video_buttons.*
import retrofit2.Call
import soko.ekibun.bangumi.R
import soko.ekibun.bangumi.api.ApiCallback
import soko.ekibun.bangumi.api.bangumi.Bangumi
import soko.ekibun.bangumi.api.bangumi.bean.CollectionStatusType
import soko.ekibun.bangumi.api.bangumi.bean.Subject
import soko.ekibun.bangumi.api.bangumi.bean.SubjectProgress
import soko.ekibun.bangumi.model.ParseInfoModel
import soko.ekibun.bangumi.model.UserModel
import soko.ekibun.bangumi.parser.ParseInfo
import soko.ekibun.bangumi.util.JsonUtil

class SubjectPresenter(private val context: VideoActivity){
    val api by lazy { Bangumi.createInstance() }
    private val subjectView by lazy{ SubjectView(context) }
    private val userModel by lazy { UserModel(context) }
    private val parseInfoModel by lazy { ParseInfoModel(context) }

    val subject by lazy{ JsonUtil.toEntity(context.intent.getStringExtra(VideoActivity.EXTRA_SUBJECT), Subject::class.java) }

    init{
        subjectView.updateSubject(subject)
        refreshSubject(subject)
        refershCollection(subject)
        refreshProgress(subject)
        refreshLines(subject)

        context.subject_swipe.setOnRefreshListener{
            refreshSubject(subject)
        }

        context.item_detail.setOnClickListener {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(subject.url))
        }

        context.videoPresenter.playNext = {position: Int ->
            parseInfoModel.getInfo(subject)?.let{
                if(it.video?.id.isNullOrEmpty()) return@let
                val episode = subjectView.episodeAdapter.data[position].t
                val episodeNext = subjectView.episodeAdapter.data[position+1].t
                context.videoPresenter.next = if(episodeNext == null || (episodeNext.status?:"") !in listOf("Air")) null else position + 1
                context.runOnUiThread { context.videoPresenter.play(episode, it) }
            }
        }

        subjectView.episodeAdapter.setOnItemClickListener { _, _, position ->
            context.videoPresenter.playNext(position)
        }

        subjectView.episodeAdapter.setOnItemLongClickListener { _, _, position ->
            userModel.getToken()?.let{token->
                subjectView.episodeAdapter.data[position].t?.let{ep->
                    val status = context.resources.getStringArray(R.array.episode_status)
                    val dialog = AlertDialog.Builder(context)
                            .setItems(status) { _, which ->
                                api.updateProgress(ep.id, SubjectProgress.EpisodeProgress.EpisodeStatus.types[which],token.access_token?:"").enqueue(
                                        ApiCallback.build(context, {
                                            refreshProgress(subject)
                                        }))
                            }.create()
                    //dialog.window.setGravity(Gravity.BOTTOM)
                    dialog.show()
                }
            }
            true
        }

        context.nested_scroll.setOnScrollChangeListener { v: NestedScrollView, _: Int, _: Int, oldScrollX: Int, oldScrollY: Int ->
            if(v.tag == true){
                v.tag = null
                v.scrollTo(oldScrollX, oldScrollY)
                v.smoothScrollTo(oldScrollX, oldScrollY) } }
    }

    private fun refreshProgress(subject: Subject){
        userModel.getToken()?.let{token ->
            api.progress(token.user_id.toString(), subject.id, token.access_token?:"").enqueue(ApiCallback.build(context, {
                subjectView.progress = it
            }))
        }
    }

    private var subjectCall : Call<Subject>? = null
    private fun refreshSubject(subject: Subject){
        context.data_layout.visibility = View.GONE
        context.subject_swipe.isRefreshing = true
        subjectCall?.cancel()
        subjectCall = api.subject(subject.id)
        subjectCall?.enqueue(ApiCallback.build(context, {
            subjectView.updateSubject(it)
        }, { context.subject_swipe.isRefreshing = false }))
    }

    fun refreshLines(subject: Subject){
        val info = parseInfoModel.getInfo(subject)
        context.tv_lines.text = info?.video?.let{ context.resources.getStringArray(R.array.parse_type)[it.type]}?:context.tv_lines.text

        context.cl_lines.setOnClickListener {
            val view = context.layoutInflater.inflate(R.layout.dialog_edit_lines, context.cl_lines, false)
            info?.let{
                view.item_api.setText(it.api)
                view.item_video_type.setSelection(it.video?.type?:0)
                view.item_video_id.setText(it.video?.id)
                view.item_danmaku_type.setSelection(it.danmaku?.type?:0)
                view.item_danmaku_id.setText(it.danmaku?.id)
            }
            AlertDialog.Builder(context)
                    .setView(view)
                    .setPositiveButton("提交"){ _: DialogInterface, i: Int ->
                        val parseInfo = ParseInfo(view.item_api.text.toString(),
                                ParseInfo.ParseItem(view.item_video_type.selectedItemId.toInt(), view.item_video_id.text.toString()),
                                ParseInfo.ParseItem(view.item_danmaku_type.selectedItemId.toInt(), view.item_danmaku_id.text.toString()))
                        parseInfoModel.saveInfo(subject, parseInfo)
                        refreshLines(subject)
                    }.show()
        }
    }

    fun refershCollection(subject: Subject){
        userModel.getToken()?.let{token ->
            //Log.v("token", token.toString())
            api.collectionStatus(subject.id, token.access_token?:"").enqueue(ApiCallback.build(context, {body->
                val status = body.status
                if(status != null){
                    context.iv_chase.setImageDrawable(context.resources.getDrawable(
                            if(status.id in listOf(1, 2, 3, 4)) R.drawable.ic_heart else R.drawable.ic_heart_outline, context.theme))
                    context.tv_chase.text = status.name?:""
                }

                context.cl_chase.setOnClickListener {
                    val view = context.layoutInflater.inflate(R.layout.dialog_edit_subject, context.cl_chase, false)
                    if(status != null){
                        view.item_status.setSelection(status.id-1)
                        view.item_rating.rating = body.rating.toFloat()
                        view.item_comment.setText(body.comment)
                        view.item_private.isChecked = body.private == 1
                    }
                    AlertDialog.Builder(context)
                            .setView(view)
                            .setPositiveButton("提交"){ _: DialogInterface, i: Int ->
                                val new_status = CollectionStatusType.status[view.item_status.selectedItemId.toInt()]
                                val new_rating = view.item_rating.rating.toInt()
                                val new_comment = view.item_comment.text.toString()
                                val new_privacy = if(view.item_private.isChecked) 1 else 0
                                //Log.v("new", "$new_status,$new_rating,$new_comment")
                                api.updateCollectionStatus(subject.id, token.access_token?:"",
                                        new_status, new_comment, new_rating, new_privacy).enqueue(ApiCallback.build(context,{},{
                                    refershCollection(subject)
                                }))
                            }.show()
                }
            }))
        }
    }
}