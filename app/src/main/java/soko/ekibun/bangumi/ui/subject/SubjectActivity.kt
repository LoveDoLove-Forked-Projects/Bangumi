package soko.ekibun.bangumi.ui.subject

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_subject.*
import soko.ekibun.bangumi.R
import soko.ekibun.bangumi.api.bangumi.bean.Subject
import soko.ekibun.bangumi.ui.video.VideoActivity
import soko.ekibun.bangumi.util.JsonUtil

class SubjectActivity : AppCompatActivity() {
    private val subjectPresenter: SubjectPresenter by lazy{ SubjectPresenter(this) }

    private val subject by lazy{ JsonUtil.toEntity(intent.getStringExtra(SubjectActivity.EXTRA_SUBJECT), Subject::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subject)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        subjectPresenter.init(subject)
    }

    private fun processBack(){
        when {
            episode_detail_list.visibility == View.VISIBLE -> subjectPresenter.subjectView.showEpisodeDetail(false)
            else -> finish()
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK){
            processBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> processBack()
        }
        return super.onOptionsItemSelected(item)
    }


    companion object{
        private const val EXTRA_SUBJECT = "extraSubject"

        fun startActivity(context: Context, subject: Subject) {
            if((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)== 0)
                context.startActivity(parseIntent(context, subject))
            else
                VideoActivity.startActivity(context, subject)
        }

        private fun parseIntent(context: Context, subject: Subject): Intent {
            val intent = Intent(context, SubjectActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK // or Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.putExtra(EXTRA_SUBJECT, JsonUtil.toJson(subject))
            return intent
        }
    }
}
