/*
 * Copyright 2012 madvertise Mobile Advertising GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Container for rich media ads using the MRAID version 1.0 - standard
 */

package de.madvertise.android.sdk;

import de.madvertise.android.sdk.MadvertiseView.AnimationEndListener;
import de.madvertise.android.sdk.MadvertiseView.MadvertiseViewCallbackListener;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.VideoView;

import java.io.IOException;

//import java.io.IOException;

public class MadvertiseMraidView extends WebView {

    private static final int CLOSE_BUTTON_SIZE = 50;
    protected static final int STATE_LOADING = 0;
    protected static final int STATE_HIDDEN = 1;
    protected static final int STATE_DEFAULT = 2;
    protected static final int STATE_EXPANDED = 3;
    private int mState;
    private int mIndex;
    private boolean mOnScreen;
    private int mPlacementType;
    private FrameLayout mExpandLayout;
    private ViewGroup mOriginalParent;
    private Handler mLoadingCompletedHandler;
    private ExpandProperties mExpandProperties;
    private MadvertiseViewCallbackListener mListener;
    private AnimationEndListener mAnimationEndListener;
    private MadvertiseView mMadView;
    private ImageButton mCloseButton;
    private boolean mViewable;
    private static String sMraidJS;
    private VideoView mVideo;

    public MadvertiseMraidView(Context context, MadvertiseViewCallbackListener listener,
            AnimationEndListener animationEndListener, Handler loadingCompletedHandler,
            MadvertiseView madView) {
        this(context);
        this.mLoadingCompletedHandler = loadingCompletedHandler;
        this.mAnimationEndListener = animationEndListener;
        this.mListener = listener;
        this.mMadView = madView;
    }

    public MadvertiseMraidView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = getSettings();
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setJavaScriptEnabled(true);
        settings.setPluginsEnabled(true);

        // Initialize the default expand properties.
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        mExpandProperties = new ExpandProperties(metrics.widthPixels, metrics.heightPixels);
        MadvertiseUtil.logMessage(null, Log.INFO, "Setting default expandProperties : "
                + mExpandProperties.toJson().toString());

        // This bridge stays available until this view is destroyed, hence no
        // reloading when displaying new ads is necessary.
        addJavascriptInterface(mBridge, "mraid_bridge");

        setWebViewClient(new WebViewClient() {
            private boolean mError = false;

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!url.endsWith("mraid.js") && !mError) {
                    MadvertiseUtil.logMessage(null, Log.DEBUG, "Setting mraid to default");
                    checkReady();

                    // Close button in default size for interstitial ads
                    if (mPlacementType == MadvertiseUtil.PLACEMENT_TYPE_INTERSTITIAL) {
                        mCloseButton = addCloseButtonToViewGroup(((ViewGroup) getParent()));
                        mCloseButton
                                .setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                    String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                mError = true;
            }
        });

