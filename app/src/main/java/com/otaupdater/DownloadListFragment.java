package com.otaupdater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.otaupdater.utils.Config;
import com.otaupdater.utils.DialogCallback;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;

public class DownloadListFragment extends ListFragment {
    private DownloadsActivity downloadsAct;

    private final ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
    private ArrayAdapter<FileInfo> fileAdapter = null;

    private int state = 0;

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
        ((TextView) getListView().getEmptyView()).setText(
                getResources().getStringArray(R.array.download_types_empty)[state]);
        updateFileList();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        downloadsAct = (DownloadsActivity) activity;
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.download_list, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateFileList();
    }

    @Override
    public void onDestroy() {
        if (fileAdapter != null) {
            fileList.clear();
            fileAdapter = null;
        }
        super.onDestroy();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final FileInfo info = fileList.get(position);
        final Dialog dlg = new AlertDialog.Builder(getActivity())
                .setTitle(info.toString())
                .setIcon(R.drawable.ic_archive)
                .setItems(new String[]{
                        getString(R.string.delete)
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                        case 0:
                            if (info.file.delete()) {
                                Toast.makeText(getActivity(), R.string.toast_delete, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getActivity(), R.string.toast_delete_error, Toast.LENGTH_SHORT).show();
                            }
                            updateFileList();
                            break;
                        }
                    }
                })
                .create();

        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                if (getActivity() instanceof DialogCallback) ((DialogCallback) getActivity()).onDialogShown(dlg);
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (getActivity() instanceof DialogCallback) ((DialogCallback) getActivity()).onDialogClosed(dlg);
            }
        });
        dlg.show();
    }

    protected void updateFileList() {
        File dir = state == 0 ? Config.ROM_DL_PATH_FILE : Config.KERNEL_DL_PATH_FILE;
        File[] files = dir.listFiles();
        fileList.clear();
        for (File file : files) {
            if (file.isDirectory()) continue;
            fileList.add(new FileInfo(file));
        }

        if (fileAdapter == null) {
            fileAdapter = new ArrayAdapter<FileInfo>(getActivity(), R.layout.download_file, fileList);
            setListAdapter(fileAdapter);
        } else {
            fileAdapter.notifyDataSetChanged();
        }
    }

    protected class FileInfo {
        private File file;
        private String name;
        private String version = null;

        public FileInfo(File file) {
            this.file = file;

            name = file.getName();
            if (!name.endsWith(".zip")) return;

            name = name.substring(0, name.length() - 4);
            if (!name.contains("__")) return;

            int split = name.indexOf("__");
            version = name.substring(split + 2);

            name = name.substring(0, split);
            name = name.replace('_', ' ');
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        @Override
        public String toString() {
            if (version == null) {
                return getString(R.string.downloads_file_nover, name);
            } else {
                return getString(R.string.downloads_file, name, version);
            }
        }
    }
}
