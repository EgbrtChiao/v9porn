package com.u9porn.ui.porn9video.play;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.rubensousa.floatingtoolbar.FloatingToolbar;
import com.helper.loadviewhelper.help.OnLoadViewListener;
import com.helper.loadviewhelper.load.LoadViewHelper;
import com.jaeger.library.StatusBarUtil;
import com.orhanobut.logger.Logger;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.sdsmdg.tastytoast.TastyToast;
import com.u9porn.R;
import com.u9porn.adapter.VideoCommentAdapter;
import com.u9porn.data.db.entity.V9PornItem;
import com.u9porn.data.db.entity.VideoResult;
import com.u9porn.data.model.VideoComment;
import com.u9porn.service.DownloadVideoService;
import com.u9porn.ui.MvpActivity;
import com.u9porn.ui.porn9video.author.AuthorActivity;
import com.u9porn.ui.user.UserLoginActivity;
import com.u9porn.utils.AddressHelper;
import com.u9porn.utils.AppUtils;
import com.u9porn.utils.DialogUtils;
import com.u9porn.utils.LoadHelperUtils;
import com.u9porn.utils.constants.Keys;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * @author flymegoc
 */
public abstract class BasePlayVideo extends MvpActivity<PlayVideoView, PlayVideoPresenter> implements PlayVideoView {

    private final String TAG = BasePlayVideo.class.getSimpleName();

    @BindView(R.id.recyclerView_video_comment)
    RecyclerView recyclerViewVideoComment;
    @BindView(R.id.floatingToolbar)
    FloatingToolbar floatingToolbar;
    @BindView(R.id.fab)
    FloatingActionButton fab;
    @BindView(R.id.app_bar_layout)
    AppBarLayout appBarLayout;
    @BindView(R.id.tv_play_video_title)
    TextView tvPlayVideoTitle;
    @BindView(R.id.tv_play_video_author)
    TextView tvPlayVideoAuthor;
    @BindView(R.id.tv_play_video_add_date)
    TextView tvPlayVideoAddDate;
    @BindView(R.id.tv_play_video_info)
    TextView tvPlayVideoInfo;
    @BindView(R.id.coordinator)
    CoordinatorLayout coordinator;

    @BindView(R.id.et_video_comment)
    AppCompatEditText etVideoComment;
    @BindView(R.id.iv_video_comment_send)
    ImageView ivVideoCommentSend;
    @BindView(R.id.et_comment_input_layout)
    LinearLayout etCommentInputLayout;
    @BindView(R.id.comment_swipeRefreshLayout)
    SwipeRefreshLayout commentSwipeRefreshLayout;
    @BindView(R.id.user_info_layout)
    ConstraintLayout userInfoLayout;
    @BindView(R.id.video_player_container)
    FrameLayout videoPlayerContainer;

    private AlertDialog mAlertDialog;
    private AlertDialog favoriteDialog;
    private AlertDialog commentVideoDialog;

    private LoadViewHelper helper;

    protected V9PornItem v9PornItem;

    private VideoCommentAdapter videoCommentAdapter;
    private boolean isVideoError = true;
    private boolean isComment = true;
    private VideoComment videoComment;

