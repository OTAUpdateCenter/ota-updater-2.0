/*
 * Copyright (C) 2014 OTA Update Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.otaupdater;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdView;
import com.otaupdater.utils.Config;

public class AdsFragment extends Fragment {

    private AdView adView = null;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Config.getInstance(activity).hasProKey()) {
            getActivity()
                    .getFragmentManager()
                    .beginTransaction()
                    .remove(this)
                    .commit();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ads, container, false);

        if (!this.isDetached()) {
//            adView = (AdView) view.findViewById(R.id.ad_view);
//            AdRequest adReq = new AdRequest.Builder()
//                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
//                    .addTestDevice("A462D6D0A148B8607AC0EA1173E32B11")
//                    .build();
//            adView.loadAd(adReq);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adView != null) adView.resume();
    }

    @Override
    public void onPause() {
        if (adView != null) adView.pause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (adView != null) adView.destroy();
        super.onDestroy();
    }
}
