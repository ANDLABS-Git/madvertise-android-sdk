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

package de.madvertise.android.sdk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import de.madvertise.android.sdk.MadvertiseView.MadvertiseViewCallbackListener;
import de.madvertise.android.sdk.MadvertiseUtil;

/**
 * Defines an ad from a JSON object, that contains all necessary information
 * which is provided by the madvertise ad server. Icons and banners are
 * synchronously fetched from the madvertise server and stored on the device.
 * Click action is handled asynchronously.
 */
public class MadvertiseAd {

    private final String CLICK_URL_CODE = "click_url";

    private final String BANNER_URL_CODE = "url";

    private final String TEXT_CODE = "text";

    private final String IMPRESSION_TRACKING_ARRAY_CODE = "tracking";

    private String mClickUrl;

    private String mBannerUrl;

    private String mText;

    private String mBannerType;

    private boolean mHasBanner = false;

    // this one is not needed at the moment, but may become necessary in future versions
    private int mBannerHeight = 53;

    // this one is not needed at the moment, but may become necessary in future versions
    private int mBannerWidth = 320;

    private JSONArray mImpressionTrackingArray;

    private JSONArray mJsonNames;

    private JSONArray mJsonValues;

    private byte[] mImageByteArray;

    private Context mContext;

    private MadvertiseViewCallbackListener mCallbackListener;

    private boolean mIsMraid = false;

    /**
     * Constructor, blocking due to http request, should be called in a thread
     * pool, a request queue, a network thread
     *
     * @param context the applications context
     * @param json json object containing all ad information
     */
    protected MadvertiseAd(final Context context, final JSONObject json,
            final MadvertiseViewCallbackListener listener) {
        this.mContext = context;
        this.mCallbackListener = listener;

        MadvertiseUtil.logMessage(null, Log.DEBUG, "Creating ad");

        // init json arrays and print all keys / values
        mJsonNames = json.names();
        try {
            mJsonValues = json.toJSONArray(mJsonNames);

            for (int i = 0; i < mJsonNames.length(); i++) {
                MadvertiseUtil.logMessage(null, Log.DEBUG, "Key => " + mJsonNames.getString(i)
                        + " Value => " + mJsonValues.getString(i));
            }

            // first get not nested values
            mClickUrl = MadvertiseUtil.getJSONValue(json, CLICK_URL_CODE);
            mText = MadvertiseUtil.getJSONValue(json, TEXT_CODE);
            mImpressionTrackingArray = MadvertiseUtil.getJSONArray(json, IMPRESSION_TRACKING_ARRAY_CODE);

            // check, if we have a banner
            JSONObject bannerJson = MadvertiseUtil.getJSONObject(json, "banner");
            if (bannerJson == null) {
            	return;
            }

            // logic for new ad response
            mBannerUrl = MadvertiseUtil.getJSONValue(bannerJson, BANNER_URL_CODE);
            mHasBanner = true;
            mBannerType = MadvertiseUtil.getJSONValue(bannerJson, "type");
               
            // check, if rich media banner
            JSONObject richMediaJson = MadvertiseUtil.getJSONObject(bannerJson, "rich_media");
            if (richMediaJson == null) {
        		return;
        	}
            
            // check, if mraid type
            if (!MadvertiseUtil.getJSONBoolean(richMediaJson, "mraid")) {
            	mHasBanner = false;
            	mBannerUrl = "";
            	return;
            }

            mIsMraid = true;

            // overwrite banner url
            mBannerUrl = MadvertiseUtil.getJSONValue(richMediaJson, "full_url");

            // only for testing
            
            // only sound after click, no video
            mBannerUrl = "http://dl.dropbox.com/u/48980623/overlay-mraid/index.html";
            
            // nothing happens after first click on play button, second click => excpetion!
            //mBannerUrl = "http://dl.dropbox.com/u/48980623/overlay-mraid-2/index.html";
                        // get sizes for rich media ad     
            try {
                mBannerHeight = Integer.parseInt(MadvertiseUtil.getJSONValue(richMediaJson, "height"));
                mBannerWidth = Integer.parseInt(MadvertiseUtil.getJSONValue(richMediaJson, "width"));
            } catch (NumberFormatException e) {
            	mBannerHeight = 53;
            	mBannerWidth = 320;
            }
            
         // only for testing
        	mBannerHeight = 350;
        	mBannerWidth = 310;
                             
        } catch (JSONException e) {
            MadvertiseUtil.logMessage(null, Log.DEBUG, "Error in json string");
            if (mCallbackListener != null) {
                mCallbackListener.onError(e);
            }
            e.printStackTrace();
        }
    }

    /**
     * Handles the click action (opens the click url)
     */
    protected void handleClick() {
        if (mClickUrl != null && !mClickUrl.equals("")) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mClickUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                mContext.startActivity(intent);
                if (mCallbackListener != null) {
                    mCallbackListener.onAdClicked();
                }
            } catch (Exception e) {
                MadvertiseUtil.logMessage(null, Log.DEBUG, "Failed to open URL : " + mClickUrl);
                if (mCallbackListener != null) {
                    mCallbackListener.onError(e);
                }
                e.printStackTrace();
            }
        }
    }

    protected String getClickURL() {
        return mClickUrl;
    }

    public String getBannerUrl() {
        return mBannerUrl;
    }

    protected String getText() {
        return mText;
    }

    protected boolean hasBanner() {
        return mHasBanner;
    }

    protected byte[] getImageByteArray() {
        return mImageByteArray;
    }

    protected String getBannerType() {
        return mBannerType;
    }

    protected int getBannerHeight() {
        return mBannerHeight;
    }

    protected int getBannerWidth() {
        return mBannerWidth;
    }

    protected JSONArray getImpressionTrackingArray() {
    	return mImpressionTrackingArray;
    }
    
    protected boolean isMraid() {
        return mIsMraid;
    }
}