    @Inject
    protected PlayVideoPresenter playVideoPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base_play_video);
        ButterKnife.bind(this);
        initPlayerView();
        v9PornItem = (V9PornItem) getIntent().getSerializableExtra(Keys.KEY_INTENT_V9PORN_ITEM);

        initListener();
        initDialog();
        initLoadHelper();
        initVideoComments();
        initData();
        initBottomMenu();

    }

    /**
     * 初始化视频引擎视图
     */
    public abstract void initPlayerView();

    private void initListener() {
        ivVideoCommentSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String comment = etVideoComment.getText().toString().trim();
                commentOrReplyVideo(comment);
            }
        });
        commentSwipeRefreshLayout.setEnabled(false);
        AppUtils.setColorSchemeColors(this, commentSwipeRefreshLayout);
        commentSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (v9PornItem.getVideoResultId() == 0) {
                    commentSwipeRefreshLayout.setRefreshing(false);
                    return;
                }
                String videoId = v9PornItem.getVideoResult().getVideoId();
                presenter.loadVideoComment(videoId, v9PornItem.getViewKey(), true);
            }
        });
        userInfoLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isComment = true;
                videoCommentAdapter.setClickPosition(-1);
                videoCommentAdapter.notifyDataSetChanged();
                etVideoComment.setHint(R.string.comment_video_hint_tip);
            }
        });
    }

    /**
     * 评论视频或者回复评论
     *
     * @param comment 留言内容
     */
    private void commentOrReplyVideo(String comment) {
        if (TextUtils.isEmpty(comment)) {
            showMessage("请填写评论", TastyToast.INFO);
            return;
        }

        if (!presenter.isUserLogin()) {
            showMessage("请先登录帐号", TastyToast.INFO);
            goToLogin();
            return;
        }
        if (v9PornItem.getVideoResultId() == 0) {
            showMessage("视频地址还未解析成功，无法评论", TastyToast.INFO);
            return;
        }
        String vid = v9PornItem.getVideoResult().getVideoId();
        String uid = String.valueOf(presenter.getLoginUserId());
        if (isComment) {
            commentVideoDialog.show();
            presenter.commentVideo(comment, uid, vid, v9PornItem.getViewKey());
        } else {
            if (videoComment == null) {
                showMessage("请先选择需要回复的评论！", TastyToast.INFO);
                return;
            }
            commentVideoDialog.show();
            String username = videoComment.getuName();
            String commentId = videoComment.getReplyId();
            presenter.replyComment(comment, username, vid, commentId, v9PornItem.getViewKey());
        }
    }

    private void initData() {
        V9PornItem tmp = presenter.findV9PornItemByViewKey(v9PornItem.getViewKey());
        if (tmp == null || tmp.getVideoResult() == null) {
            presenter.loadVideoUrl(v9PornItem);
        } else {
            v9PornItem = tmp;
            videoPlayerContainer.setVisibility(View.VISIBLE);
            Logger.t(TAG).d("使用已有播放地址");
            //浏览历史
            v9PornItem.setViewHistoryDate(new Date());
            presenter.updateV9PornItemForHistory(v9PornItem);
            VideoResult videoResult = v9PornItem.getVideoResult();
            setToolBarLayoutInfo(v9PornItem);
            playVideo(v9PornItem.getTitle(), presenter.getVideoCacheProxyUrl(videoResult.getVideoUrl()), videoResult.getVideoName(), videoResult.getThumbImgUrl());
            //加载评论
            presenter.loadVideoComment(videoResult.getVideoId(), v9PornItem.getViewKey(), true);
        }
    }

    private void setToolBarLayoutInfo(final V9PornItem v9PornItem) {
        if (v9PornItem.getVideoResultId() == 0) {
            return;
        }
        String searchTitleTag = "...";
        VideoResult videoResult = v9PornItem.getVideoResult();
        if (v9PornItem.getTitle().contains(searchTitleTag) || v9PornItem.getTitle().endsWith(searchTitleTag)) {
            tvPlayVideoTitle.setText(videoResult.getVideoName());
        } else {
            tvPlayVideoTitle.setText(v9PornItem.getTitle());
        }
        tvPlayVideoAuthor.setText(videoResult.getOwnerName());
        tvPlayVideoAddDate.setText(videoResult.getAddDate());
        tvPlayVideoInfo.setText(videoResult.getUserOtherInfo());
        tvPlayVideoAuthor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!presenter.isUserLogin()) {
                    goToLogin();
                    showMessage("请先登录", TastyToast.INFO);
                    return;
                }
                if (v9PornItem.getVideoResultId() == 0) {
                    showMessage("视频还未解析成功！", TastyToast.INFO);
                    return;
                }
                Intent intent = new Intent(BasePlayVideo.this, AuthorActivity.class);
                intent.putExtra(Keys.KEY_INTENT_UID, v9PornItem.getVideoResult().getOwnerId());
                startActivityForResultWithAnimotion(intent, 1);
            }
        });
    }

    private void initLoadHelper() {
        helper = new LoadViewHelper(commentSwipeRefreshLayout);
        helper.setListener(new OnLoadViewListener() {
            @Override
            public void onRetryClick() {
                if (isVideoError) {
                    presenter.loadVideoUrl(v9PornItem);
                } else {
                    //加载评论
                    if (v9PornItem.getVideoResultId() == 0) {
                        return;
                    }
                    presenter.loadVideoComment(v9PornItem.getVideoResult().getVideoId(), v9PornItem.getViewKey(), true);
                }
            }
        });
    }

    private void initDialog() {
        mAlertDialog = DialogUtils.initLoadingDialog(this, "视频地址解析中...");
        favoriteDialog = DialogUtils.initLoadingDialog(this, "收藏中,请稍后...");
        commentVideoDialog = DialogUtils.initLoadingDialog(this, "提交评论中,请稍后...");
    }

    private void initVideoComments() {

        List<VideoComment> videoCommentList = new ArrayList<>();
        videoCommentAdapter = new VideoCommentAdapter(this, R.layout.item_video_comment, videoCommentList);

        recyclerViewVideoComment.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewVideoComment.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerViewVideoComment.setAdapter(videoCommentAdapter);
        videoCommentAdapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
            @Override
            public void onLoadMoreRequested() {
                //加载评论
                if (v9PornItem.getVideoResultId() == 0) {
                    videoCommentAdapter.loadMoreFail();
                    return;
                }
                presenter.loadVideoComment(v9PornItem.getVideoResult().getVideoId(), v9PornItem.getViewKey(), false);
            }
        }, recyclerViewVideoComment);
        videoCommentAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (floatingToolbar.isShowing()) {
                    floatingToolbar.hide();
                }
                isComment = false;
                videoCommentAdapter.setClickPosition(position);
                videoCommentAdapter.notifyDataSetChanged();
                videoComment = (VideoComment) adapter.getData().get(position);
                etVideoComment.setHint("回复：" + videoComment.getuName());
            }
        });

    }

    private void initBottomMenu() {
        floatingToolbar.attachFab(fab);
        floatingToolbar.attachRecyclerView(recyclerViewVideoComment);
        floatingToolbar.setClickListener(new FloatingToolbar.ItemClickListener() {
            @Override
            public void onItemClick(MenuItem item) {
                onOptionsItemSelected(item);
            }

            @Override
            public void onItemLongClick(MenuItem item) {

            }
        });
    }

    /**
     * 开始播放视频
     *
     * @param title      视频标题
     * @param videoUrl   视频链接
     * @param name       视频名字
     * @param thumImgUrl 视频缩略图
     */
    public abstract void playVideo(String title, String videoUrl, String name, String thumImgUrl);


    @NonNull
    @Override
    public PlayVideoPresenter createPresenter() {
        getActivityComponent().inject(this);
        return playVideoPresenter;
    }

    @Override
    public void showParsingDialog() {
        if (mAlertDialog == null) {
            return;
        }
        mAlertDialog.show();
    }

    @Override
    public void parseVideoUrlSuccess(V9PornItem v9PornItem) {
        this.v9PornItem = v9PornItem;
        videoPlayerContainer.setVisibility(View.VISIBLE);
        setToolBarLayoutInfo(v9PornItem);
        VideoResult videoResult = v9PornItem.getVideoResult();
        //开始播放
        playVideo(v9PornItem.getTitle(), presenter.getVideoCacheProxyUrl(videoResult.getVideoUrl()), "", videoResult.getThumbImgUrl());
        helper.showContent();
        presenter.loadVideoComment(videoResult.getVideoId(), v9PornItem.getViewKey(), true);
        boolean neverAskForWatchDownloadTip = presenter.isNeverAskForWatchDownloadTip();
        if (!neverAskForWatchDownloadTip) {
            showWatchDownloadVideoTipDialog();
        }
        dismissDialog();
    }

    private void showWatchDownloadVideoTipDialog() {
        QMUIDialog.MessageDialogBuilder builder = new QMUIDialog.MessageDialogBuilder(this);
        builder.setTitle("温馨提示");
        builder.setMessage("1. 通常你无法在线观看视频就意味着你也无法下载视频，所以如果你不能在线观看视频就不要想着下载了再看了，那样绝大多数时候都是不能下载的；\n" +
                "2. 如果在线观看速度慢可以选择先下载后再观看，因为是多线程下载，有时候能够比在线观看要快；\n" +
                "3. 如果想要更好的在线观看和下载体验，目前最好的办法就是挂代理（非设置中的HTTP代理）；\n" +
                "4. 点击作者名字可查看该作者其他视频（需要登录帐号）。");
        builder.addAction("我知道了", new QMUIDialogAction.ActionListener() {
            @Override
            public void onClick(QMUIDialog dialog, int index) {
                dialog.dismiss();
            }
        });
        builder.addAction("不再提示", new QMUIDialogAction.ActionListener() {
            @Override
            public void onClick(QMUIDialog dialog, int index) {
                dialog.dismiss();
                presenter.setNeverAskForWatchDownloadTip(true);
            }
        });
        builder.show();
    }

    @Override
    public void errorParseVideoUrl(String errorMessage) {
        dismissDialog();
        isVideoError = true;
        helper.showError();
        LoadHelperUtils.setErrorText(helper.getLoadError(), R.id.tv_error_text, "解析视频地址失败了，点击重试");
        showMessage(errorMessage, TastyToast.ERROR);
    }

    @Override
    public void favoriteSuccess() {
        presenter.setFavoriteNeedRefresh(true);
        showMessage("收藏成功", TastyToast.SUCCESS);
    }

    @Override
    public void setVideoCommentData(List<VideoComment> videoCommentList, boolean pullToRefresh) {
        if (pullToRefresh) {
            recyclerViewVideoComment.smoothScrollToPosition(0);
        }
        videoCommentAdapter.setNewData(videoCommentList);
        commentSwipeRefreshLayout.setEnabled(true);
    }

    @Override
    public void setMoreVideoCommentData(List<VideoComment> videoCommentList) {
        videoCommentAdapter.loadMoreComplete();
        videoCommentAdapter.addData(videoCommentList);
    }

    @Override
    public void noMoreVideoCommentData(String message) {
        videoCommentAdapter.loadMoreEnd(true);
        //showMessage(message, TastyToast.INFO);
    }

    @Override
    public void loadMoreVideoCommentError(String message) {
        videoCommentAdapter.loadMoreFail();
    }

    @Override
    public void loadVideoCommentError(String message) {
        isVideoError = false;
        helper.showError();
        LoadHelperUtils.setErrorText(helper.getLoadError(), R.id.tv_error_text, "加载评论失败了，点击重试");
        //showMessage(message, TastyToast.Error);
    }

    @Override
    public void commentVideoSuccess(String message) {
        cleanVideoCommentInput();
        reFreshData();
        showMessage(message, TastyToast.SUCCESS);
    }

    @Override
    public void commentVideoError(String message) {
        showMessage(message, TastyToast.ERROR);
    }

    @Override
    public void replyVideoCommentSuccess(String message) {
        cleanVideoCommentInput();
        isComment = true;
        etVideoComment.setHint(R.string.comment_video_hint_tip);
        videoCommentAdapter.setClickPosition(-1);
        reFreshData();
        showMessage(message, TastyToast.SUCCESS);
    }

    private void reFreshData() {
        if (v9PornItem.getVideoResultId() == 0) {
            return;
        }
        //刷新
        commentSwipeRefreshLayout.setRefreshing(true);
        String videoId = v9PornItem.getVideoResult().getVideoId();
        presenter.loadVideoComment(videoId, v9PornItem.getViewKey(), true);
    }

    @Override
    public void replyVideoCommentError(String message) {
        showMessage(message, TastyToast.ERROR);
    }

    private void cleanVideoCommentInput() {
        etVideoComment.setText("");
    }

    @Override
    public void showError(String message) {
        showMessage(message, TastyToast.ERROR);
        dismissDialog();
    }

    @Override
    public void showLoading(boolean pullToRefresh) {
        helper.showLoading();
        LoadHelperUtils.setLoadingText(helper.getLoadIng(), R.id.tv_loading_text, "拼命加载评论中...");
    }

    @Override
    public void showContent() {
        if (videoCommentAdapter.getData().size() == 0) {
            isVideoError = false;
            helper.showEmpty();
            LoadHelperUtils.setEmptyText(helper.getLoadEmpty(), R.id.tv_empty_info, "暂无评论");
        } else {
            //flLoadHolder.setVisibility(View.GONE);
            helper.showContent();
        }
        commentSwipeRefreshLayout.setRefreshing(false);
        dismissDialog();
    }

    @Override
    public void showMessage(String msg, int type) {
        super.showMessage(msg, type);
        dismissDialog();
    }

    private void dismissDialog() {
        if (mAlertDialog != null && mAlertDialog.isShowing() && !isFinishing()) {
            mAlertDialog.dismiss();
        }
        if (favoriteDialog != null && favoriteDialog.isShowing() && !isFinishing()) {
            favoriteDialog.dismiss();
        }
        if (commentVideoDialog != null && commentVideoDialog.isShowing() && !isFinishing()) {
            commentVideoDialog.dismiss();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.play_video, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.menu_play_collect) {
            favoriteVideo();
            return true;
        } else if (id == R.id.menu_play_download) {
            startDownloadVideo();
            return true;
        } else if (id == R.id.menu_play_share) {
            shareVideoUrl();
            return true;
        } else if (id == R.id.menu_play_comment) {
            showMessage("向下滑动即可评论", TastyToast.INFO);
            return true;
        } else if (id == R.id.menu_play_close) {
            floatingToolbar.hide();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startDownloadVideo() {
        presenter.downloadVideo(v9PornItem, false);
        Intent intent = new Intent(this, DownloadVideoService.class);
        startService(intent);
    }

    private void favoriteVideo() {
        if (v9PornItem == null || v9PornItem.getVideoResultId() == 0) {
            showMessage("还未成功解析视频链接，不能收藏！", TastyToast.INFO);
            return;
        }
        VideoResult videoResult = v9PornItem.getVideoResult();
        if (!presenter.isUserLogin()) {
            goToLogin();
            showMessage("请先登录", TastyToast.SUCCESS);
            return;
        }
        if (Integer.parseInt(videoResult.getAuthorId()) == presenter.getLoginUserId()) {
            showMessage("不能收藏自己的视频", TastyToast.WARNING);
            return;
        }
        favoriteDialog.show();
        presenter.favorite(String.valueOf(presenter.getLoginUserId()), videoResult.getVideoId(), videoResult.getAuthorId());
    }

    private void shareVideoUrl() {
        if (v9PornItem == null || v9PornItem.getVideoResultId() == 0) {
            showMessage("还未成功解析视频链接，不能分享！", TastyToast.INFO);
            return;
        }
        String url = v9PornItem.getVideoResult().getVideoUrl();
        if (TextUtils.isEmpty(url)) {
            showMessage("还未成功解析视频链接，不能分享！", TastyToast.INFO);
            return;
        }
        Intent textIntent = new Intent(Intent.ACTION_SEND);
        textIntent.setType("text/plain");
        textIntent.putExtra(Intent.EXTRA_TEXT, "链接：" + url);
        startActivity(Intent.createChooser(textIntent, "分享视频地址"));
    }

    private void goToLogin() {
        Intent intent = new Intent(this, UserLoginActivity.class);
        startActivityWithAnimotion(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == AuthorActivity.AUTHORACTIVITY_RESULT_CODE) {
            v9PornItem = (V9PornItem) data.getSerializableExtra(Keys.KEY_INTENT_V9PORN_ITEM);
            recyclerViewVideoComment.smoothScrollToPosition(0);
            videoCommentAdapter.getData().clear();
            videoCommentAdapter.notifyDataSetChanged();
            initData();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_USER) {
            //这里没必要，因为我们使用的是setColorForSwipeBack，并不会有这个虚拟的view，而是设置的padding
            StatusBarUtil.hideFakeStatusBarView(this);
        } else if (newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        }
    }
}
