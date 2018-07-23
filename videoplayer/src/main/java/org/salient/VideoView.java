package org.salient;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.util.Comparator;

/**
 * > Created by Mai on 2018/7/10
 * *
 * > Description: 视频播放视图
 * *
 */
public class VideoView<T> extends FrameLayout {

    private final String TAG = VideoView.class.getSimpleName();
    private final int ROOT_VIEW_POSITION = -1;
    private final int CONTROL_PANEL_POSITION = 1;
    public WindowType mWindowType = WindowType.NORMAL;
    public int widthRatio = 0;
    public int heightRatio = 0;

    // settable by the client
    private T mData = null;
    private Object dataSourceObjects;
    private int mScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    private AbsControlPanel mControlPanel;
    private FrameLayout textureViewContainer;
    private DetachAction mDetachAction = DetachAction.NOTHING;
    private boolean mSmartMode = true;

    public VideoView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public VideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {

        View view = View.inflate(context, R.layout.salient_layout_video_view, null);
        addView(view, ROOT_VIEW_POSITION);

        textureViewContainer = findViewById(R.id.surface_container);

        try {
            if (isCurrentPlay()) {
                mScreenOrientation = ((AppCompatActivity) context).getRequestedOrientation();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    /**
     * 是否当前播放地址
     *
     * @return boolean
     */
    public boolean isCurrentPlay() {
        return VideoLayerManager.instance().isCurrentPlaying(this);
    }

    public void setUp(String url) {
        setUp(url, WindowType.NORMAL, null);
    }

    public void setUp(String url, T data) {
        setUp(url, WindowType.NORMAL, data);
    }

    public void setUp(String url, WindowType windowType) {
        setUp(url, windowType, null);
    }

    public void setUp(Object dataSourceObjects, WindowType windowType, T data) {
        this.dataSourceObjects = dataSourceObjects;
        this.mWindowType = windowType;
        this.mData = data;
        if (mSmartMode) {
            autoMatch();
        } else if (mControlPanel != null) {
            mControlPanel.onStateIdle();
        }
    }

    public boolean isSmartMode() {
        return mSmartMode;
    }

    public void setSmartMode(boolean mSmartMode) {
        this.mSmartMode = mSmartMode;
    }

    private void autoMatch() {
        if (VideoLayerManager.instance().isCurrentPlaying(this)) {
            if (mWindowType == WindowType.NORMAL) {
                if (VideoLayerManager.instance().getSecondFloor() != null) {
                    closeWindowFullScreen();
                }
                removeTextureView();
                addTextureView();
                AbsControlPanel currentControlPanel = VideoLayerManager.instance().getCurrentControlPanel();
                if (currentControlPanel != null) {
                    currentControlPanel.onStateIdle();
                }
                VideoLayerManager.instance().setFirstFloor(this);

                if (mControlPanel != null) {
                    mControlPanel.notifyStateChange();
                }
            }
        } else {
            if (mControlPanel != null) {
                mControlPanel.onStateIdle();
            }
        }
    }

    public void setData(T data) {
        mData = data;
    }

    public T getData() {
        return mData;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mWindowType == WindowType.FULLSCREEN || mWindowType == WindowType.TINY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        if (widthRatio != 0 && heightRatio != 0) {
            int specWidth = MeasureSpec.getSize(widthMeasureSpec);
            int specHeight = (int) ((specWidth * (float) heightRatio) / widthRatio);
            setMeasuredDimension(specWidth, specHeight);

            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(specWidth, MeasureSpec.EXACTLY);
            int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(specHeight, MeasureSpec.EXACTLY);
            getChildAt(0).measure(childWidthMeasureSpec, childHeightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

    }

    /**
     * 开启小窗模式
     */
    private void startWindowTiny() {


    }

    public int getScreenOrientation() {
        return mScreenOrientation;
    }

    /**
     * 开始播放
     */
    public void start() {
        Log.d(TAG, "start [" + this.hashCode() + "] ");

        if (dataSourceObjects == null) {
            Toast.makeText(getContext(), "No Url", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isCurrentPlay()) {
            switch (MediaPlayerManager.instance().getCurrentState()) {
                case IDLE://从初始状态开始播放
                    startVideo();
                    break;
                case PLAYBACK_COMPLETED: // 重播:
                    //startVideo();
                    MediaPlayerManager.instance().start();
                    break;
                case PAUSED://从暂停状态恢复播放
                    MediaPlayerManager.instance().start();
                    break;
            }
        } else {
            startVideo();
        }

    }

    /**
     * 暂停
     */
    public void pause() {
        if (isCurrentPlay()) {
            if (MediaPlayerManager.instance().getCurrentState() == MediaPlayerManager.PlayerState.PLAYING) {
                Log.d(TAG, "pause [" + this.hashCode() + "] ");
                MediaPlayerManager.instance().pause();
            }
        }
    }

    protected void startVideo() {
        VideoLayerManager.instance().completeAll();
        Log.d(TAG, "startVideo [" + this.hashCode() + "] ");

        initTextureView();
        addTextureView();

        MediaPlayerManager.instance().setOnAudioFocusChangeListener(getContext());

        Utils.scanForActivity(getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        MediaPlayerManager.instance().setCurrentDataSource(dataSourceObjects);

        VideoLayerManager.instance().setFirstFloor(this);

    }

    public Object getDataSourceObjects() {
        return dataSourceObjects;
    }

    public void setDataSourceObjects(Object dataSourceObjects) {
        this.dataSourceObjects = dataSourceObjects;
    }

    /**
     * 进入全屏模式
     * <p>
     * 注意：这里会重新创建一个VideoView实例，
     * 动态添加到{@link Window#ID_ANDROID_CONTENT }所指的ContentView中
     */
    public void startWindowFullscreen(int screenOrientation) {
        Log.i(TAG, "startWindowFullscreen " + " [" + this.hashCode() + "] ");
        Utils.hideSupportActionBar(getContext());

        ViewGroup vp = (Utils.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(R.id.salient_video_fullscreen_id);
        if (old != null) {
            vp.removeView(old);
        }
        textureViewContainer.removeView(MediaPlayerManager.instance().textureView);

        try {
            VideoView<T> fullScreenVideoView = new VideoView<>(getContext());
            fullScreenVideoView.setId(R.id.salient_video_fullscreen_id);
            LayoutParams lp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            vp.addView(fullScreenVideoView, lp);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                fullScreenVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN);
            } else {
                fullScreenVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }

            fullScreenVideoView.setUp(dataSourceObjects, WindowType.FULLSCREEN, mData);

            fullScreenVideoView.addTextureView();

            AbsControlPanel controlPanel = getControlPanel();
            if (controlPanel != null) {
                Class<? extends AbsControlPanel> cls = controlPanel.getClass();
                //参数类型
                Class<?>[] params = {Context.class};
                //参数值
                Object[] values = {getContext()};
                Constructor<? extends AbsControlPanel> constructor = cls.getDeclaredConstructor(params);
                AbsControlPanel absControlPanel = constructor.newInstance(values);
                fullScreenVideoView.setControlPanel(absControlPanel);
                absControlPanel.onEnterFullScreen();
            }

            VideoLayerManager.instance().setSecondFloor(fullScreenVideoView);

            Utils.setRequestedOrientation(getContext(), screenOrientation);

            MediaPlayerManager.instance().mClickFullScreenTime = System.currentTimeMillis();

            MediaPlayerManager.instance().updateState(MediaPlayerManager.instance().getCurrentState());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //退出全屏或小窗
    public void closeWindowFullScreen() {
        Log.i(TAG, "closeWindowFullScreen " + " [" + this.hashCode() + "] ");
        MediaPlayerManager.instance().clearFloatScreen(getContext());
        addTextureView();
        MediaPlayerManager.instance().updateState(MediaPlayerManager.instance().getCurrentState());
        if (getControlPanel() != null) {
            getControlPanel().onExitFullScreen();
        }
    }

    public void addTextureView() {
        Log.d(TAG, "addTextureView [" + this.hashCode() + "] ");
        FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER);
        textureViewContainer.addView(MediaPlayerManager.instance().textureView, layoutParams);
    }

    public void initTextureView() {
        removeTextureView();
        MediaPlayerManager.instance().surfaceTexture = null;
        MediaPlayerManager.instance().textureView = new ResizeTextureView(getContext());
        MediaPlayerManager.instance().textureView.setSurfaceTextureListener(MediaPlayerManager.instance());
    }

    public void removeTextureView() {
        if (MediaPlayerManager.instance().textureView != null && MediaPlayerManager.instance().textureView.getParent() != null) {
            ((ViewGroup) MediaPlayerManager.instance().textureView.getParent()).removeView(MediaPlayerManager.instance().textureView);
        }
    }

    public AbsControlPanel getControlPanel() {

        return mControlPanel;
    }

    /**
     * 设置控制面板
     *
     * @param mControlPanel AbsControlPanel
     */
    public void setControlPanel(AbsControlPanel mControlPanel) {
        if (mControlPanel != null) {
            mControlPanel.setTarget(this);
            ViewParent parent = mControlPanel.getParent();
            if (parent != null) {
                ((ViewGroup) parent).removeView(mControlPanel);
            }
            textureViewContainer.setOnClickListener(mControlPanel);
            textureViewContainer.setOnTouchListener(mControlPanel);
        }
        this.mControlPanel = mControlPanel;
        View child = getChildAt(CONTROL_PANEL_POSITION);
        if (child != null) {
            removeViewAt(CONTROL_PANEL_POSITION);
        }
        if (this.mControlPanel != null) {
            getTextureViewContainer().setOnClickListener(this.mControlPanel);
        }
        addView(this.mControlPanel, CONTROL_PANEL_POSITION);
        if (this.mControlPanel != null) {
            this.mControlPanel.onStateIdle();
        }
    }

    public FrameLayout getTextureViewContainer() {
        return textureViewContainer;
    }

    public void completeVideo() {
        Log.i(TAG, "completeVideo " + " [" + this.hashCode() + "] ");
        MediaPlayerManager mediaPlayerManager = MediaPlayerManager.instance();
        if (MediaPlayerManager.instance().getCurrentState() == MediaPlayerManager.PlayerState.PLAYING
                || MediaPlayerManager.instance().getCurrentState() == MediaPlayerManager.PlayerState.PAUSED) {//保存进度
            long position = mediaPlayerManager.getCurrentPositionWhenPlaying();
            //Utils.saveProgress(getContext(), Utils.getCurrentFromDataSource(dataSourceObjects, currentUrlMapIndex), position);
        }
        MediaPlayerManager.instance().updateState(MediaPlayerManager.PlayerState.IDLE);
        if (mControlPanel != null) {
            mControlPanel.onStateIdle();
        }
        mediaPlayerManager.cancelProgressTimer();
        textureViewContainer.removeView(MediaPlayerManager.instance().textureView);
        mediaPlayerManager.currentVideoWidth = 0;
        mediaPlayerManager.currentVideoHeight = 0;

        mediaPlayerManager.abandonAudioFocus(getContext());

        Utils.scanForActivity(getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clearFullscreenLayout();

        //Utils.setRequestedOrientation(getContext(), mScreenOrientation);

        if (mediaPlayerManager.surfaceTexture != null) {
            mediaPlayerManager.surfaceTexture.release();
        }

        if (mediaPlayerManager.surface != null) {
            mediaPlayerManager.surface.release();
        }
        mediaPlayerManager.textureView = null;
        mediaPlayerManager.surfaceTexture = null;

    }

    private void clearFullscreenLayout() {
        ViewGroup vp = (Utils.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
        View oldF = vp.findViewById(R.id.salient_video_fullscreen_id);
        View oldT = vp.findViewById(R.id.salient_video_tiny_id);
        if (oldF != null) {
            vp.removeView(oldF);
        }
        if (oldT != null) {
            vp.removeView(oldT);
        }
        Utils.showSupportActionBar(getContext());
    }

    /**
     * @param detachAction DetachAction
     */
    public void setDetachStrategy(DetachAction detachAction) {
        mDetachAction = detachAction;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isCurrentPlay()) {
            Log.d(getClass().getSimpleName(), "onDetachedFromWindow : " + hashCode());
            switch (mDetachAction) {
                case NOTHING:
                    break;
                case PAUSE:
                    MediaPlayerManager.instance().pause();
                    break;
                case STOP:
                    MediaPlayerManager.instance().releaseAllVideos();
                    break;
                case MINIFY:
                    //小屏
                    startWindowTiny();
                    break;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VideoView && mComparator.compare(this,(VideoView) obj) == 0;
    }

    public void setComparator(@NonNull Comparator<VideoView> mComparator) {
        this.mComparator = mComparator;
    }

    public Comparator getComparator() {
        return mComparator;
    }

    private Comparator<VideoView> mComparator = new Comparator<VideoView>() {

        @Override
        public int compare(VideoView self, VideoView currentFloor) {
            if (self == currentFloor && self.getDataSourceObjects() == MediaPlayerManager.instance().getCurrentDataSource()) {
                return 0;
            }
            return -1;
        }
    };

    public enum DetachAction {
        NOTHING,
        PAUSE,
        STOP,
        MINIFY
    }

    public enum WindowType {
        NORMAL,
        LIST,
        FULLSCREEN,
        TINY
    }
}
