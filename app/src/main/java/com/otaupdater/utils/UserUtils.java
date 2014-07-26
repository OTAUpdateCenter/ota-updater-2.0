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

package com.otaupdater.utils;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.otaupdater.R;

import org.json.JSONException;
import org.json.JSONObject;

public class UserUtils {
    public static void showLoginDialog(final Context ctx, String defUsername,
            final DialogCallback dlgCallback, final LoginCallback loginCallback) {
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(ctx).inflate(R.layout.login_dialog, null);
        if (view == null) return;
        final EditText inputUsername = (EditText) view.findViewById(R.id.auth_username);
        final EditText inputPassword = (EditText) view.findViewById(R.id.auth_password);

        if (defUsername != null) inputUsername.setText(defUsername);

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(R.string.alert_login_title);
        builder.setView(view);
        builder.setPositiveButton(R.string.login, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) { /* set below */ }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (loginCallback != null) loginCallback.onCancel();
            }
        });

        final AlertDialog dlg = builder.create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                if (dlgCallback != null) dlgCallback.onDialogShown(dlg);
                Button button = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button == null) return;
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String username = inputUsername.getText().toString();
                        final String password = inputPassword.getText().toString();

                        if (username.length() == 0 || password.length() == 0) {
                            Toast.makeText(ctx, R.string.toast_blank_userpass_error, Toast.LENGTH_LONG).show();
                            return;
                        }

                        dlg.dismiss();

                        APIUtils.userLogin(ctx, username, password, new APIUtils.ProgressDialogAPICallback(ctx, ctx.getString(R.string.alert_logging_in), dlgCallback) {
                            @Override
                            public void onSuccess(String message, JSONObject respObj) {
                                try {
                                    String realUsername = respObj.getString("username");
                                    String hmacKey = respObj.getString("key");
                                    Config.getInstance(ctx).storeLogin(realUsername, hmacKey);
                                    if (loginCallback != null) loginCallback.onLoggedIn(realUsername);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onError(String message, JSONObject respObj) {
                                //TODO show some error
                            }
                        });
                    }
                });
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (dlgCallback != null) dlgCallback.onDialogClosed(dlg);
            }
        });
        dlg.show();
    }

    public static void showAccountDialog(final Context ctx, final DialogCallback dlgCallback) {
        final Config cfg = Config.getInstance(ctx);

        final AlertDialog dlg = new AlertDialog.Builder(ctx)
                .setTitle("OTA Update Center Account")
                .setMessage("Logged in as: " + cfg.getUsername())
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialogInterface, int i) { }
                })
                .setNegativeButton("Sign Out", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        APIUtils.userLogout(ctx, new APIUtils.APIAdapter() {
                            @Override
                            public void onSuccess(String message, JSONObject respObj) {
                                cfg.clearLogin();
                            }
                        });
                    }
                })
                .create();

        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                if (dlgCallback != null) dlgCallback.onDialogShown(dlg);
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (dlgCallback != null) dlgCallback.onDialogClosed(dlg);
            }
        });
        dlg.show();
    }

    public static String userHmac(Context ctx, String str) {
        Config cfg = Config.getInstance(ctx);
        if (!cfg.isUserLoggedIn()) return null;
        return Utils.hmac(str, cfg.getHmacKey());
    }

    public static interface LoginCallback {
        void onLoggedIn(String username);
        void onCancel();
        void onError(String error);
    }
}