        // Comment this in to enable video tag-capability.
        this.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback)
            {
                MadvertiseUtil.logMessage(null, Log.INFO, "showing VideoView");
                super.onShowCustomView(view, callback);
                if (view instanceof FrameLayout) {
                    FrameLayout frame = (FrameLayout) view;
                    if (frame.getFocusedChild() instanceof VideoView) {
                        mVideo = (VideoView) ((FrameLayout) view).getFocusedChild();
//                        frame.removeView(mVideo);
//                        ((ViewGroup) getParent()).addView(mVideo);

                        // Will also be called onError
                        mVideo.setOnCompletionListener(new OnCompletionListener() {

                            @Override
                            public void onCompletion(MediaPlayer player) {
                                player.stop();
                            }
                        });

                        mVideo.setOnErrorListener(new OnErrorListener() {

                            @Override
                            public boolean onError(MediaPlayer mp, int what, int extra) {
                                MadvertiseUtil.logMessage(null, Log.WARN,
                                        "Error while playing video");

                                if (mListener != null) {
                                    mListener.onError(new IOException("Error while playing video"));
                                }

                                // We return false in order to call
                                // onCompletion()
                                return false;
                            }
                        });

                        mVideo.start();
                    }
                }
            }

            @Override
            public void onHideCustomView() {
                pauseWebView();
            }
        });
    }

    protected void loadAd(MadvertiseAd ad) {
        loadAd(ad.getBannerUrl());
    }

    protected void loadAd(String url) {
        MadvertiseUtil.logMessage(null, Log.INFO, "loading html Ad: " + url);

        if (sMraidJS == null) {
            sMraidJS = MadvertiseUtil.convertStreamToString(getContext().getResources()
                    .openRawResource(de.madvertise.android.sdk.R.raw.mraid));
        }

        loadUrl("javascript:" + sMraidJS);

        if (url.endsWith(".js")) {
            final int lastIndex = url.lastIndexOf("/");
            final String jsFile = url.substring(lastIndex, url.length() - 1);
            final String baseUrl = url.substring(0, lastIndex - 1);

            loadDataWithBaseURL(baseUrl, "<html><head>" +
                    "<script type=\"text/javascript\" src=\"" + jsFile + "\"/>" +
                    "</head><body>MRAID Ad</body></html>", "text/html", "utf-8", null);
        } else {
            loadUrl(url);
        }
    }

    @Override
    public void loadUrl(String url) {
        MadvertiseUtil.logMessage(null, Log.INFO, "Loading url now: " + url);
        super.loadUrl(url);
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding,
            String historyUrl) {
        MadvertiseUtil.logMessage(null, Log.INFO, "Loading url now: " + baseUrl + " with data: "
                + data);
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }

    private void checkReady() {
        injectJs("mraid.setExpandProperties(" + mExpandProperties.toJson() + ");");
        setState(STATE_DEFAULT);
        fireEvent("ready");
        checkViewable();
        if (mLoadingCompletedHandler != null) {
            mLoadingCompletedHandler.sendEmptyMessage(MadvertiseView.MAKE_VISIBLE);
        }
    }

    private void checkViewable() {
        boolean viewable;
        if (mOnScreen && getVisibility() == View.VISIBLE) {
            viewable = true;
        } else {
            viewable = false;
        }
        if (viewable != mViewable && mState == STATE_DEFAULT) {
            mViewable = viewable;
            injectJs("mraid.setViewable(" + mViewable + ");");
        }
    }

    // to be called from the Ad (js side)
    Object mBridge = new Object() {

        @SuppressWarnings("unused")
        public void logMessage(String str) {
            MadvertiseUtil.logMessage(null, Log.INFO, "Called logMessage from Ad with : " + str);
        }

        public void expand() {
            MadvertiseUtil.logMessage(null, Log.INFO, "Called expand from Ad or Java.");
            post(new Runnable() {
                @Override
                public void run() {
                    MadvertiseMraidView.this.resize(
                            mExpandProperties.width,
                            mExpandProperties.height);
                }
            });
            setState(STATE_EXPANDED);
            if (mMadView != null) {
                mMadView.setFetchingAdsEnabled(false);
            }
        }

        @SuppressWarnings("unused")
        // because it IS used from the js side
        public void expand(final String url) {
            MadvertiseUtil.logMessage(null, Log.INFO, "Called expand from Ad with : " + url);
            expand();
            post(new Runnable() {
                @Override
                public void run() {
                    loadUrl(url);
                }
            });
        }

        @SuppressWarnings("unused")
        // because it IS used from the js side
        public void close() {
            MadvertiseUtil.logMessage(null, Log.INFO, "Called close from Ad with.");
            post(new Runnable() {
                @Override
                public void run() {
                    MadvertiseMraidView.this.close();
                }
            });
        }

        @SuppressWarnings("unused")
        // because it IS used from the js side
        public void open(final String url) {
            MadvertiseUtil.logMessage(null, Log.INFO, "Called open from Ad with : " + url);
            post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onAdClicked();
                    }
                    final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url),
                            getContext().getApplicationContext(),
                            MadvertiseActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                }
            });
        }

        @SuppressWarnings("unused")
        // because it IS used from the js side
        public void setExpandProperties(String json) {
            MadvertiseUtil.logMessage(null, Log.INFO, "Called setExpandProperties from Ad with : "
                    + json);
            mExpandProperties.readJson(json);
        }
    };

    // to be called from the App (java side)
    public int getPlacementType() {
        return mPlacementType;
    }

    public void setPlacementType(int placementType) {
        if (placementType != MadvertiseUtil.PLACEMENT_TYPE_INTERSTITIAL
                && placementType != MadvertiseUtil.PLACEMENT_TYPE_INLINE) {
            MadvertiseUtil
                    .logMessage(
                            null,
                            Log.WARN,
                            "Placement type must be one of MadvertiseUtil.PLACEMENT_TYPE_INLINE or MadvertiseUtil.PLACEMENT_TYPE_INTERSTITIAL");
        } else {
            mPlacementType = placementType;
            injectJs("mraid.setPlacementType(" + mPlacementType + ");");
        }
    }

    protected void setState(int state) {
        mState = state;
        injectJs("mraid.setState(" + state + ");");
    }

    protected void fireEvent(String event) {
        injectJs("mraid.fireEvent('" + event + "');");
    }

    protected void fireErrorEvent(String message, String action) {
        injectJs("mraid.fireErrorEvent('" + message + "', '" + action + "');");
    }

    protected ExpandProperties getExpandProperties() {
        return mExpandProperties;
    }

    protected void injectJs(final String jsCode) {
        post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:" + jsCode);
            }
        });
    }

    private void resize(final int width, final int height) {
        final FrameLayout content = (FrameLayout) getRootView().findViewById(android.R.id.content);
        final FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(width, height);
        final View placeholderView = new View(getContext());
        placeholderView.setLayoutParams(getLayoutParams());
        mExpandLayout = new FrameLayout(getContext());
        final FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        mExpandLayout.setLayoutParams(layoutParams);
        mOriginalParent = (ViewGroup) getParent();

        int index = 0;
        if (mOriginalParent != null) {
            int count = mOriginalParent.getChildCount();
            for (index = 0; index < count; index++) {
                if (mOriginalParent.getChildAt(index) == this)
                    break;
            }

            mIndex = index;

            this.setLayoutParams(adParams);
            mOriginalParent.removeView(this);
            mExpandLayout.addView(this);

            mCloseButton = addCloseButtonToViewGroup(((ViewGroup) getParent()));
            mCloseButton.setId(43);

            if (!mExpandProperties.useCustomClose) {
                mCloseButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }

            content.addView(mExpandLayout);
            mOriginalParent.addView(placeholderView, mIndex);
            mOriginalParent.setVisibility(View.GONE);
            mState = STATE_EXPANDED;
        }
    }

    private void close() {
        switch (mState) {
            case STATE_EXPANDED:
                if (mExpandLayout != null && mOriginalParent != null) {
                    ((ViewGroup) mExpandLayout.getParent()).removeView(mExpandLayout);
                    mExpandLayout.removeView(this);
                    this.setLayoutParams(mOriginalParent.getChildAt(mIndex).getLayoutParams());
                    mOriginalParent.removeViewAt(mIndex);
                    mOriginalParent.addView(this, mIndex);
                    mOriginalParent.setVisibility(View.VISIBLE);
                }
                setState(STATE_DEFAULT);
                if (mListener != null) {
                    mListener.onApplicationResume();
                }
                if (mMadView != null) {
                    mMadView.setFetchingAdsEnabled(true);
                }
                break;
            case STATE_DEFAULT:
                // Set MadvertiseView to GONE. Note: This will cause this view
                // to be GONE too.
                ((ViewGroup) getParent()).setVisibility(View.GONE);
                
                setState(STATE_HIDDEN);
                if (mMadView != null) {
                    mMadView.setFetchingAdsEnabled(false);
                }
                
                pauseWebView();
                break;
        }
    }

    private ImageButton addCloseButtonToViewGroup(final ViewGroup parent) {
        final ImageButton closeButton = new ImageButton(getContext());
        final FrameLayout.LayoutParams closeButtonParams = new FrameLayout.LayoutParams(
                CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);
        closeButtonParams.gravity = Gravity.RIGHT;
        closeButton.setLayoutParams(closeButtonParams);
        closeButton.setBackgroundColor(Color.TRANSPARENT);
        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });

        parent.addView(closeButton);

        return closeButton;
    }
    
    private void pauseWebView() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            onPause();
        } 
    }
    
    private void resumeWebView() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            onResume();
        }
    }

    @Override
    public void onAttachedToWindow() {
        mOnScreen = true;
        checkViewable();
        resumeWebView();
    }

    @Override
    public void onDetachedFromWindow() {
        mOnScreen = false;
        checkViewable();
        pauseWebView();
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if(!hasWindowFocus) {
            pauseWebView();
        } else {
            resumeWebView();
        }
    };

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        checkViewable();
        
        if(visibility == View.INVISIBLE) {
            pauseWebView();
        }
    }

    @Override
    protected void onAnimationEnd() {
        super.onAnimationEnd();
        if (mAnimationEndListener != null) {
            mAnimationEndListener.onAnimationEnd();
        }
    }

    public class ExpandProperties {

        private static final String WIDTH = "width";
        private static final String HEIGHT = "height";
        private static final String USE_CUSTOM_CLOSE = "useCustomClose";
        private static final String IS_MODAL = "isModal";
        private int mMaxWidth;
        private int mMaxHeight;
        private float mScale = getResources().getDisplayMetrics().density;
        public int width;
        public int height;
        public boolean useCustomClose;
        public boolean isModal;

        public ExpandProperties(final int width, final int height) {
            this.width = width;
            this.height = height;
            this.mMaxWidth = width;
            this.mMaxHeight = height;
        }

        public String toJson() {
            final JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(WIDTH, width);
                jsonObject.put(HEIGHT, height);
                jsonObject.put(USE_CUSTOM_CLOSE, useCustomClose);
                jsonObject.put(IS_MODAL, isModal);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return jsonObject.toString();
        }

        public void readJson(final String json) {
            JSONObject jsonObject;
            try {
                jsonObject = new JSONObject(json);

                if (jsonObject.has(HEIGHT)) {
                    width = (int) (jsonObject.getInt(WIDTH) * mScale);
                }
                if (jsonObject.has(WIDTH)) {
                    height = (int) (jsonObject.getInt(HEIGHT) * mScale);
                }
                if (jsonObject.has(USE_CUSTOM_CLOSE)) {
                    useCustomClose = jsonObject.getBoolean(USE_CUSTOM_CLOSE);
                    if (useCustomClose && mCloseButton != null) {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                mCloseButton.setImageDrawable(null);
                            }
                        });
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            checkSizeParams();
        }

        private void checkSizeParams() {
            if (width > mMaxWidth || height > mMaxHeight) {
                final float ratio = height / (float) width;
                // respect the ratio
                final int diffWidth = (int) ((float) (width - mMaxWidth) * ratio);
                final int diffHeight = (int) ((float) (height - mMaxHeight) * ratio);

                if (diffWidth > diffHeight) {
                    width = mMaxWidth;
                    height = (int) (width * ratio);
                } else {
                    height = mMaxHeight;
                    width = (int) (height / ratio);
                }
            }
        }
    }
}
